package com.riffado.meetings.mcp

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpClientConfigTest {
    private val url = "http://192.168.1.42:8765/mcp"
    private val token = "rfd_abc"

    @Test fun mcpRemoteSnippetForClaudeDesktop() {
        val text = McpClientConfig.snippet(McpClientConfig.Style.MCP_REMOTE, url, token)
        val server = McpProtocol.parse(text)["mcpServers"]!!.jsonObject["riffado"]!!.jsonObject
        assertEquals("npx", server["command"]!!.jsonPrimitive.content)
        val args = server["args"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("-y", "mcp-remote", url, "--allow-http", "--transport", "http-only", "--header", "Authorization:\${RIFFADO_AUTH}"), args)
        assertTrue(args.none { it.contains(' ') }) // some clients don't escape spaces in args
        assertEquals("Bearer rfd_abc", server["env"]!!.jsonObject["RIFFADO_AUTH"]!!.jsonPrimitive.content)
        assertTrue(text.contains("\n  \"mcpServers\""))
    }

    @Test fun httpSnippetForCursorAndClaudeCode() {
        val server = McpProtocol.parse(McpClientConfig.snippet(McpClientConfig.Style.HTTP, url, token))["mcpServers"]!!.jsonObject["riffado"]!!.jsonObject
        assertEquals("http", server["type"]!!.jsonPrimitive.content)
        assertEquals(url, server["url"]!!.jsonPrimitive.content)
        assertEquals("Bearer rfd_abc", server["headers"]!!.jsonObject["Authorization"]!!.jsonPrimitive.content)
    }
}
