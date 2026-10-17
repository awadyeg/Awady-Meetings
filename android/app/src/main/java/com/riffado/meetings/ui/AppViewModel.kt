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
    
    init {
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

    fun delete(id: String) {
        viewModelScope.launch { app.recordings.delete(id) }
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
}
