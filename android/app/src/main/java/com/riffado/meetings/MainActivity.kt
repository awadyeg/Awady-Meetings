package com.riffado.meetings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riffado.meetings.ui.AppViewModel
import com.riffado.meetings.ui.DetailScreen
import com.riffado.meetings.ui.HomeScreen
import com.riffado.meetings.ui.MeetingsTheme
import com.riffado.meetings.ui.ProviderManagementScreen
import com.riffado.meetings.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeetingsTheme { App(viewModel, intent) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

private const val SETTINGS = "settings"
private const val PROVIDER_MANAGEMENT = "provider_management"

@Composable
private fun App(viewModel: AppViewModel, intent: Intent) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf<String?>(null) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let {
            viewModel.importAudio(it) { id ->
                if (id != null) {
                    screen = id
                    Toast.makeText(context, R.string.import_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.import_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(intent) {
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_VIEW -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    ?: intent.data
                uri?.let {
                    viewModel.importAudio(it) { id ->
                        if (id != null) {
                            screen = id
                            Toast.makeText(context, R.string.import_success, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, R.string.import_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.startRecording()
        } else {
            Toast.makeText(context, R.string.permission_needed, Toast.LENGTH_LONG).show()
        }
    }

    val startRecording = {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) viewModel.startRecording() else permissionLauncher.launch(needed.toTypedArray())
    }

    BackHandler(enabled = screen != null) { screen = null }

    when (val current = screen) {
        null -> HomeScreen(
            recordings = recordings,
            live = live,
            configured = settings.canTranscribe,
            onRecord = startRecording,
            onImport = { audioPickerLauncher.launch("audio/*") },
            onPause = viewModel::pauseRecording,
            onResume = viewModel::resumeRecording,
            onStop = viewModel::stopRecording,
            onOpen = { screen = it },
            onSettings = { screen = SETTINGS },
        )
        SETTINGS -> SettingsScreen(
            initial = settings,
            callRecording = viewModel.callSettings.collectAsStateWithLifecycle().value,
            onSave = {
                viewModel.saveSettings(it)
                Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                screen = null
            },
            onSaveCallRecording = viewModel::saveCallSettings,
            onManageProviders = { screen = PROVIDER_MANAGEMENT },
            onBack = { screen = null },
        )
        PROVIDER_MANAGEMENT -> {
            val transcriptionSettings by viewModel.transcriptionProviders.collectAsStateWithLifecycle()
            ProviderManagementScreen(
                settings = transcriptionSettings,
                onSave = { updated ->
                    viewModel.saveTranscriptionProviders(updated)
                    Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                    screen = SETTINGS
                },
                onBack = { screen = SETTINGS },
            )
        }
        else -> {
            val recording = recordings.firstOrNull { it.id == current }
            if (recording == null) {
                LaunchedEffect(current) { screen = null }
            } else {
                DetailScreen(
                    recording = recording,
                    segmentPaths = viewModel.segmentPaths(recording.id),
                    canProcess = settings.canTranscribe,
                    onBack = { screen = null },
                    onProcess = { summaryOnly -> viewModel.process(recording.id, summaryOnly) },
                    onRename = { viewModel.rename(recording.id, it) },
                    detectingSpeakers = recording.id in viewModel.detectingSpeakers.collectAsStateWithLifecycle().value,
                    onRedetectSpeakers = {
                        viewModel.redetectSpeakers(recording.id) {
                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                        }
                    },
                    onRenameSpeaker = { speaker, name -> viewModel.renameSpeaker(recording.id, speaker, name) },
                    onDelete = {
                        viewModel.delete(recording.id)
                        screen = null
                    },
                )
            }
        }
    }
}
