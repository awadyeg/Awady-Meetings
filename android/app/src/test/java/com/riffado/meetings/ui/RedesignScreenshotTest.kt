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

    @Test fun tasksDark() = shot("tasks_dark", true) { PreviewTasks() }
    @Test fun tasksExtracting() = shot("tasks_done_extracting_light", false) { PreviewTasksDone() }
    @Test fun commentsLight() = shot("comments_light", false) { PreviewComments() }
    @Test fun commentsDark() = shot("comments_dark", true) { PreviewComments() }
    @Test fun commentEditorLight() = shot("comment_editor_light", false) { PreviewComments(editLine = 3) }
    @Test fun exportSheetLight() = shot("export_sheet_light", false) { PreviewExportSheet() }
    @Test fun exportSheetDark() = shot("export_sheet_dark", true) { PreviewExportSheet(busy = com.riffado.meetings.export.ExportKind.NOTION) }
    @Test fun personLight() = shot("person_light", false) { PreviewPerson() }
    @Test fun personDark() = shot("person_dark", true) { PreviewPerson() }
    @Config(qualifiers = "w411dp-h1460dp-xxhdpi")
    @Test fun mcpSettingsLight() = shot("mcp_settings_light", false) { PreviewMcpSettings() }
    @Config(qualifiers = "w411dp-h1460dp-xxhdpi")
    @Test fun mcpSettingsDark() = shot("mcp_settings_dark", true) { PreviewMcpSettings(httpStyle = true) }
    @Test fun askAiLight() = shot("ask_ai_light", false) { PreviewAskAi() }
    @Test fun askAiEmptyDark() = shot("ask_ai_empty_dark", true) { PreviewAskAi(empty = true) }

    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    @Test fun askAiRecordingArabic() = shot("ask_ai_recording_ar", false) { PreviewAskAi(empty = true, scopeTitle = "أحمد حسن") }

    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    @Test fun tasksArabicRtl() = shot("tasks_ar_rtl", false) { PreviewTasks() }

    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    @Test fun commentsArabicRtl() = shot("comments_ar_rtl", false) { PreviewComments(editLine = 1) }

    @Config(qualifiers = "ar-w411dp-h1460dp-xxhdpi")
    @Test fun mcpSettingsArabicRtl() = shot("mcp_settings_ar_rtl", false) { PreviewMcpSettings() }

    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    @Test fun personArabicRtl() = shot("person_ar_rtl", false) { PreviewPerson("أحمد حسن") }

    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    @Test fun exportSheetArabic() = shot("export_sheet_ar_rtl", false) { PreviewExportSheet(notionConfigured = false) }

    @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    @Test fun homeArabicRtl() = shot("home_ar_rtl", false) { PreviewHome() }
}
