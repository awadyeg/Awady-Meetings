package com.riffado.meetings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.RecordingStatus
import com.riffado.meetings.recording.LiveRecording
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 0 → large title fully visible, 1 → scrolled past (inline title + glass bar). */
@Composable
fun LazyListState.collapseFraction(thresholdDp: Int = 48): Float {
    val px = with(LocalDensity.current) { thresholdDp.dp.toPx() }
    val f by remember(this, px) {
        derivedStateOf { if (firstVisibleItemIndex > 0) 1f else (firstVisibleItemScrollOffset / px).coerceIn(0f, 1f) }
    }
    return f
}

@Composable
fun HomeScreen(
    recordings: List<Recording>,
    live: LiveRecording,
    configured: Boolean,
    onImport: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    focusSearch: Int = 0,
    onAsk: () -> Unit = {},
    bottomInset: androidx.compose.ui.unit.Dp = 140.dp,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 meetings, 2 calls
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPerson by rememberSaveable { mutableStateOf<String?>(null) }
    val people = remember(recordings) { RecordingSearch.extractPeople(recordings) }

    val filtered = remember(recordings, filter, selectedPerson, searchQuery) {
        recordings
            .let { list -> when (filter) { 1 -> list.filter { !it.isCall }; 2 -> list.filter { it.isCall }; else -> list } }
            .let { list -> selectedPerson?.let { RecordingSearch.filterByPerson(list, it) } ?: list }
            .let { list -> if (searchQuery.isBlank()) list else RecordingSearch.search(list, searchQuery) }
    }
    val groups = remember(filtered) { HomeLogic.group(filtered, System.currentTimeMillis()) }

    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) {
        if (focusSearch > 0) {
            listState.animateScrollToItem(0)
            runCatching { focus.requestFocus() }
        }
    }

    val haze = rememberHazeState()
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight
    val title = stringResource(R.string.tab_home)
    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                AmbientBackdrop()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topPad, bottom = bottomInset),
                ) {
                    item(key = "title") { LargeTitle(title) }
                    item(key = "search") {
                        SearchField(
                            searchQuery, { searchQuery = it }, stringResource(R.string.search_hint),
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            clearDescription = stringResource(R.string.action_clear),
                            focusRequester = focus,
                        )
                    }
                    item(key = "ask") {
                        AskEntry(onAsk, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    item(key = "filter") {
                        SegmentedControl(
                            listOf(stringResource(R.string.filter_all), stringResource(R.string.filter_meetings), stringResource(R.string.filter_calls)),
                            filter, { filter = it },
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    if (people.isNotEmpty()) {
                        item(key = "people") { PeopleRow(people, selectedPerson) { selectedPerson = it } }
                    }
                    if (!configured) {
                        item(key = "setup") { SetupCard(onSettings) }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "empty") { EmptyState(searching = searchQuery.isNotEmpty() || selectedPerson != null || filter != 0, onImport) }
                    }
                    groups.forEach { group ->
                        item(key = "h-${group.section}") {
                            Text(
                                sectionTitle(group.section),
                                style = AppleType.title3,
                                color = Apple.colors.label,
                                modifier = Modifier.animateItem().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                            )
                        }
                        items(group.items, key = { it.id }) { rec ->
                            RecordingCard(
                                rec, live, group.section,
                                onClick = { onOpen(rec.id) },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
            GlassTopBar(
                title = title,
                collapsed = listState.collapseFraction(),
                trailing = {
                    GlassIconButton(Icons.Filled.AutoAwesome, stringResource(R.string.ask_title), onAsk, size = 40.dp, iconSize = 20.dp, tint = Apple.colors.purple)
                    GlassIconButton(Icons.Filled.FileDownload, stringResource(R.string.action_import), onImport, size = 40.dp, iconSize = 20.dp, tint = Apple.colors.tint)
                },
            )
        }
    }
}

@Composable
private fun sectionTitle(section: HomeLogic.Section): String = stringResource(
    when (section) {
        HomeLogic.Section.TODAY -> R.string.section_today
        HomeLogic.Section.YESTERDAY -> R.string.section_yesterday
        HomeLogic.Section.THIS_WEEK -> R.string.section_this_week
        HomeLogic.Section.OLDER -> R.string.section_older
    },
)

private fun timeLabel(ms: Long, section: HomeLogic.Section): String = when (section) {
    HomeLogic.Section.TODAY, HomeLogic.Section.YESTERDAY -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
    HomeLogic.Section.THIS_WEEK -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ms))
    HomeLogic.Section.OLDER -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
}

@Composable
private fun RecordingCard(rec: Recording, live: LiveRecording, section: HomeLogic.Section, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Apple.colors
    val isLive = rec.id == live.recordingId
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(22.dp)
    val snippet = remember(rec.summary) { HomeLogic.summarySnippet(rec.summary) }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(source, 0.97f)
            .glassCard(shape)
            .clickable(source, indication = null, enabled = !isLive, role = Role.Button) { haptics.tap(); onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(name = if (rec.isCall) rec.callContactName else null, isCall = rec.isCall)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rec.displayName,
                    style = AppleType.headline.auto(rec.displayName),
                    color = c.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(timeLabel(rec.createdAt, section), style = AppleType.footnote, color = c.secondaryLabel, maxLines = 1)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (rec.isCall) Icons.Filled.Call else Icons.Filled.Groups,
                    contentDescription = stringResource(if (rec.isCall) R.string.filter_calls else R.string.filter_meetings),
                    tint = if (rec.isCall) c.green else c.tint,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    formatDuration(if (isLive) live.elapsedMs else rec.durationMs),
                    style = AppleType.footnote.copy(fontFeatureSettings = "tnum"),
                    color = c.secondaryLabel,
                )
                if (rec.isCall && rec.callContactName != null && rec.callNumber != null) {
                    Text(" · " + ltrIsolate(rec.callNumber), style = AppleType.footnote, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (isLive || rec.status != RecordingStatus.DONE) {
                    Text(" · ", style = AppleType.footnote, color = c.secondaryLabel)
                    Text(
                        statusLabel(rec.status),
                        style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = when {
                            isLive -> c.red
                            rec.status == RecordingStatus.FAILED || rec.status == RecordingStatus.INTERRUPTED -> c.red
                            rec.status.isBusy -> c.orange
                            else -> c.secondaryLabel
                        },
                        maxLines = 1,
                    )
                }
                if (rec.status.isBusy) {
                    Spacer(Modifier.width(6.dp))
                    if (LocalStaticAnimations.current) CircularProgressIndicator(progress = { 0.7f }, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = c.orange)
                    else CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = c.orange)
                }
            }
            if (snippet != null) {
                Spacer(Modifier.height(6.dp))
                Text(snippet, style = AppleType.subheadline.auto(snippet), color = c.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PeopleRow(people: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    val c = Apple.colors
    val haptics = rememberHaptics()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "__all") {
            PersonBubble(stringResource(R.string.filter_everyone), selected == null, onClick = { haptics.selection(); onSelect(null) }) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(c.fill), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Groups, null, tint = c.tint, modifier = Modifier.size(26.dp))
                }
            }
        }
        items(people, key = { it }) { name ->
            PersonBubble(name, selected == name, onClick = { haptics.selection(); onSelect(if (selected == name) null else name) }) {
                Avatar(name, isCall = true, size = 52.dp)
            }
        }
    }
}

@Composable
private fun PersonBubble(label: String, selected: Boolean, onClick: () -> Unit, avatar: @Composable () -> Unit) {
    val c = Apple.colors
    Column(
        Modifier.width(64.dp).clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(58.dp).then(if (selected) Modifier.border(2.dp, c.tint, CircleShape) else Modifier).padding(3.dp),
            contentAlignment = Alignment.Center,
        ) { avatar() }
        Spacer(Modifier.height(4.dp))
        Text(
            label, style = AppleType.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) c.tint else c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SetupCard(onSettings: () -> Unit) {
    val c = Apple.colors
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().glassCard(RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Filled.Key, c.orange)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.home_not_configured), style = AppleType.subheadline, color = c.label, modifier = Modifier.weight(1f))
        }
        CapsuleButton(stringResource(R.string.action_open_settings), onSettings, style = CapsuleStyle.Filled)
    }
}

@Composable
private fun EmptyState(searching: Boolean, onImport: () -> Unit) {
    val c = Apple.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(84.dp).glass(CircleShape, elevation = 8.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.GraphicEq, null, tint = c.tint, modifier = Modifier.size(40.dp))
        }
        Text(
            stringResource(if (searching) R.string.search_no_results else R.string.home_empty_title),
            style = AppleType.title3, color = c.label, textAlign = TextAlign.Center,
        )
        if (!searching) {
            Text(stringResource(R.string.home_empty), style = AppleType.subheadline, color = c.secondaryLabel, textAlign = TextAlign.Center)
            CapsuleButton(stringResource(R.string.action_import), onImport, icon = Icons.Filled.FileDownload)
        }
    }
}

/** "✨ Ask AI about your recordings" glass row. */
@Composable
fun AskEntry(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = stringResource(R.string.ask_entry)) {
    val c = Apple.colors
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Row(
        modifier.fillMaxWidth().height(44.dp).pressScale(source, 0.97f)
            .glass(RoundedCornerShape(22.dp), elevation = 4.dp)
            .clickable(source, indication = null, role = Role.Button) { haptics.tap(); onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = c.purple, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = AppleType.body, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.tertiaryLabel, modifier = Modifier.size(20.dp))
    }
}
