package com.riffado.meetings.export

import com.riffado.meetings.data.Comments
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.Speakers
import com.riffado.meetings.data.TaskItem
import com.riffado.meetings.data.TaskLogic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Section headings and words used in exports; English defaults, localised from resources on device. */
data class ExportLabels(
    val date: String = "Date",
    val duration: String = "Duration",
    val people: String = "People",
    val summary: String = "Summary",
    val tasks: String = "Tasks",
    val bookmarks: String = "Bookmarks",
    val notes: String = "Notes",
    val transcript: String = "Transcript",
    val due: String = "due",
    val exportedBy: String = "Exported from Awady meetings",
)

/** Everything an export needs about one recording, gathered once. */
data class ExportData(
    val recording: Recording,
    val tasks: List<TaskItem>,
) {
    val people: List<String> get() = RecordingExport.people(recording)
    val lines: List<Speakers.Turn> get() = Comments.lines(recording)
}

/** Pure text exports: Markdown file and the short WhatsApp/share text. */
object RecordingExport {
    fun people(r: Recording): List<String> = buildList {
        if (r.isCall) {
            r.callContactName?.takeIf { it.isNotBlank() }?.let(::add)
            r.callNumber?.takeIf { it.isNotBlank() }?.let { if (isEmpty()) add(it) }
        }
        r.speakerNames.forEach { (id, name) ->
            if (name.isNotBlank() && name != "Speaker $id" && name != "المتحدث $id") add(name)
        }
    }.distinct()

    /** Tasks to export: extracted tasks for the recording, else open `- [ ]` items from the minutes. */
    fun tasksFor(r: Recording, all: List<TaskItem>): List<TaskItem> {
        val extracted = all.filter { it.recordingId == r.id }
        if (extracted.isNotEmpty() || r.tasksExtractedAt != null) return extracted.sortedWith(compareBy(nullsLast()) { it.dueAt })
        return r.summary.orEmpty().lines().map { it.trim() }
            .filter { it.startsWith("- [ ]") || it.startsWith("* [ ]") }
            .map { it.drop(5).trim() }.filter { it.isNotEmpty() }
            .map { TaskItem(TaskLogic.taskId(r.id, it), r.id, it) }
    }

    fun formatDuration(ms: Long): String {
        val t = ms / 1000
        return if (t >= 3600) String.format(Locale.US, "%d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
        else String.format(Locale.US, "%02d:%02d", t / 60, t % 60)
    }

    fun formatDate(ms: Long, locale: Locale = Locale.getDefault(), tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("EEE d MMM yyyy, HH:mm", locale).apply { timeZone = tz }.format(Date(ms))

    fun formatDue(task: TaskItem, locale: Locale = Locale.getDefault(), tz: TimeZone = TimeZone.getDefault()): String? {
        val due = task.dueAt ?: return task.dueText
        val pattern = if (task.allDay) "EEE d MMM yyyy" else "EEE d MMM yyyy, HH:mm"
        return SimpleDateFormat(pattern, locale).apply { timeZone = tz }.format(Date(due))
    }

    /** "Title — Sara · due Thu 22 Oct 2026" */
    fun taskLine(task: TaskItem, labels: ExportLabels, locale: Locale = Locale.getDefault(), tz: TimeZone = TimeZone.getDefault()): String =
        buildString {
            append(task.title)
            val meta = listOfNotNull(task.assignee, formatDue(task, locale, tz)?.let { "${labels.due} $it" })
            if (meta.isNotEmpty()) append(" — ").append(meta.joinToString(" · "))
        }

    /**
     * The summary without its own "# Title" line (the export already has the title) and without a
     * leading "## Summary" heading that would repeat the export's section heading.
     */
    fun summaryBody(r: Recording, sectionName: String = "Summary"): String? {
        var lines = r.summary?.trim()?.takeIf { it.isNotEmpty() }?.lines() ?: return null
        if (lines.first().trim().startsWith("# ")) lines = lines.drop(1).dropWhile { it.isBlank() }
        val first = lines.firstOrNull()?.trim().orEmpty()
        if (first.startsWith("#")) {
            val h = first.trimStart('#').trim()
            if (h.equals(sectionName, true) || h.equals("Summary", true) || h == "الملخص") lines = lines.drop(1)
        }
        return lines.joinToString("\n").trim().takeIf { it.isNotEmpty() }
    }

    private fun noteLine(r: Recording, c: com.riffado.meetings.data.LineComment): String {
        val who = c.speaker?.let { Speakers.label(it, r.speakerNames) + ": " }.orEmpty()
        return who + c.quote
    }

    fun markdown(data: ExportData, labels: ExportLabels = ExportLabels(), locale: Locale = Locale.getDefault(), tz: TimeZone = TimeZone.getDefault()): String {
        val r = data.recording
        return buildString {
            append("# ").append(r.title).append("\n\n")
            append("- **").append(labels.date).append(":** ").append(formatDate(r.createdAt, locale, tz)).append('\n')
            if (r.durationMs > 0) append("- **").append(labels.duration).append(":** ").append(formatDuration(r.durationMs)).append('\n')
            val people = data.people
            if (people.isNotEmpty()) append("- **").append(labels.people).append(":** ").append(people.joinToString(", ")).append('\n')
            if (r.isCall && !r.callNumber.isNullOrBlank() && r.callNumber !in people) append("- ").append(r.callNumber).append('\n')

            summaryBody(r, labels.summary)?.let { append("\n## ").append(labels.summary).append("\n\n").append(demoteHeadings(it)).append('\n') }

            if (data.tasks.isNotEmpty()) {
                append("\n## ").append(labels.tasks).append("\n\n")
                data.tasks.forEach { t -> append(if (t.done) "- [x] " else "- [ ] ").append(taskLine(t, labels, locale, tz)).append('\n') }
            }
            if (r.bookmarks.isNotEmpty()) {
                append("\n## ").append(labels.bookmarks).append("\n\n")
                r.bookmarks.forEach { append("- ").append(formatDuration(it)).append('\n') }
            }
            if (r.comments.isNotEmpty()) {
                append("\n## ").append(labels.notes).append("\n\n")
                r.comments.forEach { c ->
                    val at = c.startMs?.let { "[${formatDuration(it)}] " }.orEmpty()
                    append("- ").append(at).append("> ").append(noteLine(r, c)).append("\n  ").append(c.text).append('\n')
                }
            }
            val lines = data.lines
            if (lines.isNotEmpty()) {
                append("\n## ").append(labels.transcript).append("\n\n")
                val anchored = Comments.anchor(r.comments, lines.map { it.text })
                lines.forEachIndexed { i, t ->
                    if (t.speaker != null) append("**").append(Speakers.label(t.speaker, r.speakerNames)).append(":** ")
                    append(t.text.trim()).append("\n")
                    anchored.byLine[i]?.let { append("> 📝 ").append(it.text).append('\n') }
                    append('\n')
                }
            }
            append("\n---\n_").append(labels.exportedBy).append("_\n")
        }
    }

    /** Short text for WhatsApp & co: title, date, summary without markdown, tasks. */
    fun shareText(data: ExportData, labels: ExportLabels = ExportLabels(), locale: Locale = Locale.getDefault(), tz: TimeZone = TimeZone.getDefault()): String {
        val r = data.recording
        return buildString {
            append('*').append(r.title).append("*\n")
            append(formatDate(r.createdAt, locale, tz))
            if (data.people.isNotEmpty()) append(" · ").append(data.people.joinToString(", "))
            append('\n')
            summaryBody(r, labels.summary)?.let { body ->
                // Tasks are listed separately below, so drop the summary's own checklist lines.
                val text = plain(body.lines().filterNot { l -> l.trim().let { it.startsWith("- [") || it.startsWith("* [") } }.joinToString("\n"))
                if (text.isNotBlank()) append('\n').append(text.trim()).append('\n')
            }
            if (data.tasks.isNotEmpty()) {
                append("\n*").append(labels.tasks).append("*\n")
                data.tasks.forEach { t -> append(if (t.done) "✅ " else "☐ ").append(taskLine(t, labels, locale, tz)).append('\n') }
            }
        }.trim()
    }

    /** Markdown → WhatsApp-friendly text: headings become *bold*, bullets become •, `**x**` becomes *x*. */
    fun plain(markdown: String): String =
        markdown.lines().joinToString("\n") { line ->
            val t = line.trimEnd()
            val s = t.trimStart()
            when {
                s.startsWith("#") -> "*" + s.trimStart('#').trim().replace("**", "") + "*"
                s.startsWith("- ") || s.startsWith("* ") -> "• " + s.drop(2).replace("**", "*")
                else -> t.replace("**", "*")
            }
        }.replace(Regex("\n{3,}"), "\n\n")

    private fun demoteHeadings(md: String): String =
        md.lines().joinToString("\n") { if (it.startsWith("#")) "#$it" else it }

    /** File-system-safe file name for the export. */
    fun fileName(r: Recording, ext: String): String {
        val base = r.title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").replace(Regex("\\s+"), " ").trim().take(60).ifBlank { "recording" }
        return "$base.$ext"
    }
}
