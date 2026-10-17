package com.riffado.meetings.ai

import com.riffado.meetings.data.ProviderConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/**
 * Munsit Arabic speech-to-text with speaker diarization support.
 * Uses the diarization endpoint to get speaker-labeled transcripts that map into the existing speaker model.
 */
object MunsitClient {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class DiarizationResponse(
        val statusCode: Int,
        val data: DiarizationData,
        val message: String,
    )

    @Serializable
    private data class DiarizationData(
        val merged: List<MergedSegment>,
        val duration: Double,
        val transcriptionId: String? = null,
    )

    @Serializable
    private data class MergedSegment(
        val start: Double,
        val end: Double,
        val speaker: String,
        val text: String,
    )

    fun transcribe(config: ProviderConfig, file: File, language: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
            .addFormDataPart("model", config.model)
            .build()

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/audio/diarization/transcribe")
            .header("x-api-key", config.apiKey)
            .post(body)
            .build()

        val text = OpenAiClient.http.newCall(request).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(res.code, munsitError(res.code, raw))
            raw
        }

        val response = json.decodeFromString<DiarizationResponse>(text)
        if (response.statusCode != 200) {
            throw IOException("Munsit returned status ${response.statusCode}: ${response.message}")
        }

        return formatDiarizedTranscript(response.data)
    }

    /**
     * Formats the merged segments into "Speaker N: text" format,
     * mapping Munsit's SPEAKER_00, SPEAKER_01, etc. to Speaker 1, Speaker 2, etc.
     */
    private fun formatDiarizedTranscript(data: DiarizationData): String {
        if (data.merged.isEmpty()) {
            throw IOException("Munsit returned an empty transcript")
        }

        val speakerMap = mutableMapOf<String, Int>()
        val lines = mutableListOf<String>()

        for (segment in data.merged) {
            val speakerNum = speakerMap.getOrPut(segment.speaker) { speakerMap.size + 1 }
            val text = segment.text.trim()
            if (text.isNotBlank()) {
                lines.add("Speaker $speakerNum: [${(segment.start * 1000).toLong()}-${(segment.end * 1000).toLong()}ms] $text")
            }
        }

        return lines.joinToString("\n")
    }

    private fun munsitError(code: Int, body: String): String {
        val detail = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return if (detail.isNullOrBlank()) {
            OpenAiClient.errorMessage(code, body)
        } else {
            "HTTP $code: $detail"
        }
    }
}
