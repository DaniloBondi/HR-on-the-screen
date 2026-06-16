package com.example.network

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class LiveVitalsHttpServer(
    private val vitalsProvider: () -> String,
    private val tokenValidator: (String) -> Boolean,
    val port: Int = 8080
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        isRunning = true
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d("VitalsHttpServer", "Server started on port $port")
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e("VitalsHttpServer", "Server exception loop: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("VitalsHttpServer", "Error closing server socket", e)
        }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = PrintWriter(socket.getOutputStream())

            val requestLine = reader.readLine() ?: return
            Log.d("VitalsHttpServer", "Request: $requestLine")

            // Read all headers to clear buffer
            var headerLine: String?
            while (reader.readLine().also { headerLine = it } != null && headerLine!!.isNotEmpty()) {
                // Read next header...
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendError(output, 400, "Bad Request")
                return
            }

            val method = parts[0]
            val pathWithParams = parts[1]

            if (method == "OPTIONS") {
                sendOptionsResponse(output)
                return
            }

            if (method != "GET") {
                sendError(output, 405, "Method Not Allowed")
                return
            }

            if (pathWithParams.startsWith("/vitals")) {
                val token = extractQueryParam(pathWithParams, "token")
                if (token == null) {
                    sendError(output, 401, "Unauthorized - Missing token parameter")
                    return
                }

                if (!tokenValidator(token)) {
                    sendError(output, 403, "Forbidden - Invalid token")
                    return
                }

                val jsonResponse = vitalsProvider()
                sendStringResponse(output, "200 OK", "application/json", jsonResponse)
            } else {
                val welcomeHtml = """
                    {"status": "active", "service": "Vitals Dashboard Realtime BLE Streaming API", "endpoints": {"vitals": "/vitals?token=<your_token>"}}
                """.trimIndent()
                sendStringResponse(output, "200 OK", "application/json", welcomeHtml)
            }
        } catch (e: Exception) {
            Log.e("VitalsHttpServer", "Error handling client", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                Log.e("VitalsHttpServer", "Error closing client socket", e)
            }
        }
    }

    private fun extractQueryParam(url: String, key: String): String? {
        val queryStart = url.indexOf('?')
        if (queryStart == -1) return null
        val query = url.substring(queryStart + 1)
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx != -1) {
                val k = pair.substring(0, idx)
                if (k == key) {
                    return pair.substring(idx + 1)
                }
            }
        }
        return null
    }

    private fun sendOptionsResponse(out: PrintWriter) {
        out.print("HTTP/1.1 240 No Content\r\n")
        out.print("Access-Control-Allow-Origin: *\r\n")
        out.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        out.print("Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With\r\n")
        out.print("Connection: close\r\n\r\n")
        out.flush()
    }

    private fun sendStringResponse(out: PrintWriter, status: String, contentType: String, data: String) {
        out.print("HTTP/1.1 $status\r\n")
        out.print("Content-Type: $contentType\r\n")
        out.print("Content-Length: ${data.toByteArray().size}\r\n")
        out.print("Access-Control-Allow-Origin: *\r\n")
        out.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        out.print("Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With\r\n")
        out.print("Connection: close\r\n\r\n")
        out.print(data)
        out.flush()
    }

    private fun sendError(out: PrintWriter, code: Int, message: String) {
        val jsonPayload = """{"error": "$message", "code": $code}"""
        sendStringResponse(out, "$code $message", "application/json", jsonPayload)
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (networkInterface in interfaces) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress) {
                            val hostAddress = address.hostAddress ?: ""
                            val isIPv4 = hostAddress.indexOf(':') < 0
                            if (isIPv4) {
                                return hostAddress
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e("IPAddress", "Error getting IP Address", ex)
            }
            return "127.0.0.1"
        }
    }
}
