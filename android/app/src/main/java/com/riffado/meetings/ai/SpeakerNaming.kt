package com.riffado.meetings.ai

import android.util.Log
import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.callrecorder.CallRecorderDiagnostics
import com.riffado.meetings.data.Speakers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object SpeakerNaming {
    suspend fun detect(app: MeetingsApp, id: String): String = withContext(Dispatchers.Default) {
        fun log(message: String) {
            Log.d("SpeakerIdentifier", message)
            CallRecorderDiagnostics.log(app, "Speaker ID $id: $message")
        }
        val recording = app.recordings.get(id) ?: return@withContext "Recording no longer exists"
        val profile = app.voiceProfiles.profile.value
        if (profile == null && !recording.isCall) return@withContext "Enroll your voice in Settings first"
        if (Speakers.ids(recording.transcript).isEmpty()) return@withContext "No speaker labels in this transcript"
        var extractor: SherpaOnnxEmbeddingExtractor? = null
        try {
            if (profile != null && app.speakerModel.isReady()) extractor = SherpaOnnxEmbeddingExtractor(app.speakerModel.modelFile)
            val result = SpeakerIdentifier(extractor, ::log).identifySpeakers(
                recording, app.recordings.segmentFiles(recording).firstOrNull() ?: File(app.filesDir, "missing-audio"),
                profile, threshold = app.voiceProfiles.threshold.value,
            )
            app.recordings.update(id) { current ->
                if (current.transcript != recording.transcript || current.speakerTurns != recording.speakerTurns) current
                else {
                    val manual = current.speakerNames.filterKeys { it !in current.autoNamedSpeakers }
                    val automatic = result.names.filterKeys { it in result.autoNamedLabels && it !in manual }
                    current.copy(speakerNames = manual + automatic, autoNamedSpeakers = automatic.keys)
                }
            }
            when {
                result.matchedUser != null -> "Matched you as Speaker ${result.matchedUser} (${"%.2f".format(Locale.US, result.scores[result.matchedUser])})"
                result.autoNamedLabels.isNotEmpty() -> "Named call participant"
                profile != null && extractor == null -> "Download the voice model in Settings first"
                result.failures.isNotEmpty() -> result.failures.values.first()
                else -> "No match above threshold"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Identification failed: ${e.message}")
            "Speaker identification failed: ${e.message}"
        } catch (e: LinkageError) {
            log("Native speaker identification unavailable: ${e.message}")
            "Speaker identification is unavailable on this device"
        } finally {
            extractor?.close()
        }
    }
}
