package com.pqnas.mobile.auth

import android.net.Uri

data class PairQrPayload(
    val version: Int,
    val pairToken: String,
    val origin: String,
    val appName: String
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

        if (pairToken.isBlank() || origin.isBlank()) return null

        return PairQrPayload(
            version = version,
            pairToken = pairToken,
            origin = origin,
            appName = if (appName.isBlank()) "DNA-Nexus" else appName
        )
    }
}