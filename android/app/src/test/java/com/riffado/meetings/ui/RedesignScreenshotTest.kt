package com.riffado.meetings.ui

import android.app.Application
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the Phase 1 screens to PNG. Opt-in: `./gradlew testDebugUnitTest -Pscreenshots`.
 * Note: Robolectric cannot run RenderEffect, so glass shows its non-blurred fallback here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RedesignScreenshotTest {
    private val dir = File(System.getProperty("riffado.screenshot.dir") ?: "build/screenshots").apply { mkdirs() }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) =
        captureRoboImage(File(dir, "$name.png").path) {
            androidx.compose.runtime.CompositionLocalProvider(LocalStaticAnimations provides true) { MeetingsTheme(darkTheme = dark) { content() } }
        }

    @Test fun homeLight() = shot("home_light", false) { PreviewHome() }
    @Test fun homeDark() = shot("home_dark", true) { PreviewHome() }
    @Test fun homeRecordingPill() = shot("home_recording_pill", false) { PreviewHomeRecording() }
    @Test fun recorderDark() = shot("recorder_dark", true) { PreviewRecorder() }
    @Test fun recorderLight() = shot("recorder_light", false) { PreviewRecorder() }
    @Test fun detailLight() = shot("detail_light", false) { PreviewDetail() }
    @Test fun detailTranscriptDark() = shot("detail_transcript_dark", true) { PreviewDetail(transcriptTab = true) }
    @Test fun tasksLight() = shot("tasks_light", false) { PreviewTasks() }

    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    @Test fun homeArabicRtl() = shot("home_ar_rtl", false) { PreviewHome() }
}
