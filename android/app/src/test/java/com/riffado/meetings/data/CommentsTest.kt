package com.riffado.meetings.data

import android.app.Application
import com.riffado.meetings.ai.AskAi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommentsTest {
    private val transcript = "Speaker 1: Let's start.\nSpeaker 2: The beta ships Thursday.\nSpeaker 1: .\nSpeaker 1: Great, thanks."
    private val rec = Recording(id = "r1", title = "Sync", createdAt = 1, transcript = transcript, speakerNames = mapOf("2" to "Sara"))
    private var ids = 0
    private val newId = { "c${++ids}" }

    @Test fun linesSkipEmptyAndDotTurns() {
        assertEquals(listOf("Let's start.", "The beta ships Thursday.", "Great, thanks."), Comments.lines(rec).map { it.text })
    }

    @Test fun addEditDelete() {
        val lines = Comments.lines(rec)
        var list = Comments.upsert(emptyList(), 1, lines[1], "  check date  ", now = 10, newId = newId)
        assertEquals(1, list.size)
        with(list[0]) {
            assertEquals("check date", text); assertEquals(1, lineIndex); assertEquals("2", speaker)
            assertEquals("The beta ships Thursday.", quote); assertEquals(10L, createdAt)
        }
        list = Comments.upsert(list, 0, lines[0], "first", now = 11, newId = newId)
        assertEquals(listOf(0, 1), list.map { it.lineIndex })          // ordered by line
        list = Comments.upsert(list, 1, lines[1], "moved to Friday", now = 12, newId = newId)
        assertEquals(2, list.size)
        assertEquals("moved to Friday", list[1].text); assertEquals(12L, list[1].updatedAt); assertEquals("c1", list[1].id)
        list = Comments.upsert(list, 1, lines[1], "   ", now = 13, newId = newId)  // blank deletes
        assertEquals(listOf("first"), list.map { it.text })
        assertTrue(Comments.delete(list, list[0].id).isEmpty())
    }

    @Test fun anchorsMovedLinesAndKeepsOrphans() {
        val c1 = LineComment("a", 1, "The beta ships Thursday.", "note A")
        val c2 = LineComment("b", 2, "Gone line", "note B")
        val anchored = Comments.anchor(listOf(c1, c2), listOf("Intro", "Hello", "The beta ships Thursday.", "x"))
        assertEquals("a", anchored.byLine[2]?.id)  // re-anchored by quote
        assertNull(anchored.byLine[1])
        assertEquals(listOf("b"), anchored.orphans.map { it.id })
    }

    @Test fun persistsThroughRecordingStore() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val store = RecordingStore(ctx)
        val lines = Comments.lines(rec)
        val withNote = rec.copy(comments = Comments.upsert(emptyList(), 1, lines[1], "check date", 5, newId))
        store.upsert(withNote)
        val reloaded = RecordingStore(ctx).get("r1")!!
        assertEquals(withNote.comments, reloaded.comments)
    }

    @Test fun oldJsonWithoutCommentsStillLoads() {
        val old = """{"id":"x","title":"Old","createdAt":3,"status":"DONE","summary":"s"}"""
        val r = Json { ignoreUnknownKeys = true }.decodeFromString(Recording.serializer(), old)
        assertTrue(r.comments.isEmpty())
    }

    @Test fun notesReachAskAiContext() {
        val lines = Comments.lines(rec)
        val r = rec.copy(summary = "Weekly sync.", comments = Comments.upsert(emptyList(), 1, lines[1], "Confirm the venue budget", 5, newId))
        val ctx = AskAi.buildContext(listOf(r), "venue budget")
        assertTrue(ctx.prompt, ctx.prompt.contains("Confirm the venue budget"))
        assertTrue(ctx.prompt.contains("Sara: The beta ships Thursday."))
        val scoped = AskAi.buildContext(listOf(r), "anything", scopedTo = "r1")
        assertTrue(scoped.prompt.contains("Confirm the venue budget"))
    }
}
