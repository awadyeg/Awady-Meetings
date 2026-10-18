package com.riffado.meetings.ai

import android.util.Log
import com.riffado.meetings.MeetingsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs task extraction for one recording against the configured summary provider and stores the result. */
object TaskExtraction {
    private const val TAG = "TaskExtraction"

    /** @return number of tasks stored. Throws when the provider is not configured or the call fails. */
    suspend fun run(app: MeetingsApp, recordingId: String): Int {
        val recording = app.recordings.get(recordingId) ?: error("Recording not found")
        val settings = app.settings.settings.value
        check(settings.canSummarize) { "Summary provider not configured" }
        if (recording.summary.isNullOrBlank() && recording.transcript.isNullOrBlank()) return 0
        var maxChars = TaskExtractor.MAX_INPUT_CHARS
        while (true) {
            try {
                val reply = withContext(Dispatchers.IO) {
                    OpenAiClient.chat(settings.effectiveSummary, TaskExtractor.SYSTEM_PROMPT, TaskExtractor.userPrompt(recording, maxChars = maxChars))
                }
                val tasks = TaskExtractor.parse(reply, recordingId)
                if (tasks.isEmpty() && TaskExtractor.extractJson(reply) == null) {
                    Log.w(TAG, "Unparseable task reply for $recordingId: ${reply.take(200)}")
                    error("Could not read the task list from the AI reply")
                }
                app.tasks.replaceForRecording(recordingId, tasks)
                app.recordings.update(recordingId) { it.copy(tasksExtractedAt = System.currentTimeMillis()) }
                return tasks.size
            } catch (e: Exception) {
                if (AskAi.isTooLarge(e) && maxChars > 2_500) {
                    maxChars /= 2
                } else throw e
            }
        }
    }
}
