package com.lladlam.melox.core.provider.thirdparty

import android.content.Context
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val ResolverUrlTtlMs = 120_000L
internal const val ResolverTestSongId = 22676167L

internal enum class ResolverKind(val label: String, val preferencesName: String) {
    Meting("Meting", "melox_meting_api"),
    Chksz("CHKSZ", "melox_chksz_api"),
    Unm("UNM", "melox_unm_api"),
}

/** Kept separate from playback and from the official providers' credentials. */
internal data class ResolverSettings(
    val kind: ResolverKind,
    val endpoint: String = "",
    val credential: String = "",
    val servers: String = "kuwo,kugou,bilibili",
    val enabled: Boolean = false,
) {
    val configured: Boolean get() = enabled && validResolverEndpoint(endpoint) &&
        (kind != ResolverKind.Chksz || credential.isNotBlank())

    // Never include raw credentials in the playback cache identity or logs.
    fun cacheIdentity(): String {
        if (!configured) return "${kind.name}:disabled"
        val fields = listOf(endpoint, credential, servers).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(fields.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "${kind.name}:$digest"
    }
}

internal fun validResolverEndpoint(value: String): Boolean = value.trim().toHttpUrlOrNull()?.let {
    it.username.isEmpty() && it.password.isEmpty() && it.fragment == null
} == true

/** Read legacy preference names so existing CHKSZ keys and settings are preserved. */
internal object ResolverSettingsStore {
    fun read(context: Context, kind: ResolverKind): ResolverSettings {
        val prefs = context.applicationContext.getSharedPreferences(kind.preferencesName, Context.MODE_PRIVATE)
        val endpoint = prefs.getString("baseurl", "").orEmpty().trim().ifEmpty {
            if (kind == ResolverKind.Chksz) "https://api.chksz.com" else ""
        }
        val credential = prefs.getString(if (kind == ResolverKind.Chksz) "apikey" else "auth", "").orEmpty().trim()
        val enabled = if (prefs.contains("enabled")) prefs.getBoolean("enabled", false)
            else if (kind == ResolverKind.Chksz) credential.isNotEmpty() else endpoint.isNotEmpty()
        return ResolverSettings(
            kind, endpoint, credential,
            prefs.getString("servers", "kuwo,kugou,bilibili").orEmpty().ifBlank { "kuwo,kugou,bilibili" },
            enabled,
        )
    }

    fun write(context: Context, settings: ResolverSettings) {
        require(settings.endpoint.isBlank() || validResolverEndpoint(settings.endpoint)) { "Invalid HTTP(S) endpoint" }
        context.applicationContext.getSharedPreferences(settings.kind.preferencesName, Context.MODE_PRIVATE).edit()
            .putString("baseurl", settings.endpoint.trim())
            .putString(if (settings.kind == ResolverKind.Chksz) "apikey" else "auth", settings.credential.trim())
            .putString("servers", settings.servers.trim())
            .putBoolean("enabled", settings.enabled)
            .apply()
    }

    fun clear(context: Context, kind: ResolverKind) {
        context.applicationContext.getSharedPreferences(kind.preferencesName, Context.MODE_PRIVATE).edit()
            .remove("baseurl").remove("apikey").remove("auth").remove("servers")
            .putBoolean("enabled", false).apply()
    }
}
