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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R
import com.riffado.meetings.export.ExportKind

/** Glass bottom sheet with the export / share destinations for one recording. */
@Composable
fun ExportSheet(
    title: String,
    busy: ExportKind?,
    notionConfigured: Boolean,
    onExport: (ExportKind) -> Unit,
    onNotionSetup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Apple.colors
    val haptics = rememberHaptics()
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(10.dp).fillMaxWidth()
                .glass(RoundedCornerShape(32.dp), elevation = 24.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(top = 10.dp, bottom = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).clip(CircleShape).background(c.tertiaryLabel.copy(alpha = 0.5f)))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.IosShare, null, tint = c.tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.export_title), style = AppleType.headline, color = c.label)
                    Text(title, style = AppleType.footnote.auto(title), color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(InsetShape).background(c.cell)) {
                val rows = listOf(
                    Triple(ExportKind.PDF, Icons.Filled.PictureAsPdf, c.red),
                    Triple(ExportKind.MARKDOWN, Icons.AutoMirrored.Filled.Notes, c.indigo),
                    Triple(ExportKind.WHATSAPP, Icons.AutoMirrored.Filled.Send, Color(0xFF25D366)),
                    Triple(ExportKind.NOTION, Icons.Filled.ViewQuilt, Color(0xFF37352F)),
                )
                rows.forEachIndexed { i, (kind, icon, color) ->
                    val (label, sub) = when (kind) {
                        ExportKind.PDF -> R.string.export_pdf to R.string.export_pdf_sub
                        ExportKind.MARKDOWN -> R.string.export_markdown to R.string.export_markdown_sub
                        ExportKind.WHATSAPP -> R.string.export_whatsapp to R.string.export_whatsapp_sub
                        ExportKind.NOTION -> R.string.export_notion to if (notionConfigured) R.string.export_notion_sub else R.string.export_notion_setup
                    }
                    ExportRow(
                        icon, color, stringResource(label), stringResource(sub),
                        busy = busy == kind, enabled = busy == null,
                        warn = kind == ExportKind.NOTION && !notionConfigured,
                    ) {
                        haptics.tap()
                        if (kind == ExportKind.NOTION && !notionConfigured) onNotionSetup() else onExport(kind)
                    }
                    if (i < rows.lastIndex) InsetDivider(startIndent = 60.dp)
                }
            }
        }
    }
}

@Composable
private fun ExportRow(icon: ImageVector, color: Color, label: String, sub: String, busy: Boolean, enabled: Boolean, warn: Boolean, onClick: () -> Unit) {
    val c = Apple.colors
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = AppleType.body.copy(fontWeight = FontWeight.Medium), color = if (enabled || busy) c.label else c.secondaryLabel)
            Text(sub, style = AppleType.footnote, color = if (warn) c.orange else c.secondaryLabel, maxLines = 2)
        }
        if (busy) {
            // Indeterminate spinners never go idle, which hangs Robolectric screenshot capture.
            if (LocalStaticAnimations.current) CircularProgressIndicator(progress = { 0.7f }, modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = c.tint)
            else CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.tint)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.tertiaryLabel, modifier = Modifier.size(20.dp))
        }
    }
}
