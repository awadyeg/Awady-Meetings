package com.riffado.meetings.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/** Starts the real server on 127.0.0.1 (ephemeral port) and talks HTTP to it. Plain JVM, no Android. */
class McpHttpServerTest {
    private val token = McpToken.generate()
    private val tools = object : McpToolbox {
        override fun list() = buildJsonArray { add(buildJsonObject { put("name", "echo") }) }
        override fun call(name: String, arguments: JsonObject) = buildJsonObject {
            put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", "ok:$name") }) })
            put("isError", false)
        }
    }
    private lateinit var server: McpHttpServer
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()

    @Before fun start() {
        server = McpHttpServer(McpProtocol(tools), { token }, port = 0, bindAddress = InetAddress.getLoopbackAddress())
        server.start()
    }

    @After fun stop() = server.stop()

    private val url get() = "http://127.0.0.1:${server.boundPort}/mcp"

    private fun post(body: String, auth: String? = "Bearer $token", accept: String = "application/json, text/event-stream", path: String = "/mcp", extra: Map<String, String> = emptyMap()) =
        Request.Builder().url("http://127.0.0.1:${server.boundPort}$path").post(body.toRequestBody(jsonType))
            .header("Accept", accept)
            .apply { auth?.let { header("Authorization", it) }; extra.forEach { (k, v) -> header(k, v) } }
            .build().let { client.newCall(it).execute() }

    @Test fun rejectsMissingOrWrongToken() {
        post("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", auth = null).use {
            assertEquals(401, it.code)
            assertTrue(it.header("WWW-Authenticate")!!.startsWith("Bearer"))
        }
        post("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", auth = "Bearer rfd_wrong").use { assertEquals(401, it.code) }
        post("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", auth = token).use { assertEquals(401, it.code) } // no "Bearer "
        // Auth is checked before routing, so unknown paths don't leak either.
        post("{}", auth = null, path = "/other").use { assertEquals(401, it.code) }
        client.newCall(Request.Builder().url(url).get().build()).execute().use { assertEquals(401, it.code) }
    }

    @Test fun fullLegacyHandshake() {
        post("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"junit","version":"1"}}}""").use {
            assertEquals(200, it.code)
            assertTrue(it.header("Content-Type")!!.startsWith("application/json"))
            val result = McpProtocol.parse(it.body!!.string())["result"]!!.jsonObject
            assertEquals("2025-11-25", result["protocolVersion"]!!.jsonPrimitive.content)
        }
        post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").use {
            assertEquals(202, it.code)
            assertEquals(0L, it.body!!.contentLength())
        }
        post("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").use {
            val tools = McpProtocol.parse(it.body!!.string())["result"]!!.jsonObject["tools"]!!.jsonArray
            assertEquals("echo", tools[0].jsonObject["name"]!!.jsonPrimitive.content)
        }
        post("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{}}}""").use {
            val result = McpProtocol.parse(it.body!!.string())["result"]!!.jsonObject
            assertEquals("ok:echo", result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        }
        assertTrue(server.requestCount >= 4)
    }

    @Test fun getAndDeleteAre405_unknownPath404() {
        client.newCall(Request.Builder().url(url).get().header("Authorization", "Bearer $token").build()).execute().use {
            assertEquals(405, it.code); assertEquals("POST", it.header("Allow"))
        }
        client.newCall(Request.Builder().url(url).delete().header("Authorization", "Bearer $token").build()).execute().use { assertEquals(405, it.code) }
        post("{}", path = "/nope").use { assertEquals(404, it.code) }
    }

    @Test fun sseWhenClientOnlyAcceptsEventStream() {
        post("""{"jsonrpc":"2.0","id":7,"method":"ping"}""", accept = "text/event-stream").use {
            assertTrue(it.header("Content-Type")!!.startsWith("text/event-stream"))
            val text = it.body!!.string()
            assertTrue(text.startsWith("event: message\ndata: {"))
            assertTrue(text.contains("\"id\":7"))
        }
    }

    @Test fun originChecks() {
        post("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", extra = mapOf("Origin" to "https://evil.example")).use { assertEquals(403, it.code) }
        post("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", extra = mapOf("Origin" to "http://localhost:6274")).use { assertEquals(200, it.code) }
        assertTrue(McpHttpServer.originAllowed("http://192.168.1.5:8765", "192.168.1.5:8765"))
        assertFalse(McpHttpServer.originAllowed("http://rebind.evil:8765", "rebind.evil:8765"))
        assertFalse(McpHttpServer.originAllowed("null", "localhost"))
    }

    @Test fun keepAliveServesSeveralRequestsOnOneSocketAndChunkedBodies() {
        Socket("127.0.0.1", server.boundPort).use { s ->
            s.soTimeout = 5000
            val out = s.getOutputStream()
            val body = """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
            val chunked = "${Integer.toHexString(body.length)}\r\n$body\r\n0\r\n\r\n"
            val req1 = "POST /mcp HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer $token\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body"
            val req2 = "POST /mcp HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer $token\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n$chunked"
            out.write((req1 + req2).toByteArray()); out.flush()
            val text = s.getInputStream().readBytes().toString(Charsets.UTF_8)
            assertEquals(2, Regex("HTTP/1.1 200 OK").findAll(text).count())
        }
    }

    @Test fun oversizedBodyIs413() {
        Socket("127.0.0.1", server.boundPort).use { s ->
            s.soTimeout = 5000
            s.getOutputStream().write("POST /mcp HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer $token\r\nContent-Length: ${(1 shl 20) + 10}\r\n\r\n".toByteArray())
            val text = s.getInputStream().readBytes().toString(Charsets.UTF_8)
            assertTrue(text, text.startsWith("HTTP/1.1 413"))
        }
    }
}
