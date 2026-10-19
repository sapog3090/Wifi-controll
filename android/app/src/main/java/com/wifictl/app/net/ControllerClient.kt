package com.wifictl.app.net

import com.wifictl.app.core.AuthFrame
import com.wifictl.app.core.AuthResult
import com.wifictl.app.core.OutputFrame
import com.wifictl.app.core.Protocol
import com.wifictl.app.core.Request
import com.wifictl.app.core.ResultFrame
import com.wifictl.app.core.SecurityUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.InetSocketAddress
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager

/** Клиент-контроллер: TLS-подключение к агенту с пиннингом отпечатка. */
class ControllerClient private constructor(
    private val socket: SSLSocket,
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
    val allowArbitrary: Boolean,
) {
    suspend fun run(
        kind: String,
        value: String,
        params: Map<String, String> = emptyMap(),
        onOutput: suspend (OutputFrame) -> Unit,
    ): ResultFrame = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().replace("-", "")
        sendLine(Protocol.encode(Request(id = id, kind = kind, value = value, params = params)))
        while (true) {
            val line = reader.readLine() ?: throw java.io.IOException("соединение закрыто")
            when (val frame = Protocol.decode(line)) {
                is OutputFrame -> if (frame.id == id) onOutput(frame)
                is ResultFrame -> if (frame.id == id) return@withContext frame
                else -> {}
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException()
    }

    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    private fun sendLine(text: String) {
        writer.write(text)
        writer.write("\n")
        writer.flush()
    }

    companion object {
        suspend fun connect(
            host: String,
            port: Int,
            token: String,
            fingerprint: String?,
            connectTimeoutMs: Int = 8000,
        ): ControllerClient = withContext(Dispatchers.IO) {
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(SecurityUtil.pinningTrustManager(fingerprint)), null)
            }
            val socket = sslContext.socketFactory.createSocket() as SSLSocket
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.startHandshake() // здесь срабатывает проверка отпечатка

            val reader = socket.inputStream.bufferedReader()
            val writer = socket.outputStream.bufferedWriter()

            writer.write(Protocol.encode(AuthFrame(token = token)))
            writer.write("\n")
            writer.flush()

            val resp = Protocol.decode(reader.readLine() ?: throw java.io.IOException("нет ответа"))
            if (resp !is AuthResult || !resp.ok) {
                socket.close()
                val msg = (resp as? AuthResult)?.message ?: "отказано"
                throw SecurityException("аутентификация не удалась: $msg")
            }
            ControllerClient(socket, reader, writer, resp.allowArbitrary)
        }
    }
}
