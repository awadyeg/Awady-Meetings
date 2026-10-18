package com.riffado.meetings.ai

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.riffado.meetings.MainActivity
import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.R
import com.riffado.meetings.data.AppSettings
import com.riffado.meetings.data.RecordingStatus
import com.riffado.meetings.data.Speakers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Transcribes every segment of a recording, then generates meeting minutes. */
class ProcessingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app = context.applicationContext as MeetingsApp

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val summaryOnly = inputData.getBoolean(KEY_SUMMARY_ONLY, false)
        val recording = app.recordings.get(id) ?: return Result.failure()
        val settings = app.settings.settings.value

        runCatching { setForeground(foregroundInfo(recording.title)) }
            .onFailure { Log.w(TAG, "Could not promote worker to foreground", it) }

        return try {
            var transcript = recording.transcript
            var transcribedBy = recording.transcribedBy
            val speakerTurns = mutableListOf<Speakers.Turn>()
            var nextSpeaker = 1
            var transcribed = false
            
            if (!summaryOnly || transcript.isNullOrBlank()) {
                app.recordings.update(id) { it.copy(status = RecordingStatus.TRANSCRIBING, error = null) }
                val files = app.recordings.segmentFiles(recording).filter { it.exists() && it.length() > 0 }
                
                // Use transcription coordinator for fallback support
                val coordinator = TranscriptionCoordinator(applicationContext, app.transcriptionProviders)
                
                transcript = withContext(Dispatchers.IO) {
                    files.map { file ->
                        val (segmentTranscript, providerId, _) = coordinator.transcribe(file, settings.language)
                        transcribedBy = providerId // Track which provider succeeded
                        val labels = mutableMapOf<String, String>()
                        val turns = Speakers.parse(segmentTranscript).map { turn ->
                            turn.copy(
                                speaker = turn.speaker?.let { labels.getOrPut(it) { (nextSpeaker++).toString() } },
                                audioSegment = file.name,
                            )
                        }
                        speakerTurns += turns
                        turns.joinToString("\n") { turn ->
                            if (turn.speaker == null) turn.text else "Speaker ${turn.speaker}: ${turn.text}"
                        }
                    }
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                }
                
                transcribed = true
                app.recordings.update(id) { 
                    it.copy(
                        transcript = transcript,
                        speakerTurns = speakerTurns,
                        transcribedBy = transcribedBy,
                    )
                }
            }

            if (transcribed && Speakers.ids(transcript).isNotEmpty() &&
                (app.voiceProfiles.profile.value != null || recording.isCall)) {
                SpeakerNaming.detect(app, id)
            }

            if (settings.canSummarize && !transcript.isNullOrBlank()) {
                app.recordings.update(id) { it.copy(status = RecordingStatus.SUMMARIZING) }
                val summary = withContext(Dispatchers.IO) {
                    val names = app.recordings.get(id)?.speakerNames ?: emptyMap()
                    val namedTranscript = Speakers.applyNames(transcript, names)
                    summarizeWithChunking(
                        recordingId = id,
                        transcript = namedTranscript,
                        config = settings.effectiveSummary,
                    )
                }
                val generatedTitle = Prompts.titleFrom(summary)
                app.recordings.update(id) {
                    it.copy(
                        summary = summary,
                        title = if (it.titleIsDefault && generatedTitle != null) generatedTitle else it.title,
                        titleIsDefault = it.titleIsDefault && generatedTitle == null,
                    )
                }
                // Non-fatal: a failed task extraction must not fail the recording.
                try {
                    TaskExtraction.run(app, id)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Task extraction failed for $id", e)
                }
            }

            val done = app.recordings.update(id) { it.copy(status = RecordingStatus.DONE, error = null) }
            notifyResult(id.hashCode(), applicationContext.getString(R.string.notif_done, done?.title ?: ""))
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Processing failed for $id", e)
            val retryable = (e is ApiException && e.retryable) || (e is IOException && e !is ApiException)
            if (retryable && runAttemptCount < MAX_ATTEMPTS) {
                app.recordings.update(id) { it.copy(status = RecordingStatus.QUEUED, error = e.message) }
                Result.retry()
            } else {
                val failed = app.recordings.update(id) {
                    it.copy(status = RecordingStatus.FAILED, error = e.message ?: e.javaClass.simpleName)
                }
                notifyResult(id.hashCode(), applicationContext.getString(R.string.notif_failed, failed?.title ?: ""))
                Result.failure()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(app.recordings.get(inputData.getString(KEY_ID) ?: "")?.title ?: "")
    
    /**
     * Summarize transcript with automatic chunking for long transcripts.
     * Uses map-reduce: chunk → summarize each → merge summaries.
     * Handles 413 and TPM 429 errors by shrinking chunk size and retrying.
     */
    private suspend fun summarizeWithChunking(
        recordingId: String,
        transcript: String,
        config: com.riffado.meetings.data.ProviderConfig,
        initialTokenBudget: Int = 5000,
    ): String {
        var tokenBudget = initialTokenBudget
        var attempt = 0
        val maxAttempts = 3
        
        while (attempt < maxAttempts) {
            try {
                // Check if chunking is needed
                val estimatedTokens = TranscriptChunker.estimateTokens(transcript) + 
                                    TranscriptChunker.estimateTokens(Prompts.MEETING_SYSTEM)
                
                if (estimatedTokens <= tokenBudget) {
                    // Single chunk fits
                    Log.d(TAG, "Transcript fits in single request ($estimatedTokens tokens)")
                    return OpenAiClient.chat(config, Prompts.MEETING_SYSTEM, transcript)
                }
                
                // Split into chunks
                val chunks = TranscriptChunker.chunkTranscript(transcript, Prompts.MEETING_SYSTEM, tokenBudget) { Log.d(TAG, it) }
                
                if (chunks.size == 1) {
                    // Couldn't split further, try anyway
                    Log.w(TAG, "Transcript too large but couldn't split, attempting anyway")
                    return OpenAiClient.chat(config, Prompts.MEETING_SYSTEM, transcript)
                }
                
                Log.d(TAG, "Summarizing in ${chunks.size} chunks (budget: $tokenBudget tokens)")
                
                // Map: Summarize each chunk
                val chunkSummaries = chunks.mapIndexed { index, chunk ->
                    app.recordings.update(recordingId) { 
                        it.copy(error = "Summarizing part ${index + 1} of ${chunks.size}...")
                    }
                    
                    Log.d(TAG, "Summarizing chunk ${index + 1}/${chunks.size}")
                    OpenAiClient.chat(
                        config,
                        Prompts.CHUNK_SUMMARY_SYSTEM,
                        chunk,
                    )
                }
                
                // Reduce: Merge chunk summaries
                app.recordings.update(recordingId) { 
                    it.copy(error = "Merging summaries...")
                }
                
                Log.d(TAG, "Merging ${chunkSummaries.size} chunk summaries")
                val mergedSummary = OpenAiClient.chat(
                    config,
                    Prompts.MERGE_SUMMARIES_SYSTEM,
                    chunkSummaries.joinToString("\n\n---\n\n"),
                )
                
                // Clear progress status
                app.recordings.update(recordingId) { it.copy(error = null) }
                
                return mergedSummary
                
            } catch (e: ApiException) {
                // Check for 413 (payload too large) or 429 with TPM limit
                val isPayloadTooLarge = e.code == 413
                val isTPMLimitExceeded = e.code == 429 && e.message?.contains("TPM", ignoreCase = true) == true
                
                if ((isPayloadTooLarge || isTPMLimitExceeded) && attempt < maxAttempts - 1) {
                    // Shrink chunk size and retry
                    tokenBudget = (tokenBudget * 0.6).toInt() // Reduce by 40%
                    attempt++
                    
                    Log.w(TAG, "Chunking attempt $attempt failed with ${e.code}, shrinking to $tokenBudget tokens")
                    
                    // Respect Retry-After if present
                    val retryAfter = e.message?.let { msg ->
                        Regex("retry[_\\s]after[:\\s]*(\\d+)", RegexOption.IGNORE_CASE)
                            .find(msg)?.groupValues?.get(1)?.toLongOrNull()
                    } ?: 2L
                    
                    kotlinx.coroutines.delay(retryAfter * 1000)
                    continue
                } else {
                    throw e
                }
            }
        }
        
        throw IllegalStateException("Failed to summarize after $maxAttempts chunking attempts")
    }

    private fun foregroundInfo(title: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, MeetingsApp.CHANNEL_PROCESSING)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(applicationContext.getString(R.string.notif_processing, title))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_ID, notification)
        }
    }

    private fun notifyResult(notificationId: Int, text: String) {
        val nm = NotificationManagerCompat.from(applicationContext)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, MeetingsApp.CHANNEL_PROCESSING)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission revoked", e)
        }
    }

    companion object {
        private const val TAG = "ProcessingWorker"
        private const val KEY_ID = "id"
        private const val KEY_SUMMARY_ONLY = "summary_only"
        private const val PROGRESS_ID = 2001
        private const val MAX_ATTEMPTS = 3

        /** Queues processing for [id]; replaces any pending run for the same recording. */
        suspend fun enqueue(context: Context, id: String, summaryOnly: Boolean) {
            val app = context.applicationContext as MeetingsApp
            app.recordings.update(id) { it.copy(status = RecordingStatus.QUEUED, error = null) }
            val request = OneTimeWorkRequestBuilder<ProcessingWorker>()
                .setInputData(workDataOf(KEY_ID to id, KEY_SUMMARY_ONLY to summaryOnly))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("process-$id", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
