package com.wifictl.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream

/** Результат выполнения команды. */
data class ExecResult(val exitCode: Int, val timedOut: Boolean = false)

/**
 * Выполнение shell-команд на устройстве через `sh -c`.
 * Команды работают от UID приложения (без root) — этого достаточно для
 * большинства информационных макросов.
 */
object Executor {

    suspend fun run(
        command: String,
        timeoutMs: Long,
        onOutput: suspend (stream: String, data: String) -> Unit,
    ): ExecResult = withContext(Dispatchers.IO) {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(false)
            .start()

        coroutineScope {
            val out = launch { pump(process.inputStream, "stdout", onOutput) }
            val err = launch { pump(process.errorStream, "stderr", onOutput) }

            val finished = waitFor(process, timeoutMs)
            if (!finished) {
                process.destroyForcibly()
                out.join(); err.join()
                onOutput("stderr", "[wifictl] команда прервана по таймауту (${timeoutMs}мс)\n")
                return@coroutineScope ExecResult(124, timedOut = true)
            }
            out.join(); err.join()
            ExecResult(process.exitValue())
        }
    }

    private suspend fun pump(
        stream: InputStream,
        name: String,
        onOutput: suspend (String, String) -> Unit,
    ) {
        val reader = BufferedReader(stream.reader())
        reader.useLines { lines ->
            for (line in lines) {
                onOutput(name, line + "\n")
            }
        }
    }

    private fun waitFor(process: Process, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) return true
            Thread.sleep(50)
        }
        return !process.isAlive
    }
}
