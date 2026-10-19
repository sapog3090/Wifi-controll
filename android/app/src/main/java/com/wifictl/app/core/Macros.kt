package com.wifictl.app.core

/** Определение одного макроса. */
data class Macro(
    val name: String,
    val commands: List<String>,
    val description: String = "",
    val params: List<String> = emptyList(),
)

class MacroException(message: String) : Exception(message)

/** Движок макросов: ключевое слово -> плоский список команд. */
class MacroEngine(private val macros: Map<String, Macro>) {

    fun names(): List<String> = macros.keys.sorted()

    fun all(): List<Macro> = macros.values.toList()

    fun get(name: String): Macro? = macros[name]

    /** Развернуть макрос в список готовых команд с подстановкой параметров. */
    fun expand(name: String, params: Map<String, String> = emptyMap()): List<String> {
        val commands = expandRecursive(name, emptyList())
        return commands.map { substitute(it, name, params) }
    }

    private fun expandRecursive(name: String, seen: List<String>): List<String> {
        if (name in seen) {
            throw MacroException("обнаружена рекурсия макросов: ${(seen + name).joinToString(" -> ")}")
        }
        val macro = macros[name] ?: throw MacroException("неизвестный макрос: $name")
        val result = mutableListOf<String>()
        for (cmd in macro.commands) {
            if (cmd.startsWith(INCLUDE_PREFIX)) {
                result += expandRecursive(cmd.removePrefix(INCLUDE_PREFIX).trim(), seen + name)
            } else {
                result += cmd
            }
        }
        return result
    }

    private fun substitute(command: String, macroName: String, params: Map<String, String>): String {
        val needed = PLACEHOLDER.findAll(command).map { it.groupValues[1] }.toSet()
        val missing = needed - params.keys
        if (missing.isNotEmpty()) {
            throw MacroException("макрос $macroName: не заданы параметры: ${missing.sorted().joinToString(", ")}")
        }
        var out = command
        for ((key, value) in params) {
            out = out.replace("{$key}", value)
        }
        return out
    }

    companion object {
        private const val INCLUDE_PREFIX = "@"
        private val PLACEHOLDER = Regex("""\{([a-zA-Z_][a-zA-Z0-9_]*)\}""")

        /** Набор макросов по умолчанию (можно расширять в UI/хранилище). */
        fun defaults(): MacroEngine = MacroEngine(
            listOf(
                Macro("root", listOf("id", "uptime", "df -h"), "Снимок состояния"),
                Macro("net", listOf("ip -brief address || ifconfig"), "Сетевые интерфейсы"),
                Macro("battery", listOf("dumpsys battery | grep level || echo n/a"), "Уровень батареи"),
                Macro("full", listOf("@root", "@net"), "root + сеть"),
            ).associateBy { it.name },
        )
    }
}
