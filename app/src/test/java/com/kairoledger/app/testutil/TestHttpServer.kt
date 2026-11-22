package com.kairoledger.app.testutil

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * 轻量级纯 ServerSocket 本地 HTTP/WebDAV 测试服务器。
 * 避免 Android UnitTest 编译类路径缺少 jdk.httpserver 模块的问题，同时完整支持 GET/POST/PUT/PROPFIND/MKCOL。
 */
class TestHttpHeaders {
    private val map = linkedMapOf<String, MutableList<String>>()

    fun add(name: String, value: String) {
        val key = map.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
        map.getOrPut(key) { mutableListOf() }.add(value)
    }

    fun getFirst(name: String): String? {
        val key = map.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: return null
        return map[key]?.firstOrNull()
    }

    fun entries(): Set<Map.Entry<String, List<String>>> = map.entries
}

class TestHttpExchange(
    val requestMethod: String,
    val requestURI: URI,
    val requestHeaders: TestHttpHeaders,
    requestBytes: ByteArray,
    private val rawOut: OutputStream,
    private val socket: Socket
) {
    val requestBody: InputStream = ByteArrayInputStream(requestBytes)
    val responseHeaders: TestHttpHeaders = TestHttpHeaders()

    private val bodyBuffer = ByteArrayOutputStream()
    private var statusCode: Int = 200
    private var declaredLength: Long = -1L
    private var headersSent: Boolean = false
    private var closed: Boolean = false

    val responseBody: OutputStream = object : OutputStream() {
        override fun write(b: Int) {
            bodyBuffer.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            bodyBuffer.write(b, off, len)
        }

        override fun close() {
            flushResponse()
        }
    }

    fun sendResponseHeaders(code: Int, responseLength: Long) {
        statusCode = code
        declaredLength = responseLength
        if (responseLength <= 0L) {
            flushResponse()
        }
    }

    private fun flushResponse() {
        if (headersSent) return
        headersSent = true
        val payload = bodyBuffer.toByteArray()
        val effectiveLength = if (declaredLength >= 0L) payload.size else 0
        val headerBuilder = StringBuilder()
        headerBuilder.append("HTTP/1.1 ").append(statusCode).append(' ').append(reasonPhrase(statusCode)).append("\r\n")
        headerBuilder.append("Connection: close\r\n")
        headerBuilder.append("Content-Length: ").append(effectiveLength).append("\r\n")
        for ((k, values) in responseHeaders.entries()) {
            if (k.equals("Connection", true) || k.equals("Content-Length", true)) continue
            for (v in values) {
                headerBuilder.append(k).append(": ").append(v).append("\r\n")
            }
        }
        headerBuilder.append("\r\n")
        rawOut.write(headerBuilder.toString().toByteArray(StandardCharsets.ISO_8859_1))
        if (effectiveLength > 0) {
            rawOut.write(payload)
        }
        rawOut.flush()
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { flushResponse() }
        runCatching { socket.close() }
    }

    private fun reasonPhrase(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        207 -> "Multi-Status"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        500 -> "Internal Server Error"
        else -> "Status"
    }
}

class TestHttpServer private constructor(
    private val serverSocket: ServerSocket
) {
    private data class ContextEntry(
        val prefix: String,
        val handler: (TestHttpExchange) -> Unit
    )

    private val contexts = CopyOnWriteArrayList<ContextEntry>()
    private val pool = Executors.newCachedThreadPool()

    @Volatile
    private var running = false

    @Suppress("unused")
    var executor: Executor? = null

    val address: InetSocketAddress
        get() = InetSocketAddress(serverSocket.inetAddress, serverSocket.localPort)

    fun createContext(prefix: String, handler: (TestHttpExchange) -> Unit) {
        contexts.add(ContextEntry(prefix, handler))
    }

    fun start() {
        running = true
        val acceptThread = Thread {
            while (running && !serverSocket.isClosed) {
                val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
                pool.execute { handleSocket(socket) }
            }
        }
        acceptThread.isDaemon = true
        acceptThread.start()
    }

    fun stop(@Suppress("UNUSED_PARAMETER") delaySeconds: Int) {
        running = false
        runCatching { serverSocket.close() }
        runCatching { pool.shutdownNow() }
    }

    private fun handleSocket(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val requestLine = readLineCrlf(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawTarget = parts[1]
            val uri = URI.create(if (rawTarget.startsWith("/")) rawTarget else "/$rawTarget")

            val headers = TestHttpHeaders()
            while (true) {
                val line = readLineCrlf(input) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    headers.add(
                        line.substring(0, colonIdx).trim(),
                        line.substring(colonIdx + 1).trim()
                    )
                }
            }

            val contentLength = headers.getFirst("Content-Length")?.toIntOrNull() ?: 0
            val bodyBytes = if (contentLength > 0) {
                readExactBytes(input, contentLength)
            } else {
                ByteArray(0)
            }

            val exchange = TestHttpExchange(
                requestMethod = method,
                requestURI = uri,
                requestHeaders = headers,
                requestBytes = bodyBytes,
                rawOut = socket.getOutputStream(),
                socket = socket
            )

            val path = uri.path ?: "/"
            val matched = contexts
                .filter { path == it.prefix || path.startsWith(it.prefix) || it.prefix == "/" }
                .maxByOrNull { it.prefix.length }

            if (matched != null) {
                matched.handler(exchange)
                exchange.close()
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        } catch (_: Exception) {
            runCatching { socket.close() }
        }
    }

    private fun readLineCrlf(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (baos.size() == 0) null else baos.toString(StandardCharsets.ISO_8859_1.name())
            }
            if (b == '\n'.code) {
                val bytes = baos.toByteArray()
                val end = if (prev == '\r'.code && bytes.isNotEmpty()) bytes.size - 1 else bytes.size
                return String(bytes, 0, end, StandardCharsets.ISO_8859_1)
            }
            baos.write(b)
            prev = b
        }
    }

    private fun readExactBytes(input: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buf, offset, count - offset)
            if (read == -1) break
            offset += read
        }
        return if (offset == count) buf else buf.copyOf(offset)
    }

    companion object {
        fun create(addr: InetSocketAddress, backlog: Int): TestHttpServer {
            val ss = ServerSocket(addr.port, backlog.coerceAtLeast(1), addr.address ?: InetAddress.getLoopbackAddress())
            return TestHttpServer(ss)
        }
    }
}
