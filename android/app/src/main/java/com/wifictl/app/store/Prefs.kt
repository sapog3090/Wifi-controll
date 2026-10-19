package com.wifictl.app.store

import android.content.Context
import android.util.Base64
import com.wifictl.app.core.AgentIdentity
import com.wifictl.app.core.PairingPayload
import com.wifictl.app.core.Protocol
import com.wifictl.app.core.SecurityUtil
import kotlinx.serialization.builtins.ListSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.KeyStore
import java.security.cert.X509Certificate

/** Хранилище: токен агента, его TLS-идентичность и сопряжённые устройства. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("wifictl", Context.MODE_PRIVATE)
    private val ksPassword = CharArray(0)

    // --- токен агента -----------------------------------------------------

    fun agentToken(): String {
        sp.getString(KEY_TOKEN, null)?.let { return it }
        val token = SecurityUtil.generateToken()
        sp.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    // --- TLS-идентичность агента (стабильный отпечаток) -------------------

    fun agentIdentity(): AgentIdentity {
        sp.getString(KEY_IDENTITY, null)?.let { stored ->
            try {
                return loadIdentity(Base64.decode(stored, Base64.NO_WRAP))
            } catch (_: Exception) {
                // повреждено — пересоздадим ниже
            }
        }
        val identity = SecurityUtil.generateIdentity()
        sp.edit().putString(KEY_IDENTITY, Base64.encodeToString(saveIdentity(identity), Base64.NO_WRAP)).apply()
        return identity
    }

    private fun saveIdentity(identity: AgentIdentity): ByteArray {
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("wifictl", identity.keyPair.private, ksPassword, arrayOf(identity.certificate))
        }
        return ByteArrayOutputStream().use { out ->
            ks.store(out, ksPassword)
            out.toByteArray()
        }
    }

    private fun loadIdentity(bytes: ByteArray): AgentIdentity {
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(ByteArrayInputStream(bytes), ksPassword)
        }
        val key = ks.getKey("wifictl", ksPassword) as java.security.PrivateKey
        val cert = ks.getCertificate("wifictl") as X509Certificate
        return AgentIdentity(KeyPair(cert.publicKey, key), cert)
    }

    // --- сопряжённые устройства (на стороне контроллера) ------------------

    fun savedDevices(): List<PairingPayload> {
        val raw = sp.getString(KEY_DEVICES, null) ?: return emptyList()
        return try {
            Protocol.json.decodeFromString(ListSerializer(PairingPayload.serializer()), raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDevice(device: PairingPayload) {
        val devices = savedDevices().filterNot { it.name == device.name }.toMutableList()
        devices.add(device)
        persistDevices(devices)
    }

    fun removeDevice(name: String) {
        persistDevices(savedDevices().filterNot { it.name == name })
    }

    private fun persistDevices(devices: List<PairingPayload>) {
        val raw = Protocol.json.encodeToString(ListSerializer(PairingPayload.serializer()), devices)
        sp.edit().putString(KEY_DEVICES, raw).apply()
    }

    companion object {
        private const val KEY_TOKEN = "agent_token"
        private const val KEY_IDENTITY = "agent_identity"
        private const val KEY_DEVICES = "paired_devices"
    }
}
