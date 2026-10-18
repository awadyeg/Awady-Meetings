package com.riffado.meetings.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.ai.SpeakerNaming
import com.riffado.meetings.ai.ProcessingWorker
import com.riffado.meetings.callrecorder.CallParticipantResolver
import com.riffado.meetings.data.AppSettings
import com.riffado.meetings.data.AudioImporter
import com.riffado.meetings.recording.RecorderState
import com.riffado.meetings.recording.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MeetingsApp

    private val detecting = MutableStateFlow<Set<String>>(emptySet())
    val detectingSpeakers = detecting.asStateFlow()

    val recordings = app.recordings.recordings
    val settings = app.settings.settings
    val live = RecorderState.live
    val callSettings = app.callRecordings.settings
    val transcriptionProviders = app.transcriptionProviders.settings
    
    private val levelHistory = MutableStateFlow<List<Float>>(emptyList())
    /** Recent input levels of the active recording, for the live waveform (survives navigation). */
    val levels = levelHistory.asStateFlow()
    private var levelsFor: String? = null

    init {
        viewModelScope.launch {
            live.collect { l ->
                if (l.recordingId != levelsFor) {
                    levelsFor = l.recordingId
                    levelHistory.value = emptyList()
                }
                if (l.isActive) levelHistory.value = (levelHistory.value + if (l.isPaused) 0f else l.level).takeLast(LEVEL_HISTORY)
            }
        }
        // Backfill call participant info for existing recordings on app start
        viewModelScope.launch {
            backfillCallParticipants()
        }
    }

    fun startRecording() = RecordingService.send(app, RecordingService.ACTION_START)
    fun pauseRecording() = RecordingService.send(app, RecordingService.ACTION_PAUSE)
    fun resumeRecording() = RecordingService.send(app, RecordingService.ACTION_RESUME)
    fun stopRecording() = RecordingService.send(app, RecordingService.ACTION_STOP)
    
    fun saveCallSettings(settings: com.riffado.meetings.data.CallRecordingSettings) {
        app.callRecordings.save(settings)
    }

    fun importAudio(uri: Uri, callback: (String?) -> Unit) {
        viewModelScope.launch {
            val id = AudioImporter.importAudio(app, uri, app.recordings)
            callback(id)
        }
    }

    fun process(id: String, summaryOnly: Boolean) {
        viewModelScope.launch { ProcessingWorker.enqueue(app, id, summaryOnly) }
    }

    fun rename(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            app.recordings.update(id) { it.copy(title = trimmed, titleIsDefault = false) }
        }
    }

    fun renameSpeaker(id: String, speaker: String, name: String) {
        viewModelScope.launch {
            app.recordings.update(id) {
                val names = if (name.isBlank()) it.speakerNames - speaker else it.speakerNames + (speaker to name.trim())
                it.copy(speakerNames = names, autoNamedSpeakers = it.autoNamedSpeakers - speaker)
            }
        }
    }

    fun redetectSpeakers(id: String, onResult: (String) -> Unit) {
        if (id in detecting.value) return
        detecting.value += id
        viewModelScope.launch {
            try {
                onResult(SpeakerNaming.detect(app, id))
            } finally {
                detecting.value -= id
            }
        }
    }

    fun addBookmark(id: String, atMs: Long) {
        viewModelScope.launch {
            app.recordings.update(id) { it.copy(bookmarks = (it.bookmarks + atMs).distinct().sorted()) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            app.recordings.delete(id)
            app.tasks.deleteForRecording(id)
        }
    }

    /** Adds / edits / (blank text) deletes the note on a transcript line. */
    fun saveComment(recordingId: String, lineIndex: Int, line: com.riffado.meetings.data.Speakers.Turn, text: String) {
        viewModelScope.launch {
            app.recordings.update(recordingId) {
                it.copy(comments = com.riffado.meetings.data.Comments.upsert(it.comments, lineIndex, line, text, System.currentTimeMillis()))
            }
        }
    }

    // ---- Tasks ----
    data class ExtractionProgress(val done: Int, val total: Int)

    val tasks = app.tasks.tasks
    private val extracting = MutableStateFlow<ExtractionProgress?>(null)
    /** Progress of an on-demand task extraction run (null when idle). */
    val taskExtraction = extracting.asStateFlow()

    fun setTaskDone(id: String, done: Boolean) {
        viewModelScope.launch { app.tasks.setDone(id, done) }
    }

    /** Recordings with minutes/transcript that never had tasks extracted (or all of them when [all]). */
    fun extractableRecordings(all: Boolean): List<String> =
        recordings.value.filter {
            it.status == com.riffado.meetings.data.RecordingStatus.DONE &&
                (!it.summary.isNullOrBlank() || !it.transcript.isNullOrBlank()) &&
                (all || it.tasksExtractedAt == null)
        }.map { it.id }

    /** Extracts tasks for [ids] one by one (sequential to stay under provider rate limits). */
    fun extractTasks(ids: List<String>, onResult: (String) -> Unit) {
        if (extracting.value != null || ids.isEmpty()) return
        if (!settings.value.canSummarize) {
            onResult(app.getString(com.riffado.meetings.R.string.ask_not_configured)); return
        }
        extracting.value = ExtractionProgress(0, ids.size)
        viewModelScope.launch {
            var found = 0
            var failed = 0
            var lastError: String? = null
            ids.forEachIndexed { i, id ->
                try {
                    found += com.riffado.meetings.ai.TaskExtraction.run(app, id)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    lastError = e.message ?: e.javaClass.simpleName
                }
                extracting.value = ExtractionProgress(i + 1, ids.size)
            }
            extracting.value = null
            val res = app.resources
            val msg = res.getQuantityString(com.riffado.meetings.R.plurals.tasks_extract_result, found, found, ids.size - failed)
            onResult(if (failed > 0) msg + "\n" + app.getString(com.riffado.meetings.R.string.tasks_extract_failed, failed, lastError) else msg)
        }
    }

    fun saveSettings(value: AppSettings) = app.settings.save(value)
    
    fun saveTranscriptionProviders(value: com.riffado.meetings.data.TranscriptionSettings) = 
        app.transcriptionProviders.save(value)

    fun segmentPaths(id: String): List<String> =
        app.recordings.get(id)?.let { rec -> app.recordings.segmentFiles(rec).map { it.absolutePath } }
            ?: emptyList()
    
    /**
     * Backfill call participant info for existing call recordings that lack it.
     * Runs in background on app start.
     */
    private suspend fun backfillCallParticipants() = withContext(Dispatchers.IO) {
        try {
            val callRecordings = recordings.value.filter { it.isCall && it.callNumber == null }
            if (callRecordings.isEmpty()) return@withContext
            
            callRecordings.forEach { recording ->
                val (number, contactName) = CallParticipantResolver.resolveCallParticipant(
                    app,
                    recording.createdAt
                )
                
                if (number != null || contactName != null) {
                    app.recordings.update(recording.id) {
                        val newTitle = when {
                            !contactName.isNullOrBlank() -> contactName
                            !number.isNullOrBlank() -> number
                            else -> it.title
                        }
                        it.copy(
                            title = newTitle,
                            titleIsDefault = contactName == null && number == null,
                            callNumber = number,
                            callContactName = contactName,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail backfill - not critical
            android.util.Log.w("AppViewModel", "Call participant backfill failed", e)
        }
    }

    private companion object {
        const val LEVEL_HISTORY = 240
    }
}
