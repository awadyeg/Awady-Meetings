package com.riffado.meetings.mcp

import kotlinx.serialization.json.JsonElement
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal HTTP/1.1 server for the MCP Streamable HTTP transport.
 *
 *  - `POST /mcp` carries one JSON-RPC message (or a legacy batch); the reply is JSON, or a single-event
 *    SSE stream when the client only accepts `text/event-stream`.
 *  - `GET` / `DELETE /mcp` → 405 (no standalone SSE stream, no sessions).
 *  - Every request needs `Authorization: Bearer <token>` (401 otherwise, checked before anything else).
 *  - Browser requests with a foreign `Origin` are refused (403) against DNS rebinding.
 *
 * Pure JVM (java.net) so it runs in plain unit tests.
 */
class McpHttpServer(
    private val protocol: McpProtocol,
    private val token: () -> String,
    private val port: Int,
    private val bindAddress: InetAddress? = null, // null = all interfaces
    private val maxBodyBytes: Int = 1 shl 20,
    private val onRequest: (method: String, status: Int) -> Unit = { _, _ -> },
) {
    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null
    private val requests = AtomicLong()

    /** Port actually bound (useful with port 0 in tests). */
    val boundPort: Int get() = server?.localPort ?: -1
    val requestCount: Long get() = requests.get()
    val isRunning: Boolean get() = server?.isClosed == false

    @Throws(IOException::class)
    fun start() {
        check(server == null) { "already started" }
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(bindAddress, port), 32)
        server = socket
        val workers = Executors.newFixedThreadPool(MAX_CONNECTIONS) { r -> Thread(r, "mcp-http").apply { isDaemon = true } }
        pool = workers
        Thread({ acceptLoop(socket, workers) }, "mcp-accept").apply { isDaemon = true }.start()
    }

    fun stop() {
        runCatching { server?.close() }
        pool?.shutdownNow()
        runCatching { pool?.awaitTermination(2, TimeUnit.SECONDS) }
        server = null
        pool = null
    }

    private fun acceptLoop(socket: ServerSocket, workers: ExecutorService) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                break
            } catch (_: IOException) {
                continue
            }
            try {
                workers.execute { serve(client) }
            } catch (_: Exception) {
                runCatching { client.close() }
            }
        }
    }

    private class Request(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

    private class HttpError(val status: Int, message: String) : Exception(message)

    private fun serve(client: Socket) {
        client.use { s ->
            s.soTimeout = IDLE_TIMEOUT_MS
            s.tcpNoDelay = true
            val input = BufferedInputStream(s.getInputStream())
            val output = s.getOutputStream()
            var served = 0
            while (!s.isClosed && served < MAX_REQUESTS_PER_CONNECTION) {
                val request = try {
                    readRequest(input) ?: return
                } catch (e: HttpError) {
                    respond(output, e.status, "text/plain", (e.message ?: "").toByteArray(), close = true)
                    return
                } catch (_: SocketTimeoutException) {
                    return
                } catch (_: IOException) {
                    return
                }
                served++
                requests.incrementAndGet()
                val keepAlive = !request.headers["connection"].orEmpty().equals("close", ignoreCase = true)
                val status = try {
                    handle(request, output, keepAlive)
                } catch (_: IOException) {
                    return
                }
                onRequest(request.method, status)
                if (!keepAlive) return
            }
        }
    }

    /** Returns the HTTP status sent. */
    private fun handle(req: Request, out: OutputStream, keepAlive: Boolean): Int {
        // 1. Authentication first, for every path and method.
        val auth = req.headers["authorization"].orEmpty()
        val presented = if (auth.startsWith("Bearer ", ignoreCase = true)) auth.substring(7).trim() else ""
        val expected = token()
        if (expected.isEmpty() || !constantTimeEquals(presented, expected)) {
            val body = """{"error":"unauthorized","message":"Missing or invalid bearer token"}""".toByteArray()
            respond(out, 401, JSON, body, keepAlive, extra = mapOf("WWW-Authenticate" to "Bearer realm=\"riffado-mcp\""))
            return 401
        }
        // 2. DNS-rebinding protection.
        req.headers["origin"]?.let { origin ->
            if (!originAllowed(origin, req.headers["host"])) {
                respond(out, 403, JSON, McpProtocol.error(kotlinx.serialization.json.JsonNull, -32600, "Forbidden origin").toString().toByteArray(), keepAlive)
                return 403
            }
        }
        // 3. Routing.
        val path = req.path.substringBefore('?').trimEnd('/').ifEmpty { "/" }
        if (path != ENDPOINT) {
            respond(out, 404, "text/plain", "Not found. The MCP endpoint is $ENDPOINT".toByteArray(), keepAlive)
            return 404
        }
        when (req.method) {
            "POST" -> Unit
            "GET", "DELETE" -> {
                respond(out, 405, "text/plain", "Method not allowed".toByteArray(), keepAlive, extra = mapOf("Allow" to "POST"))
                return 405
            }
            else -> {
                respond(out, 405, "text/plain", "Method not allowed".toByteArray(), keepAlive, extra = mapOf("Allow" to "POST"))
                return 405
            }
        }
        val reply = protocol.handlePost(String(req.body, Charsets.UTF_8), req.headers)
        val body = reply.body
        if (body == null) {
            respond(out, reply.status, null, ByteArray(0), keepAlive)
            return reply.status
        }
        val accept = req.headers["accept"].orEmpty()
        if (accept.contains("text/event-stream") && !accept.contains("application/json") && !accept.contains("*/*")) {
            val sse = "event: message\ndata: ${body}\n\n".toByteArray()
            respond(out, reply.status, "text/event-stream", sse, keepAlive, extra = mapOf("Cache-Control" to "no-cache", "X-Accel-Buffering" to "no"))
        } else {
            respond(out, reply.status, JSON, serialize(body), keepAlive)
        }
        return reply.status
    }

    private fun serialize(body: JsonElement): ByteArray = body.toString().toByteArray(Charsets.UTF_8)

    // ---- HTTP parsing ---------------------------------------------------------------------------

    private fun readRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        if (requestLine.isEmpty()) return readRequest(input) // tolerate stray CRLF between requests
        val parts = requestLine.split(' ')
        if (parts.size < 3 || !parts[2].startsWith("HTTP/1.")) throw HttpError(400, "Bad request line")
        val headers = LinkedHashMap<String, String>()
        var headerBytes = 0
        while (true) {
            val line = readLine(input) ?: throw HttpError(400, "Unexpected end of headers")
            if (line.isEmpty()) break
            headerBytes += line.length
            if (headerBytes > MAX_HEADER_BYTES) throw HttpError(431, "Headers too large")
            val colon = line.indexOf(':')
            if (colon <= 0) throw HttpError(400, "Bad header")
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            headers[name] = headers[name]?.let { "$it, $value" } ?: value
        }
        val body = when {
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true -> readChunked(input)
            headers["content-length"] != null -> {
                val len = headers["content-length"]!!.toLongOrNull() ?: throw HttpError(400, "Bad Content-Length")
                if (len < 0) throw HttpError(400, "Bad Content-Length")
                if (len > maxBodyBytes) throw HttpError(413, "Body too large")
                readExactly(input, len.toInt())
            }
            else -> ByteArray(0)
        }
        return Request(parts[0].uppercase(), parts[1], headers, body)
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input) ?: throw HttpError(400, "Bad chunk")
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: throw HttpError(400, "Bad chunk size")
            if (size == 0) {
                while (readLine(input)?.isNotEmpty() == true) Unit // trailers
                break
            }
            if (out.size() + size > maxBodyBytes) throw HttpError(413, "Body too large")
            out.write(readExactly(input, size))
            readLine(input)
        }
        return out.toByteArray()
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) throw IOException("Unexpected end of body")
            off += r
        }
        return buf
    }

    /** Reads a CRLF (or LF) terminated ISO-8859-1 line; null on clean EOF before any byte. */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().removeSuffix("\r")
            sb.append(b.toChar())
            if (sb.length > MAX_LINE) throw HttpError(431, "Line too long")
        }
    }

    private fun respond(
        out: OutputStream,
        status: Int,
        contentType: String?,
        body: ByteArray,
        keepAlive: Boolean = true,
        close: Boolean = false,
        extra: Map<String, String> = emptyMap(),
    ) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
        if (contentType != null) sb.append("Content-Type: ").append(contentType).append(if (contentType.startsWith("text/") || contentType == JSON) "; charset=utf-8" else "").append("\r\n")
        sb.append("Content-Length: ").append(body.size).append("\r\n")
        sb.append("Connection: ").append(if (keepAlive && !close) "keep-alive" else "close").append("\r\n")
        sb.append("Server: riffado-mcp\r\n")
        extra.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    companion object {
        const val ENDPOINT = "/mcp"
        const val DEFAULT_PORT = 8765
        private const val JSON = "application/json"
        private const val MAX_CONNECTIONS = 8
        private const val MAX_REQUESTS_PER_CONNECTION = 1000
        private const val IDLE_TIMEOUT_MS = 60_000
        private const val MAX_HEADER_BYTES = 32 * 1024
        private const val MAX_LINE = 16 * 1024

        fun constantTimeEquals(a: String, b: String): Boolean =
            MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

        /**
         * Allows browser pages served from localhost or from the very IP address the client dialled;
         * refuses any origin with a DNS name (DNS rebinding makes a hostile domain resolve to the phone).
         */
        fun originAllowed(origin: String, host: String?): Boolean {
            if (origin == "null") return false
            val originHost = runCatching { URI(origin).host }.getOrNull()?.lowercase()?.trim('[', ']') ?: return false
            if (originHost == "localhost" || originHost == "127.0.0.1" || originHost == "::1") return true
            val requestHost = host?.let { h -> if (h.startsWith("[")) h.substringBefore(']').trimStart('[') else h.substringBeforeLast(':') }?.lowercase()
            return requestHost != null && requestHost == originHost && isIpLiteral(originHost)
        }

        private fun isIpLiteral(host: String): Boolean =
            host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) || (host.contains(':') && host.all { it.isLetterOrDigit() || it == ':' || it == '.' })

        private fun reason(status: Int) = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            413 -> "Payload Too Large"
            431 -> "Request Header Fields Too Large"
            else -> "Status"
        }
    }
}
