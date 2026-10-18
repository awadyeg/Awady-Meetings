package com.riffado.meetings.ui

import com.riffado.meetings.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.TaskItem
import com.riffado.meetings.data.RecordingStatus
import com.riffado.meetings.data.Speakers
import com.riffado.meetings.recording.LiveRecording
import kotlin.math.abs
import kotlin.math.sin

/** Sample data + previews for the Liquid Glass redesign (also rendered to PNG by the screenshot tests). */
object PreviewData {
    private const val H = 3_600_000L
    val now = System.currentTimeMillis()
    private const val D = 24 * H

    val recordings = listOf(
        Recording(
            id = "1", title = "Weekly product sync", createdAt = now - H, durationMs = 1_520_000, status = RecordingStatus.DONE,
            segments = listOf("1-0.m4a"),
            summary = "# Weekly product sync\n## Summary\nThe team agreed to ship the redesign next sprint and move the MCP server to phase 2.\n## Action items\n- [ ] Sara prepares the release notes\n- [ ] Omar reviews the onboarding flow",
        ),
        Recording(
            id = "2", title = "أحمد حسن", createdAt = now - 2 * H, durationMs = 312_000, status = RecordingStatus.DONE,
            segments = listOf("2-0.m4a"), tags = setOf("call"), callContactName = "أحمد حسن", callNumber = "+20 100 123 4567",
            summary = "## الملخص\nاتفقنا على تسليم النسخة التجريبية يوم الخميس ومراجعة الـ API مع الفريق.",
        ),
        Recording(
            id = "3", title = "+971 50 555 0101", createdAt = now - 26 * H, durationMs = 95_000, status = RecordingStatus.TRANSCRIBING,
            segments = listOf("3-0.m4a"), tags = setOf("call"), callNumber = "+971 50 555 0101",
        ),
        Recording(
            id = "4", title = "Client project review", createdAt = now - 80 * H, durationMs = 2_710_000, status = RecordingStatus.DONE,
            segments = listOf("4-0.m4a"),
            summary = "## Summary\nReviewed milestones with the client; budget approved for the Android app.",
        ),
        Recording(
            id = "5", title = "Sara Ali", createdAt = now - 300 * H, durationMs = 640_000, status = RecordingStatus.FAILED,
            segments = listOf("5-0.m4a"), tags = setOf("call"), callContactName = "Sara Ali", callNumber = "+971 55 222 3344",
        ),
    )

    val detail = Recording(
        id = "d", title = "Weekly product sync", createdAt = now - H, durationMs = 1_520_000, status = RecordingStatus.DONE,
        segments = listOf("d-0.m4a"), transcribedBy = "elevenlabs", bookmarks = listOf(65_000, 410_000),
        speakerNames = mapOf("1" to "Mohamed", "2" to "Sara"),
        transcript = "Speaker 1: Let's start with the redesign status.\nSpeaker 2: الشاشة الرئيسية خلصت والـ glass effect شغال.\nSpeaker 1: Great, and the recorder view?\nSpeaker 2: Done too, with bookmarks.",
        speakerTurns = listOf(
            Speakers.Turn("1", "Let's start with the redesign status.", 0, 4_000, "d-0.m4a"),
            Speakers.Turn("2", "الشاشة الرئيسية خلصت والـ glass effect شغال.", 4_000, 9_000, "d-0.m4a"),
            Speakers.Turn("1", "Great, and the recorder view?", 9_000, 12_000, "d-0.m4a"),
            Speakers.Turn("2", "Done too, with bookmarks.", 12_000, 15_000, "d-0.m4a"),
        ),
        summary = "# Weekly product sync\n## Summary\nThe team agreed to ship the redesign next sprint.\n## Key points\n- Glass tab bar is done\n- الملخص بالعربي بيظهر من اليمين\n## Action items\n- [ ] Sara prepares the release notes",
    )

    private val today = com.riffado.meetings.data.TaskLogic.startOfDay(now)
    val tasks = listOf(
        TaskItem("t1", "1", "Prepare the release notes", assignee = "Sara", dueAt = today - D, dueText = "yesterday", createdAt = now - H),
        TaskItem("t2", "2", "تسليم النسخة التجريبية", assignee = "أحمد حسن", dueAt = today + 15 * H + H / 2, allDay = false, createdAt = now - 2 * H),
        TaskItem("t3", "1", "Review the onboarding flow", assignee = "Omar", dueAt = today + D, createdAt = now - H),
        TaskItem("t4", "4", "Send the Android app budget to the client", dueAt = today + 4 * D, createdAt = now - 80 * H),
        TaskItem("t5", "2", "مراجعة الـ API مع الفريق", dueAt = today + 12 * D, createdAt = now - 2 * H),
        TaskItem("t6", "4", "Share the milestone deck", assignee = "Mohamed", createdAt = now - 80 * H),
        TaskItem("t7", "1", "Book the demo room", dueAt = today - 2 * D, done = true, createdAt = now - H),
    )

    /** Extra recordings so the person page has calls and meetings to show. */
    val personRecordings = recordings + listOf(
        Recording(
            id = "p1", title = "Sara Ali", createdAt = now - 50 * H, durationMs = 540_000, status = RecordingStatus.DONE,
            segments = listOf("p1-0.m4a"), tags = setOf("call"), callContactName = "Sara Ali", callNumber = "+971 55 222 3344",
            summary = "## Summary\nSara confirmed the contract terms; she will send the signed copy by Thursday.",
        ),
        Recording(
            id = "p2", title = "Design review with Sara", createdAt = now - 120 * H, durationMs = 2_280_000, status = RecordingStatus.DONE,
            segments = listOf("p2-0.m4a"), speakerNames = mapOf("1" to "Mohamed", "2" to "Sara Ali"),
            summary = "## Summary\nWalked through the Liquid Glass screens; agreed on the person page layout.",
        ),
        Recording(
            id = "p3", title = "أحمد حسن", createdAt = now - 70 * H, durationMs = 410_000, status = RecordingStatus.DONE,
            segments = listOf("p3-0.m4a"), tags = setOf("call"), callContactName = "أحمد حسن", callNumber = "+20 100 123 4567",
            summary = "## الملخص\nراجعنا خطة الإطلاق واتفقنا على موعد العرض للعميل.",
        ),
        Recording(
            id = "p4", title = "اجتماع فريق التطوير", createdAt = now - 200 * H, durationMs = 1_960_000, status = RecordingStatus.DONE,
            segments = listOf("p4-0.m4a"), speakerNames = mapOf("1" to "محمد", "2" to "أحمد حسن"),
            summary = "## الملخص\nتوزيع مهام الإصدار الثاني ومراجعة الأخطاء المفتوحة.",
        ),
    )
    val personTasks = tasks + listOf(
        TaskItem("pt1", "p1", "Send the signed contract", assignee = "Sara Ali", dueAt = today + 3 * D, dueText = "by Thursday", createdAt = now - 50 * H),
        TaskItem("pt2", "p2", "Share the person page mock-ups", dueAt = today + D, createdAt = now - 120 * H),
        TaskItem("pt3", "p3", "تجهيز العرض للعميل", assignee = "أحمد حسن", dueAt = today + 2 * D, createdAt = now - 70 * H),
    )

    val askConversation = Conversation(
        entries = listOf(
            ChatEntry(true, "What did we agree on the beta release?"),
            ChatEntry(
                false,
                "You agreed to **ship the redesign next sprint** [1], and Ahmed confirmed the beta build is due **Thursday**, with an API review with the team [2].\n\n- Sara prepares the release notes [1]\n- Omar reviews the onboarding flow [1]",
                sources = listOf(
                    com.riffado.meetings.ai.AskAi.Source(1, "1", "Weekly product sync", now - H),
                    com.riffado.meetings.ai.AskAi.Source(2, "2", "أحمد حسن", now - 2 * H),
                ),
            ),
            ChatEntry(true, "إمتى ميعاد تسليم النسخة التجريبية؟"),
        ),
        busy = true,
    )

    val commented = detail.copy(
        comments = listOf(
            com.riffado.meetings.data.LineComment("c1", 1, "الشاشة الرئيسية خلصت والـ glass effect شغال.", "نتأكد من الأداء على الأجهزة القديمة قبل الإصدار", "2", 4_000, now, now),
            com.riffado.meetings.data.LineComment("c2", 3, "Done too, with bookmarks.", "Ask Sara for the bookmark export format", "2", 12_000, now, now),
        ),
    )

    val levels = List(160) { i -> (abs(sin(i / 3.0)) * (0.3 + 0.7 * abs(sin(i / 17.0)))).toFloat() * 0.6f }
}

@Composable
fun PreviewHome() {
    Box(Modifier.fillMaxSize()) {
        HomeScreen(PreviewData.recordings, LiveRecording(), configured = true, onImport = {}, onOpen = {}, onSettings = {})
        GlassTabBar(AppTab.Home, {}, recording = false, onRecord = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

@Composable
fun PreviewHomeRecording() {
    val live = LiveRecording(recordingId = "x", elapsedMs = 754_000, level = 0.4f)
    Box(Modifier.fillMaxSize()) {
        HomeScreen(PreviewData.recordings, live, configured = false, onImport = {}, onOpen = {}, onSettings = {})
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MiniRecordingPill(live, PreviewData.levels, {}, {}, {}, {}, Modifier.padding(bottom = 12.dp))
            GlassTabBar(AppTab.Home, {}, recording = true, onRecord = {})
        }
    }
}

@Composable
fun PreviewRecorder() {
    RecorderScreen(
        live = LiveRecording(recordingId = "x", elapsedMs = 754_000, level = 0.4f),
        title = "Weekly product sync",
        levels = PreviewData.levels,
        bookmarks = listOf(65_000),
        onMinimize = {}, onPause = {}, onResume = {}, onStop = {}, onBookmark = {},
    )
}

@Composable
fun PreviewDetail(transcriptTab: Boolean = false) {
    val rec = if (transcriptTab) PreviewData.detail.copy(summary = null) else PreviewData.detail
    DetailScreen(
        recording = rec, segmentPaths = emptyList(), canProcess = true,
        onBack = {}, onProcess = {}, onRename = {}, onRenameSpeaker = { _, _ -> }, onDelete = {},
    )
}

@Composable
fun PreviewComments(editLine: Int? = null) {
    DetailScreen(
        recording = PreviewData.commented, segmentPaths = emptyList(), canProcess = true,
        onBack = {}, onProcess = {}, onRename = {}, onRenameSpeaker = { _, _ -> }, onDelete = {},
        previewEditLine = editLine, initialTab = 1,
    )
}

@Composable
fun PreviewExportSheet(busy: com.riffado.meetings.export.ExportKind? = null, notionConfigured: Boolean = true) {
    DetailScreen(
        recording = PreviewData.commented, segmentPaths = emptyList(), canProcess = true,
        onBack = {}, onProcess = {}, onRename = {}, onRenameSpeaker = { _, _ -> }, onDelete = {},
        previewExportSheet = true, exporting = busy, notionConfigured = notionConfigured,
    )
}

@Composable
fun PreviewPerson(name: String = "Sara Ali") {
    val person = PersonLogic.summary(name, PreviewData.personRecordings)
    PersonScreen(
        person = person, tasks = PersonLogic.openTasks(person, PreviewData.personTasks), live = LiveRecording(),
        onBack = {}, onOpen = {}, onAsk = {}, onToggleTask = { _, _ -> }, onAddToCalendar = {}, now = PreviewData.now,
    )
}

/** Settings → Connect AI agents (MCP), rendered on its own (the full Settings screen needs MeetingsApp). */
@Composable
fun PreviewMcpSettings(enabled: Boolean = true, httpStyle: Boolean = false) {
    val c = Apple.colors
    val state = McpUiState(
        settings = com.riffado.meetings.mcp.McpSettings(enabled = enabled, port = 8765, token = "rfd_9Jx2kQvW7mZpL4tN8sR1cY6bH3dF0gUaEoIiXwVnKqT", keepAwake = true),
        server = if (enabled) com.riffado.meetings.mcp.McpServerState.Running(8765, PreviewData.now - 3_600_000, requests = 12) else com.riffado.meetings.mcp.McpServerState.Stopped,
        addresses = listOf(
            com.riffado.meetings.mcp.McpNetwork.LanAddress("192.168.1.42", "wlan0", com.riffado.meetings.mcp.McpNetwork.Kind.WIFI),
            com.riffado.meetings.mcp.McpNetwork.LanAddress("100.101.7.23", "tun0", com.riffado.meetings.mcp.McpNetwork.Kind.TAILSCALE),
        ),
    )
    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, bottom = 40.dp),
    ) {
        LargeTitle(stringResource(R.string.settings_title))
        Spacer(Modifier.height(8.dp))
        McpSettingsSection(state, McpActions({}, {}, {}, {}), initialStyle = if (httpStyle) 1 else 0)
    }
}

@Composable
fun PreviewTasks() {
    Box(Modifier.fillMaxSize()) {
        TasksScreen(
            tasks = PreviewData.tasks, recordings = PreviewData.recordings, onOpen = {}, onToggle = { _, _ -> },
            onAddToCalendar = {}, onAddAllToCalendar = {}, onExtractMissing = {}, onExtractAll = {},
            missingCount = 2, extraction = null, now = PreviewData.now,
        )
        GlassTabBar(AppTab.Tasks, {}, recording = false, onRecord = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

@Composable
fun PreviewTasksDone() {
    TasksScreen(
        tasks = PreviewData.tasks, recordings = PreviewData.recordings, onOpen = {}, onToggle = { _, _ -> },
        onAddToCalendar = {}, onAddAllToCalendar = {}, onExtractMissing = {}, onExtractAll = {},
        missingCount = 0, extraction = TaskExtractionUi(2, 5), now = PreviewData.now, initialSegment = 1,
    )
}

@Composable
fun PreviewAskAi(empty: Boolean = false, scopeTitle: String? = null) {
    AskAiScreen(
        conversation = if (empty) Conversation() else PreviewData.askConversation,
        scopeTitle = scopeTitle,
        onAsk = {}, onRetry = {}, onClear = {}, onOpenRecording = {}, onBack = {},
    )
}

@Preview(name = "Home – light", showSystemUi = true)
@Composable
private fun HomeLightPreview() = MeetingsTheme(darkTheme = false) { PreviewHome() }

@Preview(name = "Home – dark", showSystemUi = true)
@Composable
private fun HomeDarkPreview() = MeetingsTheme(darkTheme = true) { PreviewHome() }

@Preview(name = "Home – recording pill", showSystemUi = true)
@Composable
private fun HomeRecordingPreview() = MeetingsTheme(darkTheme = false) { PreviewHomeRecording() }

@Preview(name = "Recorder – dark", showSystemUi = true)
@Composable
private fun RecorderPreview() = MeetingsTheme(darkTheme = true) { PreviewRecorder() }

@Preview(name = "Detail – light", showSystemUi = true)
@Composable
private fun DetailPreview() = MeetingsTheme(darkTheme = false) { PreviewDetail() }

@Preview(name = "Tasks – light", showSystemUi = true)
@Composable
private fun TasksPreview() = MeetingsTheme(darkTheme = false) { PreviewTasks() }
