package com.riffado.meetings.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riffado.meetings.R
import com.riffado.meetings.recording.LiveRecording
import kotlinx.coroutines.delay

/** Digits that don't jiggle while counting (tabular figures). */
private val Tabular = "tnum"

/** Set to true to freeze looping animations (screenshot tests, reduce-motion). */
val LocalStaticAnimations = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
private fun PulsingDot(color: Color, active: Boolean, size: Int = 10) {
    val base = Modifier.size(size.dp)
    if (active && !LocalStaticAnimations.current) {
        // Infinite transitions only exist while recording, so idle screens stay idle (battery, tests).
        val t = rememberInfiniteTransition(label = "pulse")
        val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulseAlpha")
        Box(base.graphicsLayer { alpha = a }.clip(CircleShape).background(color))
    } else {
        Box(base.clip(CircleShape).background(color))
    }
}

/** Full-screen recording view (Voice Memos style). */
@Composable
fun RecorderScreen(
    live: LiveRecording,
    title: String?,
    levels: List<Float>,
    bookmarks: List<Long>,
    onMinimize: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onBookmark: () -> Unit,
) {
    val c = Apple.colors
    val haptics = rememberHaptics()
    var flash by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(flash) { if (flash != null) { delay(1600); flash = null } }

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(accent = c.red)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Box(Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 12.dp)) {
                GlassIconButton(Icons.Filled.KeyboardArrowDown, stringResource(R.string.recorder_minimize), onMinimize, Modifier.align(Alignment.CenterStart))
                Row(
                    Modifier.align(Alignment.Center).glass(CircleShape, elevation = 4.dp).padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulsingDot(if (live.isPaused) c.orange else c.red, active = !live.isPaused)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (live.isPaused) R.string.recording_paused else R.string.recording_live),
                        style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = c.label,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (!title.isNullOrBlank()) {
                Text(title, style = AppleType.title3.auto(title), color = c.secondaryLabel, maxLines = 1, modifier = Modifier.padding(horizontal = 24.dp))
            }
            Spacer(Modifier.weight(0.6f))
            Text(
                formatDuration(live.elapsedMs),
                style = AppleType.timer.copy(fontFeatureSettings = Tabular),
                color = c.label,
                modifier = Modifier.semantics { contentDescription = formatDuration(live.elapsedMs) },
            )
            Spacer(Modifier.height(28.dp))
            LiveWaveform(
                levels = levels,
                modifier = Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 8.dp),
                color = if (live.isPaused) c.secondaryLabel else c.red,
                playhead = c.red,
            )
            Spacer(Modifier.height(18.dp))
            AnimatedContent(flash, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() }, label = "bm") { at ->
                if (at != null) {
                    Row(Modifier.glass(CircleShape, elevation = 2.dp).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bookmark, null, tint = c.orange, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.recorder_bookmarked, formatDuration(at)), style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                    }
                } else if (bookmarks.isNotEmpty()) {
                    Text(
                        pluralBookmarks(bookmarks.size),
                        style = AppleType.footnote,
                        color = c.secondaryLabel,
                    )
                } else Spacer(Modifier.height(30.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    Icons.Filled.BookmarkAdd, stringResource(R.string.recorder_bookmark),
                    onClick = { haptics.heavy(); flash = live.elapsedMs; onBookmark() },
                    size = 64.dp, iconSize = 28.dp, tint = c.orange, enabled = !live.isPaused,
                )
                StopButton(stringResource(R.string.action_stop), onClick = { haptics.confirm(); onStop() })
                GlassIconButton(
                    if (live.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    stringResource(if (live.isPaused) R.string.action_resume else R.string.action_pause),
                    onClick = if (live.isPaused) onResume else onPause,
                    size = 64.dp, iconSize = 30.dp,
                )
            }
            Spacer(Modifier.height(36.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun pluralBookmarks(n: Int): String = androidx.compose.ui.res.pluralStringResource(R.plurals.recorder_bookmarks, n, n)

@Composable
private fun StopButton(description: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val c = Apple.colors
    Box(
        Modifier.size(84.dp).pressScale(source, 0.9f)
            .border(4.dp, c.label.copy(alpha = 0.85f), CircleShape)
            .padding(8.dp)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp).shadow(6.dp, RoundedCornerShape(9.dp), spotColor = c.red).clip(RoundedCornerShape(9.dp))
                .background(Brush.verticalGradient(listOf(c.red.copy(alpha = 0.85f), c.red))),
        )
    }
}

/** Floating capsule shown while a recording runs and the full-screen recorder is not visible. */
@Composable
fun MiniRecordingPill(
    live: LiveRecording,
    levels: List<Float>,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Apple.colors
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(source, 0.97f)
            .glass(CircleShape, elevation = 14.dp)
            .clickable(source, indication = null, role = Role.Button) { haptics.tap(); onOpen() }
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(if (live.isPaused) c.orange else c.red, active = !live.isPaused)
        Spacer(Modifier.width(10.dp))
        Text(formatDuration(live.elapsedMs), style = AppleType.headline.copy(fontFeatureSettings = Tabular), color = c.label)
        Spacer(Modifier.width(10.dp))
        LiveWaveform(levels.takeLast(40), Modifier.width(72.dp).height(26.dp), color = if (live.isPaused) c.secondaryLabel else c.red, playhead = Color.Transparent)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(c.fill)
                .clickable { haptics.tap(); if (live.isPaused) onResume() else onPause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (live.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                stringResource(if (live.isPaused) R.string.action_resume else R.string.action_pause),
                tint = c.label, modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(c.red.copy(alpha = 0.16f)).clickable { haptics.confirm(); onStop() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Stop, stringResource(R.string.action_stop), tint = c.red, modifier = Modifier.size(20.dp)) }
    }
}

enum class AppTab { Home, Tasks, Search, Settings }

private data class TabItem(val tab: AppTab, val icon: ImageVector, val label: Int)

/**
 * Floating Liquid Glass tab bar: Home · Tasks · ⏺ · Search · Settings.
 * The selected tab sits on a sliding glass "lens"; the centre record button is raised and red.
 */
@Composable
fun GlassTabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    recording: Boolean,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Apple.colors
    val haptics = rememberHaptics()
    val items = listOf(
        TabItem(AppTab.Home, Icons.Filled.Home, R.string.tab_home),
        TabItem(AppTab.Tasks, Icons.Filled.Checklist, R.string.tab_tasks),
        null,
        TabItem(AppTab.Search, Icons.Filled.Search, R.string.tab_search),
        TabItem(AppTab.Settings, Icons.Filled.Settings, R.string.tab_settings),
    )
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.BottomCenter) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(64.dp).glass(CircleShape, elevation = 18.dp).padding(4.dp),
        ) {
            val slot = maxWidth / items.size
            val selIndex = items.indexOfFirst { it?.tab == selected }.coerceAtLeast(0)
            val lensX by animateDpAsState(slot * selIndex, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "lens")
            Box(
                Modifier.offset { IntOffset(lensX.roundToPx(), 0) }.width(slot).height(56.dp).clip(CircleShape)
                    .background(c.label.copy(alpha = if (c.isDark) 0.12f else 0.07f))
                    .specularEdge(CircleShape, 0.5f),
            )
            Row(Modifier.fillMaxWidth()) {
                items.forEach { item ->
                    if (item == null) {
                        Spacer(Modifier.width(slot))
                    } else {
                        val isSel = item.tab == selected
                        Column(
                            Modifier.width(slot).height(56.dp)
                                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                                    haptics.selection(); onSelect(item.tab)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(item.icon, null, tint = if (isSel) c.tint else c.label.copy(alpha = 0.75f), modifier = Modifier.size(24.dp))
                            Text(
                                stringResource(item.label),
                                style = AppleType.caption2.copy(fontSize = 10.sp, fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium),
                                color = if (isSel) c.tint else c.label.copy(alpha = 0.75f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        RecordButton(recording, onClick = { haptics.confirm(); onRecord() }, modifier = Modifier.offset(y = (-10).dp))
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Apple.colors
    val source = remember { MutableInteractionSource() }
    Box(modifier.size(78.dp), contentAlignment = Alignment.Center) {
        if (recording && !LocalStaticAnimations.current) {
            val t = rememberInfiniteTransition(label = "ring")
            val ring by t.animateFloat(1f, 1.18f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "ringScale")
            Box(Modifier.size(70.dp).graphicsLayer { scaleX = ring; scaleY = ring; alpha = 2f - ring * 1.4f }.clip(CircleShape).background(c.red.copy(alpha = 0.35f)))
        }
        Box(
            Modifier.size(66.dp).pressScale(source, 0.9f)
                .shadow(14.dp, CircleShape, ambientColor = c.red, spotColor = c.red)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(c.red.copy(alpha = 0.82f), c.red)))
                .specularEdge(CircleShape)
                .clickable(source, indication = null, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(!recording, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(Icons.Filled.Mic, stringResource(R.string.action_record), tint = Color.White, modifier = Modifier.size(30.dp))
            }
            AnimatedVisibility(recording, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                val openDesc = stringResource(R.string.recorder_open)
                Box(Modifier.size(22.dp).semantics { contentDescription = openDesc }.clip(RoundedCornerShape(6.dp)).background(Color.White))
            }
        }
    }
}
