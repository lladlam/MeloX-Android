package com.lladlam.melox.core.provider.kugou

import org.json.JSONObject
import org.json.JSONArray

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

internal fun kugouFirstString(value: JSONObject, vararg keys: String): String =
    kugouObjects(value)
        .asSequence()
        .flatMap { item -> keys.asSequence().map(item::optString) }
        .firstOrNull(String::isNotBlank)
        .orEmpty()

internal fun kugouFirstLong(value: JSONObject, vararg keys: String): Long =
    kugouObjects(value)
        .asSequence()
        .flatMap { item -> keys.asSequence().mapNotNull { key ->
            when (val raw = item.opt(key)) {
                is Number -> sequenceOf(raw.toLong())
                is String -> raw.toLongOrNull()?.let(::sequenceOf) ?: emptySequence()
                else -> emptySequence()
            }
        }.flatten() }
        .firstOrNull() ?: -1L

private fun kugouObjects(value: Any?): List<JSONObject> = when (value) {
    is JSONObject -> buildList {
        add(value)
        value.keys().forEach { key -> addAll(kugouObjects(value.opt(key))) }
    }
    is JSONArray -> buildList {
        for (index in 0 until value.length()) addAll(kugouObjects(value.opt(index)))
    }
    else -> emptyList()
}
