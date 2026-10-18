package com.riffado.meetings.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.riffado.meetings.R
import com.riffado.meetings.data.AppSettings
import com.riffado.meetings.data.CallRecordingSettings
import com.riffado.meetings.data.ProviderConfig
import com.riffado.meetings.data.SettingsStore
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun String.isCustomTxHost() =
    contains("elevenlabs.io") || contains("sarvam.ai") || contains("munsit.com") || contains("faseeh.ai") || contains("generativelanguage.googleapis.com")

/**
 * Settings, as iOS grouped inset lists. Every setting of the previous screen is kept:
 * voice profile, presets, fallback chain, call recording, quick record help, reliability checklist,
 * diagnostics, transcription + summary endpoints, language and auto-processing.
 */
@Composable
fun SettingsScreen(
    initial: AppSettings,
    callRecording: CallRecordingSettings,
    onSave: (AppSettings) -> Unit,
    onSaveCallRecording: (CallRecordingSettings) -> Unit,
    onManageProviders: () -> Unit,
    onBack: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp = 140.dp,
    mcp: McpUiState? = null,
    mcpActions: McpActions? = null,
) {
    val context = LocalContext.current
    val c = Apple.colors
    var txUrl by remember { mutableStateOf(initial.transcription.baseUrl) }
    var txKey by remember { mutableStateOf(initial.transcription.apiKey) }
    var txModel by remember { mutableStateOf(initial.transcription.model) }
    var sumUrl by remember { mutableStateOf(initial.summary.baseUrl) }
    var sumKey by remember { mutableStateOf(initial.summary.apiKey) }
    var sumModel by remember { mutableStateOf(initial.summary.model) }
    var language by remember { mutableStateOf(initial.language) }
    var auto by remember { mutableStateOf(initial.autoProcess) }
    var notionToken by remember { mutableStateOf(initial.notionToken) }
    var notionPage by remember { mutableStateOf(initial.notionParentPage) }

    var callEnabled by remember { mutableStateOf(callRecording.enabled) }
    var callAudioSource by remember { mutableStateOf(callRecording.audioSource) }
    var callAutoProcess by remember { mutableStateOf(callRecording.autoProcess) }
    var callSpeakerHint by remember { mutableStateOf(callRecording.speakerphoneHint) }

    val haptics = rememberHaptics()
    val save = {
        onSaveCallRecording(
            CallRecordingSettings(enabled = callEnabled, audioSource = callAudioSource, autoProcess = callAutoProcess, speakerphoneHint = callSpeakerHint),
        )
        onSave(
            AppSettings(
                transcription = ProviderConfig(txUrl, txKey, txModel),
                summary = ProviderConfig(sumUrl, sumKey, sumModel),
                language = language,
                autoProcess = auto,
                notionToken = notionToken,
                notionParentPage = notionPage,
            ),
        )
        haptics.confirm()
    }

    val scroll = rememberScrollState()
    val collapsePx = with(LocalDensity.current) { 48.dp.toPx() }
    val haze = rememberHazeState()
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight
    val title = stringResource(R.string.settings_title)

    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().hazeSource(haze).background(c.background).imePadding()
                    .verticalScroll(scroll).padding(top = topPad, bottom = bottomInset),
            ) {
                LargeTitle(title)
                Spacer(Modifier.height(8.dp))

                MyVoiceSection()

                // ---- Transcription providers -----------------------------------------------
                InsetGroup(header = stringResource(R.string.settings_presets), footer = stringResource(R.string.settings_speakers_help)) {
                    row {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CapsuleButton("OpenAI", {
                                if (txUrl.isCustomTxHost()) txKey = sumKey
                                txUrl = SettingsStore.OPENAI_URL; txModel = "whisper-1"
                                sumUrl = SettingsStore.OPENAI_URL; sumModel = "gpt-4o-mini"
                            })
                            CapsuleButton("Groq", {
                                if (txUrl.isCustomTxHost()) txKey = sumKey
                                txUrl = SettingsStore.GROQ_URL; txModel = SettingsStore.GROQ_TRANSCRIPTION_MODEL
                                sumUrl = SettingsStore.GROQ_URL; sumModel = SettingsStore.GROQ_SUMMARY_MODEL
                            })
                            CapsuleButton(stringResource(R.string.settings_preset_speakers), {
                                if (!txUrl.isCustomTxHost() && sumKey.isBlank() && sumUrl == txUrl) sumKey = txKey
                                if (!txUrl.isCustomTxHost()) txKey = ""
                                txUrl = SettingsStore.ELEVENLABS_URL; txModel = SettingsStore.ELEVENLABS_MODEL
                            })
                            CapsuleButton(stringResource(R.string.settings_preset_sarvam), {
                                if (!txUrl.contains("sarvam.ai") && sumKey.isBlank() && sumUrl == SettingsStore.GROQ_URL) sumKey = txKey
                                if (!txUrl.contains("sarvam.ai")) txKey = ""
                                txUrl = SettingsStore.SARVAM_URL; txModel = SettingsStore.SARVAM_MODEL
                                if (sumUrl != SettingsStore.GROQ_URL) { sumUrl = SettingsStore.GROQ_URL; sumModel = SettingsStore.GROQ_SUMMARY_MODEL }
                            })
                            CapsuleButton(stringResource(R.string.settings_preset_munsit), {
                                if (!txUrl.contains("munsit.com") && !txUrl.contains("faseeh.ai") && sumKey.isBlank() && sumUrl == SettingsStore.GROQ_URL) sumKey = txKey
                                if (!txUrl.contains("munsit.com") && !txUrl.contains("faseeh.ai")) txKey = ""
                                txUrl = SettingsStore.MUNSIT_URL; txModel = SettingsStore.MUNSIT_MODEL
                                if (sumUrl != SettingsStore.GROQ_URL) { sumUrl = SettingsStore.GROQ_URL; sumModel = SettingsStore.GROQ_SUMMARY_MODEL }
                            })
                            CapsuleButton(stringResource(R.string.settings_preset_gemini), {
                                if (!txUrl.contains("generativelanguage.googleapis.com") && sumKey.isBlank() && sumUrl == SettingsStore.GROQ_URL) sumKey = txKey
                                if (!txUrl.contains("generativelanguage.googleapis.com")) txKey = ""
                                txUrl = SettingsStore.GEMINI_URL; txModel = SettingsStore.GEMINI_MODEL
                                if (sumUrl != SettingsStore.GROQ_URL) { sumUrl = SettingsStore.GROQ_URL; sumModel = SettingsStore.GROQ_SUMMARY_MODEL }
                            })
                        }
                    }
                    row {
                        InsetRow(
                            stringResource(R.string.settings_fallback_chain),
                            subtitle = stringResource(R.string.settings_fallback_chain_desc),
                            icon = Icons.Filled.Layers, iconColor = c.indigo, chevron = true, onClick = onManageProviders,
                        )
                    }
                }

                // ---- Transcription endpoint --------------------------------------------------
                InsetGroup(header = stringResource(R.string.settings_transcription)) {
                    row { InsetTextFieldRow(stringResource(R.string.settings_base_url), txUrl, { txUrl = it }, keyboardType = KeyboardType.Uri, stacked = true) }
                    row { InsetTextFieldRow(stringResource(R.string.settings_api_key), txKey, { txKey = it }, password = true, stacked = true) }
                    row { InsetTextFieldRow(stringResource(R.string.settings_model), txModel, { txModel = it }) }
                    row { InsetTextFieldRow(stringResource(R.string.settings_language), language, { language = it }) }
                }

                // ---- Summary endpoint --------------------------------------------------------
                InsetGroup(header = stringResource(R.string.settings_summary), footer = stringResource(R.string.settings_api_key_summary_hint)) {
                    row { InsetTextFieldRow(stringResource(R.string.settings_base_url), sumUrl, { sumUrl = it }, keyboardType = KeyboardType.Uri, stacked = true) }
                    row { InsetTextFieldRow(stringResource(R.string.settings_api_key), sumKey, { sumKey = it }, password = true, stacked = true) }
                    row { InsetTextFieldRow(stringResource(R.string.settings_model), sumModel, { sumModel = it }) }
                }

                InsetGroup(footer = stringResource(R.string.settings_privacy)) {
                    row { InsetToggleRow(stringResource(R.string.settings_auto), auto, { auto = it }, icon = Icons.Filled.AutoAwesome, iconColor = c.purple) }
                }

                // ---- Call recording -----------------------------------------------------------
                InsetGroup(header = stringResource(R.string.settings_notion), footer = stringResource(R.string.settings_notion_help)) {
                    row { InsetTextFieldRow(stringResource(R.string.settings_notion_token), notionToken, { notionToken = it }, placeholder = "ntn_…", password = true, stacked = true) }
                    row {
                        InsetTextFieldRow(
                            stringResource(R.string.settings_notion_page), notionPage, { notionPage = it },
                            placeholder = "https://www.notion.so/…", keyboardType = KeyboardType.Uri, stacked = true,
                        )
                    }
                    if (notionPage.isNotBlank() && com.riffado.meetings.export.NotionExport.parentPageId(notionPage) == null) {
                        row { InsetContent { Text(stringResource(R.string.settings_notion_page_invalid), style = AppleType.footnote, color = c.red) } }
                    }
                }
                Spacer(Modifier.height(22.dp))
                // ---- MCP server for AI agents (applies immediately, not via Save) -------------
                if (mcp != null && mcpActions != null) McpSettingsSection(mcp, mcpActions)
                InsetGroup(header = stringResource(R.string.settings_call_recording), footer = stringResource(R.string.settings_call_help)) {
                    row { InsetToggleRow(stringResource(R.string.settings_call_auto_record), callEnabled, { callEnabled = it }, icon = Icons.Filled.PhoneInTalk) }
                    row { AudioSourceRow(callAudioSource) { callAudioSource = it } }
                    row { InsetToggleRow(stringResource(R.string.settings_call_auto_process), callAutoProcess, { callAutoProcess = it }, icon = Icons.Filled.AutoAwesome, iconColor = c.purple) }
                    row { InsetToggleRow(stringResource(R.string.settings_call_speakerphone_hint), callSpeakerHint, { callSpeakerHint = it }, icon = Icons.Filled.SpeakerPhone, iconColor = c.orange) }
                    row {
                        val enabled = remember { isAccessibilityServiceEnabled(context) }
                        InsetRow(
                            stringResource(R.string.settings_call_accessibility_service),
                            icon = Icons.Filled.Accessibility, iconColor = c.tint, chevron = true,
                            trailing = {
                                StatusPill(
                                    stringResource(if (enabled) R.string.settings_call_accessibility_enabled else R.string.settings_call_accessibility_disabled),
                                    if (enabled) c.green else c.red,
                                )
                            },
                            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        )
                    }
                }

                PermissionGroup(context)

                // ---- Quick record -------------------------------------------------------------
                InsetGroup(header = stringResource(R.string.quick_record_setup_title), footer = stringResource(R.string.quick_record_setup_desc)) {
                    row { InsetRow(stringResource(R.string.quick_record_power_button), icon = Icons.Filled.PowerSettingsNew, iconColor = c.red) }
                    row { InsetRow(stringResource(R.string.quick_record_qs_tile), icon = Icons.Filled.ViewModule, iconColor = c.tint) }
                    row { InsetRow(stringResource(R.string.quick_record_shortcut), icon = Icons.Filled.Shortcut, iconColor = c.orange) }
                }

                ReliabilityGroup(context)
                DiagnosticsGroup(context)

                InsetGroup {
                    row { InsetButtonRow(stringResource(R.string.action_save), save, centered = true) }
                }
            }

            GlassTopBar(
                title = title,
                collapsed = (scroll.value / collapsePx).coerceIn(0f, 1f),
                trailing = { CapsuleButton(stringResource(R.string.action_save), save, style = CapsuleStyle.Filled) },
            )
        }
    }
}

@Composable
private fun AudioSourceRow(value: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sources = remember {
        listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION (recommended)",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
            MediaRecorder.AudioSource.MIC to "MIC",
        )
    }
    val label = sources.find { it.first == value }?.second ?: sources[0].second
    Box {
        InsetRow(
            stringResource(R.string.settings_call_audio_source),
            subtitle = label,
            icon = Icons.Filled.Mic, iconColor = Apple.colors.red, chevron = true,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, shape = RoundedCornerShape(18.dp), containerColor = Apple.colors.cell) {
            sources.forEach { (source, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onChange(source); expanded = false })
            }
        }
    }
}

@Composable
private fun PermissionGroup(context: Context) {
    val c = Apple.colors
    val permissions = remember {
        listOf(
            Triple(Manifest.permission.RECORD_AUDIO, R.string.permission_microphone, Icons.Filled.Mic),
            Triple(Manifest.permission.READ_PHONE_STATE, R.string.permission_phone, Icons.Filled.Call),
            Triple(Manifest.permission.READ_CONTACTS, R.string.permission_contacts, Icons.Filled.Contacts),
            Triple(Manifest.permission.READ_CALL_LOG, R.string.permission_call_log, Icons.Filled.History),
            Triple(Manifest.permission.POST_NOTIFICATIONS, R.string.permission_notifications, Icons.Filled.Notifications),
        )
    }
    InsetGroup(header = stringResource(R.string.settings_call_permissions)) {
        permissions.forEach { (perm, labelRes, icon) ->
            row {
                val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                InsetRow(
                    stringResource(labelRes), icon = icon, iconColor = if (granted) c.green else c.secondaryLabel, chevron = !granted,
                    trailing = { StatusPill(stringResource(if (granted) R.string.reliability_status_ok else R.string.reliability_status_missing), if (granted) c.green else c.orange) },
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.fromParts("package", context.packageName, null) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReliabilityGroup(context: Context) {
    val c = Apple.colors
    InsetGroup(header = stringResource(R.string.reliability_section), footer = stringResource(R.string.battery_samsung_hint)) {
        row { InsetContent { Text(stringResource(R.string.reliability_checklist), style = AppleType.subheadline, color = c.secondaryLabel) } }
        listOf(
            Triple(R.string.reliability_accessibility, R.string.reliability_accessibility_desc, isAccessibilityServiceEnabled(context)),
            Triple(R.string.reliability_battery, R.string.reliability_battery_desc, isBatteryOptimizationDisabled(context)),
            Triple(R.string.reliability_permissions, R.string.reliability_permissions_desc, areAllPermissionsGranted(context)),
            Triple(R.string.reliability_notifications, R.string.reliability_notifications_desc, isNotificationPermissionGranted(context)),
        ).forEach { (t, d, ok) ->
            row {
                InsetRow(
                    stringResource(t), subtitle = stringResource(d),
                    trailing = { StatusPill(stringResource(if (ok) R.string.reliability_status_ok else R.string.reliability_status_missing), if (ok) c.green else c.red) },
                )
            }
        }
        row {
            InsetRow(
                stringResource(R.string.battery_optimization_title),
                subtitle = stringResource(R.string.battery_optimization_desc),
                icon = Icons.Filled.BatteryChargingFull, iconColor = c.green,
            )
        }
        row {
            InsetButtonRow(stringResource(R.string.battery_optimization_button), {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }, icon = Icons.Filled.Security)
        }
    }
}

@Composable
private fun DiagnosticsGroup(context: Context) {
    val c = Apple.colors
    var diagnosticLog by remember { mutableStateOf(com.riffado.meetings.callrecorder.CallRecorderDiagnostics.read(context)) }
    var isTestRecording by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    InsetGroup(header = stringResource(R.string.diagnostics_title)) {
        row {
            InsetButtonRow(stringResource(R.string.diagnostics_refresh), {
                diagnosticLog = com.riffado.meetings.callrecorder.CallRecorderDiagnostics.read(context)
            }, icon = Icons.Filled.Refresh)
        }
        row {
            InsetButtonRow(stringResource(R.string.diagnostics_test), {
                if (!isTestRecording) {
                    isTestRecording = true
                    com.riffado.meetings.callrecorder.CallRecorderDiagnostics.log(context, "TEST: Simulating call recording start")
                    android.widget.Toast.makeText(context, R.string.diagnostics_test_start, android.widget.Toast.LENGTH_SHORT).show()
                    val intent = Intent(context, com.riffado.meetings.callrecorder.CallRecorderService::class.java).apply {
                        action = com.riffado.meetings.callrecorder.CallRecorderService.ACTION_START
                    }
                    try {
                        context.startForegroundService(intent)
                    } catch (e: Exception) {
                        com.riffado.meetings.callrecorder.CallRecorderDiagnostics.log(context, "TEST ERROR: ${e.message}")
                    }
                    scope.launch {
                        delay(5000)
                        com.riffado.meetings.callrecorder.CallRecorderDiagnostics.log(context, "TEST: Simulating call recording stop")
                        val stopIntent = Intent(context, com.riffado.meetings.callrecorder.CallRecorderService::class.java).apply {
                            action = com.riffado.meetings.callrecorder.CallRecorderService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                        android.widget.Toast.makeText(context, R.string.diagnostics_test_stop, android.widget.Toast.LENGTH_SHORT).show()
                        isTestRecording = false
                        diagnosticLog = com.riffado.meetings.callrecorder.CallRecorderDiagnostics.read(context)
                    }
                }
            }, enabled = !isTestRecording, icon = Icons.Filled.PlayCircle)
        }
        row {
            InsetButtonRow(stringResource(R.string.diagnostics_clear), {
                com.riffado.meetings.callrecorder.CallRecorderDiagnostics.clear(context)
                diagnosticLog = com.riffado.meetings.callrecorder.CallRecorderDiagnostics.read(context)
            }, color = c.red, icon = Icons.Filled.Delete)
        }
        row {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 300.dp).padding(12.dp)
                    .clip(RoundedCornerShape(12.dp)).background(c.cellElevated).padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    Text(
                        diagnosticLog.ifBlank { stringResource(R.string.diagnostics_empty) },
                        style = AppleType.caption, fontFamily = FontFamily.Monospace, color = c.label,
                    )
                }
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return try {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        enabledServices?.contains(context.packageName) == true
    } catch (e: Exception) {
        false
    }
}

private fun isBatteryOptimizationDisabled(context: Context): Boolean {
    return try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } catch (e: Exception) {
        false
    }
}

private fun areAllPermissionsGranted(context: Context): Boolean {
    val permissions = listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
    )
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun isNotificationPermissionGranted(context: Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

