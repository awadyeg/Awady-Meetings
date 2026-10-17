package com.riffado.meetings.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.ai.SherpaOnnxEmbeddingExtractor
import com.riffado.meetings.data.RecordingStatus
import com.riffado.meetings.data.VoiceProfile
import com.riffado.meetings.recording.RecorderState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class VoiceEnrollmentState(
    val busy: Boolean = false,
    val recordedSeconds: Int = 0,
    val message: String? = null,
    val recording: Boolean = false,
)

class VoiceEnrollmentViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MeetingsApp
    val profile = app.voiceProfiles.profile
    val threshold = app.voiceProfiles.threshold
    val modelState = app.speakerModel.state
    private val mutableState = MutableStateFlow(VoiceEnrollmentState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun rename(name: String) {
        profile.value?.let { app.voiceProfiles.save(it.copy(displayName = name.trim().ifEmpty { "Me" })) }
    }

    fun setThreshold(value: Float) = app.voiceProfiles.setThreshold(value.coerceIn(0.30f, 0.90f))
    fun delete() = app.voiceProfiles.delete()
    fun permissionDenied() { mutableState.value = VoiceEnrollmentState(message = "Microphone permission is required") }
    fun cancel() { job?.cancel() }

    fun enroll(displayName: String) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            mutableState.value = VoiceEnrollmentState(busy = true, message = "Preparing voice model…")
            try {
                check(RecorderState.live.value.recordingId == null && app.recordings.recordings.value.none { it.status == RecordingStatus.RECORDING }) { "Stop the current recording before enrolling" }
                val model = app.speakerModel.ensureDownloaded()
                val samples = record()
                mutableState.value = VoiceEnrollmentState(busy = true, recordedSeconds = 30, message = "Learning your voice…")
                val embedding = SherpaOnnxEmbeddingExtractor(model).use { it.embed(samples) }
                app.voiceProfiles.save(VoiceProfile(
                    displayName = displayName.trim().ifEmpty { "Me" }, embedding = embedding,
                    audioSource = VoiceProfile.AudioSource.Enrollment(samples.size * 1000L / 16000),
                ))
                mutableState.value = VoiceEnrollmentState(message = "Voice profile saved")
            } catch (e: CancellationException) {
                mutableState.value = VoiceEnrollmentState(message = "Enrollment cancelled")
                throw e
            } catch (e: Exception) {
                mutableState.value = VoiceEnrollmentState(message = e.message ?: "Enrollment failed")
            } catch (e: LinkageError) {
                mutableState.value = VoiceEnrollmentState(message = "Voice enrollment is unavailable on this device")
            }
        }
    }

    private suspend fun record(): FloatArray = withContext(Dispatchers.IO) {
        check(ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone permission is required" }
        val minBuffer = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBuffer > 0) { "16 kHz microphone recording is unavailable" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 3200))
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Could not open microphone" }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Could not start microphone" }
            val samples = FloatArray(30 * 16000)
            val buffer = ShortArray(1600)
            var offset = 0
            var lastData = System.nanoTime()
            while (offset < samples.size) {
                coroutineContext.ensureActive()
                val count = recorder.read(buffer, 0, minOf(buffer.size, samples.size - offset), AudioRecord.READ_NON_BLOCKING)
                check(count >= 0) { "Microphone read failed ($count)" }
                if (count == 0) {
                    check(System.nanoTime() - lastData < 5_000_000_000L) { "Microphone is not delivering audio" }
                    kotlinx.coroutines.delay(20)
                    continue
                }
                lastData = System.nanoTime()
                repeat(count) { samples[offset + it] = buffer[it] / 32768f }
                offset += count
                mutableState.value = VoiceEnrollmentState(busy = true, recordedSeconds = offset / 16000, recording = true)
            }
            check(samples.any { kotlin.math.abs(it) > 0.01f }) { "No speech detected. Try again in a quiet room." }
            samples
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } finally {
                recorder.release()
            }
        }
    }
}
