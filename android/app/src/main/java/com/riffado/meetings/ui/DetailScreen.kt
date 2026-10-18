package com.riffado.meetings.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.RecordingStatus
import com.riffado.meetings.data.Speakers
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.riffado.meetings.data.Comments
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
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
    onAsk: () -> Unit = {},
    onExtractTasks: (() -> Unit)? = null,
    taskCount: Int = 0,
    onSaveComment: (lineIndex: Int, line: Speakers.Turn, text: String) -> Unit = { _, _, _ -> },
    previewEditLine: Int? = null,
    initialTab: Int? = null,
    onExport: (com.riffado.meetings.export.ExportKind) -> Unit = {},
    exporting: com.riffado.meetings.export.ExportKind? = null,
    notionConfigured: Boolean = false,
    onNotionSetup: () -> Unit = {},
    previewExportSheet: Boolean = false,
    onOpenPerson: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val c = Apple.colors
    var tab by rememberSaveable(recording.id) { mutableIntStateOf(initialTab ?: if (recording.summary != null) 0 else 1) }
    var editingLine by remember(recording.id) { mutableStateOf(previewEditLine) }
    var exportSheet by remember(recording.id) { mutableStateOf(previewExportSheet) }
    var wasExporting by remember { mutableStateOf(false) }
    LaunchedEffect(exporting) {
        if (exporting != null) wasExporting = true
        else if (wasExporting) { wasExporting = false; exportSheet = false }
    }
    val lines = remember(recording.transcript, recording.speakerTurns) { Comments.lines(recording) }
    val anchored = remember(recording.comments, lines) { Comments.anchor(recording.comments, lines.map { it.text }) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editingSpeaker by remember { mutableStateOf<String?>(null) }
    val speakerIds = remember(recording.transcript) { Speakers.ids(recording.transcript) }

    val player = if (segmentPaths.isNotEmpty()) rememberPlayerController(segmentPaths) else null
    var waveform by remember(recording.id) { mutableStateOf<Waveform?>(null) }
    LaunchedEffect(recording.id, segmentPaths) {
        if (segmentPaths.isNotEmpty()) {
            waveform = WaveformExtractor.load(context, recording.id, segmentPaths.map(::File))
            waveform?.let { wf -> player?.hintSegmentMs = wf.segmentWindows.map { it * Waveform.WINDOW_MS } }
        }
    }

    val scroll = rememberScrollState()
    val collapsePx = with(LocalDensity.current) { 48.dp.toPx() }
    val haze = rememberHazeState()
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight
    val share = { exportSheet = true }

    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                AmbientBackdrop(accent = if (recording.isCall) c.green else c.tint)
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = topPad, bottom = if (player != null) 210.dp else 48.dp)) {
                    LargeTitle(recording.title)
                    MetaLine(recording)
                    Spacer(Modifier.height(14.dp))

                    if (recording.status.isBusy) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.orange)
                            Spacer(Modifier.width(8.dp))
                            Text(statusLabel(recording.status) + "…", style = AppleType.subheadline, color = c.orange)
                        }
                    }
                    recording.error?.let {
                        Text(
                            it, style = AppleType.footnote.auto(it), color = c.red,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp)).background(c.red.copy(alpha = 0.1f)).padding(12.dp),
                        )
                    }
                    ProcessButtons(recording, canProcess, onProcess)

                    if (speakerIds.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.speakers_title))
                        FlowRow(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            speakerIds.forEach { id -> SpeakerChip(id, recording.speakerNames, onClick = { editingSpeaker = id }) }
                            CapsuleButton(
                                stringResource(if (detectingSpeakers) R.string.speakers_identifying else R.string.speakers_redetect),
                                onRedetectSpeakers,
                                icon = Icons.Filled.PersonSearch,
                                enabled = !recording.status.isBusy && !detectingSpeakers,
                            )
                        }
                    }

                    if (recording.bookmarks.isNotEmpty() && player != null) {
                        SectionLabel(stringResource(R.string.detail_bookmarks))
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recording.bookmarks.forEach { at ->
                                CapsuleButton(formatDuration(at), { player.seekGlobal(at); player.play() }, icon = Icons.Filled.Bookmark, color = c.orange)
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    SegmentedControl(
                        listOf(stringResource(R.string.tab_summary), stringResource(R.string.tab_transcript)),
                        tab, { tab = it },
                        Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(InsetShape).background(c.cell).padding(16.dp),
                    ) {
                        if (tab == 0) {
                            val summary = recording.summary
                            if (summary.isNullOrBlank()) EmptyNote(stringResource(R.string.summary_empty), Icons.Filled.AutoAwesome)
                            else SimpleMarkdown(summary)
                        } else {
                            if (recording.transcript.isNullOrBlank()) EmptyNote(stringResource(R.string.transcript_empty), Icons.Filled.GraphicEq)
                            else TranscriptView(recording, lines, anchored, player, waveform, onEditLine = { editingLine = it })
                        }
                    }
                }
            }

            GlassTopBar(
                title = recording.title,
                collapsed = (scroll.value / collapsePx).coerceIn(0f, 1f),
                leading = {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack, size = 40.dp, iconSize = 20.dp)
                },
                trailing = {
                    GlassIconButton(
                        Icons.Filled.AutoAwesome, stringResource(R.string.ask_title), onAsk, size = 40.dp, iconSize = 20.dp, tint = c.purple,
                        enabled = recording.summary != null || recording.transcript != null,
                    )
                    GlassIconButton(
                        Icons.Filled.Share, stringResource(R.string.action_share), share, size = 40.dp, iconSize = 20.dp,
                        enabled = recording.summary != null || recording.transcript != null,
                    )
                    Box {
                        GlassIconButton(Icons.Filled.MoreHoriz, stringResource(R.string.action_more), { menu = true }, size = 40.dp, iconSize = 22.dp)
                        DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(18.dp), containerColor = c.cell) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = { menu = false; renaming = true },
                            )
                            if (speakerIds.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.speakers_redetect)) },
                                    leadingIcon = { Icon(Icons.Filled.PersonSearch, null) },
                                    enabled = !recording.status.isBusy && !detectingSpeakers,
                                    onClick = { menu = false; onRedetectSpeakers() },
                                )
                            }
                            if (onExtractTasks != null && (recording.summary != null || recording.transcript != null)) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.tasks_extract_this) +
                                                if (recording.tasksExtractedAt != null) " ($taskCount)" else "",
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                                    enabled = !recording.status.isBusy,
                                    onClick = { menu = false; onExtractTasks() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_title)) },
                                leadingIcon = { Icon(Icons.Filled.IosShare, null) },
                                onClick = { menu = false; exportSheet = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete), color = c.red) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = c.red) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )

            if (player != null) {
                PlayerBar(
                    player, waveform, recording.bookmarks,
                    Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            if (exportSheet) {
                ExportSheet(
                    title = recording.title,
                    busy = exporting,
                    notionConfigured = notionConfigured,
                    onExport = onExport,
                    onNotionSetup = { exportSheet = false; onNotionSetup() },
                    onDismiss = { exportSheet = false },
                )
            }

            editingLine?.let { index ->
                lines.getOrNull(index)?.let { line ->
                    val existing = anchored.byLine[index]
                    CommentEditor(
                        speaker = line.speaker?.let { Speakers.label(it, recording.speakerNames) },
                        quote = line.text.trim(),
                        initial = existing?.text.orEmpty(),
                        canDelete = existing != null,
                        onSave = { text -> editingLine = null; onSaveComment(index, line, text) },
                        onDelete = { editingLine = null; onSaveComment(index, line, "") },
                        onDismiss = { editingLine = null },
                    )
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
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.action_delete), color = c.red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
            shape = RoundedCornerShape(28.dp),
        )
    }

    editingSpeaker?.let { speaker ->
        var value by remember(speaker) { mutableStateOf(recording.speakerNames[speaker] ?: "") }
        AlertDialog(
            onDismissRequest = { editingSpeaker = null },
            title = { Text(stringResource(R.string.speaker_rename_title, speaker)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, placeholder = { Text("Speaker $speaker") }, shape = RoundedCornerShape(12.dp))
                    Text(stringResource(R.string.speaker_rename_hint), style = AppleType.footnote, color = c.secondaryLabel)
                }
            },
            confirmButton = { TextButton(onClick = { editingSpeaker = null; onRenameSpeaker(speaker, value) }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editingSpeaker = null }) { Text(stringResource(R.string.action_cancel)) } },
            shape = RoundedCornerShape(28.dp),
        )
    }

    if (renaming) {
        var value by remember { mutableStateOf(recording.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename_title)) },
            text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, shape = RoundedCornerShape(12.dp)) },
            confirmButton = { TextButton(onClick = { renaming = false; onRename(value) }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } },
            shape = RoundedCornerShape(28.dp),
        )
    }
}

@Composable
private fun MetaLine(recording: Recording) {
    val c = Apple.colors
    Column(Modifier.padding(horizontal = 20.dp)) {
        val who = if (recording.isCall) listOfNotNull(recording.callContactName, recording.callNumber?.let(::ltrIsolate)).joinToString(" · ") else null
        if (!who.isNullOrBlank() && who != recording.title && who != recording.callNumber?.let(::ltrIsolate)) {
            Text(who, style = AppleType.subheadline.auto(who), color = c.label)
        }
        Text(
            "${formatDate(recording.createdAt)} · ${formatDuration(recording.durationMs)} · ${statusLabel(recording.status)}",
            style = AppleType.footnote, color = c.secondaryLabel,
        )
        recording.transcribedBy?.let {
            Text(stringResource(R.string.detail_transcribed_by, providerDisplayName(it)), style = AppleType.footnote, color = c.tint)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold).auto(text), color = Apple.colors.secondaryLabel,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyNote(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Apple.colors.tertiaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = AppleType.subheadline.auto(text), color = Apple.colors.secondaryLabel)
    }
}

@Composable
private fun SpeakerChip(id: String, names: Map<String, String>, onClick: () -> Unit) {
    val label = Speakers.label(id, names)
    val color = speakerColor(id)
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Row(
        Modifier.height(40.dp).pressScale(source, 0.95f).glass(CircleShape, elevation = 3.dp)
            .clickable(source, indication = null, role = Role.Button) { haptics.tap(); onClick() }
            .padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.7f), color))), contentAlignment = Alignment.Center) {
            Text(HomeLogic.initials(label)?.take(1) ?: id, color = Color.White, style = AppleType.footnote.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold).auto(label), color = Apple.colors.label, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.Edit, null, tint = Apple.colors.tertiaryLabel, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun speakerColor(id: String): Color {
    val c = Apple.colors
    val palette = listOf(c.tint, c.orange, c.purple, c.green, c.pink, c.teal)
    return palette[((id.toIntOrNull() ?: 1) - 1).mod(palette.size)]
}

/**
 * Transcript as tappable lines: tapping a line with a timestamp seeks the player there; long-press
 * adds or edits a note on that line. Notes show inline under their line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TranscriptView(
    recording: Recording,
    turns: List<Speakers.Turn>,
    anchored: Comments.Anchored,
    player: PlayerController?,
    waveform: Waveform?,
    onEditLine: (Int) -> Unit,
) {
    val c = Apple.colors
    val haptics = rememberHaptics()
    val timed = player != null && turns.any { it.startMs != null }
    // Offset of each segment file from the start of the recording (for global timestamps).
    val segmentStart = remember(waveform, player?.paths) {
        val names = player?.paths?.map { File(it).name }.orEmpty()
        val lens = waveform?.segmentWindows?.map { it * Waveform.WINDOW_MS }.orEmpty()
        names.mapIndexed { i, n -> n to lens.take(i).sum() }.toMap()
    }
    val (playingSeg, playingOffset) = player?.segmentPosition() ?: (null to -1L)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.comments_hint), style = AppleType.caption, color = c.tertiaryLabel,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        )
        var previous: String? = null
        turns.forEachIndexed { index, turn ->
            val text = turn.text.trim()
            val start = turn.startMs
            val active = timed && start != null && player!!.playing.or(player.positionMs > 0) &&
                (turn.audioSegment == null || turn.audioSegment == playingSeg) &&
                playingOffset >= start && playingOffset < (turn.endMs ?: (start + 1))
            val note = anchored.byLine[index]
            val bg by animateColorAsState(
                when {
                    active -> c.tint.copy(alpha = 0.12f)
                    note != null -> c.orange.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
                label = "line",
            )
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
                    .combinedClickable(
                        onClick = {
                            if (timed && start != null) { haptics.selection(); player!!.seekSegment(turn.audioSegment, start); player.play() }
                        },
                        onLongClick = { haptics.heavy(); onEditLine(index) },
                        onLongClickLabel = stringResource(R.string.comments_add),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                if (turn.speaker != null && turn.speaker != previous) {
                    val label = Speakers.label(turn.speaker, recording.speakerNames)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold).auto(label), color = speakerColor(turn.speaker))
                        if (start != null) {
                            Spacer(Modifier.width(8.dp))
                            val global = start + (segmentStart[turn.audioSegment] ?: 0L)
                            Text(formatDuration(global), style = AppleType.caption.copy(fontFeatureSettings = "tnum"), color = c.tertiaryLabel)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(text, style = AppleType.body.auto(text), color = c.label, modifier = Modifier.fillMaxWidth())
                if (note != null) {
                    Spacer(Modifier.height(6.dp))
                    NoteBubble(note.text, onClick = { haptics.tap(); onEditLine(index) })
                }
            }
            previous = turn.speaker
        }
        if (anchored.orphans.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.comments_orphans), style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 8.dp))
            anchored.orphans.forEach { o ->
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("“${o.quote}”", style = AppleType.footnote.auto(o.quote), color = c.secondaryLabel, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    NoteBubble(o.text, onClick = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProcessButtons(recording: Recording, canProcess: Boolean, onProcess: (Boolean) -> Unit) {
    if (recording.status.isBusy || recording.status == RecordingStatus.RECORDING) return
    if (recording.segments.isEmpty()) return
    val hasTranscript = !recording.transcript.isNullOrBlank()
    FlowRow(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!hasTranscript || recording.status == RecordingStatus.FAILED) {
            CapsuleButton(
                stringResource(if (recording.status == RecordingStatus.FAILED) R.string.action_retry else R.string.action_transcribe),
                { onProcess(hasTranscript) },
                style = CapsuleStyle.Filled,
                icon = if (recording.status == RecordingStatus.FAILED) Icons.Filled.Refresh else Icons.Filled.GraphicEq,
                enabled = canProcess,
            )
        }
        if (hasTranscript && recording.status != RecordingStatus.FAILED) {
            CapsuleButton(stringResource(R.string.action_retranscribe), { onProcess(false) }, icon = Icons.Filled.GraphicEq, enabled = canProcess)
        }
        if (hasTranscript) {
            CapsuleButton(stringResource(R.string.action_resummarize), { onProcess(true) }, icon = Icons.Filled.AutoAwesome, enabled = canProcess, color = Apple.colors.purple)
        }
    }
}

/** Floating glass player with a waveform scrubber, ±10 s and play/pause. */
@Composable
private fun PlayerBar(player: PlayerController, waveform: Waveform?, bookmarks: List<Long>, modifier: Modifier = Modifier) {
    val c = Apple.colors
    val haptics = rememberHaptics()
    val duration = player.durationMs.takeIf { it > 0 } ?: waveform?.durationMs ?: 0L
    val progress = if (duration > 0) player.positionMs.toFloat() / duration else 0f
    Column(
        modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), elevation = 18.dp).padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        WaveformScrubber(
            waveform = waveform,
            progress = progress,
            onSeek = { f -> player.seekGlobal((f * duration).toLong()) },
            markers = if (duration > 0) bookmarks.map { (it.toFloat() / duration).coerceIn(0f, 1f) } else emptyList(),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(player.positionMs), style = AppleType.caption.copy(fontFeatureSettings = "tnum"), color = c.secondaryLabel)
            Spacer(Modifier.weight(1f))
            if (player.paths.size > 1) {
                Text(stringResource(R.string.part_of, player.segmentIndex + 1, player.paths.size), style = AppleType.caption, color = c.tertiaryLabel)
                Spacer(Modifier.weight(1f))
            }
            Text("-" + formatDuration((duration - player.positionMs).coerceAtLeast(0)), style = AppleType.caption.copy(fontFeatureSettings = "tnum"), color = c.secondaryLabel)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            PlainIcon(Icons.Filled.Replay10, stringResource(R.string.player_back10)) { haptics.tap(); player.skip(-10_000) }
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier.size(54.dp).pressScale(source, 0.9f).clip(CircleShape).background(c.label)
                    .clickable(source, indication = null, role = Role.Button) { haptics.tap(); player.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(if (player.playing) R.string.action_pause else R.string.action_play),
                    tint = c.background, modifier = Modifier.size(30.dp),
                )
            }
            PlainIcon(Icons.Filled.Forward10, stringResource(R.string.player_forward10)) { haptics.tap(); player.skip(10_000) }
        }
    }
}

@Composable
private fun PlainIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.size(48.dp).pressScale(source, 0.85f).clip(CircleShape).clickable(source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Apple.colors.label, modifier = Modifier.size(28.dp)) }
}

private fun providerDisplayName(providerId: String): String = when (providerId) {
    "elevenlabs" -> "ElevenLabs"
    "munsit" -> "Munsit"
    "gemini" -> "Gemini"
    "groq" -> "Groq"
    "sarvam" -> "Sarvam"
    else -> providerId.replaceFirstChar { it.uppercaseChar() }
}
