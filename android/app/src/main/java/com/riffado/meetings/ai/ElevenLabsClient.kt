package com.riffado.meetings.ai

import com.riffado.meetings.data.ProviderConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/** ElevenLabs Scribe speech-to-text with speaker diarization. Returns `Speaker N: text` lines. */
object ElevenLabsClient {
    private val json = Json { ignoreUnknownKeys = true }

    fun transcribe(config: ProviderConfig, file: File, language: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model_id", config.model)
            .addFormDataPart("diarize", "true")
            .addFormDataPart("timestamps_granularity", "word")
            .addFormDataPart("tag_audio_events", "false")
            .apply { if (language.isNotBlank()) addFormDataPart("language_code", language) }
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/speech-to-text")
            .header("xi-api-key", config.apiKey)
            .post(body)
            .build()
        val text = OpenAiClient.http.newCall(request).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(res.code, elevenLabsError(res.code, raw))
            raw
        }
        val obj = json.parseToJsonElement(text).jsonObject
        val result = format(obj)
        if (result.isBlank()) throw IOException("ElevenLabs returned an empty transcript")
        return result
    }

    private fun format(obj: JsonObject): String {
        val words = obj["words"]?.jsonArray ?: return obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val numbers = LinkedHashMap<String, Int>()
        data class Line(val number: Int, val text: StringBuilder, val start: Long?, var end: Long?)
        val lines = mutableListOf<Line>()
        for (el in words) {
            val w = el.jsonObject
            if (w["type"]?.jsonPrimitive?.contentOrNull == "audio_event") continue
            val token = (w["text"]?.jsonPrimitive?.contentOrNull ?: "").replace(Regex("[\\r\\n]+"), " ")
            val speakerId = w["speaker_id"]?.jsonPrimitive?.contentOrNull
                ?: return obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val n = numbers.getOrPut(speakerId) { numbers.size + 1 }
            val last = lines.lastOrNull()
            val start = w["start"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.times(1000)?.toLong()
            val end = w["end"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.times(1000)?.toLong()
            if (last != null && last.number == n) {
                last.text.append(token)
                if (end != null) last.end = end
            } else lines += Line(n, StringBuilder(token), start, end)
        }
        return lines
            .filter { it.text.isNotBlank() }
            .joinToString("\n") {
                val timing = if (it.start != null && it.end != null) "[${it.start}-${it.end}ms] " else ""
                "Speaker ${it.number}: $timing${it.text.toString().trim()}"
            }
    }

    private fun elevenLabsError(code: Int, body: String): String {
        val detail = runCatching {
            val d = json.parseToJsonElement(body).jsonObject["detail"]
            when (d) {
                is JsonObject -> d["message"]?.jsonPrimitive?.contentOrNull
                null -> null
                else -> d.jsonPrimitive.contentOrNull
            }
        }.getOrNull()
        return if (detail.isNullOrBlank()) OpenAiClient.errorMessage(code, body) else "HTTP $code: $detail"
    }
}
