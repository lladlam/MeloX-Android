package com.lladlam.melox.core.provider.kugou

import org.json.JSONObject

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

private val KugouArtworkKeys = arrayOf(
    "Image", "image", "img", "imgurl", "img_url", "album_img", "AlbumImage",
    "sizable_cover", "cover", "cover_url", "pic", "banner7url", "sizable_avatar", "avatar",
)

internal fun kugouArtworkUrl(item: JSONObject): String? {
    fun fromObject(value: JSONObject): String? = KugouArtworkKeys
        .asSequence()
        .map(value::optString)
        .firstOrNull(String::isNotBlank)
        ?.let(::normalizeKugouArtworkUrl)

    fromObject(item)?.let { return it }
    for (key in listOf("album_info", "albumInfo", "audio_info", "base")) {
        val nested = item.optJSONObject(key) ?: continue
        fromObject(nested)?.let { return it }
    }
    val transParam = when (val raw = item.opt("trans_param")) {
        is JSONObject -> raw
        is String -> raw.trim().takeIf(String::isNotBlank)?.let {
            runCatching { JSONObject(it) }.getOrNull()
        }
        else -> null
    }
    return transParam?.let(::fromObject)
}
