package com.riffado.meetings.export

import com.riffado.meetings.data.LineComment
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.TaskItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

object Fixtures {
    val zone: ZoneId = ZoneId.of("Asia/Dubai")
    val tz: TimeZone = TimeZone.getTimeZone("Asia/Dubai")
    val locale: Locale = Locale.US
    val created = LocalDateTime.of(2026, 10, 18, 14, 30).atZone(zone).toInstant().toEpochMilli()

    val recording = Recording(
        id = "r1", title = "Weekly sync", createdAt = created, durationMs = 1_520_000,
        transcript = "Speaker 1: Let's start.\nSpeaker 2: الشاشة الرئيسية خلصت.\nSpeaker 1: Ship it Thursday.",
        speakerNames = mapOf("1" to "Mohamed", "2" to "Sara"),
        summary = "# Weekly sync\n## Summary\nWe agreed to **ship** on Thursday.\n## Action items\n- [ ] Sara: release notes",
        bookmarks = listOf(65_000),
        comments = listOf(LineComment("c1", 2, "Ship it Thursday.", "Confirm with QA", "1", 9_000)),
        tasksExtractedAt = 1,
    )

    val tasks = listOf(
        TaskItem("t1", "r1", "Release notes", assignee = "Sara", dueAt = LocalDate.of(2026, 10, 22).atStartOfDay(zone).toInstant().toEpochMilli()),
        TaskItem("t2", "r1", "Book room", done = true),
    )

    val data = ExportData(recording, tasks)
}
