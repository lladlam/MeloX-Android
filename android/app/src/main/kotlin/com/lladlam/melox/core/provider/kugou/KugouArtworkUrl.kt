package com.lladlam.melox.core.provider.kugou

internal fun normalizeKugouArtworkUrl(value: String): String? {
    val normalized = value.trim()
        .takeIf(String::isNotBlank)
        ?.replace("{size}", "400", ignoreCase = true)
        ?: return null
    return when {
        normalized.startsWith("//") -> "https:$normalized"
        normalized.startsWith("http://", ignoreCase = true) -> "https://${normalized.substringAfter("://")}"
        else -> normalized
    }
}
