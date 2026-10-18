package com.riffado.meetings.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.riffado.meetings.R
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riffado.meetings.ai.SpeakerModelState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyVoiceSection(vm: VoiceEnrollmentViewModel = viewModel()) {
    val context = LocalContext.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val model by vm.modelState.collectAsStateWithLifecycle()
    var name by rememberSaveable(profile?.enrolledAt) { mutableStateOf(profile?.displayName ?: "Me") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) vm.enroll(name) else vm.permissionDenied()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(vm, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.cancel()
        }
    }
    val c = Apple.colors
    InsetGroup(header = stringResource(R.string.voice_title), footer = stringResource(R.string.voice_footer)) {
        row {
            InsetTextFieldRow(stringResource(R.string.voice_display_name), name, { name = it; vm.rename(it) }, enabled = !state.busy)
        }
        row {
            InsetRow(
                stringResource(R.string.voice_profile),
                icon = Icons.Filled.RecordVoiceOver, iconColor = c.purple,
                trailing = {
                    StatusPill(
                        profile?.let { stringResource(R.string.voice_enrolled_on, DateFormat.getDateInstance().format(Date(it.enrolledAt))) }
                            ?: stringResource(R.string.voice_not_enrolled),
                        if (profile != null) c.green else c.secondaryLabel,
                    )
                },
            )
        }
        row {
            InsetButtonRow(stringResource(R.string.voice_record), {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.enroll(name)
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            }, enabled = !state.busy, icon = Icons.Filled.Mic)
        }
        val progress = model
        val showModel = progress is SpeakerModelState.Preparing || progress is SpeakerModelState.Downloading || progress is SpeakerModelState.Error
        if (showModel || state.busy || state.message != null) {
            row {
                InsetContent {
                    when (progress) {
                        is SpeakerModelState.Preparing -> {
                            Text("Preparing voice model…", style = AppleType.subheadline, color = c.label)
                            LinearProgressIndicator(progress = { progress.bytes.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth(), color = c.tint, trackColor = c.fill)
                        }
                        is SpeakerModelState.Downloading -> {
                            Text("Downloading voice model: ${progress.bytes / 1024} / ${progress.total / 1024} KB", style = AppleType.subheadline, color = c.label)
                            LinearProgressIndicator(progress = { progress.bytes.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth(), color = c.tint, trackColor = c.fill)
                        }
                        is SpeakerModelState.Error -> {
                            Text(progress.message, style = AppleType.subheadline, color = c.red)
                            CapsuleButton("Retry", vm::retryModel, enabled = !state.busy)
                        }
                        else -> Unit
                    }
                    if (state.busy) {
                        if (state.recording) {
                            Text("${30 - state.recordedSeconds} seconds remaining", style = AppleType.headline, color = c.red)
                            LinearProgressIndicator(progress = { state.recordedSeconds / 30f }, modifier = Modifier.fillMaxWidth(), color = c.red, trackColor = c.fill)
                            Text(
                                "Read aloud at your normal pace, repeating as needed: Today I am recording my voice so my meetings are easier to follow. I enjoy sharing ideas, asking questions, and learning something new every day.",
                                style = AppleType.subheadline, color = c.secondaryLabel,
                            )
                        } else if (model is SpeakerModelState.Ready) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.tint, trackColor = c.fill)
                        }
                        CapsuleButton(stringResource(R.string.action_cancel), vm::cancel, style = CapsuleStyle.Destructive)
                    }
                    state.message?.takeUnless { showModel }?.let { Text(it, style = AppleType.subheadline, color = c.label) }
                }
            }
        }
        row {
            InsetContent {
                Text(stringResource(R.string.voice_threshold, "%.2f".format(Locale.US, threshold)), style = AppleType.body, color = c.label)
                Slider(
                    value = threshold.coerceIn(0.30f, 0.90f), onValueChange = vm::setThreshold, valueRange = 0.30f..0.90f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.tint, inactiveTrackColor = c.fill),
                )
            }
        }
        row {
            InsetButtonRow(stringResource(R.string.voice_delete), { confirmDelete = true }, color = c.red, enabled = profile != null && !state.busy, icon = Icons.Filled.Delete)
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete voice profile?") },
        text = { Text("You will need to record your voice again to enable voice matching.") },
        confirmButton = { TextButton(onClick = { vm.delete(); confirmDelete = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
