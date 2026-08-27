package com.lladlam.melox.core.provider.lxuser

/** Metadata shared by LX Music user-source importers. */
data class LxUserScriptMetadata(
    val name: String? = null,
    val version: String? = null,
    val author: String? = null,
    val description: String? = null,
    val homepage: String? = null,
    val raw: Map<String, String> = emptyMap(),
) {
    companion object {
        private val field = Regex("^\\s*(?:[*#/]\\s*)?@([A-Za-z][\\w-]*)\\s+(.+?)\\s*$")

        /** Reads @name-style fields from the leading comment block only. */
        fun parse(script: String): LxUserScriptMetadata {
            val values = linkedMapOf<String, String>()
            for (line in script.lineSequence().take(80)) {
                val match = field.find(line) ?: continue
                values[match.groupValues[1].lowercase()] = match.groupValues[2].trim()
            }
            return LxUserScriptMetadata(
                values["name"], values["version"], values["author"],
                values["description"], values["homepage"], values.toMap(),
            )
        }
    }
}
