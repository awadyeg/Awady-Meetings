package com.riffado.meetings.ui

import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.TaskItem

/** Everything the person page shows about one contact / named speaker. */
data class PersonSummary(
    val name: String,
    val phone: String?,
    val recordings: List<Recording>,
    val calls: Int,
    val meetings: Int,
    val totalMs: Long,
    val lastAt: Long?,
)

/** Pure helpers for the person page (same matching rules as the Home person filter). */
object PersonLogic {
    fun summary(name: String, all: List<Recording>): PersonSummary {
        val recs = RecordingSearch.filterByPerson(all, name).sortedByDescending { it.createdAt }
        val key = RecordingSearch.normalizeArabic(name)
        val phone = recs.filter { it.isCall && it.callContactName?.let(RecordingSearch::normalizeArabic) == key }
            .mapNotNull { it.callNumber?.takeIf(String::isNotBlank) }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return PersonSummary(
            name = name,
            phone = phone,
            recordings = recs,
            calls = recs.count { it.isCall },
            meetings = recs.count { !it.isCall },
            totalMs = recs.sumOf { it.durationMs },
            lastAt = recs.firstOrNull()?.createdAt,
        )
    }

    /** Open tasks from this person's recordings, plus open tasks assigned to them anywhere. */
    fun openTasks(person: PersonSummary, tasks: List<TaskItem>): List<TaskItem> {
        val ids = person.recordings.map { it.id }.toSet()
        val key = RecordingSearch.normalizeArabic(person.name)
        return tasks.filter { !it.done && (it.recordingId in ids || it.assignee?.let(RecordingSearch::normalizeArabic) == key) }
            .sortedWith(compareBy<TaskItem, Long?>(nullsLast()) { it.dueAt }.thenByDescending { it.createdAt })
    }

    /** "1h 05m" / "12m" / "45s"; unit suffixes are localised by the caller (Arabic: س / د / ث). */
    fun formatTotal(ms: Long, h: String = "h", m: String = "m", sec: String = "s"): String {
        val s = ms / 1000
        return when {
            s >= 3600 -> "%d%s %02d%s".format(s / 3600, h, (s % 3600) / 60, m)
            s >= 60 -> "%d%s".format(s / 60, m)
            else -> "%d%s".format(s, sec)
        }
    }
}
