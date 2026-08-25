package com.lladlam.melox.core.remoteconfig

import org.json.JSONObject

data class MeloXRemoteFallbackConfig(
    val enabled: Boolean,
    val order: List<String>,
    val disabledProviders: Set<String>,
    val timeoutMs: Int,
)

data class MeloXRemoteNotice(
    val id: String,
    val level: String,
    val title: String,
    val message: String,
    val frequency: String,
)

data class MeloXRemoteConfig(
    val schemaVersion: Int,
    val configVersion: Int,
    val issuedAtEpochSeconds: Long,
    val minVersionCode: Int,
    val maxVersionCode: Int,
    val disabledCapabilities: Set<String>,
    val strategies: Map<String, String>,
    val fallback: MeloXRemoteFallbackConfig,
    val notice: MeloXRemoteNotice?,
) {
    fun appliesTo(versionCode: Int): Boolean = versionCode in minVersionCode..maxVersionCode

    companion object {
        fun parse(payload: ByteArray): MeloXRemoteConfig {
            val root = JSONObject(payload.toString(Charsets.UTF_8))
            require(root.getInt("schemaVersion") == 1) { "Unsupported remote config schema" }
            val configVersion = root.getInt("configVersion")
            require(configVersion > 0) { "Invalid remote config version" }
            val minVersionCode = root.getInt("minVersionCode")
            val maxVersionCode = root.getInt("maxVersionCode")
            require(minVersionCode > 0 && maxVersionCode >= minVersionCode) { "Invalid app version range" }

            val disabledCapabilities = root.getJSONArray("disabledCapabilities").strings()
                .filterTo(linkedSetOf()) { it in MeloXRemoteConfigDefaults.AllowedCapabilities }
            val strategyJson = root.getJSONObject("strategies")
            val strategies = MeloXRemoteConfigDefaults.Strategies.mapValues { (name, default) ->
                strategyJson.optString(name).takeIf { it == "v1" } ?: default
            }
            val fallbackJson = root.getJSONObject("crossProviderFallback")
            val order = fallbackJson.getJSONArray("order").strings()
                .filter { it in MeloXRemoteConfigDefaults.AllowedProviders }
                .distinct()
                .takeIf { it.size == MeloXRemoteConfigDefaults.AllowedProviders.size }
                ?: MeloXRemoteConfigDefaults.FallbackOrder
            val disabledProviders = fallbackJson.getJSONArray("disabledProviders").strings()
                .filterTo(linkedSetOf()) { it in MeloXRemoteConfigDefaults.AllowedProviders }
            val timeoutMs = fallbackJson.getInt("timeoutMs").coerceIn(3_000, 10_000)
            val noticeJson = root.optJSONObject("notice")
            val notice = noticeJson?.let {
                MeloXRemoteNotice(
                    id = it.getString("id").take(80),
                    level = it.getString("level").takeIf { value -> value in setOf("info", "warning", "outage") }
                        ?: "info",
                    title = it.getString("title").take(60),
                    message = it.getString("message").take(300),
                    frequency = it.getString("frequency").takeIf { value -> value in setOf("once", "daily") }
                        ?: "once",
                ).takeIf { value -> value.id.isNotBlank() && value.title.isNotBlank() && value.message.isNotBlank() }
            }
            return MeloXRemoteConfig(
                schemaVersion = 1,
                configVersion = configVersion,
                issuedAtEpochSeconds = root.getLong("issuedAtEpochSeconds"),
                minVersionCode = minVersionCode,
                maxVersionCode = maxVersionCode,
                disabledCapabilities = disabledCapabilities,
                strategies = strategies,
                fallback = MeloXRemoteFallbackConfig(
                    enabled = fallbackJson.getBoolean("enabled"),
                    order = order,
                    disabledProviders = disabledProviders,
                    timeoutMs = timeoutMs,
                ),
                notice = notice,
            )
        }
    }
}

enum class MeloXRemoteConfigSource { BuiltIn, VerifiedRemote, VersionInapplicable }

data class MeloXRemoteConfigStatus(
    val source: MeloXRemoteConfigSource = MeloXRemoteConfigSource.BuiltIn,
    val config: MeloXRemoteConfig = MeloXRemoteConfigDefaults.Config,
    val keyId: String? = null,
    val lastCheckedAtEpochMs: Long = 0L,
    val lastUpdatedAtEpochMs: Long = 0L,
    val error: String? = null,
    val refreshing: Boolean = false,
)

private fun org.json.JSONArray.strings(): List<String> = buildList {
    for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
}
