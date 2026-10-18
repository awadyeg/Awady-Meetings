package com.riffado.meetings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.riffado.meetings.data.AppSettings
import com.riffado.meetings.data.CallRecordingSettings
import com.riffado.meetings.data.ProviderConfig
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.RecordingStatus
import com.riffado.meetings.recording.LiveRecording
import android.media.MediaRecorder

/**
 * Preview composables for screenshot documentation.
 * These can be rendered in Android Studio's preview pane or used with Paparazzi.
 */

@Preview(
    name = "Home Screen with Filter Tabs",
    showBackground = true,
    showSystemUi = true,
)
@Composable
private fun PreviewHomeScreenWithFilters() {
    val sampleRecordings = listOf(
        Recording(
            id = "1",
            title = "Team standup meeting",
            createdAt = System.currentTimeMillis() - 3600000,
            durationMs = 900000,
            status = RecordingStatus.DONE,
            segments = listOf("1-0.m4a"),
            tags = emptySet(),
        ),
        Recording(
            id = "2",
            title = "Ahmed Hassan",
            createdAt = System.currentTimeMillis() - 7200000,
            durationMs = 300000,
            status = RecordingStatus.DONE,
            segments = listOf("2-0.m4a"),
            tags = setOf("call"),
        ),
        Recording(
            id = "3",
            title = "Client project review",
            createdAt = System.currentTimeMillis() - 10800000,
            durationMs = 1800000,
            status = RecordingStatus.RECORDED,
            segments = listOf("3-0.m4a"),
            tags = emptySet(),
        ),
        Recording(
            id = "4",
            title = "+20 100 123 4567",
            createdAt = System.currentTimeMillis() - 14400000,
            durationMs = 180000,
            status = RecordingStatus.DONE,
            segments = listOf("4-0.m4a"),
            tags = setOf("call"),
        ),
    )
    
    MeetingsTheme {
        Surface {
            HomeScreen(
                recordings = sampleRecordings,
                live = LiveRecording(),
                configured = true,
                onImport = {},
                onOpen = {},
                onSettings = {},
            )
        }
    }
}

@Preview(
    name = "Settings - Call Recording Section",
    showBackground = true,
    heightDp = 800,
)
@Composable
private fun PreviewSettingsCallRecording() {
    val settings = AppSettings(
        transcription = ProviderConfig("https://api.groq.com/openai/v1", "***", "whisper-large-v3"),
        summary = ProviderConfig("https://api.groq.com/openai/v1", "***", "llama-3.1-70b-versatile"),
        language = "ar",
        autoProcess = true,
    )
    
    val callSettings = CallRecordingSettings(
        enabled = true,
        audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
        autoProcess = true,
        speakerphoneHint = false,
    )
    
    MeetingsTheme {
        Surface {
            SettingsScreen(
                initial = settings,
                callRecording = callSettings,
                onSave = {},
                onSaveCallRecording = {},
                onManageProviders = {},
                onBack = {},
            )
        }
    }
}

@Preview(
    name = "Home Screen - Calls Filter Active",
    showBackground = true,
    showSystemUi = true,
)
@Composable
private fun PreviewHomeScreenCallsOnly() {
    val callRecordings = listOf(
        Recording(
            id = "1",
            title = "Ahmed Hassan",
            createdAt = System.currentTimeMillis() - 3600000,
            durationMs = 300000,
            status = RecordingStatus.DONE,
            segments = listOf("1-0.m4a"),
            tags = setOf("call"),
        ),
        Recording(
            id = "2",
            title = "+20 100 123 4567",
            createdAt = System.currentTimeMillis() - 7200000,
            durationMs = 180000,
            status = RecordingStatus.TRANSCRIBING,
            segments = listOf("2-0.m4a"),
            tags = setOf("call"),
        ),
    )
    
    MeetingsTheme {
        Surface {
            HomeScreen(
                recordings = callRecordings,
                live = LiveRecording(),
                configured = true,
                onImport = {},
                onOpen = {},
                onSettings = {},
            )
        }
    }
}

@Preview(
    name = "Detail Screen - Arabic Summary with English Terms (LTR Locale)",
    showBackground = true,
    showSystemUi = true,
)
@Composable
private fun PreviewDetailScreenArabicSummary() {
    val recording = Recording(
        id = "1",
        title = "متابعة إعدادات شبكة أبو ...",
        createdAt = System.currentTimeMillis() - 3600000,
        durationMs = 248000,
        status = RecordingStatus.DONE,
        segments = listOf("1-0.m4a"),
        tags = setOf("call"),
        transcript = """
            sayed
            ألو.
            
            awady
            ألو.
            
            sayed
            أنا مهيد مش ضامن إن هو يرجع ثكره، فأنا مش هيصي. router بأكامي بخصوص إل عندي حاجتين لازم بني. نحره أنلب لك إل وصله لإل إنت قلت عليها من قبل. إل بنها ولا خط adaptor يوجد مفيش adaptor طبي. بس هيصي واقفين على.
            
            awady
            وخط بكمان.
            
            sayed
            الخط، ألا أنا مش عارف بصراحة هو فيه ولا مفيهوش، بس مسأل مهيد.
            
            awady
            الصورة كان باين إنه مفيهوش.
        """.trimIndent(),
        summary = """
            ## تفتح
            
            * أنجز من قبل يحيب نحتاج إلى (special task) ووجود عمل خاص مراجعة.
            * للمراجعة Messenger عبر awady سيتم إرسال ملف مهم إلى السرعة.
            
            ## Decisions
            
            * سيستخدم سائق لتوصيل المعدات إن الموقع.
            * بأي طريقة router ولا adaptor طلب إل sayed سيتوقف ماتاحة.
            
            ## Action items
            
            * [ ] (sayed) في المودة adaptor يسأل مهيد عن وجود الخط أو
            * [ ] (sayed) مع 4G router الحفقق من توفير Haidshow
            * [ ] (sayed) من استلاك محمد لبطاقة SIM اشباع
            * [ ] (awady) إلى special task إرسال الملف الخاص بإل Messenger عبر (sayed)
            * [ ] (sayed) لتنسيق سائق لتوصيل المعدات إلى الموقع
            * [ ] (awady) بعد انتهاء الاجتماعات awady مراجعة العمل المنجز من يحيب من قبل
        """.trimIndent(),
        speakerNames = mapOf("sayed" to "Sayed", "awady" to "Awady"),
    )
    
    MeetingsTheme {
        Surface {
            DetailScreen(
                recording = recording,
                segmentPaths = emptyList(),
                canProcess = true,
                onBack = {},
                onProcess = {},
                onRename = {},
                onRenameSpeaker = { _, _ -> },
                onDelete = {},
            )
        }
    }
}

@Preview(
    name = "Markdown - Mixed Arabic/English Content",
    showBackground = true,
    widthDp = 360,
)
@Composable
private fun PreviewMarkdownMixedContent() {
    val markdown = """
        ## تفتح
        
        * أنجز من قبل يحيب نحتاج إلى (special task) ووجود عمل خاص مراجعة.
        * للمراجعة Messenger عبر awady سيتم إرسال ملف مهم إلى السرعة.
        
        ## English Heading
        
        * This is an English bullet point with proper LTR alignment.
        * [ ] This is a checkbox item in English.
        
        ## مشاكل تقنية
        
        * [ ] router يجب شراء جديد من المتجر.
        * [x] adapter تم العثور على كابل في المخزن.
    """.trimIndent()
    
    MeetingsTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                SimpleMarkdown(markdown)
            }
        }
    }
}
