package com.wifictl.app.net

import com.wifictl.app.core.AgentIdentity
import com.wifictl.app.core.AuthFrame
import com.wifictl.app.core.AuthResult
import com.wifictl.app.core.Frame
import com.wifictl.app.core.MacroEngine
import com.wifictl.app.core.MacroException
import com.wifictl.app.core.OutputFrame
import com.wifictl.app.core.Protocol
import com.wifictl.app.core.Request
import com.wifictl.app.core.ResultFrame
import com.wifictl.app.core.SecurityUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

/** Конфигурация агента. */
data class AgentConfig(
    val port: Int = 8765,
    val token: String,
    val allowArbitrary: Boolean = false,
    val denyBinaries: List<String> = listOf("rm", "mkfs", "dd", "reboot"),
    val commandTimeoutMs: Long = 30_000,
)

/**
 * TLS-сервер агента. Принимает подключения, проверяет токен, выполняет
 * макросы/команды, стримит вывод. Запускается в [scope], останавливается
 * через [stop] (закрытие серверного сокета прерывает accept).
 */
class AgentServer(
    private val identity: AgentIdentity,
    private val config: AgentConfig,
    private val engine: MacroEngine,
    private val onLog: (String) -> Unit,
) {
    @Volatile
    private var serverSocket: SSLServerSocket? = null

    suspend fun serve(scope: CoroutineScope) = withContext(Dispatchers.IO) {
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(identity.keyManagers(), null, java.security.SecureRandom())
        }
        val socket = sslContext.serverSocketFactory
            .createServerSocket(config.port) as SSLServerSocket
        serverSocket = socket
        onLog("Агент слушает порт ${config.port}")

        while (scope.isActive && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: Exception) {
                if (socket.isClosed) break else continue
            }
            scope.launch(Dispatchers.IO) { handle(client) }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
    }

    private suspend fun handle(socket: Socket) {
        val peer = socket.inetAddress?.hostAddress ?: "unknown"
        onLog("Подключение: $peer")
        try {
            socket.use {
                val reader = it.getInputStream().bufferedReader()
                val writer = it.getOutputStream().bufferedWriter()

                val firstLine = reader.readLine() ?: return
                val auth = Protocol.decode(firstLine)
                if (auth !is AuthFrame || !SecurityUtil.tokensMatch(config.token, auth.token)) {
                    send(writer, AuthResult(ok = false, message = "неверный токен"))
                    onLog("Отклонён: $peer (токен)")
                    return
                }
                send(writer, AuthResult(ok = true, allowArbitrary = config.allowArbitrary))

                while (true) {
                    val line = reader.readLine() ?: break
                    val frame = Protocol.decode(line)
                    if (frame is Request) dispatch(writer, frame, peer)
                }
            }
        } catch (e: Exception) {
            onLog("Ошибка соединения $peer: ${e.message}")
        }
    }

    private suspend fun dispatch(writer: BufferedWriter, req: Request, peer: String) {
        when (req.kind) {
            "list" -> {
                for (m in engine.all()) {
                    val desc = if (m.description.isNotEmpty()) "  — ${m.description}" else ""
                    send(writer, OutputFrame(req.id, "", "stdout", "${m.name}$desc\n"))
                }
                send(writer, ResultFrame(req.id, exitCode = 0))
            }
            "macro" -> runMacro(writer, req, peer)
            "command" -> runArbitrary(writer, req, peer)
            else -> send(writer, ResultFrame(req.id, exitCode = 2, error = "неизвестный kind: ${req.kind}"))
        }
    }

    private suspend fun runMacro(writer: BufferedWriter, req: Request, peer: String) {
        val commands = try {
            engine.expand(req.value, req.params)
        } catch (e: MacroException) {
            send(writer, ResultFrame(req.id, exitCode = 2, error = e.message ?: "ошибка макроса"))
            return
        }
        runSequence(writer, req, peer, commands)
    }

    private suspend fun runArbitrary(writer: BufferedWriter, req: Request, peer: String) {
        if (!config.allowArbitrary) {
            send(writer, ResultFrame(req.id, exitCode = 126, error = "произвольные команды запрещены"))
            return
        }
        runSequence(writer, req, peer, listOf(req.value))
    }

    private suspend fun runSequence(writer: BufferedWriter, req: Request, peer: String, commands: List<String>) {
        var lastExit = 0
        for (command in commands) {
            val denied = firstBinary(command)?.let { bin -> config.denyBinaries.contains(bin) } ?: true
            if (denied) {
                onLog("Запрет '$command' от $peer")
                send(writer, ResultFrame(req.id, exitCode = 126, error = "команда запрещена политикой: $command"))
                return
            }
            onLog("Выполняю '$command' ($peer)")
            val result = Executor.run(command, config.commandTimeoutMs) { stream, data ->
                send(writer, OutputFrame(req.id, command, stream, data))
            }
            lastExit = result.exitCode
            if (result.exitCode != 0) break
        }
        send(writer, ResultFrame(req.id, exitCode = lastExit))
    }

    private fun firstBinary(command: String): String? {
        val tokens = command.trim().split(Regex("\\s+"))
        for (tok in tokens) {
            if (tok.contains("=") && !tok.startsWith("-") && !tok.startsWith("/")) continue
            return tok.substringAfterLast('/')
        }
        return null
    }

    @Synchronized
    private fun send(writer: BufferedWriter, frame: Frame) {
        writer.write(Protocol.encode(frame))
        writer.write("\n")
        writer.flush()
    }
}
