package com.riffado.meetings.ui

import com.riffado.meetings.data.Recording
import java.util.Calendar
import java.util.TimeZone

/** Pure helpers behind the home list; kept free of Android/Compose so they are unit-testable. */
object HomeLogic {
    enum class Section { TODAY, YESTERDAY, THIS_WEEK, OLDER }

    data class Group(val section: Section, val items: List<Recording>)

    private fun startOfDay(ms: Long, zone: TimeZone): Long = Calendar.getInstance(zone).run {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    private fun daysBefore(dayStart: Long, days: Int, zone: TimeZone): Long = Calendar.getInstance(zone).run {
        timeInMillis = dayStart
        add(Calendar.DAY_OF_YEAR, -days)
        timeInMillis
    }

    fun sectionOf(createdAt: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): Section {
        val today = startOfDay(now, zone)
        val yesterday = daysBefore(today, 1, zone)
        val weekAgo = daysBefore(today, 6, zone)
        return when {
            createdAt >= today -> Section.TODAY
            createdAt >= yesterday -> Section.YESTERDAY
            createdAt >= weekAgo -> Section.THIS_WEEK
            else -> Section.OLDER
        }
    }

    /** Groups recordings (newest first inside each group); empty groups are dropped. */
    fun group(recordings: List<Recording>, now: Long, zone: TimeZone = TimeZone.getDefault()): List<Group> =
        recordings.sortedByDescending { it.createdAt }
            .groupBy { sectionOf(it.createdAt, now, zone) }
            .toSortedMap()
            .map { (section, items) -> Group(section, items) }

    private val heading = Regex("^#{1,6}\\s")
    private val bullet = Regex("^\\s*(?:[-*•]|\\d+[.)])\\s+(?:\\[[ xX]]\\s+)?")
    private val emphasis = Regex("[*_`]{1,3}")

    /** First meaningful prose from Markdown minutes, without headings or list markers. */
    fun summarySnippet(summary: String?, maxChars: Int = 160): String? {
        if (summary.isNullOrBlank()) return null
        val text = summary.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !heading.containsMatchIn(it) && it != "-" }
            .map { it.replace(bullet, "").replace(emphasis, "").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (text.isEmpty()) return null
        return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "…"
    }

    /** Up to two initials for an avatar; null for phone numbers / empty names (callers show an icon). */
    fun initials(name: String?): String? {
        val clean = name?.trim().orEmpty()
        if (clean.isEmpty()) return null
        if (clean.none { it.isLetter() }) return null
        val words = clean.split(Regex("\\s+")).filter { w -> w.any { it.isLetter() } }
        val letters = words.take(2).mapNotNull { w -> w.firstOrNull { it.isLetter() } }
        return letters.joinToString("") { it.uppercase() }.ifEmpty { null }
    }

    /** Open action items (`- [ ] ...`) from Markdown minutes. */
    fun actionItems(summary: String?): List<String> =
        summary.orEmpty().lines()
            .map { it.trim() }
            .filter { it.startsWith("- [ ]") || it.startsWith("* [ ]") }
            .map { it.drop(5).trim() }
            .filter { it.isNotEmpty() && it != "-" }
}
