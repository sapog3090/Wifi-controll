package com.wifictl.app.core

import android.util.Base64
import kotlinx.serialization.Serializable

/** Данные сопряжения, кодируемые в QR-код. */
@Serializable
data class PairingPayload(
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
    val fingerprint: String,
)

object Pairing {
    private const val PREFIX = "wifictl1:"

    /** Закодировать в компактную строку для QR. */
    fun encode(payload: PairingPayload): String {
        val jsonStr = Protocol.json.encodeToString(PairingPayload.serializer(), payload)
        val b64 = Base64.encodeToString(
            jsonStr.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return PREFIX + b64
    }

    /** Разобрать строку из QR обратно в [PairingPayload]. */
    fun decode(raw: String): PairingPayload {
        val body = raw.trim().removePrefix(PREFIX)
        val bytes = Base64.decode(body, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return Protocol.json.decodeFromString(
            PairingPayload.serializer(),
            String(bytes, Charsets.UTF_8),
        )
    }

    fun isPairingString(raw: String): Boolean = raw.trim().startsWith(PREFIX)
}
