package com.pqnas.mobile.security

import android.util.Base64
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object PinnedTls {
    private const val SPKI_SHA256_PREFIX = "sha256/"

    fun normalizeSpkiSha256Pin(raw: String): String? {
        val cleaned = raw.trim().replace(" ", "+")
        if (!cleaned.startsWith(SPKI_SHA256_PREFIX)) return null

        val b64 = cleaned.removePrefix(SPKI_SHA256_PREFIX).trim()
        if (b64.isBlank()) return null

        val decoded = runCatching {
            Base64.decode(b64, Base64.DEFAULT)
        }.getOrNull() ?: return null

        if (decoded.size != 32) return null

        return SPKI_SHA256_PREFIX + Base64.encodeToString(decoded, Base64.NO_WRAP)
    }

    fun applyTo(builder: OkHttpClient.Builder, tlsPinSha256: String) {
        val normalizedPin = normalizeSpkiSha256Pin(tlsPinSha256)
            ?: throw IllegalArgumentException("Malformed server TLS pin")

        val trustManager = SpkiPinTrustManager(normalizedPin)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
    }

    fun certificateSpkiSha256Pin(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(certificate.publicKey.encoded)
        return SPKI_SHA256_PREFIX + Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun systemDefaultTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)

        return tmf.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: throw IllegalStateException("No default X509TrustManager available")
    }

    private class SpkiPinTrustManager(
        private val expectedPin: String
    ) : X509TrustManager {
        private val defaultTrustManager: X509TrustManager = systemDefaultTrustManager()

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            defaultTrustManager.acceptedIssuers

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?
        ) {
            defaultTrustManager.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?
        ) {
            if (chain.isNullOrEmpty()) {
                throw CertificateException("Empty server certificate chain")
            }

            val leaf = chain[0]
            leaf.checkValidity()

            val actualPin = certificateSpkiSha256Pin(leaf)
            if (actualPin != expectedPin) {
                throw CertificateException("Server TLS identity does not match QR trust pin")
            }

            try {
                defaultTrustManager.checkServerTrusted(chain, authType)
            } catch (_: CertificateException) {
                // The QR-carried SPKI pin is the explicit trust root for
                // self-signed/internal DNA-Nexus servers.
                //
                // We still require:
                // - HTTPS
                // - non-expired leaf certificate
                // - exact leaf SPKI SHA-256 match
                // - normal OkHttp hostname verification after the TLS handshake
            }
        }
    }
}
