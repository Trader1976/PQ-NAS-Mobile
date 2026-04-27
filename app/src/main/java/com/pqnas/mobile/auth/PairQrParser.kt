package com.pqnas.mobile.auth

import android.net.Uri
import com.pqnas.mobile.security.PinnedTls

data class PairQrPayload(
    val version: Int,
    val pairToken: String,
    val origin: String,
    val appName: String,
    val tlsPinSha256: String
)

object PairQrParser {
    fun parse(raw: String): PairQrPayload? {
        val text = raw.trim()
        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null

        if (uri.scheme?.lowercase() != "dna") return null
        if (uri.host?.lowercase() != "pair") return null

        val version = uri.getQueryParameter("v")?.toIntOrNull() ?: 1
        val pairToken = uri.getQueryParameter("pt")?.trim().orEmpty()
        val origin = uri.getQueryParameter("origin")?.trim().orEmpty()
        val appName = uri.getQueryParameter("app")?.trim().orEmpty()

        val rawTlsPin = uri.getQueryParameter("tls_pin_sha256")
            ?: uri.getQueryParameter("tls_pin")
            ?: uri.getQueryParameter("pin")
            ?: ""

        val tlsPinSha256 = PinnedTls.normalizeSpkiSha256Pin(rawTlsPin) ?: return null

        if (pairToken.isBlank() || origin.isBlank()) return null

        val originUri = runCatching { Uri.parse(origin) }.getOrNull() ?: return null
        if (originUri.scheme?.lowercase() != "https") return null
        if (originUri.host.isNullOrBlank()) return null

        // Reject userinfo (e.g. https://legit.com@evil.com) – prevents display spoofing
        if (!originUri.userInfo.isNullOrBlank()) return null

        // Reject fragment – origins should never carry one
        if (!originUri.fragment.isNullOrBlank()) return null

        return PairQrPayload(
            version = version,
            pairToken = pairToken,
            origin = origin.trim().trimEnd('/'),
            appName = if (appName.isBlank()) "DNA-Nexus" else appName,
            tlsPinSha256 = tlsPinSha256
        )
    }
}
