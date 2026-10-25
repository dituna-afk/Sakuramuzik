package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CookieItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val isSecure: Boolean = true,
    val isHttpOnly: Boolean = false
) {
    fun toRawCookieString(): String {
        val builder = StringBuilder("$name=$value")
        if (domain.isNotBlank()) {
            builder.append("; Domain=$domain")
        }
        if (path.isNotBlank()) {
            builder.append("; Path=$path")
        }
        if (isSecure) {
            builder.append("; Secure")
        }
        if (isHttpOnly) {
            builder.append("; HttpOnly")
        }
        return builder.toString()
    }
}

data class CookieLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val url: String,
    val rawCookieHeader: String,
    val cookieCount: Int
)

object CookieParser {
    fun parseRawCookies(rawCookieString: String?, defaultDomain: String): List<CookieItem> {
        if (rawCookieString.isNullOrBlank()) return emptyList()
        val cleanDomain = extractDomain(defaultDomain)
        return rawCookieString.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("=") }
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val name = pair.substring(0, index).trim()
                val value = pair.substring(index + 1).trim()
                CookieItem(
                    name = name,
                    value = value,
                    domain = cleanDomain,
                    path = "/",
                    isSecure = defaultDomain.startsWith("https", ignoreCase = true),
                    isHttpOnly = false
                )
            }
    }

    fun extractDomain(url: String): String {
        return try {
            val withoutProtocol = url.substringAfter("://", url)
            withoutProtocol.substringBefore("/").substringBefore(":").ifBlank { url }
        } catch (_: Exception) {
            url
        }
    }
}
