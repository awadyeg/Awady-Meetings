package com.riffado.meetings.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Tools exposed over MCP. [call] returns a CallToolResult object (without `resultType`). */
interface McpToolbox {
    fun list(): JsonArray
    fun call(name: String, arguments: JsonObject): JsonObject
}

/** Thrown by a toolbox for an unknown tool or malformed arguments → JSON-RPC -32602. */
class McpInvalidParams(message: String) : Exception(message)

/**
 * JSON-RPC 2.0 / MCP message handling, independent of the HTTP plumbing.
 *
 * Dual-era:
 *  - legacy clients (protocol 2024-11-05 … 2025-11-25) use the `initialize` handshake,
 *    `notifications/initialized`, `ping`, `tools/list`, `tools/call`;
 *  - modern clients (2026-07-28) send the version in `params._meta` on every request
 *    (mirrored in the `MCP-Protocol-Version` header) and may call `server/discover`.
 * The server is stateless (no sessions), which both eras allow.
 */
class McpProtocol(
    private val tools: McpToolbox,
    private val serverName: String = "riffado-meetings",
    private val serverVersion: String = "1",
    private val instructions: String = DEFAULT_INSTRUCTIONS,
) {
    /** What the HTTP layer should send back. A null [body] means "no body" (202 Accepted). */
    data class Reply(val status: Int, val body: JsonElement?)

    /**
     * Handles one POST body. [headers] keys must be lower-case.
     */
    fun handlePost(rawBody: String, headers: Map<String, String>): Reply {
        val parsed = try {
            json.parseToJsonElement(rawBody)
        } catch (_: Exception) {
            return Reply(400, error(JsonNull, PARSE_ERROR, "Parse error"))
        }
        return when (parsed) {
            is JsonObject -> handleMessage(parsed, headers)
            // Batches were only allowed in 2025-03-26; still answered for old clients.
            is JsonArray -> {
                if (parsed.isEmpty()) return Reply(400, error(JsonNull, INVALID_REQUEST, "Empty batch"))
                val replies = parsed.map { el ->
                    if (el is JsonObject) handleMessage(el, headers, batch = true).body else error(JsonNull, INVALID_REQUEST, "Invalid request")
                }.filterNotNull()
                if (replies.isEmpty()) Reply(202, null) else Reply(200, JsonArray(replies))
            }
            else -> Reply(400, error(JsonNull, INVALID_REQUEST, "Invalid request"))
        }
    }

    private fun handleMessage(msg: JsonObject, headers: Map<String, String>, batch: Boolean = false): Reply {
        val id = msg["id"]
        val method = (msg["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if ((msg["jsonrpc"] as? JsonPrimitive)?.contentOrNull != "2.0") {
            return Reply(400, error(id ?: JsonNull, INVALID_REQUEST, "jsonrpc must be \"2.0\""))
        }
        // A JSON-RPC response or a notification: accept, nothing to send back.
        if (method == null) {
            return if (msg.containsKey("result") || msg.containsKey("error")) Reply(202, null)
            else Reply(400, error(id ?: JsonNull, INVALID_REQUEST, "Missing method"))
        }
        if (id == null || id is JsonNull) {
            return Reply(202, null) // notifications/initialized, notifications/cancelled, …
        }
        if (id !is JsonPrimitive || (!id.isString && id.contentOrNull?.toLongOrNull() == null)) {
            return Reply(400, error(JsonNull, INVALID_REQUEST, "id must be a string or integer"))
        }
        val params = msg["params"] as? JsonObject ?: JsonObject(emptyMap())

        // ---- protocol version / era ------------------------------------------------------------
        val meta = params["_meta"] as? JsonObject
        val bodyVersion = (meta?.get(META_VERSION) as? JsonPrimitive)?.contentOrNull
        val headerVersion = headers["mcp-protocol-version"]?.trim()
        if (bodyVersion != null && headerVersion != null && bodyVersion != headerVersion) {
            return Reply(400, error(id, HEADER_MISMATCH, "Header mismatch: MCP-Protocol-Version '$headerVersion' does not match body '$bodyVersion'"))
        }
        val version = bodyVersion ?: headerVersion
        val modern = version != null && version in MODERN_VERSIONS
        if (method != "initialize" && version != null && version !in MODERN_VERSIONS && version !in LEGACY_VERSIONS) {
            return Reply(400, unsupportedVersion(id, version))
        }
        if (modern && !batch) {
            mismatch(method, params, headers)?.let { return Reply(400, error(id, HEADER_MISMATCH, it)) }
        }

        return try {
            val result: JsonObject = when (method) {
                "initialize" -> initialize(params)
                "server/discover" -> discover()
                "ping" -> JsonObject(emptyMap())
                "tools/list" -> buildJsonObject { put("tools", tools.list()) }
                "tools/call" -> {
                    val name = (params["name"] as? JsonPrimitive)?.contentOrNull
                        ?: throw McpInvalidParams("Missing tool name")
                    val args = params["arguments"]?.let { it as? JsonObject ?: throw McpInvalidParams("arguments must be an object") }
                        ?: JsonObject(emptyMap())
                    tools.call(name, args)
                }
                else -> return Reply(if (modern) 404 else 200, error(id, METHOD_NOT_FOUND, "Method not found: $method"))
            }
            Reply(200, buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("result", JsonObject(result + ("resultType" to JsonPrimitive("complete"))))
            })
        } catch (e: McpInvalidParams) {
            Reply(200, error(id, INVALID_PARAMS, e.message ?: "Invalid params"))
        } catch (e: Exception) {
            Reply(200, error(id, INTERNAL_ERROR, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun initialize(params: JsonObject): JsonObject {
        val requested = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
        // Legacy negotiation: echo a supported version, otherwise offer our latest legacy one.
        val negotiated = if (requested != null && requested in LEGACY_VERSIONS) requested else LEGACY_VERSIONS.first()
        return buildJsonObject {
            put("protocolVersion", negotiated)
            putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
            putJsonObject("serverInfo") {
                put("name", serverName)
                put("title", "Riffado Meetings")
                put("version", serverVersion)
            }
            put("instructions", instructions)
        }
    }

    private fun discover(): JsonObject = buildJsonObject {
        put("supportedVersions", buildJsonArray { (MODERN_VERSIONS + LEGACY_VERSIONS).forEach { add(JsonPrimitive(it)) } })
        putJsonObject("capabilities") { putJsonObject("tools") {} }
        putJsonObject("_meta") {
            putJsonObject(META_SERVER_INFO) {
                put("name", serverName)
                put("version", serverVersion)
            }
        }
        put("instructions", instructions)
    }

    /** Modern-era header ↔ body validation (Mcp-Method, Mcp-Name). Returns an error message or null. */
    private fun mismatch(method: String, params: JsonObject, headers: Map<String, String>): String? {
        val hMethod = headers["mcp-method"] ?: return "Missing required header Mcp-Method"
        if (hMethod != method) return "Header mismatch: Mcp-Method '$hMethod' does not match body '$method'"
        if (method == "tools/call" || method == "resources/read" || method == "prompts/get") {
            val bodyName = (params["name"] as? JsonPrimitive)?.contentOrNull ?: (params["uri"] as? JsonPrimitive)?.contentOrNull
            val raw = headers["mcp-name"] ?: return "Missing required header Mcp-Name"
            val hName = decodeHeader(raw) ?: return "Malformed Mcp-Name header"
            if (hName != bodyName) return "Header mismatch: Mcp-Name '$hName' does not match body '$bodyName'"
        }
        return null
    }

    private fun unsupportedVersion(id: JsonElement, requested: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", UNSUPPORTED_VERSION)
            put("message", "Unsupported protocol version")
            putJsonObject("data") {
                put("supported", buildJsonArray { (MODERN_VERSIONS + LEGACY_VERSIONS).forEach { add(JsonPrimitive(it)) } })
                put("requested", requested)
            }
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        val MODERN_VERSIONS = listOf("2026-07-28")
        /** Newest first; the first entry is offered when a legacy client asks for something unknown. */
        val LEGACY_VERSIONS = listOf("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")

        const val META_VERSION = "io.modelcontextprotocol/protocolVersion"
        const val META_SERVER_INFO = "io.modelcontextprotocol/serverInfo"

        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
        const val HEADER_MISMATCH = -32020
        const val UNSUPPORTED_VERSION = -32022

        const val DEFAULT_INSTRUCTIONS =
            "Read-only access to the user's Riffado meeting and call recordings: summaries, transcripts, " +
                "tasks, bookmarks, notes and people. Use list_recordings / search_recordings to find recordings, " +
                "get_recording for full details, list_tasks and get_person for follow-ups, and ask for a " +
                "synthesized answer with cited recordings. Transcripts are often Arabic or mixed Arabic/English."

        fun error(id: JsonElement, code: Int, message: String): JsonObject = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }

        /** Decodes the `=?base64?…?=` sentinel used for non-ASCII header values. */
        fun decodeHeader(value: String): String? {
            if (!(value.startsWith("=?base64?") && value.endsWith("?="))) return value
            return try {
                String(java.util.Base64.getDecoder().decode(value.removePrefix("=?base64?").removeSuffix("?=")), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        fun parse(text: String): JsonObject = json.parseToJsonElement(text).jsonObject
    }
}
