package com.riffado.meetings.data

import com.riffado.meetings.data.TaskLogic.Bucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TaskLogicTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private fun at(d: Int, h: Int = 0) = LocalDateTime.of(2026, 10, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = at(18, 10) // Sunday 18 Oct 2026, 10:00
    private fun task(id: String, due: Long?, created: Long = 0) = TaskItem(id, "r", id, dueAt = due, createdAt = created)

    @Test fun bucketsByDueDate() {
        assertEquals(Bucket.OVERDUE, TaskLogic.bucketOf(task("a", at(17, 23)), now, zone))
        assertEquals(Bucket.TODAY, TaskLogic.bucketOf(task("b", at(18, 8)), now, zone)) // earlier today is still "today"
        assertEquals(Bucket.TOMORROW, TaskLogic.bucketOf(task("c", at(19)), now, zone))
        assertEquals(Bucket.THIS_WEEK, TaskLogic.bucketOf(task("d", at(24)), now, zone))
        assertEquals(Bucket.LATER, TaskLogic.bucketOf(task("e", at(25)), now, zone))
        assertEquals(Bucket.NO_DATE, TaskLogic.bucketOf(task("f", null), now, zone))
    }

    @Test fun groupsInOrderAndSortsWithinBuckets() {
        val tasks = listOf(
            task("later", at(30)), task("none-old", null, 1), task("week2", at(23)), task("week1", at(21)),
            task("overdue", at(10)), task("none-new", null, 2),
        )
        val groups = TaskLogic.group(tasks, now, zone)
        assertEquals(listOf(Bucket.OVERDUE, Bucket.THIS_WEEK, Bucket.LATER, Bucket.NO_DATE), groups.map { it.first })
        assertEquals(listOf("week1", "week2"), groups[1].second.map { it.id })
        assertEquals(listOf("none-new", "none-old"), groups[3].second.map { it.id })
    }

    @Test fun overdueIgnoresDoneTasks() {
        assertTrue(TaskLogic.isOverdue(task("a", at(1)), now, zone))
        assertFalse(TaskLogic.isOverdue(task("a", at(1)).copy(done = true), now, zone))
    }

    @Test fun mergeKeepsDoneStateByNormalisedTitle() {
        val existing = listOf(
            TaskItem("r:1", "r", "Send the notes", done = true),
            TaskItem("r:2", "r", "Old task gone", done = true),
            TaskItem("r:3", "r", "Book venue", done = false),
        )
        val fresh = listOf(
            TaskItem("r:a", "r", "send the notes."),
            TaskItem("r:b", "r", "Book venue"),
            TaskItem("r:c", "r", "New task"),
        )
        val merged = TaskLogic.merge(existing, fresh)
        assertEquals(listOf(true, false, false), merged.map { it.done })
        assertEquals(3, merged.size)
    }

    @Test fun normalisesArabicTitles() {
        assertEquals(TaskLogic.normalizeTitle("إرسال التقرير."), TaskLogic.normalizeTitle("ارسال التقرير"))
        assertEquals(TaskLogic.taskId("r", "إرسال التقرير."), TaskLogic.taskId("r", "ارسال التقرير"))
    }
}
