package com.riffado.meetings.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.riffado.meetings.R
import com.riffado.meetings.data.AppSettings
import com.riffado.meetings.data.CallRecordingSettings
import com.riffado.meetings.data.ProviderConfig
import com.riffado.meetings.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    initial: AppSettings,
    callRecording: CallRecordingSettings,
    onSave: (AppSettings) -> Unit,
    onSaveCallRecording: (CallRecordingSettings) -> Unit,
    onManageProviders: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var txUrl by remember { mutableStateOf(initial.transcription.baseUrl) }
    var txKey by remember { mutableStateOf(initial.transcription.apiKey) }
    var txModel by remember { mutableStateOf(initial.transcription.model) }
    var sumUrl by remember { mutableStateOf(initial.summary.baseUrl) }
    var sumKey by remember { mutableStateOf(initial.summary.apiKey) }
    var sumModel by remember { mutableStateOf(initial.summary.model) }
    var language by remember { mutableStateOf(initial.language) }
    var auto by remember { mutableStateOf(initial.autoProcess) }
    
    var callEnabled by remember { mutableStateOf(callRecording.enabled) }
    var callAudioSource by remember { mutableStateOf(callRecording.audioSource) }
    var callAutoProcess by remember { mutableStateOf(callRecording.autoProcess) }
    var callSpeakerHint by remember { mutableStateOf(callRecording.speakerphoneHint) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MyVoiceSection()
            HorizontalDivider()
            Text(stringResource(R.string.settings_presets), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        if (txUrl.contains("elevenlabs.io") || txUrl.contains("sarvam.ai") || txUrl.contains("munsit.com") || txUrl.contains("faseeh.ai") || txUrl.contains("generativelanguage.googleapis.com")) txKey = sumKey
                        txUrl = SettingsStore.OPENAI_URL
                        txModel = "whisper-1"
                        sumUrl = SettingsStore.OPENAI_URL
                        sumModel = "gpt-4o-mini"
                    },
                    label = { Text("OpenAI") },
                )
                AssistChip(
                    onClick = {
                        if (txUrl.contains("elevenlabs.io") || txUrl.contains("sarvam.ai") || txUrl.contains("munsit.com") || txUrl.contains("faseeh.ai") || txUrl.contains("generativelanguage.googleapis.com")) txKey = sumKey
                        txUrl = SettingsStore.GROQ_URL
                        txModel = SettingsStore.GROQ_TRANSCRIPTION_MODEL
                        sumUrl = SettingsStore.GROQ_URL
                        sumModel = SettingsStore.GROQ_SUMMARY_MODEL
                    },
                    label = { Text("Groq") },
                )
                AssistChip(
                    onClick = {
                        if (!txUrl.contains("elevenlabs.io") && !txUrl.contains("sarvam.ai") && !txUrl.contains("munsit.com") && !txUrl.contains("faseeh.ai") && !txUrl.contains("generativelanguage.googleapis.com") && sumKey.isBlank() && sumUrl == txUrl) sumKey = txKey
                        if (!txUrl.contains("elevenlabs.io") && !txUrl.contains("sarvam.ai") && !txUrl.contains("munsit.com") && !txUrl.contains("faseeh.ai") && !txUrl.contains("generativelanguage.googleapis.com")) txKey = ""
                        txUrl = SettingsStore.ELEVENLABS_URL
                        txModel = SettingsStore.ELEVENLABS_MODEL
                    },
                    label = { Text(stringResource(R.string.settings_preset_speakers)) },
                )
                AssistChip(
                    onClick = {
                        if (!txUrl.contains("sarvam.ai") && sumKey.isBlank() && sumUrl == SettingsStore.GROQ_URL) sumKey = txKey
                        if (!txUrl.contains("sarvam.ai")) txKey = ""
                        txUrl = SettingsStore.SARVAM_URL
                        txModel = SettingsStore.SARVAM_MODEL
                        if (sumUrl != SettingsStore.GROQ_URL) {
                            sumUrl = SettingsStore.GROQ_URL
                            sumModel = SettingsStore.GROQ_SUMMARY_MODEL
                        }
                    },
                    label = { Text(stringResource(R.string.settings_preset_sarvam)) },
                )
                AssistChip(
                    onClick = {
                        if (!txUrl.contains("munsit.com") && !txUrl.contains("faseeh.ai") && sumKey.isBlank() && sumUrl == SettingsStore.GROQ_URL) sumKey = txKey
                        if (!txUrl.contains("munsit.com") && !txUrl.contains("faseeh.ai")) txKey = ""
                        txUrl = SettingsStore.MUNSIT_URL
                        txModel = SettingsStore.MUNSIT_MODEL
                        if (sumUrl != SettingsStore.GROQ_URL) {
                            sumUrl = SettingsStore.GROQ_URL
                            sumModel = SettingsStore.GROQ_SUMMARY_MODEL
                        }
                    },
                    label = { Text(stringResource(R.string.settings_preset_munsit)) },
                )
                AssistChip(
                    onClick = {
                        if (!txUrl.contains("generativelanguage.googleapis.com") && sumKey.isBlank() && sumUrl == SettingsStore.GROQ_URL) sumKey = txKey
                        if (!txUrl.contains("generativelanguage.googleapis.com")) txKey = ""
                        txUrl = SettingsStore.GEMINI_URL
                        txModel = SettingsStore.GEMINI_MODEL
                        if (sumUrl != SettingsStore.GROQ_URL) {
                            sumUrl = SettingsStore.GROQ_URL
                            sumModel = SettingsStore.GROQ_SUMMARY_MODEL
                        }
                    },
                    label = { Text(stringResource(R.string.settings_preset_gemini)) },
                )
            }
            Text(
                stringResource(R.string.settings_speakers_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            
            Button(
                onClick = onManageProviders,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Manage Provider Fallback Chain")
            }

            HorizontalDivider()
            Text(stringResource(R.string.settings_call_recording), style = MaterialTheme.typography.titleMedium)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_call_auto_record), modifier = Modifier.weight(1f))
                Switch(checked = callEnabled, onCheckedChange = { callEnabled = it })
            }
            
            AudioSourcePicker(callAudioSource) { callAudioSource = it }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_call_auto_process), modifier = Modifier.weight(1f))
                Switch(checked = callAutoProcess, onCheckedChange = { callAutoProcess = it })
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_call_speakerphone_hint), modifier = Modifier.weight(1f))
                Switch(checked = callSpeakerHint, onCheckedChange = { callSpeakerHint = it })
            }
            
            AccessibilityServiceStatus(context)
            PermissionButtons(context)
            
            Text(
                stringResource(R.string.settings_call_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSaveCallRecording(
                        CallRecordingSettings(
                            enabled = callEnabled,
                            audioSource = callAudioSource,
                            autoProcess = callAutoProcess,
                            speakerphoneHint = callSpeakerHint,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
            
            HorizontalDivider()
            Text(stringResource(R.string.quick_record_setup_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.quick_record_setup_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "• " + stringResource(R.string.quick_record_power_button),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "• " + stringResource(R.string.quick_record_qs_tile),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "• " + stringResource(R.string.quick_record_shortcut),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            
            HorizontalDivider()
            ReliabilityChecklist(context)

            HorizontalDivider()
            DiagnosticsSection(context)

            HorizontalDivider()
            Text(stringResource(R.string.settings_transcription), style = MaterialTheme.typography.titleMedium)
            UrlField(txUrl) { txUrl = it }
            KeyField(txKey, null) { txKey = it }
            ModelField(txModel) { txModel = it }
            OutlinedTextField(
                value = language,
                onValueChange = { language = it },
                label = { Text(stringResource(R.string.settings_language)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Text(stringResource(R.string.settings_summary), style = MaterialTheme.typography.titleMedium)
            UrlField(sumUrl) { sumUrl = it }
            KeyField(sumKey, stringResource(R.string.settings_api_key_summary_hint)) { sumKey = it }
            ModelField(sumModel) { sumModel = it }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_auto), modifier = Modifier.weight(1f))
                Switch(checked = auto, onCheckedChange = { auto = it })
            }

            Text(
                stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSave(
                        AppSettings(
                            transcription = ProviderConfig(txUrl, txKey, txModel),
                            summary = ProviderConfig(sumUrl, sumKey, sumModel),
                            language = language,
                            autoProcess = auto,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun UrlField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.settings_base_url)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun KeyField(value: String, hint: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.settings_api_key)) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModelField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.settings_model)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioSourcePicker(value: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sources = remember {
        listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION (recommended)",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
            MediaRecorder.AudioSource.MIC to "MIC",
        )
    }
    val label = sources.find { it.first == value }?.second ?: sources[0].second
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_call_audio_source)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sources.forEach { (source, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onChange(source)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AccessibilityServiceStatus(context: Context) {
    val isEnabled = remember {
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
            enabledServices?.contains(context.packageName) == true
        } catch (e: Exception) {
            false
        }
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_call_accessibility_service),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (isEnabled) stringResource(R.string.settings_call_accessibility_enabled)
                else stringResource(R.string.settings_call_accessibility_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnabled) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
        ) {
            Text(stringResource(R.string.action_open))
        }
    }
}

@Composable
private fun PermissionButtons(context: Context) {
    val permissions = remember {
        listOf(
            Manifest.permission.RECORD_AUDIO to R.string.permission_microphone,
            Manifest.permission.READ_PHONE_STATE to R.string.permission_phone,
            Manifest.permission.READ_CONTACTS to R.string.permission_contacts,
            Manifest.permission.READ_CALL_LOG to R.string.permission_call_log,
            Manifest.permission.POST_NOTIFICATIONS to R.string.permission_notifications,
        )
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_call_permissions),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            permissions.forEach { (perm, labelRes) ->
                val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                AssistChip(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    label = { 
                        Text(
                            stringResource(labelRes) + if (granted) " ✓" else "",
                            color = if (granted) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReliabilityChecklist(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.reliability_section), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.reliability_checklist),
            style = MaterialTheme.typography.bodyMedium,
        )
        
        ReliabilityCheckItem(
            title = stringResource(R.string.reliability_accessibility),
            description = stringResource(R.string.reliability_accessibility_desc),
            isOk = isAccessibilityServiceEnabled(context),
        )
        
        ReliabilityCheckItem(
            title = stringResource(R.string.reliability_battery),
            description = stringResource(R.string.reliability_battery_desc),
            isOk = isBatteryOptimizationDisabled(context),
        )
        
        ReliabilityCheckItem(
            title = stringResource(R.string.reliability_permissions),
            description = stringResource(R.string.reliability_permissions_desc),
            isOk = areAllPermissionsGranted(context),
        )
        
        ReliabilityCheckItem(
            title = stringResource(R.string.reliability_notifications),
            description = stringResource(R.string.reliability_notifications_desc),
            isOk = isNotificationPermissionGranted(context),
        )
        
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.battery_optimization_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.battery_optimization_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(fallbackIntent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.battery_optimization_button))
            }
            Text(
                stringResource(R.string.battery_samsung_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ReliabilityCheckItem(title: String, description: String, isOk: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(if (isOk) R.string.reliability_status_ok else R.string.reliability_status_missing),
            style = MaterialTheme.typography.bodySmall,
            color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
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

@Composable
private fun DiagnosticsSection(context: Context) {
    var diagnosticLog by remember { mutableStateOf(com.riffado.meetings.callrecorder.CallRecorderDiagnostics.read(context)) }
    var isTestRecording by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.titleMedium)
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = {
                    diagnosticLog = com.riffado.meetings.callrecorder.CallRecorderDiagnostics.read(context)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Refresh")
            }
            
            OutlinedButton(
                onClick = {
                    com.riffado.meetings.callrecorder.CallRecorderDiagnostics.clear(context)
                    diagnosticLog = com.riffado.meetings.callrecorder.CallRecorderDiagnostics.read(context)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.diagnostics_clear))
            }
        }
        
        OutlinedButton(
            onClick = {
                if (!isTestRecording) {
                    isTestRecording = true
                    com.riffado.meetings.callrecorder.CallRecorderDiagnostics.log(context, "TEST: Simulating call recording start")
                    android.widget.Toast.makeText(context, R.string.diagnostics_test_start, android.widget.Toast.LENGTH_SHORT).show()
                    
                    val intent = android.content.Intent(context, com.riffado.meetings.callrecorder.CallRecorderService::class.java).apply {
                        action = com.riffado.meetings.callrecorder.CallRecorderService.ACTION_START
                    }
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    } catch (e: Exception) {
                        com.riffado.meetings.callrecorder.CallRecorderDiagnostics.log(context, "TEST ERROR: ${e.message}")
                    }
                    
                    scope.launch {
                        delay(5000)
                        com.riffado.meetings.callrecorder.CallRecorderDiagnostics.log(context, "TEST: Simulating call recording stop")
                        val stopIntent = android.content.Intent(context, com.riffado.meetings.callrecorder.CallRecorderService::class.java).apply {
                            action = com.riffado.meetings.callrecorder.CallRecorderService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                        android.widget.Toast.makeText(context, R.string.diagnostics_test_stop, android.widget.Toast.LENGTH_SHORT).show()
                        isTestRecording = false
                        diagnosticLog = com.riffado.meetings.callrecorder.CallRecorderDiagnostics.read(context)
                    }
                }
            },
            enabled = !isTestRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.diagnostics_test))
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 300.dp)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        diagnosticLog.ifBlank { stringResource(R.string.diagnostics_empty) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
