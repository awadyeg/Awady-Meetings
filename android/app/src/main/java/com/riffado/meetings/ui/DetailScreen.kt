package com.riffado.meetings.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.riffado.meetings.data.Speakers
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.riffado.meetings.R
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.RecordingStatus
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    recording: Recording,
    segmentPaths: List<String>,
    canProcess: Boolean,
    onBack: () -> Unit,
    onProcess: (summaryOnly: Boolean) -> Unit,
    onRename: (String) -> Unit,
    onRenameSpeaker: (speaker: String, name: String) -> Unit,
    onDelete: () -> Unit,
    onRedetectSpeakers: () -> Unit = {},
    detectingSpeakers: Boolean = false,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(if (recording.summary != null) 0 else 1) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editingSpeaker by remember { mutableStateOf<String?>(null) }
    val speakerIds = remember(recording.transcript) { Speakers.ids(recording.transcript) }

    Scaffold(
        topBar = {
            val titleDirection = TextDirectionDetector.detectDirection(recording.title)
            TopAppBar(
                title = { 
                    Text(
                        recording.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(
                            textDirection = titleDirection,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename))
                    }
                    IconButton(
                        enabled = recording.summary != null || recording.transcript != null,
                        onClick = {
                            val text = listOfNotNull(
                                recording.summary,
                                recording.transcript?.let { Speakers.applyNames(it, recording.speakerNames) },
                            ).joinToString("\n\n---\n\n")
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, recording.title)
                                .putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, recording.title))
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${formatDate(recording.createdAt)} · ${formatDuration(recording.durationMs)} · ${statusLabel(recording.status)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            
            // Show which provider transcribed this recording
            if (recording.transcribedBy != null) {
                Text(
                    "Transcribed by ${providerDisplayName(recording.transcribedBy)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            
            if (segmentPaths.isNotEmpty()) AudioPlayer(segmentPaths)

            if (recording.status.isBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            recording.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            ProcessButtons(recording, canProcess, onProcess)
            if (speakerIds.isNotEmpty()) {
                SpeakerChips(speakerIds, recording.speakerNames) { editingSpeaker = it }
                TextButton(onClick = onRedetectSpeakers, enabled = !recording.status.isBusy && !detectingSpeakers) {
                    Text(if (detectingSpeakers) "Identifying speakers…" else "Re-detect speakers")
                }
            }

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_summary)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_transcript)) })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                if (tab == 0) {
                    val summary = recording.summary
                    if (summary.isNullOrBlank()) {
                        Text(stringResource(R.string.summary_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        SimpleMarkdown(summary)
                    }
                } else {
                    val transcript = recording.transcript
                    if (transcript.isNullOrBlank()) {
                        Text(stringResource(R.string.transcript_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TranscriptView(transcript, recording.speakerNames)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    editingSpeaker?.let { speaker ->
        var value by remember(speaker) { mutableStateOf(recording.speakerNames[speaker] ?: "") }
        AlertDialog(
            onDismissRequest = { editingSpeaker = null },
            title = { Text(stringResource(R.string.speaker_rename_title, speaker)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        placeholder = { Text("Speaker $speaker") },
                    )
                    Text(
                        stringResource(R.string.speaker_rename_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editingSpeaker = null
                    onRenameSpeaker(speaker, value)
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingSpeaker = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (renaming) {
        var value by remember { mutableStateOf(recording.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename_title)) },
            text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    onRename(value)
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeakerChips(ids: List<String>, names: Map<String, String>, onEdit: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.speakers_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ids.forEach { id ->
                AssistChip(
                    onClick = { onEdit(id) },
                    label = { Text(Speakers.label(id, names)) },
                    leadingIcon = {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(labelColor = speakerColor(id)),
                )
            }
        }
    }
}

@Composable
private fun TranscriptView(transcript: String, names: Map<String, String>) {
    val turns = remember(transcript) { Speakers.parse(transcript) }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            turns.forEach { turn ->
                // Skip empty or punctuation-only utterances
                val cleanText = turn.text.trim()
                if (cleanText.isEmpty() || cleanText == ".") return@forEach
                
                val textDirection = TextDirectionDetector.detectDirection(cleanText)
                
                Column {
                    if (turn.speaker != null) {
                        val speakerLabel = Speakers.label(turn.speaker, names)
                        val speakerDirection = TextDirectionDetector.detectDirection(speakerLabel)
                        Text(
                            speakerLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                textDirection = speakerDirection,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            ),
                            color = speakerColor(turn.speaker),
                        )
                    }
                    Text(
                        cleanText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = textDirection,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun speakerColor(id: String): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
    )
    return palette[((id.toIntOrNull() ?: 1) - 1).mod(palette.size)]
}

@Composable
private fun ProcessButtons(recording: Recording, canProcess: Boolean, onProcess: (Boolean) -> Unit) {
    if (recording.status.isBusy || recording.status == RecordingStatus.RECORDING) return
    if (recording.segments.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (recording.transcript.isNullOrBlank() || recording.status == RecordingStatus.FAILED) {
            Button(enabled = canProcess, onClick = { onProcess(!recording.transcript.isNullOrBlank()) }) {
                Text(
                    stringResource(
                        if (recording.status == RecordingStatus.FAILED) R.string.action_retry else R.string.action_transcribe,
                    ),
                )
            }
        }
        if (!recording.transcript.isNullOrBlank() && recording.status != RecordingStatus.FAILED) {
            OutlinedButton(enabled = canProcess, onClick = { onProcess(false) }) {
                Text(stringResource(R.string.action_retranscribe))
            }
        }
        if (!recording.transcript.isNullOrBlank()) {
            OutlinedButton(enabled = canProcess, onClick = { onProcess(true) }) {
                Text(stringResource(R.string.action_resummarize))
            }
        }
    }
}

@Composable
private fun AudioPlayer(paths: List<String>) {
    val context = LocalContext.current
    val player = remember(paths) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(paths.map { MediaItem.fromUri(Uri.fromFile(File(it))) })
            prepare()
        }
    }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var index by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            index = player.currentMediaItemIndex
            delay(250)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                if (playing) {
                    Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.action_pause))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play))
                }
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Slider(
                    value = if (dragging) dragValue else if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = {
                        dragging = true
                        dragValue = it
                    },
                    onValueChangeFinished = {
                        if (duration > 0) player.seekTo((dragValue * duration).toLong())
                        dragging = false
                    },
                )
                Row {
                    Text(
                        "${formatDuration(position)} / ${formatDuration(duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (paths.size > 1) {
                        Text(
                            stringResource(R.string.part_of, index + 1, paths.size),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

private fun providerDisplayName(providerId: String): String = when (providerId) {
    "elevenlabs" -> "ElevenLabs"
    "munsit" -> "Munsit"
    "gemini" -> "Gemini"
    "groq" -> "Groq"
    "sarvam" -> "Sarvam"
    else -> providerId.replaceFirstChar { it.uppercaseChar() }
}
