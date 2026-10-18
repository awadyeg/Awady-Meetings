package com.riffado.meetings.ai

import com.riffado.meetings.data.Recording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TaskExtractorTest {
    private val dubai = ZoneId.of("Asia/Dubai")
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(dubai).toInstant().toEpochMilli()

    @Test fun parsesCleanObject() {
        val raw = """{"tasks":[
            {"title":"Send the release notes","assignee":"Sara","due":"2026-10-22","due_text":"by Thursday"},
            {"title":"Call the client","assignee":null,"due":"2026-10-20T15:30","due_text":"Tuesday 3:30pm"},
            {"title":"Review onboarding","assignee":"none","due":null,"due_text":null}
        ]}"""
        val tasks = TaskExtractor.parse(raw, "r1", now = 5, zone = dubai)
        assertEquals(3, tasks.size)
        with(tasks[0]) {
            assertEquals("Send the release notes", title)
            assertEquals("Sara", assignee)
            assertEquals(day(2026, 10, 22), dueAt)
            assertTrue(allDay)
            assertEquals("by Thursday", dueText)
            assertEquals("r1", recordingId)
            assertEquals(5L, createdAt)
            assertFalse(done)
        }
        with(tasks[1]) {
            assertNull(assignee)
            assertFalse(allDay)
            assertEquals(LocalDateTime.of(2026, 10, 20, 15, 30).atZone(dubai).toInstant().toEpochMilli(), dueAt)
        }
        with(tasks[2]) {
            assertNull(assignee)  // "none" is treated as no assignee
            assertNull(dueAt)
            assertNull(dueText)
        }
    }

    @Test fun toleratesFencesProseAndArrays() {
        val fenced = "Here are the tasks:\n```json\n[{\"task\":\"تجهيز العرض\",\"owner\":\"أحمد\",\"deadline\":\"2026-11-01\"}]\n```\nHope this helps {not json}"
        val tasks = TaskExtractor.parse(fenced, "r2", zone = dubai)
        assertEquals(1, tasks.size)
        assertEquals("تجهيز العرض", tasks[0].title)
        assertEquals("أحمد", tasks[0].assignee)
        assertEquals(day(2026, 11, 1), tasks[0].dueAt)
    }

    @Test fun acceptsStringItemsAliasKeysAndDedupes() {
        val raw = """{"action_items":["- [ ] Book the venue","Book the venue!","  ", {"title":""}]}"""
        val tasks = TaskExtractor.parse(raw, "r3")
        assertEquals(listOf("Book the venue"), tasks.map { it.title })
    }

    @Test fun emptyOrGarbageGivesEmptyList() {
        assertTrue(TaskExtractor.parse("""{"tasks":[]}""", "r").isEmpty())
        assertTrue(TaskExtractor.parse("Sorry, I can't help with that.", "r").isEmpty())
        assertNull(TaskExtractor.extractJson("no json here"))
    }

    @Test fun parseDueFormats() {
        assertEquals(TaskExtractor.Due(day(2026, 1, 5), true), TaskExtractor.parseDue("2026-01-05", dubai))
        assertFalse(TaskExtractor.parseDue("2026-01-05 09:00", dubai)!!.allDay)
        assertFalse(TaskExtractor.parseDue("2026-01-05T09:00:00", dubai)!!.allDay)
        assertEquals(
            java.time.OffsetDateTime.parse("2026-01-05T09:00:00Z").toInstant().toEpochMilli(),
            TaskExtractor.parseDue("2026-01-05T09:00:00Z", dubai)!!.epochMs,
        )
        assertNull(TaskExtractor.parseDue("next week", dubai))
        assertNull(TaskExtractor.parseDue("2026-13-45", dubai))
        assertNull(TaskExtractor.parseDue("null", dubai))
    }

    @Test fun stableIdsAcrossExtractions() {
        val a = TaskExtractor.parse("""{"tasks":[{"title":"Send the notes"}]}""", "r9")
        val b = TaskExtractor.parse("""{"tasks":[{"title":"send the notes."}]}""", "r9")
        assertEquals(a[0].id, b[0].id)
    }

    @Test fun promptCarriesMeetingDateAndContent() {
        val createdAt = LocalDateTime.of(2026, 10, 18, 14, 30).atZone(dubai).toInstant().toEpochMilli()
        val rec = Recording(
            id = "x", title = "Sync", createdAt = createdAt,
            summary = "## Action items\n- [ ] Sara: notes by Thursday",
            transcript = "Speaker 1: Sara, send the notes by Thursday.", speakerNames = mapOf("1" to "Mohamed"),
        )
        val prompt = TaskExtractor.userPrompt(rec, dubai)
        assertTrue(prompt, prompt.contains("Sunday 2026-10-18 14:30"))
        assertTrue(prompt.contains("Minutes:"))
        assertTrue(prompt.contains("Mohamed: Sara, send the notes"))  // short transcript included with names
        val long = rec.copy(transcript = "x ".repeat(10_000))
        assertFalse(TaskExtractor.userPrompt(long, dubai).contains("Transcript:"))  // too long: minutes only
        assertTrue(TaskExtractor.userPrompt(long.copy(summary = null), dubai).length < TaskExtractor.MAX_INPUT_CHARS + 200)
    }
}
