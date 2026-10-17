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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("My voice", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = name, onValueChange = { name = it; vm.rename(it) },
            label = { Text("Display name") }, enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(profile?.let { "Enrolled on ${DateFormat.getDateInstance().format(Date(it.enrolledAt))}" } ?: "Not enrolled")
        Button(enabled = !state.busy, onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.enroll(name)
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }) { Text("Record my voice (30s)") }
        when (val progress = model) {
            is SpeakerModelState.Preparing -> {
                Text("Preparing voice model…")
                LinearProgressIndicator(progress = { progress.bytes.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
            }
            is SpeakerModelState.Downloading -> {
                Text("Downloading voice model: ${progress.bytes / 1024} / ${progress.total / 1024} KB")
                LinearProgressIndicator(progress = { progress.bytes.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
            }
            is SpeakerModelState.Error -> {
                Text(progress.message, color = MaterialTheme.colorScheme.error)
                TextButton(enabled = !state.busy, onClick = vm::retryModel) { Text("Retry") }
            }
            else -> Unit
        }
        if (state.busy) {
            if (state.recording) {
                Text("${30 - state.recordedSeconds} seconds remaining")
                LinearProgressIndicator(progress = { state.recordedSeconds / 30f }, modifier = Modifier.fillMaxWidth())
                Text("Read aloud at your normal pace, repeating as needed: Today I am recording my voice so my meetings are easier to follow. I enjoy sharing ideas, asking questions, and learning something new every day.")
            } else if (model is SpeakerModelState.Ready) LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = vm::cancel) { Text("Cancel") }
        }
        state.message?.takeUnless {
            model is SpeakerModelState.Preparing || model is SpeakerModelState.Downloading || model is SpeakerModelState.Error
        }?.let { Text(it) }
        Text("Match threshold: ${"%.2f".format(Locale.US, threshold)}")
        Slider(value = threshold.coerceIn(0.30f, 0.90f), onValueChange = vm::setThreshold, valueRange = 0.30f..0.90f)
        OutlinedButton(enabled = profile != null && !state.busy, onClick = { confirmDelete = true }) { Text("Delete voice profile") }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete voice profile?") },
        text = { Text("You will need to record your voice again to enable voice matching.") },
        confirmButton = { TextButton(onClick = { vm.delete(); confirmDelete = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
