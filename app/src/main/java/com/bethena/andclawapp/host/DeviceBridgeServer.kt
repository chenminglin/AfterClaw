package com.bethena.andclawapp.host

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * 设备桥接服务器，用于在本地建立 HTTP 服务，以响应宿主或客户端的数据请求。
 * 运行在本地回环地址上。
 */
internal class DeviceBridgeServer(
    private val port: Int,
    private val responseProvider: (String) -> Response,
    private val onLog: (String) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (serverSocket != null) {
            return
        }
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) {
            onLog("Local device bridge is listening on 127.0.0.1:$port.")
            while (isActive) {
                try {
                    val client = socket.accept()
                    launch {
                        handleClient(client)
                    }
                } catch (err: IOException) {
                    if (isActive) {
                        onLog("Device bridge stopped accepting connections: ${err.message}")
                    }
                    break
                }
            }
        }
    }

    suspend fun stop() {
        acceptJob?.cancel()
        serverSocket?.close()
        serverSocket = null
        acceptJob = null
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            while (reader.readLine()?.isNotEmpty() == true) {
                // Ignore headers for now.
            }
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            val response = responseProvider(path)
            val bodyBytes = response.body.toByteArray(StandardCharsets.UTF_8)
            val writer = socket.getOutputStream()
            val statusText = if (response.statusCode == 200) "OK" else "Not Found"
            writer.write("HTTP/1.1 ${response.statusCode} $statusText\r\n".toByteArray(StandardCharsets.UTF_8))
            writer.write("Content-Type: ${response.contentType}\r\n".toByteArray(StandardCharsets.UTF_8))
            writer.write("Content-Length: ${bodyBytes.size}\r\n".toByteArray(StandardCharsets.UTF_8))
            writer.write("Connection: close\r\n".toByteArray(StandardCharsets.UTF_8))
            writer.write("\r\n".toByteArray(StandardCharsets.UTF_8))
            writer.write(bodyBytes)
            writer.flush()
        }
    }

    data class Response(
        val statusCode: Int,
        val contentType: String = "application/json; charset=utf-8",
        val body: String,
    )
}

