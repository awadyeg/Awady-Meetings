package com.riffado.meetings.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Ready-to-paste client configuration snippets for the Settings "Copy config" button. */
object McpClientConfig {
    enum class Style { MCP_REMOTE, HTTP }

    const val SERVER_KEY = "riffado"
    const val AUTH_ENV = "RIFFADO_AUTH"

    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun snippet(style: Style, url: String, token: String): String = pretty.encodeToString(JsonObject.serializer(), json(style, url, token))

    fun json(style: Style, url: String, token: String): JsonObject = buildJsonObject {
        putJsonObject("mcpServers") {
            putJsonObject(SERVER_KEY) {
                when (style) {
                    // Claude Desktop and other stdio-only clients: bridge through mcp-remote.
                    // The header goes through an env var because some clients don't escape spaces in args.
                    Style.MCP_REMOTE -> {
                        put("command", "npx")
                        putJsonArray("args") {
                            listOf("-y", "mcp-remote", url, "--allow-http", "--transport", "http-only", "--header", "Authorization:\${$AUTH_ENV}")
                                .forEach { add(JsonPrimitive(it)) }
                        }
                        putJsonObject("env") { put(AUTH_ENV, "Bearer $token") }
                    }
                    // Cursor, Claude Code (.mcp.json), Windsurf, …: native Streamable HTTP with headers.
                    Style.HTTP -> {
                        put("type", "http")
                        put("url", url)
                        putJsonObject("headers") { put("Authorization", "Bearer $token") }
                    }
                }
            }
        }
    }
}
