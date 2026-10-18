package com.riffado.meetings.ai

import com.riffado.meetings.data.ProviderConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Raised for non-2xx responses. [retryable] is true for rate limits and server errors. */
class ApiException(val code: Int, message: String) : IOException(message) {
    val retryable: Boolean get() = code == 429 || code >= 500
}

/** Minimal client for OpenAI-compatible `/audio/transcriptions` and `/chat/completions`. */
object OpenAiClient {
    internal val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.MINUTES)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(val model: String, val messages: List<ChatMessage>)

    fun transcribe(config: ProviderConfig, file: File, language: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", config.model)
            .addFormDataPart("response_format", "json")
            .apply { if (language.isNotBlank()) addFormDataPart("language", language) }
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val response = execute(config, "audio/transcriptions", body)
        val obj = json.parseToJsonElement(response).jsonObject
        return obj["text"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw IOException("Transcription response has no text field")
    }

    fun chat(config: ProviderConfig, system: String, user: String): String =
        chat(config, listOf("system" to system, "user" to user))

    /** Multi-turn chat; [messages] are (role, content) pairs with roles system/user/assistant. */
    fun chat(config: ProviderConfig, messages: List<Pair<String, String>>): String {
        val payload = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(model = config.model, messages = messages.map { (role, content) -> ChatMessage(role, content) }),
        )
        val response = execute(config, "chat/completions", payload.toRequestBody(JSON_TYPE))
        val obj = json.parseToJsonElement(response).jsonObject
        return obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw IOException("Chat response has no message content")
    }

    private fun execute(config: ProviderConfig, path: String, body: RequestBody): String {
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/$path")
            .apply { if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}") }
            .post(body)
            .build()
        http.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(res.code, errorMessage(res.code, text))
            return text
        }
    }

    internal fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            val obj = json.parseToJsonElement(body) as JsonObject
            val err = obj["error"]
            when {
                err is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
                err != null -> err.jsonPrimitive.contentOrNull
                else -> obj["message"]?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()
        return "HTTP $code${if (detail.isNullOrBlank()) "" else ": $detail"}"
    }

    private val JSON_TYPE = "application/json".toMediaType()
}
