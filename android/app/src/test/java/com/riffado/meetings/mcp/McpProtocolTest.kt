package com.riffado.meetings.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JSON-RPC dispatch for both MCP eras, against a fake toolbox. */
class McpProtocolTest {
    private val echo = object : McpToolbox {
        override fun list() = buildJsonArray {
            add(buildJsonObject { put("name", "echo"); putJsonObject("inputSchema") { put("type", "object") } })
        }
        override fun call(name: String, arguments: JsonObject): JsonObject {
            if (name != "echo") throw McpInvalidParams("Unknown tool: $name")
            return buildJsonObject {
                put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", arguments["text"]?.jsonPrimitive?.content ?: "") }) })
                put("isError", false)
            }
        }
    }
    private val protocol = McpProtocol(echo, serverVersion = "test")

    private fun post(body: String, headers: Map<String, String> = emptyMap()) = protocol.handlePost(body, headers)
    private fun obj(r: McpProtocol.Reply) = r.body!!.jsonObject

    @Test fun initializeNegotiatesLegacyVersion() {
        val r = post("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}""")
        assertEquals(200, r.status)
        val result = obj(r)["result"]!!.jsonObject
        assertEquals("2025-06-18", result["protocolVersion"]!!.jsonPrimitive.content)
        assertTrue(result["capabilities"]!!.jsonObject.containsKey("tools"))
        assertEquals("riffado-meetings", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("test", result["serverInfo"]!!.jsonObject["version"]!!.jsonPrimitive.content)
        assertEquals(1, obj(r)["id"]!!.jsonPrimitive.int)
    }

    @Test fun initializeWithUnknownVersionOffersLatestLegacy() {
        val r = post("""{"jsonrpc":"2.0","id":"a","method":"initialize","params":{"protocolVersion":"2099-01-01"}}""")
        assertEquals(McpProtocol.LEGACY_VERSIONS.first(), obj(r)["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
        assertEquals("a", obj(r)["id"]!!.jsonPrimitive.content)
    }

    @Test fun notificationsGet202WithoutBody() {
        val r = post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        assertEquals(202, r.status)
        assertNull(r.body)
        assertEquals(202, post("""{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":3}}""").status)
    }

    @Test fun pingToolsListAndCall() {
        assertEquals(JsonObject(mapOf("resultType" to JsonPrimitive("complete"))), obj(post("""{"jsonrpc":"2.0","id":2,"method":"ping"}"""))["result"])
        val tools = obj(post("""{"jsonrpc":"2.0","id":3,"method":"tools/list"}"""))["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals("echo", tools[0].jsonObject["name"]!!.jsonPrimitive.content)
        val call = obj(post("""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"echo","arguments":{"text":"مرحبا"}}}"""))["result"]!!.jsonObject
        assertEquals("مرحبا", call["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("complete", call["resultType"]!!.jsonPrimitive.content)
    }

    @Test fun errors() {
        fun code(r: McpProtocol.Reply) = obj(r)["error"]!!.jsonObject["code"]!!.jsonPrimitive.int
        assertEquals(McpProtocol.PARSE_ERROR, code(post("{nope")))
        assertEquals(400, post("{nope").status)
        assertEquals(McpProtocol.INVALID_REQUEST, code(post("""{"id":1,"method":"ping"}""")))
        assertEquals(McpProtocol.INVALID_REQUEST, code(post("""[]""")))
        val unknown = post("""{"jsonrpc":"2.0","id":5,"method":"resources/list"}""")
        assertEquals(200, unknown.status) // legacy clients get the error in a 200
        assertEquals(McpProtocol.METHOD_NOT_FOUND, code(unknown))
        assertEquals(McpProtocol.INVALID_PARAMS, code(post("""{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"nope"}}""")))
        assertEquals(McpProtocol.INVALID_PARAMS, code(post("""{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{}}""")))
        assertEquals(McpProtocol.INVALID_PARAMS, code(post("""{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"echo","arguments":[1]}}""")))
    }

    @Test fun legacyBatch() {
        val r = post("""[{"jsonrpc":"2.0","id":1,"method":"ping"},{"jsonrpc":"2.0","method":"notifications/initialized"},{"jsonrpc":"2.0","id":2,"method":"tools/list"}]""")
        assertEquals(200, r.status)
        assertEquals(2, (r.body as JsonArray).size)
    }

    // ---- modern era (2026-07-28) --------------------------------------------------------------

    private val meta = """"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientInfo":{"name":"t","version":"1"},"io.modelcontextprotocol/clientCapabilities":{}}"""
    private fun modernHeaders(method: String, name: String? = null) = buildMap {
        put("mcp-protocol-version", "2026-07-28"); put("mcp-method", method); name?.let { put("mcp-name", it) }
    }

    @Test fun modernDiscoverAndCallWithoutInitialize() {
        val d = post("""{"jsonrpc":"2.0","id":"d","method":"server/discover","params":{$meta}}""", modernHeaders("server/discover"))
        val result = obj(d)["result"]!!.jsonObject
        assertTrue(result["supportedVersions"]!!.jsonArray.map { it.jsonPrimitive.content }.containsAll(listOf("2026-07-28", "2025-11-25")))
        assertEquals("complete", result["resultType"]!!.jsonPrimitive.content)
        val c = post("""{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"echo","arguments":{"text":"hi"},$meta}}""", modernHeaders("tools/call", "echo"))
        assertEquals(200, c.status)
        assertFalse(obj(c)["result"]!!.jsonObject["isError"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun modernHeaderValidation() {
        fun code(r: McpProtocol.Reply) = obj(r)["error"]!!.jsonObject["code"]!!.jsonPrimitive.int
        val body = """{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"echo","arguments":{},$meta}}"""
        val missing = post(body, mapOf("mcp-protocol-version" to "2026-07-28", "mcp-method" to "tools/call"))
        assertEquals(400, missing.status); assertEquals(McpProtocol.HEADER_MISMATCH, code(missing))
        val wrongName = post(body, modernHeaders("tools/call", "other"))
        assertEquals(McpProtocol.HEADER_MISMATCH, code(wrongName))
        val b64 = post(body, modernHeaders("tools/call", "=?base64?" + java.util.Base64.getEncoder().encodeToString("echo".toByteArray()) + "?="))
        assertEquals(200, b64.status)
        val versionMismatch = post(body, modernHeaders("tools/call", "echo") + ("mcp-protocol-version" to "2025-11-25"))
        assertEquals(400, versionMismatch.status); assertEquals(McpProtocol.HEADER_MISMATCH, code(versionMismatch))
    }

    @Test fun modernUnknownMethodIs404AndUnknownVersionIs400() {
        val r = post("""{"jsonrpc":"2.0","id":1,"method":"prompts/list","params":{$meta}}""", modernHeaders("prompts/list"))
        assertEquals(404, r.status)
        val v = post("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""", mapOf("mcp-protocol-version" to "1900-01-01"))
        assertEquals(400, v.status)
        val err = obj(v)["error"]!!.jsonObject
        assertEquals(McpProtocol.UNSUPPORTED_VERSION, err["code"]!!.jsonPrimitive.int)
        assertEquals("1900-01-01", err["data"]!!.jsonObject["requested"]!!.jsonPrimitive.content)
    }
}
