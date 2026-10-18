package com.riffado.meetings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.TaskItem
import com.riffado.meetings.data.TaskLogic
import com.riffado.meetings.data.TaskLogic.Bucket
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.Date
import java.util.Locale

/** Progress of a bulk extraction run, mirrored from the view model. */
data class TaskExtractionUi(val done: Int, val total: Int)

/**
 * Tasks tab: action items and deadlines extracted from recordings, Open / Done, grouped by due date,
 * with links to the source recording and "Add to calendar".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(
    tasks: List<TaskItem>,
    recordings: List<Recording>,
    onOpen: (String) -> Unit,
    onToggle: (TaskItem, Boolean) -> Unit,
    onAddToCalendar: (TaskItem) -> Unit,
    onAddAllToCalendar: (List<TaskItem>) -> Unit,
    onExtractMissing: () -> Unit,
    onExtractAll: () -> Unit,
    missingCount: Int,
    extraction: TaskExtractionUi?,
    now: Long = System.currentTimeMillis(),
    bottomInset: Dp = 140.dp,
    initialSegment: Int = 0,
) {
    val c = Apple.colors
    val haze = rememberHazeState()
    val listState = rememberLazyListState()
    val title = stringResource(R.string.tab_tasks)
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight
    var segment by rememberSaveable { mutableIntStateOf(initialSegment) }
    var menu by remember { mutableStateOf(false) }
    val byId = remember(recordings) { recordings.associateBy { it.id } }
    val open = remember(tasks) { tasks.filter { !it.done && it.recordingId in byId } }
    val done = remember(tasks) { tasks.filter { it.done && it.recordingId in byId } }
    val shown = if (segment == 0) open else done
    val groups = remember(shown, now) { TaskLogic.group(shown, now) }
    val datedOpen = remember(open) { open.filter { it.dueAt != null } }

    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                AmbientBackdrop(accent = c.orange)
                LazyColumn(state = listState, contentPadding = PaddingValues(top = topPad, bottom = bottomInset), modifier = Modifier.fillMaxSize()) {
                    item(key = "title") { LargeTitle(title) }
                    item(key = "segments") {
                        SegmentedControl(
                            options = listOf(stringResource(R.string.tasks_open_n, open.size), stringResource(R.string.tasks_done_n, done.size)),
                            selected = segment,
                            onSelect = { segment = it },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    if (extraction != null) {
                        item(key = "progress") { ExtractionBanner(extraction) }
                    } else if (missingCount > 0) {
                        item(key = "missing") {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                                    .glassCard(RoundedCornerShape(18.dp))
                                    .clickable(role = Role.Button, onClick = onExtractMissing)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = c.purple, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    pluralStringResource(R.plurals.tasks_missing, missingCount, missingCount),
                                    style = AppleType.subheadline, color = c.label, modifier = Modifier.weight(1f),
                                )
                                Text(stringResource(R.string.tasks_extract), style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.tint)
                            }
                        }
                    }
                    if (segment == 0 && datedOpen.size > 1) {
                        item(key = "addall") {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                CapsuleButton(
                                    pluralStringResource(R.plurals.tasks_add_all, datedOpen.size, datedOpen.size),
                                    { onAddAllToCalendar(datedOpen) },
                                    icon = Icons.Filled.CalendarMonth,
                                    style = CapsuleStyle.Glass,
                                )
                            }
                        }
                    }
                    if (groups.isEmpty()) {
                        item(key = "empty") { EmptyTasks(segment == 0, missingCount, extraction != null, onExtractMissing) }
                    }
                    groups.forEach { (bucket, items) ->
                        item(key = "g-$segment-$bucket") {
                            Text(
                                bucketLabel(bucket, doneTab = segment == 1),
                                style = AppleType.title3.copy(fontWeight = FontWeight.Bold),
                                color = if (bucket == Bucket.OVERDUE && segment == 0) c.red else c.label,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                            )
                            InsetGroup {
                                items.forEach { task ->
                                    row {
                                        TaskRow(
                                            task = task,
                                            source = byId[task.recordingId],
                                            overdue = TaskLogic.isOverdue(task, now),
                                            dueToday = bucket == Bucket.TODAY,
                                            onToggle = { onToggle(task, !task.done) },
                                            onOpen = { onOpen(task.recordingId) },
                                            onCalendar = { onAddToCalendar(task) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            GlassTopBar(
                title = title,
                collapsed = listState.collapseFraction(),
                trailing = {
                    Box {
                        GlassIconButton(Icons.Filled.MoreHoriz, stringResource(R.string.action_more), { menu = true }, size = 40.dp, iconSize = 20.dp)
                        DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(18.dp), containerColor = c.cell) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tasks_add_all_menu)) },
                                leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
                                enabled = datedOpen.isNotEmpty(),
                                onClick = { menu = false; onAddAllToCalendar(datedOpen) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tasks_extract_missing)) },
                                leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) },
                                enabled = extraction == null && missingCount > 0,
                                onClick = { menu = false; onExtractMissing() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tasks_extract_all)) },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                enabled = extraction == null,
                                onClick = { menu = false; onExtractAll() },
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun bucketLabel(bucket: Bucket, doneTab: Boolean): String = stringResource(
    when (bucket) {
        Bucket.OVERDUE -> if (doneTab) R.string.tasks_earlier else R.string.tasks_overdue
        Bucket.TODAY -> R.string.tasks_today
        Bucket.TOMORROW -> R.string.tasks_tomorrow
        Bucket.THIS_WEEK -> R.string.tasks_this_week
        Bucket.LATER -> R.string.tasks_later
        Bucket.NO_DATE -> R.string.tasks_no_date
    },
)

/** "Thu, Oct 22" or "Thu, Oct 22, 3:30 PM" in the device locale. */
fun formatDue(task: TaskItem, locale: Locale = Locale.getDefault()): String? {
    val due = task.dueAt ?: return null
    val skeleton = if (task.allDay) "EEEMMMd" else "EEEMMMdjmm"
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    return java.text.SimpleDateFormat(pattern, locale).format(Date(due))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskRow(
    task: TaskItem,
    source: Recording?,
    overdue: Boolean,
    dueToday: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onCalendar: () -> Unit,
) {
    val c = Apple.colors
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
        CheckCircle(task.done) { if (!task.done) haptics.confirm() else haptics.tap(); onToggle() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                task.title,
                style = AppleType.body.copy(textDecoration = if (task.done) TextDecoration.LineThrough else null).auto(task.title),
                color = if (task.done) c.secondaryLabel else c.label,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                formatDue(task)?.let { label ->
                    val color = when {
                        overdue -> c.red
                        dueToday && !task.done -> c.orange
                        else -> c.secondaryLabel
                    }
                    MetaChip(Icons.Filled.Event, label, color, filled = overdue || (dueToday && !task.done))
                } ?: task.dueText?.let { MetaChip(Icons.Filled.Event, it, c.secondaryLabel) }
                task.assignee?.let { MetaChip(Icons.Filled.Person, it, c.indigo) }
                if (source != null) {
                    Row(
                        Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Article, null, tint = c.tint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            source.displayName, style = AppleType.footnote.auto(source.displayName), color = c.tint,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp),
                        )
                    }
                }
            }
        }
        if (!task.done) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onCalendar),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.EditCalendar, stringResource(R.string.tasks_add_calendar), tint = c.tint, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun MetaChip(icon: ImageVector, text: String, color: Color, filled: Boolean = false) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = if (filled) 0.16f else 0.10f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = AppleType.caption.copy(fontWeight = FontWeight.Medium).auto(text), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
    }
}

@Composable
private fun CheckCircle(checked: Boolean, onClick: () -> Unit) {
    val c = Apple.colors
    val fill by animateColorAsState(if (checked) c.green else Color.Transparent, label = "check")
    val scale by animateFloatAsState(if (checked) 1f else 0f, label = "checkScale")
    Box(
        Modifier.padding(top = 1.dp).size(24.dp).clip(CircleShape)
            .background(fill)
            .border(1.5.dp, if (checked) c.green else c.tertiaryLabel, CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (scale > 0f) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size((16 * scale).dp))
    }
}

@Composable
private fun ExtractionBanner(progress: TaskExtractionUi) {
    val c = Apple.colors
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .glassCard(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val static = LocalStaticAnimations.current
        val fraction = if (progress.total == 0) 0f else progress.done / progress.total.toFloat()
        if (static) CircularProgressIndicator(progress = { fraction.coerceAtLeast(0.1f) }, color = c.purple, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else CircularProgressIndicator(color = c.purple, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.tasks_extracting, progress.done, progress.total), style = AppleType.subheadline, color = c.label)
    }
}

@Composable
private fun EmptyTasks(openTab: Boolean, missingCount: Int, busy: Boolean, onExtract: () -> Unit) {
    val c = Apple.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(84.dp).glass(CircleShape, elevation = 8.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Checklist, null, tint = c.orange, modifier = Modifier.size(40.dp))
        }
        Text(
            stringResource(if (openTab) R.string.tasks_empty_open else R.string.tasks_empty_done),
            style = AppleType.headline, color = c.label, textAlign = TextAlign.Center,
        )
        if (openTab) {
            Text(stringResource(R.string.tasks_empty), style = AppleType.subheadline, color = c.secondaryLabel, textAlign = TextAlign.Center)
            if (missingCount > 0 && !busy) {
                Spacer(Modifier.height(4.dp))
                CapsuleButton(stringResource(R.string.tasks_extract_missing), onExtract, icon = Icons.Filled.AutoAwesome, style = CapsuleStyle.Filled)
            }
        }
    }
}
