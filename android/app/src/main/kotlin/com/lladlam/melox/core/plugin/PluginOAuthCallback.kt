package com.lladlam.melox.core.plugin

import java.net.URI
import java.net.URLDecoder

/** Pure parser shared by the exported Activity path and adversarial JVM tests. */
internal object PluginOAuthCallback {
    data class Value(val state: String, val code: String?, val declined: Boolean)
    fun parse(raw: String): Value {
        require(raw.length <= 8192)
        val uri = URI(raw)
        require(uri.scheme == "com.lladlam.melox.plugin" && uri.rawPath == "/oauth2redirect" && uri.rawAuthority == null && uri.rawFragment == null)
        val pairs = requireNotNull(uri.rawQuery).split('&').map {
            val parts = it.split('=', limit = 2); require(parts.size == 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
        }
        require(pairs.map { it.first }.distinct().size == pairs.size) { "duplicate OAuth callback parameter" }
        val values = pairs.toMap()
        val state = requireNotNull(values["state"])
        require(state.matches(Regex("[A-Za-z0-9_-]{43}"))) { "invalid OAuth state" }
        val declined = "error" in values
        val code = values["code"]
        require(!(declined && code != null))
        require(declined || (code != null && code.length in 1..4096 && code.all { it.code in 32..126 })) { "invalid OAuth code" }
        return Value(state, code, declined)
    }
}
