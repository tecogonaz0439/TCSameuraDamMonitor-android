// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.testutil

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * androidTest 用の軽量ローカル HTTP サーバー。
 *
 * 端末上テストで localhost cleartext 経路を検証するために使う。依存追加を避けるため、
 * 必要最小限の HTTP/1.1 レスポンスだけを返す。
 */
class LocalHttpServer(
    private val threadName: String = "android-test-local-http-server"
) : Closeable {
    private val responses = LinkedBlockingQueue<Response>()
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    @Volatile
    private var closed = false
    private var serverThread: Thread? = null

    fun start() {
        serverThread = thread(name = threadName) {
            while (!closed) {
                runCatching { serverSocket.accept() }
                    .onSuccess(::handle)
                    .onFailure { if (!closed) throw it }
            }
        }
    }

    fun url(path: String): String =
        "http://127.0.0.1:${serverSocket.localPort}$path"

    fun enqueue(code: Int, body: ByteArray, headers: Map<String, String> = emptyMap()) {
        responses.add(Response(code, body, headers))
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            val reader = client.getInputStream().bufferedReader(Charsets.US_ASCII)
            reader.readLine() ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val response = responses.poll(5, TimeUnit.SECONDS)
                ?: Response(500, "No queued test response.".toByteArray())
            val statusText = if (response.code == 200) "OK" else "Error"
            val header = buildString {
                append("HTTP/1.1 ${response.code} $statusText\r\n")
                response.headers.forEach { (name, value) ->
                    append("$name: $value\r\n")
                }
                append("Content-Length: ${response.body.size}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(Charsets.US_ASCII)
            client.getOutputStream().use { output ->
                output.write(header)
                output.write(response.body)
                output.flush()
            }
        }
    }

    override fun close() {
        closed = true
        serverSocket.close()
        serverThread?.join(1_000L)
    }

    private data class Response(
        val code: Int,
        val body: ByteArray,
        val headers: Map<String, String> = emptyMap()
    )
}
