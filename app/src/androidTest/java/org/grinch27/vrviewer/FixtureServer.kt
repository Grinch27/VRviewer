// Verification: deterministic loopback-only HTTP media fixture, including byte ranges for seeking.
// Research/risks: test infrastructure only; never expose a listener or server feature in the application.
package org.grinch27.vrviewer

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

internal class FixtureServer(private val bytes: ByteArray) : AutoCloseable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    @Volatile private var running = true
    val url = "http://127.0.0.1:${server.localPort}/video.mp4"
    private val worker = thread(name = "synthetic-video-http", isDaemon = true) {
        while (running) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 3_000
                    val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val request = input.readLine() ?: return@use
                    val headers = ArrayList<String>()
                    while (headers.size < 40) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        headers.add(line)
                    }
                    val range = headers.firstNotNullOfOrNull { Regex("(?i)^Range:\\s*bytes=(\\d+)-(\\d*)").find(it) }
                    val start = range?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val end = (range?.groupValues?.get(2)?.toIntOrNull() ?: bytes.lastIndex).coerceAtMost(bytes.lastIndex)
                    val valid = start in bytes.indices && end >= start
                    val partial = range != null
                    val response = buildString {
                        append("HTTP/1.1 ")
                        append(if (!valid) "416 Range Not Satisfiable" else if (partial) "206 Partial Content" else "200 OK")
                        append("\r\nContent-Type: video/mp4\r\nAccept-Ranges: bytes\r\nConnection: close\r\n")
                        append("Content-Length: ${if (valid) end - start + 1 else 0}\r\n")
                        if (partial && valid) append("Content-Range: bytes $start-$end/${bytes.size}\r\n")
                        append("\r\n")
                    }
                    val output = socket.getOutputStream()
                    output.write(response.toByteArray(Charsets.US_ASCII))
                    if (valid && !request.startsWith("HEAD ")) output.write(bytes, start, end - start + 1)
                    output.flush()
                }
            } catch (_: IOException) {
                // Seeking can cancel a client connection; closing the fixture also unblocks accept().
            }
        }
    }
    override fun close() {
        running = false
        server.close()
        worker.join(1_000)
    }
}
