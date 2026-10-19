package com.wifictl.app.core

import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/** TLS-идентичность агента: ключ, самоподписанный сертификат и его отпечаток. */
class AgentIdentity(
    val keyPair: KeyPair,
    val certificate: X509Certificate,
) {
    val fingerprint: String = SecurityUtil.fingerprintOf(certificate.encoded)

    /** KeyManager'ы для серверного TLS-контекста. */
    fun keyManagers(): Array<KeyManager> {
        val password = CharArray(0)
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("wifictl", keyPair.private, password, arrayOf(certificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, password)
        return kmf.keyManagers
    }
}

object SecurityUtil {
    init {
        // BouncyCastle нужен для генерации сертификата.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val random = SecureRandom()

    /** Случайный URL-safe токен доступа. */
    fun generateToken(nBytes: Int = 32): String {
        val bytes = ByteArray(nBytes)
        random.nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
    }

    /** Сгенерировать ключ + самоподписанный сертификат. */
    fun generateIdentity(commonName: String = "wifictl-agent"): AgentIdentity {
        val keyGen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val keyPair = keyGen.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 60_000)
        val notAfter = Date(now + 3650L * 24 * 60 * 60 * 1000)
        val subject = X500Principal("CN=$commonName")

        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now),
            notBefore,
            notAfter,
            subject,
            keyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256WithRSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.private)
        val holder = builder.build(signer)
        val cert = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(holder)
        return AgentIdentity(keyPair, cert)
    }

    /** SHA-256 отпечаток DER-сертификата в hex (нижний регистр). */
    fun fingerprintOf(der: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(der)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Сравнение токенов в постоянное время. */
    fun tokensMatch(expected: String, provided: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(), provided.toByteArray())

    /**
     * TrustManager для клиента: не проверяет цепочку CA, а пиннит SHA-256
     * отпечаток сертификата агента (TOFU). Если [expectedFingerprint] пуст —
     * доверяет на первом подключении.
     */
    fun pinningTrustManager(expectedFingerprint: String?): X509TrustManager =
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (expectedFingerprint.isNullOrEmpty()) return
                val leaf = chain?.firstOrNull()
                    ?: throw java.security.cert.CertificateException("агент не предъявил сертификат")
                val actual = fingerprintOf(leaf.encoded)
                if (!MessageDigest.isEqual(actual.toByteArray(), expectedFingerprint.lowercase().toByteArray())) {
                    throw java.security.cert.CertificateException(
                        "отпечаток сертификата агента не совпал"
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
}
