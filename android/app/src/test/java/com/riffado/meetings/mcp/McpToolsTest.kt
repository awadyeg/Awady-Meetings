package com.riffado.meetings.mcp

import com.riffado.meetings.ai.AskAi
import com.riffado.meetings.ai.AskAiEngine
import com.riffado.meetings.data.LineComment
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.RecordingStatus
import com.riffado.meetings.data.Speakers
import com.riffado.meetings.data.TaskItem
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.time.LocalDate
import java.time.ZoneId

class McpToolsTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private fun at(date: String, hour: Int) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = at("2026-10-18", 20)

    private val meeting = Recording(
        id = "m1", title = "Weekly product sync", createdAt = at("2026-10-18", 10), durationMs = 1_520_000, status = RecordingStatus.DONE,
        speakerNames = mapOf("1" to "Mohamed", "2" to "Sara Ali"),
        speakerTurns = listOf(
            Speakers.Turn("1", "Let's review the redesign.", 0, 4_000),
            Speakers.Turn("2", "الشاشة الرئيسية خلصت والـ glass effect شغال.", 4_000, 9_000),
            Speakers.Turn("1", "Ship it next sprint.", 9_000, 12_000),
        ),
        transcript = "Speaker 1: Let's review the redesign.\nSpeaker 2: الشاشة الرئيسية خلصت\nSpeaker 1: Ship it next sprint.",
        summary = "## Summary\nThe team agreed to ship the redesign next sprint.\n## Action items\n- [ ] Sara prepares the release notes",
        bookmarks = listOf(65_000),
        comments = listOf(LineComment("c1", 1, "الشاشة الرئيسية خلصت والـ glass effect شغال.", "Check on Samsung too", "2", 4_000, now, now)),
    )
    private val call1 = Recording(
        id = "c1", title = "أحمد حسن", createdAt = at("2026-10-16", 9), durationMs = 312_000, status = RecordingStatus.DONE,
        tags = setOf("call"), callContactName = "أحمد حسن", callNumber = "+20 100 123 4567",
        summary = "## الملخص\nاتفقنا على تسليم النسخة التجريبية يوم الخميس.",
        transcript = "اتفقنا على تسليم النسخة التجريبية يوم الخميس",
    )
    private val call2 = Recording(
        id = "c2", title = "Sara Ali", createdAt = at("2026-10-10", 15), durationMs = 60_000, status = RecordingStatus.DONE,
        tags = setOf("call"), callContactName = "Sara Ali", callNumber = "+971 55 222 3344",
        summary = "## Summary\nSara will send the signed contract.",
    )
    private val tasks = listOf(
        TaskItem("t1", "m1", "Prepare the release notes", assignee = "Sara Ali", dueAt = at("2026-10-17", 0), createdAt = now),
        TaskItem("t2", "c1", "تسليم النسخة التجريبية", assignee = "أحمد حسن", dueAt = at("2026-10-22", 15), allDay = false, createdAt = now),
        TaskItem("t3", "c2", "Send the signed contract", createdAt = now),
        TaskItem("t4", "m1", "Book the demo room", done = true, createdAt = now),
        TaskItem("orphan", "gone", "From a deleted recording", createdAt = now),
    )

    private var askCalls = mutableListOf<Triple<String, String?, String?>>()
    private val data = object : McpData {
        override fun recordings() = listOf(meeting, call1, call2)
        override fun tasks() = this@McpToolsTest.tasks
        override val zone: ZoneId = this@McpToolsTest.zone
        override fun now() = this@McpToolsTest.now
        override fun ask(question: String, person: String?, recordingId: String?): AskResult {
            askCalls += Triple(question, person, recordingId)
            if (question == "fail") throw AskAiEngine.AskFailure("Set up a summary provider first")
            return AskResult("They agreed to ship next sprint [1].", listOf(AskAi.Source(1, "m1", "Weekly product sync", meeting.createdAt)), 3)
        }
    }
    internal val tools = McpTools(data)

    private fun call(name: String, args: JsonObject = JsonObject(emptyMap())): JsonObject {
        val r = tools.call(name, args)
        assertFalse("tool $name failed: $r", r["isError"]!!.jsonPrimitive.boolean)
        val structured = r["structuredContent"]!!.jsonObject
        // Text block mirrors structuredContent (backwards compatibility).
        assertEquals(structured, McpProtocol.parse(r["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content))
        return structured
    }
    private fun args(vararg pairs: Pair<String, Any>) = buildJsonObject {
        pairs.forEach { (k, v) -> when (v) { is Int -> put(k, v); is Boolean -> put(k, v); else -> put(k, v.toString()) } }
    }
    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content

    @Test fun listsSixReadOnlyToolsWithSchemas() {
        val defs = tools.list().map { it.jsonObject }
        assertEquals(listOf("list_recordings", "search_recordings", "get_recording", "list_tasks", "get_person", "ask"), defs.map { it.str("name") })
        defs.forEach { d ->
            assertEquals("object", d["inputSchema"]!!.jsonObject.str("type"))
            assertTrue(d["annotations"]!!.jsonObject["readOnlyHint"]!!.jsonPrimitive.boolean)
            assertTrue(d.str("description").length > 40)
        }
        assertEquals(listOf("id"), defs[2]["inputSchema"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun listRecordingsShapeAndFilters() {
        val all = call("list_recordings")
        assertEquals(3, all["total"]!!.jsonPrimitive.int)
        val first = all["recordings"]!!.jsonArray[0].jsonObject
        assertEquals("m1", first.str("id"))
        listOf("id", "title", "type", "created_at", "duration_seconds", "duration", "status", "people", "has_summary", "has_transcript", "open_tasks", "tasks", "notes", "bookmarks")
            .forEach { assertTrue("missing $it", first.containsKey(it)) }
        assertEquals("2026-10-18T10:00:00+04:00", first.str("created_at"))
        assertEquals("25:20", first.str("duration"))
        assertEquals("meeting", first.str("type"))
        assertEquals(listOf("Mohamed", "Sara Ali"), first["people"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(1, first["open_tasks"]!!.jsonPrimitive.int)
        assertTrue(first.str("summary_preview").contains("ship the redesign"))

        val calls = call("list_recordings", args("type" to "call"))
        assertEquals(listOf("c1", "c2"), calls["recordings"]!!.jsonArray.map { it.jsonObject.str("id") })
        assertEquals("+20 100 123 4567", calls["recordings"]!!.jsonArray[0].jsonObject.str("phone"))
        assertEquals(listOf("m1", "c2"), call("list_recordings", args("person" to "sara ali"))["recordings"]!!.jsonArray.map { it.jsonObject.str("id") })
        assertEquals(listOf("c1", "c2"), call("list_recordings", args("from" to "2026-10-10", "to" to "2026-10-16"))["recordings"]!!.jsonArray.map { it.jsonObject.str("id") })
        val page = call("list_recordings", args("limit" to 1, "offset" to 1))
        assertEquals(1, page["count"]!!.jsonPrimitive.int); assertTrue(page["has_more"]!!.jsonPrimitive.boolean)
        assertEquals("c1", page["recordings"]!!.jsonArray[0].jsonObject.str("id"))
    }

    @Test fun searchIsArabicAwareAndCoversNotes() {
        // "اتفقنا ... الخميس" with a hamza-less / different alef spelling and two words (AND).
        val r = call("search_recordings", args("query" to "تسليم الخميس"))
        assertEquals(1, r["total"]!!.jsonPrimitive.int)
        val hit = r["results"]!!.jsonArray[0].jsonObject
        assertEquals("c1", hit.str("id"))
        assertTrue(hit["matches"]!!.jsonArray.isNotEmpty())
        assertTrue(hit["matches"]!!.jsonArray[0].jsonObject.str("snippet").contains("تسليم"))
        assertEquals("m1", call("search_recordings", args("query" to "samsung"))["results"]!!.jsonArray[0].jsonObject.str("id"))
        assertEquals(0, call("search_recordings", args("query" to "redesign contract"))["total"]!!.jsonPrimitive.int)
    }

    @Test fun getRecordingIncludesEverything() {
        val r = call("get_recording", args("id" to "m1"))
        assertTrue(r.str("summary").startsWith("## Summary"))
        val lines = r["transcript"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, lines.size)
        assertEquals("Sara Ali", lines[1].str("speaker"))
        assertEquals("00:04", lines[1].str("at"))
        assertFalse(r["transcript_truncated"]!!.jsonPrimitive.boolean)
        val task = r["tasks"]!!.jsonArray[0].jsonObject
        assertEquals("Prepare the release notes", task.str("title"))
        assertEquals("2026-10-17", task.str("due"))
        assertTrue(task["overdue"]!!.jsonPrimitive.boolean)
        assertEquals(2, r["tasks"]!!.jsonArray.size) // includes the done one
        assertEquals("01:05", r["bookmarks"]!!.jsonArray[0].jsonObject.str("at"))
        val note = r["notes"]!!.jsonArray[0].jsonObject
        assertEquals("Check on Samsung too", note.str("note"))
        assertEquals(1, note["line"]!!.jsonPrimitive.int)
        assertEquals("Sara Ali", note.str("speaker"))
        assertFalse(call("get_recording", args("id" to "m1", "include_transcript" to false)).containsKey("transcript"))
    }

    @Test fun getRecordingUnknownIdIsToolError() {
        val r = tools.call("get_recording", args("id" to "nope"))
        assertTrue(r["isError"]!!.jsonPrimitive.boolean)
        assertTrue(r["content"]!!.jsonArray[0].jsonObject.str("text").contains("No recording"))
    }

    @Test fun listTasksFilters() {
        val open = call("list_tasks")
        assertEquals(listOf("t1", "t2", "t3"), open["tasks"]!!.jsonArray.map { it.jsonObject.str("id") }) // orphan excluded, undated last
        val t2 = open["tasks"]!!.jsonArray[1].jsonObject
        assertEquals("2026-10-22T15:00:00+04:00", t2.str("due"))
        assertEquals("أحمد حسن", t2.str("assignee"))
        assertEquals("أحمد حسن", t2.str("recording_title"))
        assertEquals(JsonNull, open["tasks"]!!.jsonArray[2].jsonObject["due"])
        assertEquals(listOf("t4"), call("list_tasks", args("status" to "done"))["tasks"]!!.jsonArray.map { it.jsonObject.str("id") })
        assertEquals(listOf("t2"), call("list_tasks", args("due_from" to "2026-10-18", "due_to" to "2026-10-31"))["tasks"]!!.jsonArray.map { it.jsonObject.str("id") })
        assertEquals(listOf("t1", "t3"), call("list_tasks", args("person" to "Sara Ali"))["tasks"]!!.jsonArray.map { it.jsonObject.str("id") })
        assertEquals(4, call("list_tasks", args("status" to "all"))["total"]!!.jsonPrimitive.int)
    }

    @Test fun getPersonStatsAndPartialName() {
        val p = call("get_person", args("name" to "sara"))
        assertEquals("Sara Ali", p.str("name"))
        assertEquals("+971 55 222 3344", p.str("phone"))
        assertEquals(1, p["calls"]!!.jsonPrimitive.int)
        assertEquals(1, p["meetings"]!!.jsonPrimitive.int)
        assertEquals((1_520_000 + 60_000) / 1000, p["total_duration_seconds"]!!.jsonPrimitive.int)
        assertEquals(listOf("t1", "t3"), p["open_tasks"]!!.jsonArray.map { it.jsonObject.str("id") })
        assertEquals(listOf("m1", "c2"), p["recordings"]!!.jsonArray.map { it.jsonObject.str("id") })
        assertEquals("أحمد حسن", call("get_person", args("name" to "احمد"))["name"]!!.jsonPrimitive.content)
        val unknown = tools.call("get_person", args("name" to "Zed"))
        assertTrue(unknown["isError"]!!.jsonPrimitive.boolean)
        assertTrue(unknown["content"]!!.jsonArray[0].jsonObject.str("text").contains("Known people"))
    }

    @Test fun askUsesPipelineWithScopes() {
        val r = call("ask", args("question" to "What was decided?", "person" to "sara"))
        assertEquals("They agreed to ship next sprint [1].", r.str("answer"))
        assertEquals("m1", r["sources"]!!.jsonArray[0].jsonObject.str("recording_id"))
        assertEquals(3, r["searched_recordings"]!!.jsonPrimitive.int)
        assertEquals(Triple("What was decided?", "Sara Ali", null), askCalls.last())
        call("ask", args("question" to "q", "recording_id" to "c1"))
        assertEquals(Triple("q", null, "c1"), askCalls.last())
        val fail = tools.call("ask", args("question" to "fail"))
        assertTrue(fail["isError"]!!.jsonPrimitive.boolean)
        assertEquals("Set up a summary provider first", fail["content"]!!.jsonArray[0].jsonObject.str("text"))
    }

    @Test fun badArgumentsAreInvalidParams() {
        fun invalid(name: String, a: JsonObject) = try { tools.call(name, a); false } catch (_: McpInvalidParams) { true }
        assertTrue(invalid("get_recording", JsonObject(emptyMap())))
        assertTrue(invalid("list_recordings", args("type" to "podcast")))
        assertTrue(invalid("list_recordings", args("from" to "yesterday")))
        assertTrue(invalid("list_tasks", args("limit" to "many")))
        assertTrue(invalid("nope", JsonObject(emptyMap())))
    }

    @Test fun endToEndOverHttp() {
        val token = McpToken.generate()
        val server = McpHttpServer(McpProtocol(tools), { token }, 0, InetAddress.getLoopbackAddress())
        server.start()
        try {
            val client = OkHttpClient()
            fun rpc(body: String) = client.newCall(
                Request.Builder().url("http://127.0.0.1:${server.boundPort}/mcp")
                    .header("Authorization", "Bearer $token").header("Accept", "application/json, text/event-stream")
                    .post(body.toRequestBody("application/json".toMediaType())).build(),
            ).execute().use { McpProtocol.parse(it.body!!.string()) }
            assertEquals(6, rpc("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")["result"]!!.jsonObject["tools"]!!.jsonArray.size)
            val res = rpc("""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_person","arguments":{"name":"أحمد حسن"}}}""")["result"]!!.jsonObject
            assertEquals("+20 100 123 4567", res["structuredContent"]!!.jsonObject.str("phone"))
            assertNull(res["error"])
        } finally {
            server.stop()
        }
    }
}
