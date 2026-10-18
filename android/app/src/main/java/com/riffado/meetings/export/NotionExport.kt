package com.riffado.meetings.export

import com.riffado.meetings.data.Speakers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Locale
import java.util.TimeZone

/**
 * Builds Notion API payloads (https://developers.notion.com/reference/post-page) for a recording:
 * summary as headings/paragraphs/bullets, tasks as to-dos, notes, bookmarks and the transcript.
 * Respects the API limits: ≤ 2000 chars per rich-text object, ≤ 100 blocks per request.
 */
object NotionExport {
    const val MAX_TEXT = 2_000
    const val MAX_BLOCKS_PER_REQUEST = 100
    const val API_VERSION = "2022-06-28"

    /** Accepts a page URL, a dashed UUID or 32 hex chars; returns the dashed UUID or null. */
    fun parentPageId(input: String): String? {
        val compact = input.trim().substringBefore('?').substringBefore('#').trimEnd('/').replace("-", "")
        // Notion URLs end in "<slug>-<32 hex id>", so prefer the 32 hex chars at the very end.
        val h = (Regex("([0-9a-fA-F]{32})$").find(compact) ?: Regex("[0-9a-fA-F]{32}").find(compact))?.value?.lowercase()
            ?: return null
        return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
    }

    fun richText(text: String, bold: Boolean = false): JsonArray = buildJsonArray {
        text.chunked(MAX_TEXT).forEach { part ->
            add(
                buildJsonObject {
                    put("type", "text")
                    putJsonObject("text") { put("content", part) }
                    if (bold) putJsonObject("annotations") { put("bold", true) }
                },
            )
        }
    }

    private fun block(type: String, text: String, extra: Map<String, JsonElement> = emptyMap()): JsonObject =
        buildJsonObject {
            put("object", "block")
            put("type", type)
            put(type, JsonObject(mapOf("rich_text" to richText(text)) + extra))
        }

    fun heading(text: String, level: Int = 2) = block("heading_$level", text)
    fun paragraph(text: String) = block("paragraph", text)
    fun bullet(text: String) = block("bulleted_list_item", text)
    fun numbered(text: String) = block("numbered_list_item", text)
    fun todo(text: String, checked: Boolean) = block("to_do", text, mapOf("checked" to JsonPrimitive(checked)))
    fun quote(text: String) = block("quote", text)

    /** Very small Markdown → blocks converter for summaries. Strips `**` markers. */
    fun markdownBlocks(md: String): List<JsonObject> = md.lines().mapNotNull { raw ->
        val line = raw.trim().replace("**", "")
        when {
            line.isEmpty() -> null
            line.startsWith("### ") -> heading(line.drop(4), 3)
            line.startsWith("## ") -> heading(line.drop(3), 3)
            line.startsWith("# ") -> heading(line.drop(2), 2)
            line.startsWith("- [ ] ") || line.startsWith("* [ ] ") -> todo(line.drop(6), false)
            line.startsWith("- [x] ", ignoreCase = true) -> todo(line.drop(6), true)
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> bullet(line.drop(2))
            Regex("^\\d+[.)] ").containsMatchIn(line) -> numbered(line.replaceFirst(Regex("^\\d+[.)] "), ""))
            else -> paragraph(line)
        }
    }

    fun blocks(data: ExportData, labels: ExportLabels = ExportLabels(), locale: Locale = Locale.getDefault(), tz: TimeZone = TimeZone.getDefault()): List<JsonObject> {
        val r = data.recording
        val out = mutableListOf<JsonObject>()
        val meta = buildList {
            add("${labels.date}: ${RecordingExport.formatDate(r.createdAt, locale, tz)}")
            if (r.durationMs > 0) add("${labels.duration}: ${RecordingExport.formatDuration(r.durationMs)}")
            if (data.people.isNotEmpty()) add("${labels.people}: ${data.people.joinToString(", ")}")
            if (r.isCall && !r.callNumber.isNullOrBlank() && r.callNumber !in data.people) add(r.callNumber)
        }
        out += paragraph(meta.joinToString(" · "))
        RecordingExport.summaryBody(r, labels.summary)?.let { body ->
            out += heading(labels.summary)
            // Action items become real to-dos in the Tasks section when tasks exist.
            val lines = if (data.tasks.isNotEmpty()) body.lines().filterNot { it.trim().startsWith("- [") || it.trim().startsWith("* [") } else body.lines()
            out += markdownBlocks(lines.joinToString("\n"))
        }
        if (data.tasks.isNotEmpty()) {
            out += heading(labels.tasks)
            data.tasks.forEach { out += todo(RecordingExport.taskLine(it, labels, locale, tz), it.done) }
        }
        if (r.bookmarks.isNotEmpty()) {
            out += heading(labels.bookmarks)
            r.bookmarks.forEach { out += bullet(RecordingExport.formatDuration(it)) }
        }
        if (r.comments.isNotEmpty()) {
            out += heading(labels.notes)
            r.comments.forEach { c ->
                val who = c.speaker?.let { Speakers.label(it, r.speakerNames) + ": " }.orEmpty()
                out += quote("$who${c.quote}")
                out += paragraph("📝 ${c.text}")
            }
        }
        val lines = data.lines
        if (lines.isNotEmpty()) {
            out += heading(labels.transcript)
            lines.forEach { t ->
                val text = t.text.trim()
                out += if (t.speaker != null) {
                    buildJsonObject {
                        put("object", "block"); put("type", "paragraph")
                        putJsonObject("paragraph") {
                            putJsonArray("rich_text") {
                                richText(Speakers.label(t.speaker, r.speakerNames) + ": ", bold = true).forEach { add(it) }
                                richText(text).forEach { add(it) }
                            }
                        }
                    }
                } else paragraph(text)
            }
        }
        return out
    }

    /** Body for `POST /v1/pages`: the page under [parentId] with the first ≤ 100 blocks. */
    fun createPageBody(parentId: String, title: String, blocks: List<JsonObject>): JsonObject = buildJsonObject {
        putJsonObject("parent") { put("page_id", parentId) }
        putJsonObject("icon") { put("type", "emoji"); put("emoji", "🎙️") }
        putJsonObject("properties") {
            putJsonObject("title") { put("title", richText(title.take(MAX_TEXT))) }
        }
        put("children", JsonArray(blocks.take(MAX_BLOCKS_PER_REQUEST)))
    }

    /** Remaining blocks in batches for `PATCH /v1/blocks/{page_id}/children`. */
    fun appendBatches(blocks: List<JsonObject>): List<JsonObject> =
        blocks.drop(MAX_BLOCKS_PER_REQUEST).chunked(MAX_BLOCKS_PER_REQUEST).map { batch ->
            buildJsonObject { put("children", JsonArray(batch)) }
        }

    /** Reason code ("UNAUTHORIZED", "NOT_FOUND", "FORBIDDEN", "RATE_LIMITED") or the API message. */
    fun errorMessage(code: Int, body: String): String {
        val apiCode = Regex("\"code\"\\s*:\\s*\"([a-z_]+)\"").find(body)?.groupValues?.get(1)
        val msg = Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)?.groupValues?.get(1)
        return when {
            code == 401 || apiCode == "unauthorized" -> "UNAUTHORIZED"
            code == 404 || apiCode == "object_not_found" -> "NOT_FOUND"
            code == 403 || apiCode == "restricted_resource" -> "FORBIDDEN"
            code == 429 -> "RATE_LIMITED"
            else -> msg ?: "HTTP $code"
        }
    }
}
