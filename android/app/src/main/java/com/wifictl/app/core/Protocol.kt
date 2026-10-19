package com.wifictl.app.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Кадры протокола wifictl (построчный JSON поверх TLS-сокета).
 *
 * Поток: клиент шлёт [AuthFrame] -> агент [AuthResult] -> клиент [Request] ->
 * агент стримит [OutputFrame] и завершает [ResultFrame].
 *
 * Формат совместим с Python-реализацией (ключ "type" различает кадры).
 */
object Protocol {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(frame: Frame): String = when (frame) {
        is AuthFrame -> json.encodeToString(AuthFrame.serializer(), frame)
        is AuthResult -> json.encodeToString(AuthResult.serializer(), frame)
        is Request -> json.encodeToString(Request.serializer(), frame)
        is OutputFrame -> json.encodeToString(OutputFrame.serializer(), frame)
        is ResultFrame -> json.encodeToString(ResultFrame.serializer(), frame)
    }

    fun decode(raw: String): Frame {
        val obj = json.parseToJsonElement(raw) as? JsonObject
            ?: throw ProtocolException("ожидался JSON-объект")
        return when (val type = obj["type"]?.jsonPrimitive?.content) {
            "auth" -> json.decodeFromString(AuthFrame.serializer(), raw)
            "auth_result" -> json.decodeFromString(AuthResult.serializer(), raw)
            "request" -> json.decodeFromString(Request.serializer(), raw)
            "output" -> json.decodeFromString(OutputFrame.serializer(), raw)
            "result" -> json.decodeFromString(ResultFrame.serializer(), raw)
            else -> throw ProtocolException("неизвестный тип кадра: $type")
        }
    }
}

class ProtocolException(message: String) : Exception(message)

sealed interface Frame

@Serializable
data class AuthFrame(
    val token: String,
    val type: String = "auth",
) : Frame

@Serializable
data class AuthResult(
    val ok: Boolean,
    val message: String = "",
    @SerialName("allow_arbitrary") val allowArbitrary: Boolean = false,
    val type: String = "auth_result",
) : Frame

@Serializable
data class Request(
    val id: String,
    val kind: String, // "macro" | "command" | "list"
    val value: String,
    val params: Map<String, String> = emptyMap(),
    val type: String = "request",
) : Frame

@Serializable
data class OutputFrame(
    val id: String,
    val command: String,
    val stream: String, // "stdout" | "stderr"
    val data: String,
    val type: String = "output",
) : Frame

@Serializable
data class ResultFrame(
    val id: String,
    @SerialName("exit_code") val exitCode: Int = 0,
    val error: String = "",
    val done: Boolean = true,
    val type: String = "result",
) : Frame
