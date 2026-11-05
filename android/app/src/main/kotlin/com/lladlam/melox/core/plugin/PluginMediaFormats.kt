package com.lladlam.melox.core.plugin

internal object PluginMediaFormats {
    fun check(url: String, format: String? = null, drm: Boolean = false) {
        val path = java.net.URI(url).path.orEmpty().lowercase(java.util.Locale.ROOT)
        val label = format.orEmpty().lowercase(java.util.Locale.ROOT)
        require(!drm && !path.endsWith(".m3u8") && !path.endsWith(".mpd") &&
            label !in setOf("hls", "dash", "m3u8", "mpd", "drm") && !unsupportedMime(label)) { "UNSUPPORTED_MEDIA_FORMAT" }
    }
    fun unsupportedMime(mime: String): Boolean = mime.substringBefore(';').trim().lowercase(java.util.Locale.ROOT) in
        setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl", "application/dash+xml")
}
