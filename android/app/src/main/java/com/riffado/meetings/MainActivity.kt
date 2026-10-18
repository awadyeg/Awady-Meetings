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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riffado.meetings.ui.AppTab
import com.riffado.meetings.ui.AskAiScreen
import com.riffado.meetings.ui.AskAiViewModel
import com.riffado.meetings.ui.AppViewModel
import com.riffado.meetings.ui.GlassTabBar
import com.riffado.meetings.ui.MiniRecordingPill
import com.riffado.meetings.ui.RecorderScreen
import com.riffado.meetings.ui.TasksScreen
import com.riffado.meetings.ui.rememberHaptics
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

private const val PROVIDER_MANAGEMENT = "provider_management"
private const val ASK = "ask"
private const val ASK_PREFIX = "ask:"

@Composable
private fun App(viewModel: AppViewModel, intent: Intent) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val levels by viewModel.levels.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AppTab.Home) }
    /** Pushed screen on top of the tabs: a recording id or [PROVIDER_MANAGEMENT]. */
    var screen by rememberSaveable { mutableStateOf<String?>(null) }
    var showRecorder by rememberSaveable { mutableStateOf(false) }
    var searchTrigger by rememberSaveable { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    val askViewModel: AskAiViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val onImported: (String?) -> Unit = { id ->
        if (id != null) {
            screen = id
            Toast.makeText(context, R.string.import_success, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.import_failed, Toast.LENGTH_LONG).show()
        }
    }
    val audioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importAudio(it, onImported) }
    }

    LaunchedEffect(intent) {
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_VIEW -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.data
                uri?.let { viewModel.importAudio(it, onImported) }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.startRecording()
        } else {
            showRecorder = false
            haptics.reject()
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

    // When a recording finishes while the recorder is open, land on its detail page.
    var lastLiveId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(live.recordingId) {
        val previous = lastLiveId
        lastLiveId = live.recordingId
        if (live.recordingId == null && previous != null && showRecorder) {
            showRecorder = false
            screen = previous
        }
    }

    BackHandler(enabled = showRecorder || screen != null || tab != AppTab.Home) {
        when {
            showRecorder -> showRecorder = false
            screen != null -> screen = null
            else -> tab = AppTab.Home
        }
    }

    val pushSpring = spring<IntOffset>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    Box(Modifier.fillMaxSize()) {
        // ---- Tabs ------------------------------------------------------------------------------
        Crossfade(tab, label = "tabs") { current ->
            when (current) {
                AppTab.Home, AppTab.Search -> HomeScreen(
                    recordings = recordings,
                    live = live,
                    configured = settings.canTranscribe,
                    onImport = { audioPickerLauncher.launch("audio/*") },
                    onOpen = { screen = it },
                    onSettings = { tab = AppTab.Settings },
                    focusSearch = searchTrigger,
                    onAsk = { screen = ASK },
                )
                AppTab.Tasks -> TasksScreen(recordings, onOpen = { screen = it })
                AppTab.Settings -> SettingsScreen(
                    initial = settings,
                    callRecording = viewModel.callSettings.collectAsStateWithLifecycle().value,
                    onSave = {
                        viewModel.saveSettings(it)
                        Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                    },
                    onSaveCallRecording = viewModel::saveCallSettings,
                    onManageProviders = { screen = PROVIDER_MANAGEMENT },
                    onBack = { tab = AppTab.Home },
                )
            }
        }

        // ---- Pushed screens (iOS-style horizontal push, RTL aware) ----------------------------
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState != null) {
                    slideIntoContainer(SlideDirection.Start, pushSpring) togetherWith fadeOut()
                } else {
                    fadeIn() togetherWith slideOutOfContainer(SlideDirection.End, pushSpring)
                }
            },
            label = "push",
        ) { current ->
            when (current) {
                null -> Unit
                ASK -> AskAiRoute(askViewModel, null, null, onOpen = { screen = it }, onBack = { screen = null })
                PROVIDER_MANAGEMENT -> {
                    val transcriptionSettings by viewModel.transcriptionProviders.collectAsStateWithLifecycle()
                    ProviderManagementScreen(
                        settings = transcriptionSettings,
                        onSave = { updated ->
                            viewModel.saveTranscriptionProviders(updated)
                            Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                        },
                        onBack = { screen = null },
                    )
                }
                else -> if (current.startsWith(ASK_PREFIX)) {
                    val id = current.removePrefix(ASK_PREFIX)
                    AskAiRoute(
                        askViewModel, id, recordings.firstOrNull { it.id == id }?.title,
                        onOpen = { screen = it }, onBack = { screen = id },
                    )
                } else {
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
                            onAsk = { screen = ASK_PREFIX + recording.id },
                            onDelete = {
                                viewModel.delete(recording.id)
                                screen = null
                            },
                        )
                    }
                }
            }
        }

        // ---- Floating chrome -----------------------------------------------------------------
        Column(Modifier.align(Alignment.BottomCenter)) {
            AnimatedVisibility(
                visible = live.isActive && !showRecorder,
                enter = scaleIn(initialScale = 0.8f) + fadeIn(),
                exit = scaleOut(targetScale = 0.8f) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                MiniRecordingPill(
                    live = live,
                    levels = levels,
                    onOpen = { showRecorder = true },
                    onPause = viewModel::pauseRecording,
                    onResume = viewModel::resumeRecording,
                    onStop = viewModel::stopRecording,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            AnimatedVisibility(
                visible = screen == null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                GlassTabBar(
                    selected = tab,
                    onSelect = { selected ->
                        if (selected == AppTab.Search) {
                            tab = AppTab.Home
                            searchTrigger++
                        } else {
                            tab = selected
                        }
                    },
                    recording = live.isActive,
                    onRecord = {
                        showRecorder = true
                        if (!live.isActive) startRecording()
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            if (screen != null && screen != PROVIDER_MANAGEMENT) Spacer(Modifier.height(140.dp))
        }

        // ---- Full-screen recorder ------------------------------------------------------------
        AnimatedVisibility(
            visible = showRecorder && live.isActive,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut(),
        ) {
            val liveRecording = recordings.firstOrNull { it.id == live.recordingId }
            RecorderScreen(
                live = live,
                title = liveRecording?.title,
                levels = levels,
                bookmarks = liveRecording?.bookmarks.orEmpty(),
                onMinimize = { showRecorder = false },
                onPause = viewModel::pauseRecording,
                onResume = viewModel::resumeRecording,
                onStop = viewModel::stopRecording,
                onBookmark = { live.recordingId?.let { viewModel.addBookmark(it, live.elapsedMs) } },
            )
        }
    }
}

@Composable
private fun AskAiRoute(vm: AskAiViewModel, scope: String?, scopeTitle: String?, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val conversation by vm.conversation(scope).collectAsStateWithLifecycle()
    AskAiScreen(
        conversation = conversation,
        scopeTitle = scopeTitle,
        onAsk = { vm.ask(scope, it) },
        onRetry = { vm.retry(scope) },
        onClear = { vm.clear(scope) },
        onOpenRecording = onOpen,
        onBack = onBack,
    )
}
