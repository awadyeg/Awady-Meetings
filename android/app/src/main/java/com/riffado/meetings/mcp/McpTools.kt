package com.riffado.meetings.mcp

import com.riffado.meetings.MeetingsApp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Read-only riffado tools (filled in by Phase 4 item 3). */
class McpTools : McpToolbox {
    override fun list(): JsonArray = JsonArray(emptyList())
    override fun call(name: String, arguments: JsonObject): JsonObject = throw McpInvalidParams("Unknown tool: $name")

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun forApp(app: MeetingsApp): McpTools = McpTools()
    }
}
