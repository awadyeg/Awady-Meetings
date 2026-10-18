package com.riffado.meetings.mcp

import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.ai.AskAi
import com.riffado.meetings.ai.AskAiEngine
import com.riffado.meetings.data.Comments
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.Speakers
import com.riffado.meetings.data.TaskItem
import com.riffado.meetings.data.TaskLogic
import com.riffado.meetings.ui.HomeLogic
import com.riffado.meetings.ui.PersonLogic
import com.riffado.meetings.ui.RecordingSearch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Where the tools read from; the app implementation is [forApp], tests use a fake. */
interface McpData {
    fun recordings(): List<Recording>
    fun tasks(): List<TaskItem>
    /** Runs the Ask AI pipeline (blocking; called on a server worker thread). */
    fun ask(question: String, person: String?, recordingId: String?): AskResult
    val zone: ZoneId get() = ZoneId.systemDefault()
    fun now(): Long = System.currentTimeMillis()
}

data class AskResult(val answer: String, val sources: List<AskAi.Source>, val searched: Int)

/** A user-facing tool failure → `isError: true` result (not a protocol error). */
class McpToolError(message: String) : Exception(message)

/**
 * Read-only riffado tools: list_recordings, search_recordings, get_recording, list_tasks, get_person, ask.
 * Every result carries `structuredContent` (an object) and the same JSON as a text block.
 */
class McpTools(private val data: McpData) : McpToolbox {

    override fun list(): JsonArray = buildJsonArray { DEFINITIONS.forEach { add(it) } }

    override fun call(name: String, arguments: JsonObject): JsonObject {
        val args = Args(arguments)
        val result: JsonObject = try {
            when (name) {
                "list_recordings" -> listRecordings(args)
                "search_recordings" -> searchRecordings(args)
                "get_recording" -> getRecording(args)
                "list_tasks" -> listTasks(args)
                "get_person" -> getPerson(args)
                "ask" -> ask(args)
                else -> throw McpInvalidParams("Unknown tool: $name")
            }
        } catch (e: McpToolError) {
            return errorResult(e.message ?: "Error")
        }
        return buildJsonObject {
            putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", result.toString()) }) }
            put("structuredContent", result)
            put("isError", false)
        }
    }

    // ---- tools -----------------------------------------------------------------------------------

    private fun listRecordings(a: Args): JsonObject {
        val type = a.enum("type", listOf("all", "meeting", "call")) ?: "all"
        val person = a.string("person")
        val from = a.instant("from", endOfDay = false)
        val to = a.instant("to", endOfDay = true)
        val limit = a.int("limit", 20, 1, 200)
        val offset = a.int("offset", 0, 0, Int.MAX_VALUE)
        var list = data.recordings().sortedByDescending { it.createdAt }
        list = when (type) {
            "call" -> list.filter { it.isCall }
            "meeting" -> list.filter { !it.isCall }
            else -> list
        }
        if (person != null) list = RecordingSearch.filterByPerson(list, person)
        if (from != null) list = list.filter { it.createdAt >= from }
        if (to != null) list = list.filter { it.createdAt <= to }
        val tasks = data.tasks()
        val page = list.drop(offset).take(limit)
        return buildJsonObject {
            put("total", list.size)
            put("offset", offset)
            put("count", page.size)
            put("has_more", offset + page.size < list.size)
            putJsonArray("recordings") { page.forEach { add(recordingSummary(it, tasks)) } }
        }
    }

    private fun searchRecordings(a: Args): JsonObject {
        val query = a.requireString("query")
        val limit = a.int("limit", 10, 1, 50)
        val type = a.enum("type", listOf("all", "meeting", "call")) ?: "all"
        val terms = query.split(Regex("\\s+")).map(RecordingSearch::normalizeArabic).filter { it.isNotBlank() }
        if (terms.isEmpty()) throw McpInvalidParams("query must not be blank")
        val tasks = data.tasks()
        val hits = data.recordings()
            .filter { type == "all" || (type == "call") == it.isCall }
            .mapNotNull { r ->
                val fields = searchFields(r)
                val normalized = fields.map { (field, text) -> Triple(field, text, RecordingSearch.normalizeArabic(text)) }
                val all = normalized.joinToString("\n") { it.third }
                if (!terms.all { all.contains(it) }) return@mapNotNull null
                val score = terms.sumOf { t -> Regex(Regex.escape(t)).findAll(all).count() } +
                    terms.count { t -> RecordingSearch.normalizeArabic(r.displayName).contains(t) } * 5
                val snippets = normalized.mapNotNull { (field, text, norm) ->
                    val idx = terms.map { norm.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return@mapNotNull null
                    field to snippetAround(text, idx)
                }.filter { it.first != "title" }.take(3)
                Triple(r, score, snippets)
            }
            .sortedWith(compareByDescending<Triple<Recording, Int, List<Pair<String, String>>>> { it.second }.thenByDescending { it.first.createdAt })
        return buildJsonObject {
            put("query", query)
            put("total", hits.size)
            putJsonArray("results") {
                hits.take(limit).forEach { (r, score, snippets) ->
                    add(buildJsonObject {
                        recordingSummaryFields(r, tasks)
                        put("score", score)
                        putJsonArray("matches") { snippets.forEach { (f, s) -> add(buildJsonObject { put("field", f); put("snippet", s) }) } }
                    })
                }
            }
        }
    }

    private fun getRecording(a: Args): JsonObject {
        val id = a.requireString("id")
        val includeTranscript = a.bool("include_transcript") ?: true
        val maxChars = a.int("max_transcript_chars", 60_000, 1_000, 500_000)
        val r = data.recordings().firstOrNull { it.id == id } ?: throw McpToolError("No recording with id \"$id\". Use list_recordings or search_recordings to find ids.")
        val tasks = data.tasks().filter { it.recordingId == r.id }.sortedWith(TASK_ORDER)
        val lines = Comments.lines(r)
        val anchored = Comments.anchor(r.comments, lines.map { it.text })
        return buildJsonObject {
            recordingSummaryFields(r, data.tasks(), preview = false)
            put("summary", r.summary)
            putJsonArray("tasks") { tasks.forEach { add(taskJson(it, r)) } }
            putJsonArray("bookmarks") {
                r.bookmarks.sorted().forEach { ms -> add(buildJsonObject { put("at_seconds", ms / 1000); put("at", clock(ms)) }) }
            }
            putJsonArray("notes") {
                anchored.byLine.toSortedMap().forEach { (line, c) ->
                    add(noteJson(c, line, lines.getOrNull(line)?.speaker?.let { Speakers.label(it, r.speakerNames) } ?: c.speaker, orphan = false))
                }
                anchored.orphans.forEach { c -> add(noteJson(c, null, c.speaker, orphan = true)) }
            }
            if (includeTranscript) {
                var used = 0
                var truncated = false
                putJsonArray("transcript") {
                    for ((i, t) in lines.withIndex()) {
                        if (used + t.text.length > maxChars) { truncated = true; break }
                        used += t.text.length
                        add(buildJsonObject {
                            put("line", i)
                            t.speaker?.let { put("speaker", Speakers.label(it, r.speakerNames)) }
                            t.startMs?.let { put("at", clock(it)) }
                            put("text", t.text)
                        })
                    }
                }
                put("transcript_lines", lines.size)
                put("transcript_truncated", truncated)
            }
        }
    }

    private fun listTasks(a: Args): JsonObject {
        val status = a.enum("status", listOf("open", "done", "all")) ?: "open"
        val from = a.instant("due_from", endOfDay = false)
        val to = a.instant("due_to", endOfDay = true)
        val person = a.string("person")
        val recordingId = a.string("recording_id")
        val limit = a.int("limit", 50, 1, 500)
        val recordings = data.recordings()
        val byId = recordings.associateBy { it.id }
        var list = data.tasks().filter { it.recordingId in byId }
        list = when (status) {
            "open" -> list.filter { !it.done }
            "done" -> list.filter { it.done }
            else -> list
        }
        if (recordingId != null) list = list.filter { it.recordingId == recordingId }
        if (person != null) {
            val ids = RecordingSearch.filterByPerson(recordings, person).map { it.id }.toSet()
            val key = RecordingSearch.normalizeArabic(person)
            list = list.filter { it.recordingId in ids || it.assignee?.let(RecordingSearch::normalizeArabic) == key }
        }
        if (from != null || to != null) {
            list = list.filter { t -> t.dueAt != null && (from == null || t.dueAt >= from) && (to == null || t.dueAt <= to) }
        }
        list = list.sortedWith(TASK_ORDER)
        return buildJsonObject {
            put("total", list.size)
            put("count", minOf(limit, list.size))
            putJsonArray("tasks") { list.take(limit).forEach { add(taskJson(it, byId[it.recordingId])) } }
        }
    }

    private fun getPerson(a: Args): JsonObject {
        val query = a.requireString("name")
        val limit = a.int("limit", 20, 1, 200)
        val recordings = data.recordings()
        val tasks = data.tasks()
        val name = resolvePerson(query, recordings, tasks)
        val person = PersonLogic.summary(name, recordings)
        val open = PersonLogic.openTasks(person, tasks)
        val byId = recordings.associateBy { it.id }
        return buildJsonObject {
            put("name", person.name)
            put("phone", person.phone)
            put("calls", person.calls)
            put("meetings", person.meetings)
            put("recordings_count", person.recordings.size)
            put("total_duration_seconds", person.totalMs / 1000)
            put("total_duration", PersonLogic.formatTotal(person.totalMs))
            put("last_recording_at", person.lastAt?.let(::iso))
            putJsonArray("open_tasks") { open.forEach { add(taskJson(it, byId[it.recordingId])) } }
            putJsonArray("recordings") { person.recordings.take(limit).forEach { add(recordingSummary(it, tasks)) } }
        }
    }

    private fun ask(a: Args): JsonObject {
        val question = a.requireString("question")
        val person = a.string("person")?.let { resolvePerson(it, data.recordings(), data.tasks()) }
        val recordingId = a.string("recording_id")
        if (recordingId != null && data.recordings().none { it.id == recordingId }) throw McpToolError("No recording with id \"$recordingId\".")
        val result = try {
            data.ask(question, person, recordingId)
        } catch (e: McpToolError) {
            throw e
        } catch (e: AskAiEngine.AskFailure) {
            throw McpToolError(e.message ?: "Ask AI is not available")
        } catch (e: Exception) {
            throw McpToolError("Ask AI failed: ${e.message ?: e.javaClass.simpleName}")
        }
        return buildJsonObject {
            put("answer", result.answer)
            put("searched_recordings", result.searched)
            person?.let { put("person", it) }
            recordingId?.let { put("recording_id", it) }
            putJsonArray("sources") {
                result.sources.forEach { s ->
                    add(buildJsonObject {
                        put("n", s.number)
                        put("recording_id", s.recordingId)
                        put("title", s.title)
                        put("created_at", iso(s.createdAt))
                    })
                }
            }
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** Exact (normalised) person name, or the single known person containing [query]. */
    private fun resolvePerson(query: String, recordings: List<Recording>, tasks: List<TaskItem>): String {
        val known = (RecordingSearch.extractPeople(recordings) + tasks.mapNotNull { it.assignee }).distinct()
        val q = RecordingSearch.normalizeArabic(query.trim())
        known.firstOrNull { RecordingSearch.normalizeArabic(it) == q }?.let { return it }
        val partial = known.filter { RecordingSearch.normalizeArabic(it).contains(q) }
        return when {
            partial.size == 1 -> partial[0]
            partial.size > 1 -> throw McpToolError("\"$query\" matches several people: ${partial.sorted().joinToString(", ")}. Use the full name.")
            else -> throw McpToolError(
                "No person named \"$query\"." + if (known.isEmpty()) "" else " Known people: ${known.sorted().take(50).joinToString(", ")}.",
            )
        }
    }

    private fun recordingSummary(r: Recording, tasks: List<TaskItem>): JsonObject = buildJsonObject { recordingSummaryFields(r, tasks) }

    private fun JsonObjectBuilder.recordingSummaryFields(r: Recording, tasks: List<TaskItem>, preview: Boolean = true) {
        val own = tasks.filter { it.recordingId == r.id }
        put("id", r.id)
        put("title", r.displayName)
        put("type", if (r.isCall) "call" else "meeting")
        put("created_at", iso(r.createdAt))
        put("duration_seconds", r.durationMs / 1000)
        put("duration", clock(r.durationMs))
        put("status", r.status.name.lowercase())
        putJsonArray("people") { people(r).forEach { add(JsonPrimitive(it)) } }
        if (r.isCall) {
            r.callContactName?.let { put("contact", it) }
            r.callNumber?.let { put("phone", it) }
        }
        put("has_summary", !r.summary.isNullOrBlank())
        put("has_transcript", !r.transcript.isNullOrBlank() || r.speakerTurns.isNotEmpty())
        if (preview) HomeLogic.summarySnippet(r.summary, 240)?.let { put("summary_preview", it) }
        put("open_tasks", own.count { !it.done })
        put("tasks", own.size)
        put("notes", r.comments.size)
        put("bookmarks", r.bookmarks.size)
    }

    private fun people(r: Recording): List<String> = buildList {
        r.callContactName?.takeIf { it.isNotBlank() }?.let(::add)
        r.speakerNames.forEach { (id, name) -> if (name.isNotBlank() && name != "Speaker $id" && name != "المتحدث $id") add(name) }
    }.distinct()

    private fun taskJson(t: TaskItem, r: Recording?): JsonObject = buildJsonObject {
        put("id", t.id)
        put("title", t.title)
        put("done", t.done)
        put("assignee", t.assignee)
        put("due", t.dueAt?.let { if (t.allDay) date(it) else iso(it) })
        put("all_day", t.allDay)
        t.dueText?.let { put("due_text", it) }
        put("overdue", !t.done && TaskLogic.isOverdue(t, data.now(), data.zone))
        put("recording_id", t.recordingId)
        r?.let { put("recording_title", it.displayName) }
    }

    private fun noteJson(c: com.riffado.meetings.data.LineComment, line: Int?, speaker: String?, orphan: Boolean): JsonObject = buildJsonObject {
        put("line", line)
        speaker?.let { put("speaker", it) }
        put("quote", c.quote)
        put("note", c.text)
        c.startMs?.let { put("at", clock(it)) }
        put("updated_at", iso(c.updatedAt))
        if (orphan) put("orphan", true)
    }

    private fun searchFields(r: Recording): List<Pair<String, String>> = buildList {
        add("title" to r.displayName)
        add("people" to (people(r) + listOfNotNull(r.callNumber)).joinToString(", "))
        r.summary?.let { add("summary" to it) }
        val transcript = Comments.lines(r).joinToString("\n") { t -> t.speaker?.let { "${Speakers.label(it, r.speakerNames)}: ${t.text}" } ?: t.text }
        if (transcript.isNotBlank()) add("transcript" to transcript)
        if (r.comments.isNotEmpty()) add("notes" to r.comments.joinToString("\n") { it.text })
    }

    private fun snippetAround(text: String, index: Int, radius: Int = 90): String {
        val i = index.coerceIn(0, text.length)
        val start = (i - radius).coerceAtLeast(0).let { s -> text.lastIndexOf(' ', s).takeIf { it in (s - 20)..s }?.plus(1) ?: s }
        val end = (i + radius).coerceAtMost(text.length)
        return (if (start > 0) "…" else "") + text.substring(start, end).replace('\n', ' ').trim() + (if (end < text.length) "…" else "")
    }

    private fun iso(ms: Long): String = OffsetDateTime.ofInstant(Instant.ofEpochMilli(ms), data.zone).withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    private fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(data.zone).toLocalDate().toString()

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(java.util.Locale.US, s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(java.util.Locale.US, s / 60, s % 60)
    }

    private fun errorResult(message: String) = buildJsonObject {
        putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", message) }) }
        put("isError", true)
    }

    /** Typed access to tool arguments; bad types → -32602. */
    private inner class Args(private val o: JsonObject) {
        private fun prim(key: String): JsonPrimitive? = when (val v = o[key]) {
            null, is JsonNull -> null
            is JsonPrimitive -> v
            else -> throw McpInvalidParams("$key must be a string, number or boolean")
        }

        fun string(key: String): String? = prim(key)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        fun requireString(key: String): String = string(key) ?: throw McpInvalidParams("Missing required argument: $key")
        fun bool(key: String): Boolean? = prim(key)?.let { it.booleanOrNull ?: throw McpInvalidParams("$key must be a boolean") }

        fun int(key: String, default: Int, min: Int, max: Int): Int {
            val p = prim(key) ?: return default
            val v = p.intOrNull ?: p.contentOrNull?.toDoubleOrNull()?.toInt() ?: throw McpInvalidParams("$key must be an integer")
            return v.coerceIn(min, max)
        }

        fun enum(key: String, allowed: List<String>): String? {
            val v = string(key)?.lowercase() ?: return null
            val normalized = when (v) { "meetings" -> "meeting"; "calls" -> "call"; else -> v }
            if (normalized !in allowed) throw McpInvalidParams("$key must be one of ${allowed.joinToString()}")
            return normalized
        }

        /** Accepts `2026-10-18`, `2026-10-18T09:00`, `2026-10-18T09:00:00+04:00` / `Z`, or epoch millis. */
        fun instant(key: String, endOfDay: Boolean): Long? {
            val raw = string(key) ?: return null
            raw.toLongOrNull()?.let { return it }
            return try {
                when {
                    raw.length == 10 -> LocalDate.parse(raw).let { d ->
                        if (endOfDay) d.plusDays(1).atStartOfDay(data.zone).toInstant().toEpochMilli() - 1
                        else d.atStartOfDay(data.zone).toInstant().toEpochMilli()
                    }
                    raw.endsWith("Z") || Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(raw.substringAfter('T', "")) ->
                        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
                    else -> LocalDateTime.parse(raw).atZone(data.zone).toInstant().toEpochMilli()
                }
            } catch (_: DateTimeParseException) {
                throw McpInvalidParams("$key must be an ISO 8601 date or date-time (e.g. 2026-10-18 or 2026-10-18T09:00:00+04:00)")
            }
        }
    }

    companion object {
        private val TASK_ORDER = compareBy<TaskItem, Long?>(nullsLast()) { it.dueAt }.thenByDescending { it.createdAt }

        /** App-backed tools: live stores + the shared Ask AI pipeline. */
        fun forApp(app: MeetingsApp): McpTools = McpTools(object : McpData {
            override fun recordings() = app.recordings.recordings.value
            override fun tasks() = app.tasks.tasks.value
            override fun ask(question: String, person: String?, recordingId: String?): AskResult = runBlocking {
                withTimeout(ASK_TIMEOUT_MS) {
                    val a = AskAiEngine.answer(app, question, personName = person, recordingId = recordingId)
                    AskResult(a.text, a.sources, a.searched)
                }
            }
        })

        private const val ASK_TIMEOUT_MS = 180_000L

        private fun schema(required: List<String> = emptyList(), props: JsonObjectBuilder.() -> Unit) = buildJsonObject {
            put("type", "object")
            putJsonObject("properties", props)
            if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
            put("additionalProperties", false)
        }

        private fun JsonObjectBuilder.prop(name: String, type: String, description: String, enum: List<String>? = null, extra: JsonObjectBuilder.() -> Unit = {}) =
            putJsonObject(name) {
                put("type", type)
                put("description", description)
                enum?.let { putJsonArray("enum") { it.forEach { v -> add(JsonPrimitive(v)) } } }
                extra()
            }

        private fun tool(name: String, title: String, description: String, input: JsonObject): JsonElement = buildJsonObject {
            put("name", name)
            put("title", title)
            put("description", description)
            put("inputSchema", input)
            putJsonObject("annotations") {
                put("title", title)
                put("readOnlyHint", true)
                put("destructiveHint", false)
                put("idempotentHint", true)
                put("openWorldHint", name == "ask") // ask calls the user's configured LLM provider
            }
        }

        private const val DATE_HINT = "ISO 8601 date (2026-10-18) or date-time with offset; date-only values cover the whole day in the phone's time zone"

        val DEFINITIONS: List<JsonElement> = listOf(
            tool(
                "list_recordings", "List recordings",
                "List the user's meeting and call recordings, newest first, with title, date, duration, people, status, summary preview and task/note counts. Filter by type, person and date range; page with limit/offset.",
                schema {
                    prop("type", "string", "Recording type", listOf("all", "meeting", "call"))
                    prop("person", "string", "Only recordings with this person (call contact or named speaker)")
                    prop("from", "string", "Recorded on/after. $DATE_HINT")
                    prop("to", "string", "Recorded on/before. $DATE_HINT")
                    prop("limit", "integer", "Max results (1-200, default 20)") { put("minimum", 1); put("maximum", 200) }
                    prop("offset", "integer", "Skip this many results (default 0)") { put("minimum", 0) }
                },
            ),
            tool(
                "search_recordings", "Search recordings",
                "Full-text search over titles, people, summaries, transcripts and the user's notes (Arabic-aware: ignores diacritics and alef/yaa/taa-marbuta variants). All words must match. Returns matching recordings with snippets.",
                schema(listOf("query")) {
                    prop("query", "string", "Words or phrase to find (Arabic or English)")
                    prop("type", "string", "Recording type", listOf("all", "meeting", "call"))
                    prop("limit", "integer", "Max results (1-50, default 10)") { put("minimum", 1); put("maximum", 50) }
                },
            ),
            tool(
                "get_recording", "Get recording",
                "Full details of one recording: summary (markdown minutes), transcript lines with speaker names and timestamps, tasks, bookmarks and the user's notes on transcript lines.",
                schema(listOf("id")) {
                    prop("id", "string", "Recording id from list_recordings or search_recordings")
                    prop("include_transcript", "boolean", "Include transcript lines (default true)")
                    prop("max_transcript_chars", "integer", "Truncate the transcript after this many characters (default 60000)") { put("minimum", 1000) }
                },
            ),
            tool(
                "list_tasks", "List tasks",
                "Action items extracted from recordings, with assignee, due date, overdue flag and source recording. Sorted by due date (undated last).",
                schema {
                    prop("status", "string", "Task status (default open)", listOf("open", "done", "all"))
                    prop("due_from", "string", "Due on/after (undated tasks are excluded when a due range is given). $DATE_HINT")
                    prop("due_to", "string", "Due on/before. $DATE_HINT")
                    prop("person", "string", "Tasks from recordings with this person or assigned to them")
                    prop("recording_id", "string", "Only tasks from this recording")
                    prop("limit", "integer", "Max results (1-500, default 50)") { put("minimum", 1); put("maximum", 500) }
                },
            ),
            tool(
                "get_person", "Get person",
                "Profile of a person the user talks to: phone, number of calls and meetings, total time, open tasks and their recordings. Accepts a full or partial name (Arabic-aware).",
                schema(listOf("name")) {
                    prop("name", "string", "Person name, e.g. \"Sara Ali\" or \"أحمد\"")
                    prop("limit", "integer", "Max recordings to list (1-200, default 20)") { put("minimum", 1); put("maximum", 200) }
                },
            ),
            tool(
                "ask", "Ask about recordings",
                "Answer a question from the user's recordings using the app's Ask AI pipeline (retrieval + the user's configured LLM). Returns the answer with numbered sources [n] pointing to recordings. Optionally scope to one person or one recording. Can take up to a minute.",
                schema(listOf("question")) {
                    prop("question", "string", "The question, in any language")
                    prop("person", "string", "Only use recordings with this person")
                    prop("recording_id", "string", "Only use this recording")
                },
            ),
        )
    }
}
