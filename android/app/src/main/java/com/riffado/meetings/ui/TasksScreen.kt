package com.riffado.meetings.ui

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R
import com.riffado.meetings.data.Recording
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Phase 1 placeholder for the Tasks tab: read-only list of open action items (`- [ ]`) found in the
 * minutes. Checking off, due dates and calendar export come in a later phase.
 */
@Composable
fun TasksScreen(recordings: List<Recording>, onOpen: (String) -> Unit, bottomInset: Dp = 140.dp) {
    val c = Apple.colors
    val withTasks = remember(recordings) {
        recordings.map { it to HomeLogic.actionItems(it.summary) }.filter { it.second.isNotEmpty() }
    }
    val haze = rememberHazeState()
    val listState = rememberLazyListState()
    val title = stringResource(R.string.tab_tasks)
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight
    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                AmbientBackdrop(accent = c.orange)
                LazyColumn(state = listState, contentPadding = PaddingValues(top = topPad, bottom = bottomInset), modifier = Modifier.fillMaxSize()) {
                    item { LargeTitle(title) }
                    item {
                        Text(
                            stringResource(R.string.tasks_preview_note),
                            style = AppleType.footnote, color = c.secondaryLabel,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                    if (withTasks.isEmpty()) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(84.dp).glass(CircleShape, elevation = 8.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Checklist, null, tint = c.orange, modifier = Modifier.size(40.dp))
                                }
                                Text(stringResource(R.string.tasks_empty), style = AppleType.subheadline, color = c.secondaryLabel, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    items(withTasks, key = { it.first.id }) { (rec, tasks) ->
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                                .glassCard(RoundedCornerShape(22.dp))
                                .clickable { onOpen(rec.id) }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(if (rec.isCall) rec.callContactName else null, rec.isCall, size = 28.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(rec.displayName, style = AppleType.headline.auto(rec.displayName), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(formatDate(rec.createdAt), style = AppleType.caption, color = c.secondaryLabel)
                            }
                            tasks.forEach { task ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(Modifier.padding(top = 2.dp).size(20.dp).border(1.5.dp, c.tertiaryLabel, CircleShape))
                                    Spacer(Modifier.width(10.dp))
                                    Text(task, style = AppleType.body.auto(task), color = c.label, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            GlassTopBar(title = title, collapsed = listState.collapseFraction())
        }
    }
}
