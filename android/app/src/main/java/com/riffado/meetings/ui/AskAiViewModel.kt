package com.riffado.meetings.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.R
import com.riffado.meetings.ai.AskAi
import com.riffado.meetings.ai.OpenAiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatEntry(
    val fromUser: Boolean,
    val text: String,
    val sources: List<AskAi.Source> = emptyList(),
    val isError: Boolean = false,
)

data class Conversation(val entries: List<ChatEntry> = emptyList(), val busy: Boolean = false)

/**
 * Holds Ask AI conversations in memory, keyed by scope: `null` for the global chat, or a recording id.
 * Conversations survive navigation (activity-scoped) but not process death.
 */
class AskAiViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MeetingsApp
    private val conversations = mutableMapOf<String, MutableStateFlow<Conversation>>()

    private fun key(scope: String?) = scope ?: "__global"

    fun conversation(scope: String?): StateFlow<Conversation> =
        conversations.getOrPut(key(scope)) { MutableStateFlow(Conversation()) }.asStateFlow()

    fun clear(scope: String?) {
        conversations[key(scope)]?.value = Conversation()
    }

    fun ask(scope: String?, question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        val flow = conversations.getOrPut(key(scope)) { MutableStateFlow(Conversation()) }
        if (flow.value.busy) return
        val history = flow.value.entries.filterNot { it.isError }.map { (if (it.fromUser) "user" else "assistant") to it.text }
        flow.update { it.copy(entries = it.entries + ChatEntry(true, q), busy = true) }
        viewModelScope.launch {
            val reply = try {
                answer(scope, q, history)
            } catch (e: Exception) {
                Log.w(TAG, "Ask AI failed", e)
                ChatEntry(false, e.message ?: e.javaClass.simpleName, isError = true)
            }
            flow.update { it.copy(entries = it.entries + reply, busy = false) }
        }
    }

    /** Re-asks the last question after an error. */
    fun retry(scope: String?) {
        val flow = conversations[key(scope)] ?: return
        val entries = flow.value.entries
        val lastQuestion = entries.lastOrNull { it.fromUser }?.text ?: return
        val idx = entries.indexOfLast { it.fromUser }
        flow.value = flow.value.copy(entries = entries.take(idx))
        ask(scope, lastQuestion)
    }

    private suspend fun answer(scope: String?, question: String, history: List<Pair<String, String>>): ChatEntry {
        val settings = app.settings.settings.value
        if (!settings.canSummarize) {
            return ChatEntry(false, app.getString(R.string.ask_not_configured), isError = true)
        }
        val usable = app.recordings.recordings.value.filter { !it.summary.isNullOrBlank() || !it.transcript.isNullOrBlank() }
        val person = scope?.takeIf { it.startsWith(PERSON_PREFIX) }?.removePrefix(PERSON_PREFIX)
        val recordings = if (person != null) RecordingSearch.filterByPerson(usable, person) else usable
        val recordingScope = if (person != null) null else scope
        if (recordings.isEmpty()) return ChatEntry(false, app.getString(R.string.ask_no_recordings), isError = true)
        val today = SimpleDateFormat("EEEE d MMMM yyyy", Locale.US).format(Date())
        var budget = AskAi.DEFAULT_TOKEN_BUDGET
        while (true) {
            val context = AskAi.buildContext(recordings, question, tokenBudget = budget, scopedTo = recordingScope)
            val messages = AskAi.messages(context, question, history, today)
            try {
                val text = withContext(Dispatchers.IO) { OpenAiClient.chat(settings.effectiveSummary, messages) }
                return ChatEntry(false, text, AskAi.citedSources(text, context.sources))
            } catch (e: Exception) {
                if (AskAi.isTooLarge(e) && budget > MIN_BUDGET) {
                    budget = (budget * 0.55).toInt().coerceAtLeast(MIN_BUDGET)
                    Log.i(TAG, "Request too large, retrying with budget $budget")
                } else throw e
            }
        }
    }

    companion object {
        /** Scope prefix for "Ask AI about this person": `person:<name>` limits retrieval to their recordings. */
        const val PERSON_PREFIX = "person:"
        private const val TAG = "AskAi"
        private const val MIN_BUDGET = 1_500
    }
}
