package com.pqnas.mobile.files

import org.json.JSONObject
import retrofit2.HttpException
import java.util.Locale

internal fun throwSafeApiError(e: HttpException, fallback: String): Nothing {
    throw IllegalStateException(safeApiErrorMessage(e, fallback))
}

internal fun safeApiErrorMessage(e: HttpException, fallback: String): String {
    val code = e.code()
    val errorToken = readServerErrorToken(e)

    val reason = when {
        code == 400 -> "bad request"
        code == 401 -> "login expired"
        code == 403 -> "permission denied"
        code == 404 -> "not found"
        code == 409 -> "conflict"
        code == 413 -> "file is too large"
        code == 429 -> "too many requests"
        code >= 500 -> "server error"
        errorToken.contains("not_found") || errorToken.contains("not found") -> "not found"
        errorToken.contains("denied") || errorToken.contains("forbidden") -> "permission denied"
        errorToken.contains("unauthorized") || errorToken.contains("expired") -> "login expired"
        errorToken.contains("conflict") || errorToken.contains("exists") -> "conflict"
        errorToken.contains("too_large") || errorToken.contains("too large") -> "file is too large"
        else -> "request failed"
    }

    return "$fallback: $reason (HTTP $code)"
}

private fun readServerErrorToken(e: HttpException): String {
    val raw = try {
        e.response()?.errorBody()?.string().orEmpty()
    } catch (_: Exception) {
        ""
    }

    if (raw.isBlank()) return ""

    val json = try {
        JSONObject(raw)
    } catch (_: Exception) {
        return ""
    }

    val error = json.optString("error").orEmpty()
    val code = json.optString("code").orEmpty()

    // Intentionally ignore raw "message" and "detail" because those may contain
    // backend paths, stack traces, SQL errors, or other internals.
    return listOf(error, code)
        .joinToString(" ")
        .lowercase(Locale.getDefault())
}