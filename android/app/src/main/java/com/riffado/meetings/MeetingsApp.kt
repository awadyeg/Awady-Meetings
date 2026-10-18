package com.riffado.meetings

import com.riffado.meetings.ai.SpeakerModelManager
import com.riffado.meetings.data.VoiceProfileStore
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import com.riffado.meetings.data.CallRecordingStore
import com.riffado.meetings.data.RecordingStore
import com.riffado.meetings.data.SettingsStore
import com.riffado.meetings.data.TranscriptionStore
import com.riffado.meetings.recording.RecorderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MeetingsApp : Application() {
    lateinit var recordings: RecordingStore
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var callRecordings: CallRecordingStore
        private set
    lateinit var transcriptionProviders: TranscriptionStore
        private set

    val tasks by lazy { com.riffado.meetings.data.TaskStore(this) }
    val voiceProfiles by lazy { VoiceProfileStore(this) }
    val speakerModel by lazy { SpeakerModelManager(this) }

    val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        recordings = RecordingStore(this)
        settings = SettingsStore(this)
        callRecordings = CallRecordingStore(this)
        transcriptionProviders = TranscriptionStore(this, settings)
        createChannels()
        appScope.launch { recordings.recoverInterrupted(RecorderState.live.value.recordingId) }
        
        // Schedule daily provider recovery checks
        com.riffado.meetings.ai.ProviderRecoveryWorker.schedule(this)
    }

    private fun createChannels() {
        val nm = getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                getString(R.string.channel_recording),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROCESSING,
                getString(R.string.channel_processing),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    companion object {
        const val CHANNEL_RECORDING = "recording"
        const val CHANNEL_PROCESSING = "processing"

        lateinit var instance: MeetingsApp
            private set
    }
}
