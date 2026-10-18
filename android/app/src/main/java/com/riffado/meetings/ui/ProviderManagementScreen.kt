package com.riffado.meetings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R
import com.riffado.meetings.data.TranscriptionProviderConfig
import com.riffado.meetings.data.TranscriptionSettings
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProviderManagementScreen(
    settings: TranscriptionSettings,
    onBack: () -> Unit,
    onSave: (TranscriptionSettings) -> Unit,
) {
    val c = Apple.colors
    var providers by remember { mutableStateOf(settings.providers) }
    val order by remember { mutableStateOf(settings.providerOrder) }
    var editingProvider by remember { mutableStateOf<String?>(null) }
    val haptics = rememberHaptics()
    val scroll = rememberScrollState()
    val collapsePx = with(LocalDensity.current) { 48.dp.toPx() }
    val haze = rememberHazeState()
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight
    val title = stringResource(R.string.providers_title)

    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().hazeSource(haze).background(c.background).verticalScroll(scroll)
                    .padding(top = topPad, bottom = 48.dp),
            ) {
                LargeTitle(title)
                Spacer(Modifier.height(8.dp))
                InsetGroup(header = stringResource(R.string.providers_order), footer = stringResource(R.string.providers_help)) {
                    order.forEachIndexed { index, providerId ->
                        val provider = providers.find { it.id == providerId } ?: return@forEachIndexed
                        row {
                            ProviderRow(index + 1, provider, onEdit = { editingProvider = providerId }) { enabled ->
                                providers = providers.map { if (it.id == providerId) it.copy(enabled = enabled) else it }
                            }
                        }
                    }
                }
            }
            GlassTopBar(
                title = title,
                collapsed = (scroll.value / collapsePx).coerceIn(0f, 1f),
                leading = { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack, size = 40.dp, iconSize = 20.dp) },
                trailing = {
                    CapsuleButton(stringResource(R.string.action_save), {
                        haptics.confirm()
                        onSave(TranscriptionSettings(providers, order))
                        onBack()
                    }, style = CapsuleStyle.Filled)
                },
            )
        }
    }

    editingProvider?.let { id ->
        val provider = providers.find { it.id == id } ?: return@let
        ProviderEditDialog(
            provider = provider,
            onDismiss = { editingProvider = null },
            onSave = { updated ->
                providers = providers.map { if (it.id == id) updated else it }
                editingProvider = null
            },
        )
    }
}

@Composable
private fun ProviderRow(
    position: Int,
    provider: TranscriptionProviderConfig,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val c = Apple.colors
    val (status, color) = when {
        provider.apiKey.isEmpty() -> stringResource(R.string.providers_no_key) to c.red
        provider.isExhausted -> stringResource(
            R.string.providers_exhausted_until,
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(provider.exhaustedUntil!!)),
        ) to c.orange
        provider.isAvailable -> stringResource(R.string.providers_active) to c.green
        else -> stringResource(R.string.providers_off) to c.secondaryLabel
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(avatarColor(provider.id)),
            contentAlignment = Alignment.Center,
        ) { Text("$position", color = Color.White, style = AppleType.headline) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(provider.name, style = AppleType.body, color = c.label)
            Text(provider.model, style = AppleType.footnote, color = c.secondaryLabel, maxLines = 1)
            StatusPill(status, color)
        }
        IosSwitch(provider.enabled, onToggle)
    }
}

@Composable
private fun ProviderEditDialog(
    provider: TranscriptionProviderConfig,
    onDismiss: () -> Unit,
    onSave: (TranscriptionProviderConfig) -> Unit,
) {
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var model by remember { mutableStateOf(provider.model) }
    var resetDay by remember { mutableStateOf(provider.billingResetDay.toString()) }
    
    AlertDialog(
        shape = RoundedCornerShape(28.dp),
        onDismissRequest = onDismiss,
        title = { Text("Edit ${provider.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = resetDay,
                    onValueChange = { resetDay = it },
                    label = { Text("Billing Reset Day (1-28)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Day of month when quota resets (e.g., 1 for first of month)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val day = resetDay.toIntOrNull()?.coerceIn(1, 28) ?: provider.billingResetDay
                onSave(provider.copy(
                    apiKey = apiKey.trim(),
                    model = model.trim(),
                    billingResetDay = day,
                ))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
