package com.riffado.meetings.ai

import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.Speakers
import com.riffado.meetings.data.TaskItem
import com.riffado.meetings.data.TaskLogic
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns a recording's minutes into structured tasks: builds the extraction prompt and parses the
 * model's JSON reply tolerantly (code fences, prose around the JSON, top-level arrays, alias keys).
 */
object TaskExtractor {
    /** Characters of minutes/transcript sent to the model (≈ 3600 tokens at 2.5 chars/token). */
    const val MAX_INPUT_CHARS = 9_000

    val SYSTEM_PROMPT = """
        You extract action items, commitments and deadlines from meeting or call notes.
        Reply with ONLY a JSON object, no prose, in this exact shape:
        {"tasks":[{"title":"...","assignee":"..." or null,"due":"YYYY-MM-DD" or "YYYY-MM-DDTHH:mm" or null,"due_text":"..." or null}]}
        Rules:
        - title: a short actionable phrase in the same language as the notes (Arabic stays Arabic).
        - assignee: the person responsible if stated, else null.
        - due: resolve relative dates ("tomorrow", "Thursday", "بكرة", "الخميس الجاي", "end of month") against the meeting date given. Use the time only if one was said. null if no date.
        - due_text: the original wording of the deadline, else null.
        - Include scheduled follow-ups or appointments with a date (e.g. "call the client Thursday 3pm").
        - Do not invent tasks or dates. No duplicates. If there are none, reply {"tasks":[]}.
    """.trimIndent()

    /** User message: meeting date (reference for relative dates), title and the minutes (or transcript). */
    fun userPrompt(recording: Recording, zone: ZoneId = ZoneId.systemDefault(), maxChars: Int = MAX_INPUT_CHARS): String {
        val date = java.time.Instant.ofEpochMilli(recording.createdAt).atZone(zone)
        val dateText = date.format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.US))
        val summary = recording.summary?.trim().orEmpty()
        val transcript = recording.transcript?.let { Speakers.applyNames(it, recording.speakerNames).trim() }.orEmpty()
        val body = buildString {
            if (summary.isNotEmpty()) append("Minutes:\n").append(summary.take(maxChars))
            val room = maxChars - length - 20
            // Add the full transcript too when it fits: it carries the exact deadline wording.
            if (transcript.isNotEmpty() && (summary.isEmpty() || transcript.length <= room)) {
                if (isNotEmpty()) append("\n\n")
                append("Transcript:\n").append(transcript.take(maxChars))
            }
        }
        return "Meeting date: $dateText (${zone.id})\nTitle: ${recording.displayName}\n\n$body"
    }

    data class Due(val epochMs: Long, val allDay: Boolean)

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** Parses the model reply into tasks for [recordingId]. Returns an empty list when nothing parseable is found. */
    fun parse(raw: String, recordingId: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<TaskItem> {
        val element = extractJson(raw) ?: return emptyList()
        val array: List<JsonElement> = when (element) {
            is JsonArray -> element
            is JsonObject -> (listOf("tasks", "action_items", "actionItems", "items", "todos").firstNotNullOfOrNull { element[it] as? JsonArray })
                ?: emptyList()
            else -> emptyList()
        }
        val seen = HashSet<String>()
        return array.mapNotNull { item ->
            val obj = item as? JsonObject
            val title = (if (obj != null) obj.string("title", "task", "text", "action", "description") else (item as? JsonPrimitive)?.contentOrNullSafe())
                ?.trim()?.removePrefix("- [ ]")?.removePrefix("-")?.trim()?.take(200)
            if (title.isNullOrBlank()) return@mapNotNull null
            val key = TaskLogic.normalizeTitle(title)
            if (key.isEmpty() || !seen.add(key)) return@mapNotNull null
            val assignee = obj?.string("assignee", "owner", "responsible", "who")?.trim()?.takeUnless { it.isBlank() || it.lowercase() in NULLISH }
            val due = obj?.string("due", "due_date", "dueDate", "deadline", "date")?.let { parseDue(it, zone) }
            val dueText = obj?.string("due_text", "dueText", "deadline_text")?.trim()?.takeUnless { it.isBlank() || it.lowercase() in NULLISH }
            TaskItem(
                id = TaskLogic.taskId(recordingId, title),
                recordingId = recordingId,
                title = title,
                assignee = assignee,
                dueAt = due?.epochMs,
                allDay = due?.allDay ?: true,
                dueText = dueText,
                createdAt = now,
            )
        }
    }

    /** Parses `YYYY-MM-DD`, `YYYY-MM-DDTHH:mm[:ss]`, `YYYY-MM-DD HH:mm` or a full ISO offset date-time. */
    fun parseDue(text: String, zone: ZoneId = ZoneId.systemDefault()): Due? {
        val s = text.trim()
        if (s.isEmpty() || s.lowercase() in NULLISH) return null
        DATE_TIME.matchEntire(s)?.let { m ->
            val (y, mo, d, h, mi) = m.destructured
            return runCatching {
                Due(LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt()).atZone(zone).toInstant().toEpochMilli(), allDay = false)
            }.getOrNull()
        }
        DATE.matchEntire(s)?.let { m ->
            val (y, mo, d) = m.destructured
            return runCatching {
                Due(LocalDate.of(y.toInt(), mo.toInt(), d.toInt()).atStartOfDay(zone).toInstant().toEpochMilli(), allDay = true)
            }.getOrNull()
        }
        return runCatching { Due(OffsetDateTime.parse(s).toInstant().toEpochMilli(), allDay = false) }.getOrNull()
    }

    /** Finds the JSON value in a reply that may contain code fences or prose. */
    internal fun extractJson(raw: String): JsonElement? {
        val text = raw.replace(Regex("```(?:json)?", RegexOption.IGNORE_CASE), "").trim()
        runCatching { return json.parseToJsonElement(text) }
        val starts = listOf(text.indexOf('{'), text.indexOf('[')).filter { it >= 0 }
        for (start in starts.sorted()) {
            val close = if (text[start] == '{') '}' else ']'
            var end = text.lastIndexOf(close)
            while (end > start) {
                runCatching { return json.parseToJsonElement(text.substring(start, end + 1)) }
                end = text.lastIndexOf(close, end - 1)
            }
        }
        return null
    }

    private fun JsonObject.string(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNullSafe() }

    private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is JsonNull) null else content

    private val NULLISH = setOf("null", "none", "n/a", "na", "-", "unknown", "unassigned", "tbd", "غير محدد", "لا يوجد")
    private val DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val DATE_TIME = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})[T ](\d{1,2}):(\d{2})(?::\d{2}(?:\.\d+)?)?""")
}
