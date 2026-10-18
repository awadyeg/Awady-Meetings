package com.riffado.meetings.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R
import com.riffado.meetings.data.TaskItem
import com.riffado.meetings.data.TaskLogic
import com.riffado.meetings.recording.LiveRecording
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Person page: avatar, phone, call / meeting counts and total time, open tasks,
 * every recording with this person, and Ask AI scoped to their recordings.
 */
@Composable
fun PersonScreen(
    person: PersonSummary,
    tasks: List<TaskItem>,
    live: LiveRecording,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onAsk: () -> Unit,
    onToggleTask: (TaskItem, Boolean) -> Unit,
    onAddToCalendar: (TaskItem) -> Unit,
    now: Long = System.currentTimeMillis(),
    bottomInset: Dp = 40.dp,
) {
    val c = Apple.colors
    val haze = rememberHazeState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight
    val byId = person.recordings.associateBy { it.id }
    val canAsk = person.recordings.any { !it.summary.isNullOrBlank() || !it.transcript.isNullOrBlank() }

    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                AmbientBackdrop(accent = avatarColor(person.name))
                LazyColumn(state = listState, contentPadding = PaddingValues(top = topPad, bottom = bottomInset), modifier = Modifier.fillMaxSize()) {
                    item(key = "header") {
                        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(person.name, isCall = true, size = 96.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                person.name, style = AppleType.title1.copy(fontWeight = FontWeight.Bold).auto(person.name), color = c.label,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            person.phone?.let { phone ->
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.clip(CircleShape).glass(CircleShape, elevation = 2.dp)
                                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.person_call)) {
                                            haptics.tap()
                                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)))) }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Call, null, tint = c.green, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(ltrIsolate(phone), style = AppleType.subheadline.copy(fontFeatureSettings = "tnum"), color = c.label)
                                }
                            }
                        }
                    }
                    item(key = "stats") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(Icons.Filled.Call, c.green, "%d".format(person.calls), pluralStringResource(R.plurals.person_calls, person.calls), Modifier.weight(1f))
                            StatTile(Icons.Filled.Groups, c.tint, "%d".format(person.meetings), pluralStringResource(R.plurals.person_meetings, person.meetings), Modifier.weight(1f))
                            StatTile(Icons.Filled.Schedule, c.orange, PersonLogic.formatTotal(
                                person.totalMs, stringResource(R.string.unit_hour_short), stringResource(R.string.unit_minute_short), stringResource(R.string.unit_second_short),
                            ), stringResource(R.string.person_total_time), Modifier.weight(1f))
                        }
                    }
                    if (canAsk) {
                        item(key = "ask") {
                            AskEntry(onAsk, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), label = stringResource(R.string.person_ask, person.name))
                        }
                    }
                    if (tasks.isNotEmpty()) {
                        item(key = "tasks-h") { Section(stringResource(R.string.person_open_tasks_n, tasks.size)) }
                        item(key = "tasks") {
                            InsetGroup {
                                tasks.forEach { task ->
                                    row {
                                        TaskRow(
                                            task = task,
                                            source = byId[task.recordingId],
                                            overdue = TaskLogic.isOverdue(task, now),
                                            dueToday = TaskLogic.bucketOf(task, now) == TaskLogic.Bucket.TODAY,
                                            onToggle = { onToggleTask(task, !task.done) },
                                            onOpen = { onOpen(task.recordingId) },
                                            onCalendar = { onAddToCalendar(task) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "recs-h") { Section(stringResource(R.string.person_recordings_n, person.recordings.size)) }
                    if (person.recordings.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.person_no_recordings), style = AppleType.subheadline, color = c.secondaryLabel,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                    items(person.recordings, key = { it.id }) { rec ->
                        RecordingCard(
                            rec, live, HomeLogic.Section.OLDER,
                            onClick = { onOpen(rec.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            GlassTopBar(
                title = person.name,
                collapsed = (listState.collapseFraction(thresholdDp = 120)),
                leading = {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack, size = 40.dp, iconSize = 20.dp)
                },
            )
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text, style = AppleType.title3.copy(fontWeight = FontWeight.Bold).auto(text), color = Apple.colors.label,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun StatTile(icon: ImageVector, color: Color, value: String, label: String, modifier: Modifier = Modifier) {
    val c = Apple.colors
    Column(
        modifier.glassCard(RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(value, style = AppleType.title2.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum").auto(value), color = c.label, maxLines = 1)
        Text(label, style = AppleType.footnote, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
