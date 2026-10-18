package com.riffado.meetings.export

import com.riffado.meetings.ai.OpenAiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** [reason] comes from [NotionExport.errorMessage]; [pageUrl] is set when the page was created but appending failed. */
class NotionException(val reason: String, val pageUrl: String? = null) : IOException(reason)

/** Creates a Notion page for a recording (blocking; call off the main thread). */
object NotionClient {
    private const val BASE = "https://api.notion.com/v1"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    /** @return the URL of the created page. */
    fun createPage(token: String, parentId: String, title: String, blocks: List<JsonObject>): String {
        val created = call(token, "POST", "$BASE/pages", NotionExport.createPageBody(parentId, title, blocks))
        val obj = json.parseToJsonElement(created).jsonObject
        val pageId = obj["id"]?.jsonPrimitive?.content ?: throw NotionException("No page id in response")
        val pageUrl = obj["url"]?.jsonPrimitive?.content ?: "https://www.notion.so/${pageId.replace("-", "")}"
        try {
            NotionExport.appendBatches(blocks).forEach { batch -> call(token, "PATCH", "$BASE/blocks/$pageId/children", batch) }
        } catch (e: NotionException) {
            throw NotionException(e.reason, pageUrl) // page exists but is missing part of the transcript
        }
        return pageUrl
    }

    private fun call(token: String, method: String, url: String, body: JsonObject, attempt: Int = 0): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Notion-Version", NotionExport.API_VERSION)
            .method(method, body.toString().toRequestBody(JSON))
            .build()
        OpenAiClient.http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.isSuccessful) return text
            if (response.code == 429 && attempt < 2) {
                val wait = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 10) ?: 2
                Thread.sleep(wait * 1000)
                return call(token, method, url, body, attempt + 1)
            }
            throw NotionException(NotionExport.errorMessage(response.code, text))
        }
    }
}
