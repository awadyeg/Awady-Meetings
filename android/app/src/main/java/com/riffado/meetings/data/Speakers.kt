package com.riffado.meetings.data

import kotlinx.serialization.Serializable

/** Helpers for diarized transcripts made of `Speaker N: text` lines. */
object Speakers {
    private val LINE = Regex("^Speaker (\\d+): ?(.*)$")

    private val TIMING = Regex("^\\[(\\d+)-(\\d+)ms] ?(.*)$")

    @Serializable
    data class Turn(
        val speaker: String?,
        val text: String,
        val startMs: Long? = null,
        val endMs: Long? = null,
        val audioSegment: String? = null,
    )

    fun parse(transcript: String): List<Turn> =
        transcript.lines().filter { it.isNotBlank() }.map { line ->
            val m = LINE.matchEntire(line.trim())
            if (m != null) {
                val timing = TIMING.matchEntire(m.groupValues[2])
                Turn(m.groupValues[1], timing?.groupValues?.get(3) ?: m.groupValues[2],
                    timing?.groupValues?.get(1)?.toLongOrNull(), timing?.groupValues?.get(2)?.toLongOrNull())
            } else Turn(null, line.trim())
        }

    /** Speaker numbers in order of first appearance. */
    fun ids(transcript: String?): List<String> =
        transcript?.let { parse(it).mapNotNull { t -> t.speaker }.distinct() } ?: emptyList()

    fun label(id: String, names: Map<String, String>): String =
        names[id]?.takeIf { it.isNotBlank() } ?: "Speaker $id"

    /** Rewrites `Speaker N:` prefixes with the user's names, for the summary prompt and sharing. */
    fun applyNames(transcript: String, names: Map<String, String>): String =
        parse(transcript).joinToString("\n") { t ->
            if (t.speaker == null) t.text else "${label(t.speaker, names)}: ${t.text}"
        }
}
