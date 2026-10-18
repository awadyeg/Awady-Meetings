package com.riffado.meetings.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure helpers for the Tasks tab: grouping by due date and merging re-extractions. */
object TaskLogic {
    enum class Bucket { OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER, NO_DATE }

    fun normalizeTitle(title: String): String =
        com.riffado.meetings.ui.RecordingSearch.normalizeArabic(title.lowercase())
            .replace(Regex("[\\p{P}\\p{S}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun taskId(recordingId: String, title: String): String =
        "$recordingId:${Integer.toHexString(normalizeTitle(title).hashCode())}"

    fun bucketOf(task: TaskItem, now: Long, zone: ZoneId = ZoneId.systemDefault()): Bucket {
        val due = task.dueAt ?: return Bucket.NO_DATE
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, dueDate)
        return when {
            days < 0 -> Bucket.OVERDUE
            days == 0L -> Bucket.TODAY
            days == 1L -> Bucket.TOMORROW
            days < 7 -> Bucket.THIS_WEEK
            else -> Bucket.LATER
        }
    }

    /** Groups tasks into due-date buckets (in [Bucket] order, empty buckets omitted). */
    fun group(tasks: List<TaskItem>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<Bucket, List<TaskItem>>> =
        tasks.groupBy { bucketOf(it, now, zone) }
            .toSortedMap()
            .map { (bucket, items) ->
                bucket to if (bucket == Bucket.NO_DATE) items.sortedByDescending { it.createdAt }
                else items.sortedWith(compareBy<TaskItem> { it.dueAt }.thenByDescending { it.createdAt })
            }

    fun isOverdue(task: TaskItem, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        !task.done && bucketOf(task, now, zone) == Bucket.OVERDUE

    /** Fresh extraction wins, but done state carries over for tasks whose normalised title matches. */
    fun merge(existing: List<TaskItem>, fresh: List<TaskItem>): List<TaskItem> {
        val doneTitles = existing.filter { it.done }.map { normalizeTitle(it.title) }.toSet()
        return fresh.map { if (normalizeTitle(it.title) in doneTitles) it.copy(done = true) else it }
    }

    /** Start of the local day of [epochMs]. */
    fun startOfDay(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun localDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
