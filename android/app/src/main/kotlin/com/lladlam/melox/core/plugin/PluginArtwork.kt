package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.SourceId
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Opaque provenance survives track persistence; never hand plugin HTTPS directly to Coil. */
internal object PluginArtwork {
    data class Request(val source: SourceId, val url: String)
    fun wrap(manifest: PluginManifest, raw: String?): String? {
        if (raw == null) return null
        PluginResultValidator.validateUrlSyntax(raw)
        val uri = URI(raw)
        require(uri.scheme == "https" && uri.host in manifest.networkDomains && uri.port in setOf(-1, 443)) { "artwork permission denied" }
        return "melox-plugin-image://artwork?source=${encode(manifest.sourceId.value)}&url=${encode(raw)}"
    }
    fun parse(raw: String): Request {
        require(raw.length <= 16384)
        val uri = URI(raw)
        require(uri.scheme == "melox-plugin-image" && uri.host == "artwork" && uri.rawUserInfo == null && uri.port == -1 &&
            uri.path.isNullOrEmpty() && uri.fragment == null)
        val pairs = requireNotNull(uri.rawQuery).split('&').map { part ->
            val index = part.indexOf('='); require(index > 0)
            part.substring(0, index) to URLDecoder.decode(part.substring(index + 1), "UTF-8")
        }
        require(pairs.size == 2 && pairs.map { it.first }.toSet() == setOf("source", "url"))
        val values = pairs.toMap()
        val source = SourceId.parse(values.getValue("source")) ?: error("invalid artwork source")
        require(source.builtIn == null)
        val url = values.getValue("url")
        PluginResultValidator.validateUrlSyntax(url)
        return Request(source, url)
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
