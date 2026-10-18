package com.riffado.meetings.mcp

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress

/**
 * Opt-in interop harness: when `/tmp/mcp-interop/token` exists, serves the fake data from [McpToolsTest]
 * on 127.0.0.1:18765 until `/tmp/mcp-interop/stop` appears (max 3 min), so external MCP clients
 * (official TypeScript SDK, mcp-remote) can be pointed at the real server. Skipped otherwise.
 */
class McpInteropTest {
    @Test fun serveForExternalClients() {
        val dir = File("/tmp/mcp-interop")
        val tokenFile = File(dir, "token")
        assumeTrue("interop harness not requested", tokenFile.exists())
        val token = tokenFile.readText().trim()
        val server = McpHttpServer(McpProtocol(McpToolsTest().tools, serverVersion = "interop"), { token }, 18765, InetAddress.getLoopbackAddress())
        server.start()
        File(dir, "ready").writeText("${server.boundPort}")
        val stop = File(dir, "stop")
        val deadline = System.currentTimeMillis() + 180_000
        try {
            while (!stop.exists() && System.currentTimeMillis() < deadline) Thread.sleep(200)
        } finally {
            server.stop()
            File(dir, "served").writeText("${server.requestCount}")
        }
    }
}
