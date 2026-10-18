package com.riffado.meetings.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R
import com.riffado.meetings.ai.AskAi
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Ask AI chat. [scopeTitle] is null for the global chat (all recordings) or the recording title
 * when the chat is limited to one recording.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskAiScreen(
    conversation: Conversation,
    scopeTitle: String?,
    onAsk: (String) -> Unit,
    personName: String? = null,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onOpenRecording: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = Apple.colors
    val haze = rememberHazeState()
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    val haptics = rememberHaptics()
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight
    val title = stringResource(R.string.ask_title)
    val send = {
        if (input.isNotBlank() && !conversation.busy) {
            haptics.tap(); onAsk(input); input = ""
        }
    }
    val itemCount = conversation.entries.size + 2
    LaunchedEffect(conversation.entries.size, conversation.busy) {
        if (conversation.entries.isNotEmpty()) listState.animateScrollToItem(itemCount - 1)
    }

    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                AmbientBackdrop(accent = c.purple)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topPad, bottom = 120.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "header") { AskHeader(scopeTitle, personName, conversation.entries.isEmpty(), onAsk) }
                    itemsIndexed(conversation.entries) { i, entry ->
                        val isLast = i == conversation.entries.lastIndex
                        if (entry.fromUser) UserBubble(entry.text)
                        else AssistantBubble(entry, onOpenRecording, onRetry = if (entry.isError && isLast) onRetry else null)
                    }
                    item(key = "typing") { if (conversation.busy) ThinkingBubble() }
                }
            }

            GlassTopBar(
                title = title,
                collapsed = 1f,
                leading = { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack, size = 40.dp, iconSize = 20.dp) },
                trailing = {
                    GlassIconButton(
                        Icons.Filled.DeleteSweep, stringResource(R.string.ask_clear), onClear, size = 40.dp, iconSize = 20.dp,
                        enabled = conversation.entries.isNotEmpty() && !conversation.busy,
                    )
                },
            )

            // Composer
            Row(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth()
                    .glass(RoundedCornerShape(26.dp), elevation = 16.dp)
                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.weight(1f).heightIn(min = 40.dp).padding(vertical = 9.dp)) {
                    if (input.isEmpty()) {
                        Text(
                            when {
                                personName != null -> stringResource(R.string.ask_hint_person, personName)
                                scopeTitle != null -> stringResource(R.string.ask_hint_recording)
                                else -> stringResource(R.string.ask_hint_global)
                            },
                            style = AppleType.body, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = AppleType.body.copy(color = c.label, textDirection = TextDirection.Content),
                        cursorBrush = SolidColor(c.tint),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(6.dp))
                val enabled = input.isNotBlank() && !conversation.busy
                val source = remember { MutableInteractionSource() }
                Box(
                    Modifier.size(40.dp).pressScale(source, 0.88f).clip(CircleShape)
                        .background(if (enabled) Brush.verticalGradient(listOf(c.tint.copy(alpha = 0.85f), c.tint)) else SolidColor(c.fill))
                        .clickable(source, indication = null, enabled = enabled, role = Role.Button) { send() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.ArrowUpward, stringResource(R.string.ask_send), tint = if (enabled) Color.White else c.tertiaryLabel, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskHeader(scopeTitle: String?, personName: String?, empty: Boolean, onAsk: (String) -> Unit) {
    val c = Apple.colors
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.tint, c.purple, c.pink)))
                .specularEdge(CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.size(12.dp))
        val subtitle = when {
            personName != null -> stringResource(R.string.ask_scope_person, personName)
            scopeTitle != null -> stringResource(R.string.ask_scope_recording, scopeTitle)
            else -> stringResource(R.string.ask_scope_global)
        }
        Text(subtitle, style = AppleType.subheadline, color = c.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        if (empty) {
            Spacer(Modifier.size(18.dp))
            val suggestions = when {
                personName != null -> listOf(R.string.ask_suggest_person_1, R.string.ask_suggest_person_2, R.string.ask_suggest_person_3)
                scopeTitle != null -> listOf(R.string.ask_suggest_rec_1, R.string.ask_suggest_rec_2, R.string.ask_suggest_rec_3)
                else -> listOf(R.string.ask_suggest_1, R.string.ask_suggest_2, R.string.ask_suggest_3, R.string.ask_suggest_4)
            }.map { stringResource(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s -> CapsuleButton(s, { onAsk(s) }, style = CapsuleStyle.Glass) }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val c = Apple.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = AppleType.body.auto(text),
            color = Color.White,
            modifier = Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(Brush.verticalGradient(listOf(c.tint.copy(alpha = 0.88f), c.tint)))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantBubble(entry: ChatEntry, onOpenRecording: (String) -> Unit, onRetry: (() -> Unit)?) {
    val c = Apple.colors
    Column(
        Modifier.fillMaxWidth(0.94f)
            .glassCard(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
            .then(if (entry.isError) Modifier.background(c.red.copy(alpha = 0.08f)) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (entry.isError) {
            Text(entry.text, style = AppleType.subheadline.auto(entry.text), color = c.red)
            if (onRetry != null) CapsuleButton(stringResource(R.string.action_retry), onRetry, icon = Icons.Filled.Refresh, style = CapsuleStyle.Destructive)
        } else {
            SimpleMarkdown(entry.text)
            if (entry.sources.isNotEmpty()) {
                Text(stringResource(R.string.ask_sources), style = AppleType.caption.copy(fontWeight = FontWeight.SemiBold), color = c.secondaryLabel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.sources.forEach { SourceChip(it, onOpenRecording) }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(source: AskAi.Source, onOpen: (String) -> Unit) {
    val c = Apple.colors
    val haptics = rememberHaptics()
    Row(
        Modifier.clip(CircleShape).background(c.tint.copy(alpha = 0.12f))
            .clickable(role = Role.Button) { haptics.tap(); onOpen(source.recordingId) }
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(c.tint), contentAlignment = Alignment.Center) {
            Text("${source.number}", color = Color.White, style = AppleType.caption.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.Article, null, tint = c.tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(source.title, style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold).auto(source.title), color = c.tint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        Text(" · " + formatDate(source.createdAt).substringBefore(','), style = AppleType.caption, color = c.secondaryLabel, maxLines = 1)
    }
}

@Composable
private fun ThinkingBubble() {
    val c = Apple.colors
    Row(
        Modifier.glassCard(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val static = LocalStaticAnimations.current
        repeat(3) { i ->
            val alpha = if (static) 0.6f else {
                val t = rememberInfiniteTransition(label = "dot$i")
                t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse), label = "a$i").value
            }
            Box(Modifier.size(8.dp).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(c.secondaryLabel))
        }
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.ask_thinking), style = AppleType.footnote, color = c.secondaryLabel)
    }
}
