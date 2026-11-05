package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.SourceId
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

internal object PluginTrackLink {
    data class Track(val id: MusicResourceId, val title: String)
    fun create(id: MusicResourceId, title: String): String {
        require(id.sourceId.builtIn == null)
        return "melox://plugin-track?source=${encode(id.sourceId.value)}&id=${encode(id.value)}&title=${encode(title.take(1024))}"
    }
    fun parse(raw: String): Track {
        require(raw.length <= 30000)
        val uri = URI(raw)
        require(uri.scheme == "melox" && uri.host == "plugin-track" && uri.rawUserInfo == null && uri.port == -1 &&
            uri.path.isNullOrEmpty() && uri.fragment == null)
        val pairs = requireNotNull(uri.rawQuery).split('&').map { part ->
            val parts = part.split('=', limit = 2); require(parts.size == 2)
            parts[0] to URLDecoder.decode(parts[1], "UTF-8")
        }
        require(pairs.size == 3 && pairs.map { it.first }.toSet() == setOf("source", "id", "title"))
        val values = pairs.toMap()
        val source = requireNotNull(SourceId.parse(values.getValue("source")))
        require(source.builtIn == null)
        val id = values.getValue("id")
        require(id.isNotBlank() && id.toByteArray(Charsets.UTF_8).size <= 4096)
        val title = values.getValue("title")
        require(title.length <= 1024)
        return Track(MusicResourceId(source, id), title.ifBlank { id.take(1024) })
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
