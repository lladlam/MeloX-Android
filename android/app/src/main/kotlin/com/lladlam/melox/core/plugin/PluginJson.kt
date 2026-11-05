package com.lladlam.melox.core.plugin

/** Check before recursive platform JSON parsing; brackets inside strings do not consume depth. */
internal object PluginJson {
    fun check(raw: String, maxBytes: Int = 128 * 1024) {
        require(raw.toByteArray(Charsets.UTF_8).size <= maxBytes) { "plugin JSON too large" }
        var depth = 0
        var quoted = false
        var escaped = false
        for (c in raw) {
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) { "plugin JSON nesting exceeded" } }
                '}', ']' -> { depth--; require(depth >= 0) { "unbalanced plugin JSON" } }
            }
        }
        require(!quoted && depth == 0) { "incomplete plugin JSON" }
    }
}
