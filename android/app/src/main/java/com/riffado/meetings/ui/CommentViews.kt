package com.riffado.meetings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R

/** Inline glass note under a transcript line. */
@Composable
fun NoteBubble(text: String, onClick: (() -> Unit)?) {
    val c = Apple.colors
    Row(
        Modifier.fillMaxWidth()
            .glassCard(RoundedCornerShape(14.dp))
            .background(c.orange.copy(alpha = 0.08f))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.AutoMirrored.Filled.StickyNote2, null, tint = c.orange, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = AppleType.subheadline.auto(text), color = c.label, modifier = Modifier.weight(1f))
    }
}

/** Bottom glass sheet to write / edit / delete a note on a transcript line. */
@Composable
fun CommentEditor(
    speaker: String?,
    quote: String,
    initial: String,
    canDelete: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Apple.colors
    var value by rememberSaveable(quote) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val static = LocalStaticAnimations.current
    LaunchedEffect(Unit) { if (!static) runCatching { focus.requestFocus() } }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).imePadding().windowInsetsPadding(WindowInsets.navigationBars)
                .padding(10.dp).fillMaxWidth()
                .glass(RoundedCornerShape(30.dp), elevation = 24.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.StickyNote2, null, tint = c.orange, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (canDelete) R.string.comments_edit else R.string.comments_add), style = AppleType.headline, color = c.label)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.fill).padding(10.dp)) {
                Box(Modifier.width(3.dp).height(36.dp).clip(CircleShape).background(c.orange))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    speaker?.let { Text(it, style = AppleType.caption.copy(fontWeight = FontWeight.SemiBold).auto(it), color = c.secondaryLabel, modifier = Modifier.fillMaxWidth()) }
                    Text(quote, style = AppleType.footnote.auto(quote), color = c.secondaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(RoundedCornerShape(16.dp)).background(c.cell).padding(12.dp)) {
                if (value.isEmpty()) Text(stringResource(R.string.comments_placeholder), style = AppleType.body, color = c.tertiaryLabel)
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    textStyle = AppleType.body.copy(color = c.label, textDirection = TextDirection.Content),
                    cursorBrush = SolidColor(c.tint),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canDelete) CapsuleButton(stringResource(R.string.action_delete), onDelete, icon = Icons.Filled.Delete, style = CapsuleStyle.Destructive)
                Spacer(Modifier.weight(1f))
                CapsuleButton(stringResource(R.string.action_cancel), onDismiss, style = CapsuleStyle.Glass)
                CapsuleButton(stringResource(R.string.action_save), { onSave(value) }, style = CapsuleStyle.Filled, enabled = value.isNotBlank() || canDelete)
            }
        }
    }
}
