package com.riffado.meetings.ai

import android.util.Log
import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.R
import com.riffado.meetings.ui.RecordingSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Ask AI pipeline (retrieval over recordings → chat completion with numbered sources), shared by the
 * in-app chat and the MCP `ask` tool.
 */
object AskAiEngine {
    class Answer(val text: String, val sources: List<AskAi.Source>, val searched: Int)

    /** A user-facing failure (not configured, nothing to search, …). */
    class AskFailure(message: String) : Exception(message)

    /**
     * @param personName restrict retrieval to recordings with this person (call contact or named speaker)
     * @param recordingId restrict retrieval to one recording
     */
    suspend fun answer(
        app: MeetingsApp,
        question: String,
        history: List<Pair<String, String>> = emptyList(),
        personName: String? = null,
        recordingId: String? = null,
    ): Answer {
        val settings = app.settings.settings.value
        if (!settings.canSummarize) throw AskFailure(app.getString(R.string.ask_not_configured))
        val usable = app.recordings.recordings.value.filter { !it.summary.isNullOrBlank() || !it.transcript.isNullOrBlank() }
        val recordings = if (personName != null) RecordingSearch.filterByPerson(usable, personName) else usable
        if (recordings.isEmpty() || (recordingId != null && recordings.none { it.id == recordingId })) {
            throw AskFailure(app.getString(R.string.ask_no_recordings))
        }
        val today = SimpleDateFormat("EEEE d MMMM yyyy", Locale.US).format(Date())
        var budget = AskAi.DEFAULT_TOKEN_BUDGET
        while (true) {
            val context = AskAi.buildContext(recordings, question, tokenBudget = budget, scopedTo = recordingId)
            val messages = AskAi.messages(context, question, history, today)
            try {
                val text = withContext(Dispatchers.IO) { OpenAiClient.chat(settings.effectiveSummary, messages) }
                return Answer(text, AskAi.citedSources(text, context.sources), recordings.size)
            } catch (e: Exception) {
                if (AskAi.isTooLarge(e) && budget > MIN_BUDGET) {
                    budget = (budget * 0.55).toInt().coerceAtLeast(MIN_BUDGET)
                    Log.i(TAG, "Request too large, retrying with budget $budget")
                } else throw e
            }
        }
    }

    private const val TAG = "AskAi"
    private const val MIN_BUDGET = 1_500
}
