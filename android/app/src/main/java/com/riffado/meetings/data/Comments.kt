package com.riffado.meetings.data

import kotlinx.serialization.Serializable

/** A user note attached to one transcript line. */
@Serializable
data class LineComment(
    val id: String,
    /** Index into [Comments.lines] at the time the note was written. */
    val lineIndex: Int,
    /** Start of the line's text, used to re-anchor the note if the transcript changes. */
    val quote: String,
    val text: String,
    val speaker: String? = null,
    val startMs: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Pure helpers for transcript-line comments (one note per line). */
object Comments {
    const val QUOTE_CHARS = 80

    /** The transcript lines exactly as the transcript view shows them (comments index into this list). */
    fun lines(recording: Recording): List<Speakers.Turn> =
        recording.speakerTurns.ifEmpty { Speakers.parse(recording.transcript.orEmpty()) }
            .filter { it.text.trim().let { t -> t.isNotEmpty() && t != "." } }

    fun quoteOf(text: String): String = text.trim().take(QUOTE_CHARS)

    /**
     * Adds, edits or (for blank [text]) deletes the note on line [lineIndex].
     * Returns the new list, ordered by line.
     */
    fun upsert(
        comments: List<LineComment>,
        lineIndex: Int,
        line: Speakers.Turn,
        text: String,
        now: Long,
        newId: () -> String = { java.util.UUID.randomUUID().toString() },
    ): List<LineComment> {
        val quote = quoteOf(line.text)
        val existing = comments.firstOrNull { it.lineIndex == lineIndex && it.quote == quote }
        val rest = comments.filterNot { it === existing }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return rest
        val next = existing?.copy(text = trimmed, updatedAt = now)
            ?: LineComment(newId(), lineIndex, quote, trimmed, line.speaker, line.startMs, now, now)
        return (rest + next).sortedWith(compareBy({ it.lineIndex }, { it.createdAt }))
    }

    fun delete(comments: List<LineComment>, id: String): List<LineComment> = comments.filterNot { it.id == id }

    data class Anchored(val byLine: Map<Int, LineComment>, val orphans: List<LineComment>)

    /**
     * Places each note on its line. If the line at [LineComment.lineIndex] no longer starts with the
     * quote (re-transcribed), the nearest line with the same quote is used; otherwise it is an orphan.
     */
    fun anchor(comments: List<LineComment>, lines: List<String>): Anchored {
        val quotes = lines.map(::quoteOf)
        val byLine = LinkedHashMap<Int, LineComment>()
        val orphans = mutableListOf<LineComment>()
        for (c in comments.sortedBy { it.lineIndex }) {
            val idx = if (quotes.getOrNull(c.lineIndex) == c.quote && c.lineIndex !in byLine) c.lineIndex
            else quotes.indices.filter { quotes[it] == c.quote && it !in byLine }.minByOrNull { kotlin.math.abs(it - c.lineIndex) }
            if (idx != null) byLine[idx] = c else orphans += c
        }
        return Anchored(byLine, orphans)
    }
}
