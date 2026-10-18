package com.riffado.meetings.ai

import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.Speakers
import com.riffado.meetings.ui.RecordingSearch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln

/**
 * Retrieval-augmented "Ask AI" over local recordings.
 *
 * Everything here is pure Kotlin so it can be unit-tested: recordings are cut into passages
 * (the summary, then transcript chunks on speaker-turn boundaries), ranked with BM25 over
 * Arabic-normalised tokens, and packed into a prompt under a character budget derived from
 * the same token estimate used by the map-reduce summariser ([TranscriptChunker]).
 */
object AskAi {
    data class Passage(
        val recordingId: String,
        val title: String,
        val createdAt: Long,
        val kind: Kind,
        val text: String,
        val index: Int,
    ) {
        enum class Kind { SUMMARY, TRANSCRIPT }
    }

    /** A numbered source handed to the model; citations `[n]` refer to [number]. */
    data class Source(val number: Int, val recordingId: String, val title: String, val createdAt: Long)

    data class Context(val prompt: String, val sources: List<Source>, val usedChars: Int)

    /** Characters per transcript passage; small enough that several recordings fit one prompt. */
    const val PASSAGE_CHARS = 1_400

    /** Default token budget per request (same order as the summary chunks, safe for Groq free TPM). */
    const val DEFAULT_TOKEN_BUDGET = 5_000

    private val STOPWORDS = setOf(
        // English
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "is", "are", "was", "were", "be", "it", "this", "that",
        "with", "as", "at", "by", "what", "who", "when", "where", "how", "why", "did", "do", "does", "i", "we", "you", "me",
        "my", "our", "your", "about", "from", "any", "all", "tell", "please", "can", "could", "would", "should", "there",
        "speaker", "said", "say",
        // Arabic / Egyptian (normalised forms: ا for أإآ, ه for ة, ي for ى)
        "في", "من", "علي", "الي", "عن", "مع", "ان", "او", "و", "ما", "ماذا", "هل", "هو", "هي", "انا", "احنا", "انت", "ده",
        "دي", "اللي", "الذي", "التي", "كان", "كانت", "ايه", "امتي", "فين", "ازاي", "ليه", "مين", "قال", "قالت", "عايز",
        "عاوز", "بتاع", "بتاعت", "كل", "بس", "لو", "يعني", "تم", "قد", "لا", "مش",
    )

    private val TOKEN = Regex("[\\p{L}\\p{N}]+")

    fun tokenize(text: String): List<String> =
        TOKEN.findAll(RecordingSearch.normalizeArabic(text)).map { it.value }
            .map { stripArabicArticle(it) }
            .filter { it.length >= 2 && it !in STOPWORDS }
            .toList()

    /** "الاجتماع" → "اجتماع", "والعميل" → "عميل": cheap prefix stripping so Arabic words match. */
    private fun stripArabicArticle(t: String): String {
        if (t.firstOrNull()?.let { it in '\u0600'..'\u06FF' } != true) return t
        for (prefix in listOf("وال", "بال", "فال", "كال", "لل", "ال")) {
            if (t.startsWith(prefix) && t.length - prefix.length >= 2) return t.removePrefix(prefix)
        }
        return t
    }

    fun passages(recording: Recording, passageChars: Int = PASSAGE_CHARS): List<Passage> {
        val out = mutableListOf<Passage>()
        recording.summary?.takeIf { it.isNotBlank() }?.let {
            out += Passage(recording.id, recording.title, recording.createdAt, Passage.Kind.SUMMARY, it.trim(), 0)
        }
        val transcript = recording.transcript?.takeIf { it.isNotBlank() } ?: return out
        val named = Speakers.applyNames(transcript, recording.speakerNames)
        // TranscriptChunker works in tokens; convert our char target back with its own ratio.
        val budget = (passageChars / 2.5).toInt()
        TranscriptChunker.chunkTranscript(named, promptTemplate = "", tokenBudget = budget)
            .filter { it.isNotBlank() }
            .forEachIndexed { i, chunk ->
                out += Passage(recording.id, recording.title, recording.createdAt, Passage.Kind.TRANSCRIPT, chunk.trim(), i + 1)
            }
        return out
    }

    /** BM25 ranking. Title / contact matches and summaries get a small boost. Returns (passage, score) best first. */
    fun rank(recordings: List<Recording>, question: String, passageChars: Int = PASSAGE_CHARS): List<Pair<Passage, Double>> {
        val all = recordings.flatMap { passages(it, passageChars) }
        if (all.isEmpty()) return emptyList()
        val query = tokenize(question).distinct()
        if (query.isEmpty()) return emptyList()
        val docs = all.map { tokenize(it.text) }
        val avg = docs.map { it.size }.average().coerceAtLeast(1.0)
        val df = query.associateWith { q -> docs.count { q in it } }
        val n = docs.size
        val k1 = 1.4
        val b = 0.75
        val byId = recordings.associateBy { it.id }
        return all.mapIndexed { i, p ->
            val tf = docs[i].groupingBy { it }.eachCount()
            var score = 0.0
            for (q in query) {
                val f = tf[q] ?: prefixFreq(docs[i], q)
                if (f == 0) continue
                val idf = ln(1 + (n - df.getValue(q) + 0.5) / (df.getValue(q) + 0.5))
                score += idf * (f * (k1 + 1)) / (f + k1 * (1 - b + b * docs[i].size / avg))
            }
            if (score > 0) {
                val rec = byId[p.recordingId]
                val meta = tokenize(listOfNotNull(rec?.title, rec?.callContactName).joinToString(" ")).toSet()
                if (query.any { it in meta }) score *= 1.3
                if (p.kind == Passage.Kind.SUMMARY) score *= 1.15
            }
            p to score
        }.filter { it.second > 0 }.sortedByDescending { it.second }
    }

    /** Counts tokens that start with [q] (handles suffixes like "meetings"/"meeting", "اجتماعات"/"اجتماع"). */
    private fun prefixFreq(doc: List<String>, q: String): Int =
        if (q.length < 4) 0 else doc.count { it.length > q.length && it.startsWith(q) }

    /**
     * Picks passages for the prompt within [tokenBudget].
     * - Ranked passages first (at most [perRecording] per recording to keep answers diverse).
     * - If nothing matches (e.g. "what happened this week?"), falls back to the most recent summaries.
     * - For a single-recording chat the recording's summary is always included first.
     */
    fun buildContext(
        recordings: List<Recording>,
        question: String,
        tokenBudget: Int = DEFAULT_TOKEN_BUDGET,
        scopedTo: String? = null,
        perRecording: Int = 3,
        locale: Locale = Locale.getDefault(),
    ): Context {
        val pool = if (scopedTo != null) recordings.filter { it.id == scopedTo } else recordings
        val reserve = TranscriptChunker.estimateTokens(SYSTEM_PROMPT) + TranscriptChunker.estimateTokens(question) + 600
        val maxChars = ((tokenBudget - reserve).coerceAtLeast(500) * 2.5).toInt()

        val ranked = rank(pool, question).map { it.first }
        val ordered = buildList {
            if (scopedTo != null) addAll(pool.flatMap { passages(it) }.filter { it.kind == Passage.Kind.SUMMARY })
            addAll(ranked)
            if (scopedTo != null) addAll(pool.flatMap { passages(it) })          // then the rest, in order
            if (ranked.isEmpty() && scopedTo == null) {
                addAll(pool.sortedByDescending { it.createdAt }.flatMap { passages(it).take(1) })
            }
        }.distinctBy { it.recordingId to it.index }

        val picked = mutableListOf<Passage>()
        val perRec = mutableMapOf<String, Int>()
        var used = 0
        for (p in ordered) {
            val count = perRec[p.recordingId] ?: 0
            if (scopedTo == null && count >= perRecording) continue
            val cost = p.text.length + 80
            if (used + cost > maxChars) {
                if (picked.isEmpty()) {           // always give the model something, truncated
                    picked += p.copy(text = p.text.take((maxChars - 80).coerceAtLeast(200)))
                    used = maxChars
                }
                continue
            }
            picked += p; used += cost; perRec[p.recordingId] = count + 1
        }

        val numbers = LinkedHashMap<String, Source>()
        picked.forEach { p ->
            numbers.getOrPut(p.recordingId) { Source(numbers.size + 1, p.recordingId, p.title, p.createdAt) }
        }
        val dateFmt = SimpleDateFormat("EEE d MMM yyyy, HH:mm", locale)
        val body = StringBuilder()
        numbers.values.forEach { src ->
            body.append("[${src.number}] \"${src.title}\" — ${dateFmt.format(Date(src.createdAt))}\n")
            picked.filter { it.recordingId == src.recordingId }.forEach { p ->
                body.append(if (p.kind == Passage.Kind.SUMMARY) "Summary:\n" else "Transcript excerpt:\n")
                body.append(p.text).append("\n")
            }
            body.append("\n")
        }
        return Context(body.toString().trim(), numbers.values.toList(), used)
    }

    const val SYSTEM_PROMPT = """You are an assistant that answers questions about the user's own recorded meetings and phone calls.
Use ONLY the numbered sources provided. Cite every fact with the source number in square brackets, e.g. [1] or [2][3].
If the sources do not contain the answer, say so briefly; do not invent details.
Answer in the same language as the user's question (Egyptian/Modern Arabic if the question is Arabic). Be concise; use short bullet lists when listing items."""

    /** Builds the chat messages: system, earlier turns (trimmed), then sources + the new question. */
    fun messages(context: Context, question: String, history: List<Pair<String, String>>, today: String): List<Pair<String, String>> {
        val trimmed = history.takeLast(6).map { (role, text) -> role to text.take(1_500) }
        val user = buildString {
            append("Today is ").append(today).append(".\n\nSources:\n")
            append(context.prompt.ifBlank { "(no recordings matched)" })
            append("\n\nQuestion: ").append(question)
        }
        return listOf("system" to SYSTEM_PROMPT) + trimmed + ("user" to user)
    }

    private val CITATION = Regex("\\[(\\d{1,2})]")

    /** Sources actually cited in [answer]; if the model cited nothing, returns all sources. */
    fun citedSources(answer: String, sources: List<Source>): List<Source> {
        val nums = CITATION.findAll(answer).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()
        val cited = sources.filter { it.number in nums }
        return cited.ifEmpty { sources }
    }

    /** True for provider errors that mean "request too large": shrink the budget and retry. */
    fun isTooLarge(e: Throwable): Boolean = e is ApiException &&
        (e.code == 413 || (e.code == 429 && (e.message?.contains("token", ignoreCase = true) == true || e.message?.contains("TPM") == true)) ||
            (e.code == 400 && e.message?.contains("context", ignoreCase = true) == true))
}
