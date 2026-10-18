package com.riffado.meetings.ui

import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.RecordingStatus
import com.riffado.meetings.data.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonLogicTest {
    private fun call(id: String, name: String?, number: String?, at: Long, ms: Long) = Recording(
        id = id, title = name ?: number.orEmpty(), createdAt = at, durationMs = ms, status = RecordingStatus.DONE,
        tags = setOf("call"), callContactName = name, callNumber = number,
    )

    private val recs = listOf(
        call("c1", "Sara Ali", "+971 55 222 3344", 1_000, 60_000),
        call("c2", "Sara Ali", "+971 55 222 3344", 3_000, 120_000),
        call("c3", "Sara Ali", "+971 50 000 0000", 2_000, 30_000),
        call("c4", "أحمد حسن", "+20 100", 4_000, 10_000),
        Recording(id = "m1", title = "Review", createdAt = 5_000, durationMs = 3_600_000, speakerNames = mapOf("2" to "Sara Ali")),
        Recording(id = "m2", title = "Other", createdAt = 6_000, durationMs = 1_000),
    )

    @Test fun summaryCountsCallsMeetingsAndDuration() {
        val p = PersonLogic.summary("Sara Ali", recs)
        assertEquals(listOf("m1", "c2", "c3", "c1"), p.recordings.map { it.id })
        assertEquals(3, p.calls)
        assertEquals(1, p.meetings)
        assertEquals(60_000L + 120_000 + 30_000 + 3_600_000, p.totalMs)
        assertEquals(5_000L, p.lastAt)
        assertEquals("+971 55 222 3344", p.phone) // most frequent number
    }

    @Test fun arabicNameMatchesNormalised() {
        val p = PersonLogic.summary("احمد حسن", recs) // no hamza
        assertEquals(listOf("c4"), p.recordings.map { it.id })
        assertEquals("+20 100", p.phone)
    }

    @Test fun personWithOnlyMeetingsHasNoPhone() {
        val p = PersonLogic.summary("Sara Ali", recs.filter { !it.isCall })
        assertNull(p.phone)
        assertEquals(0, p.calls)
    }

    @Test fun openTasksFromTheirRecordingsOrAssignedToThem() {
        val p = PersonLogic.summary("Sara Ali", recs)
        val tasks = listOf(
            TaskItem("a", "c1", "From her call", dueAt = 500),
            TaskItem("b", "m2", "Assigned to her elsewhere", assignee = "sara ali", dueAt = 100),
            TaskItem("c", "m2", "Someone else's"),
            TaskItem("d", "c2", "Done already", done = true),
            TaskItem("e", "m1", "No due date", createdAt = 9),
        )
        assertEquals(listOf("b", "a", "e"), PersonLogic.openTasks(p, tasks).map { it.id })
    }

    @Test fun formatsTotal() {
        assertEquals("45s", PersonLogic.formatTotal(45_000))
        assertEquals("12m", PersonLogic.formatTotal(12 * 60_000L + 5_000))
        assertEquals("1h 05m", PersonLogic.formatTotal(3_900_000))
    }
}
