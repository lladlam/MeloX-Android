package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.SourceId
import org.json.JSONObject
import java.net.IDN
import java.util.Locale

/** Parsed, bounded input; a valid manifest alone does not authorize execution. */
data class PluginManifest(
    val sourceId: SourceId,
    val name: String,
    val version: String,
    val apiVersion: Int,
    val entry: String,
    val capabilities: Set<PluginCapability>,
    val networkDomains: Set<String>,
    val authentication: PluginAuthentication? = null,
    val author: String? = null,
    val homepage: String? = null,
)

data class PluginAuthentication(val type: String, val header: String = "Authorization", val prefix: String = "Bearer ", val domains: Set<String> = emptySet(), val oauth: PluginOAuthConfiguration? = null)

data class PluginOAuthConfiguration(val authorizationEndpoint: String, val tokenEndpoint: String, val clientId: String, val scopes: List<String>)

enum class PluginCapability(val wireName: String, val methods: Set<String>) {
    Search("search", setOf("search")), Playback("playback", setOf("resolvePlayback")), Lyrics("lyrics", setOf("lyrics")),
    Playlists("playlists", setOf("playlists", "searchPlaylists", "playlistDetail")),
    Albums("albums", setOf("searchAlbums", "albumDetail")),
    Artists("artists", setOf("searchArtists", "artistDetail")),
    Rankings("rankings", setOf("rankings", "rankingTracks")),
    HomeFeed("homeFeed", setOf("homeFeed")),
    UserLibrary("userLibrary", setOf("accountSummary", "userPlaylists", "savedAlbums", "followedArtists")),
    Favorites("favorites", setOf("isFavorite", "setFavorite")),
    Authentication("authentication", setOf("authenticationStatus")),
    Download("download", setOf("resolveDownload"));
}

object PluginManifestParser {
    private const val MaxManifestBytes = 32 * 1024
    private val pathSegment = Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,127}")

    /** Reject unsupported versions and capabilities instead of silently granting them. */
    fun parse(json: String): PluginManifest {
        require(json.toByteArray(Charsets.UTF_8).size <= MaxManifestBytes) { "manifest too large" }
        PluginJson.check(json, 32 * 1024)
        val value = JSONObject(json)
        listOf("id", "name", "version", "entry").forEach { require(value.opt(it) is String) { "manifest field must be a string: $it" } }
        val rawId = value.getString("id")
        val sourceId = SourceId.parse("plugin:$rawId")
            ?: throw IllegalArgumentException("invalid plugin id")
        val name = value.getString("name").trim()
        require(name.isNotEmpty() && value.getString("name").length <= 120) { "invalid plugin name" }
        val version = value.getString("version")
        require(version.length <= 80 && PluginVersion.valid(version)) { "invalid plugin version" }
        require(value.opt("apiVersion") is Number && value.getDouble("apiVersion") == 1.0) { "unsupported plugin API version" }
        val apiVersion = value.getInt("apiVersion")
        require(apiVersion == 1) { "unsupported plugin API version" }
        val entry = value.getString("entry")
        require(entry.length <= 512 && entry.endsWith(".js") &&
            entry.split('/').all { it != "." && it != ".." && pathSegment.matches(it) }
        ) { "invalid plugin entry path" }
        val rawCapabilities = value.getJSONArray("capabilities")
        require(rawCapabilities.length() <= PluginCapability.entries.size) { "too many capabilities" }
        val capabilities = buildSet {
            for (index in 0 until rawCapabilities.length()) {
                require(rawCapabilities.opt(index) is String) { "capability must be a string" }
                val name = rawCapabilities.getString(index)
                val capability = PluginCapability.entries.firstOrNull { it.wireName == name }
                    ?: throw IllegalArgumentException("unsupported capability")
                require(add(capability)) { "duplicate capability" }
            }
        }
        val domains = value.optJSONArray("networkDomains")
        require(!value.has("networkDomains") || domains != null) { "networkDomains must be an array" }
        require(domains == null || domains.length() <= 32) { "too many network domains" }
        val networkDomains = buildSet {
            if (domains != null) for (index in 0 until domains.length()) {
                require(domains.opt(index) is String) { "network domain must be a string" }
                val domain = domains.getString(index)
                val normalized = runCatching { IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES) }
                    .getOrElse { throw IllegalArgumentException("invalid network domain") }
                    .lowercase(Locale.ROOT)
                require(domain == normalized && normalized.length <= 253 && '.' in normalized &&
                    normalized.split('.').all { it.isNotEmpty() && it.length <= 63 } &&
                    normalized.any { it in 'a'..'z' }
                ) { "invalid network domain" }
                require(add(normalized)) { "duplicate network domain" }
            }
        }
        val authentication = if (value.has("authentication")) {
            require(PluginCapability.Authentication in capabilities) { "authentication capability is required" }
            val auth = value.getJSONObject("authentication")
            require(auth.opt("type") is String) { "authentication type must be a string" }
            when (auth.getString("type")) {
                "none" -> PluginAuthentication("none")
                "apiToken", "oauth2Pkce" -> {
                    val oauthMode = auth.getString("type") == "oauth2Pkce"
                    require(oauthMode || (auth.opt("header") is String && auth.opt("prefix") is String)) { "credential header and prefix must be strings" }
                    val header = if (oauthMode) "Authorization" else auth.getString("header")
                    val prefix = if (oauthMode) "Bearer " else auth.getString("prefix")
                    require(header.matches(Regex("[A-Za-z0-9-]{1,80}")) && header.lowercase(Locale.ROOT) !in setOf(
                        "host", "connection", "content-length", "transfer-encoding", "proxy-authorization", "proxy-connection", "upgrade", "cookie"
                    )) { "invalid credential header" }
                    require(prefix.length <= 80 && prefix.all { it.code in 32..126 }) { "invalid credential prefix" }
                    val requested = auth.getJSONArray("domains")
                    require(requested.length() in 1..32)
                    val domains = (0 until requested.length()).map { require(requested.opt(it) is String); requested.getString(it) }.toSet()
                    require(domains.size == requested.length() && domains.all { it in networkDomains }) { "credential domain permission denied" }
                    val oauth = if (oauthMode) {
                        require(listOf("clientSecret", "client_secret", "redirectUri", "grantType").none(auth::has)) { "unsupported OAuth option" }
                        fun endpoint(key: String): String {
                            require(auth.opt(key) is String)
                            val raw = auth.getString(key)
                            val url = java.net.URI(raw)
                            require(raw.length <= 2048 && url.scheme == "https" && url.port in setOf(-1, 443) &&
                                url.host in networkDomains && url.rawUserInfo == null && url.rawFragment == null && url.rawQuery == null) { "invalid OAuth endpoint" }
                            return raw
                        }
                        require(auth.opt("clientId") is String)
                        val client = auth.getString("clientId")
                        require(client.isNotBlank() && client.length <= 512 && client.all { it.code in 33..126 })
                        val scopes = auth.getJSONArray("scopes")
                        require(scopes.length() <= 32)
                        PluginOAuthConfiguration(endpoint("authorizationEndpoint"), endpoint("tokenEndpoint"), client,
                            (0 until scopes.length()).map { index ->
                                require(scopes.opt(index) is String)
                                scopes.getString(index).also { scope -> require(scope.length in 1..128 && scope.all { it.code in 33..126 && it != '"' && it != '\\' }) }
                            }.also { require(it.distinct().size == it.size) })
                    } else null
                    PluginAuthentication(if (oauthMode) "oauth2Pkce" else "apiToken", header, prefix, domains, oauth)
                }
                else -> error("unsupported authentication mode")
            }
        } else null
        require(PluginCapability.Authentication !in capabilities || authentication != null) { "authentication configuration is required" }
        val author = if (value.has("author")) {
            require(value.opt("author") is String)
            value.getString("author").also { require(it.isNotBlank() && it.length <= 120) }
        } else null
        val homepage = if (value.has("homepage")) {
            require(value.opt("homepage") is String)
            value.getString("homepage").also {
                val uri = java.net.URI(it)
                require(it.length <= 2048 && uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null)
            }
        } else null
        return PluginManifest(sourceId, name, version, apiVersion, entry, capabilities, networkDomains, authentication, author, homepage)
    }
}
