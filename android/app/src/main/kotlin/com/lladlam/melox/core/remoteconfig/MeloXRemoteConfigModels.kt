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

/**
 * A signed, sandboxed behaviour hook. The source must define `run(input)` and
 * return a JSON-serialisable value; see [com.lladlam.melox.core.remoteconfig.MeloXHotScripts].
 */
/**
 * Data-driven matching/search rules. Everything is bounded and validated at
 * parse time so a bad payload can only fall back to the built-in behaviour.
 */
data class MeloXRemoteRules(
    val lyricMatchOriginalToleranceMs: Long,
    val lyricMatchVersionToleranceMs: Long,
    val searchTitleStripSuffixes: List<String>,
)

/**
 * One incremental-update patch: rebuilding the APK published as [toVersion] from
 * the currently installed one (identified by [fromApkSha256]) must yield exactly
 * [apkSha256]. Every field is verified before the file is handed to the installer.
 */
data class MeloXRemoteUpdatePatch(
    val fromApkSha256: String,
    val toVersion: String,
    val apkSha256: String,
    val patchUrl: String,
    val patchSha256: String,
    val patchBytes: Long,
)

/** Cloud-controlled gate for forced security releases. The release tag must end in `-security`. */
data class MeloXRemoteSecurityUpdate(
    val enabled: Boolean,
    val message: String?,
)

data class MeloXRemoteScript(
    val name: String,
    val revision: Int,
    val source: String,
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
    /**
     * Signed UI text overrides, `localeTag -> (key -> text)`. Only applied while
     * the config is a verified remote one, so the payload signature is the only
     * authority for what text a build will show.
     */
    val textOverrides: Map<String, Map<String, String>>,
    val scripts: Map<String, MeloXRemoteScript>,
    /** ARGB theme colours, keyed `<role>` or `<light|dark>.<role>`. */
    val colorOverrides: Map<String, Int>,
    val rules: MeloXRemoteRules,
    val updatePatches: List<MeloXRemoteUpdatePatch>,
    val securityUpdate: MeloXRemoteSecurityUpdate,
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
                val allowed = MeloXRemoteConfigDefaults.AllowedStrategyValues[name].orEmpty()
                strategyJson.optString(name).takeIf { it in allowed } ?: default
            }
            val fallbackJson = root.getJSONObject("crossProviderFallback")
            // A control plane may only know a subset of the providers supported by
            // an older/newer client. Keep the valid configured prefix and append
            // every missing provider in the safe default order instead of throwing
            // the whole custom order away.
            val configuredOrder = fallbackJson.getJSONArray("order").strings()
                .filter { it in MeloXRemoteConfigDefaults.AllowedProviders }
                .distinct()
            val order = if (configuredOrder.isEmpty()) {
                MeloXRemoteConfigDefaults.FallbackOrder
            } else {
                configuredOrder + MeloXRemoteConfigDefaults.FallbackOrder
                    .filterNot { it in configuredOrder }
            }
            val disabledProviders = fallbackJson.getJSONArray("disabledProviders").strings()
                .filterTo(linkedSetOf()) { it in MeloXRemoteConfigDefaults.AllowedProviders }
            val timeoutMs = fallbackJson.getInt("timeoutMs").coerceIn(3_000, 10_000)
            val securityJson = root.optJSONObject("securityUpdate")
            val securityUpdate = MeloXRemoteSecurityUpdate(
                enabled = securityJson?.optBoolean("enabled", false) == true,
                message = securityJson?.optString("message")
                    ?.trim()
                    ?.take(300)
                    ?.takeIf(String::isNotBlank),
            )
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
                textOverrides = parseTextOverrides(root.optJSONObject("textOverrides")),
                scripts = parseScripts(root.optJSONObject("scripts")),
                colorOverrides = parseColorOverrides(root.optJSONObject("colorOverrides")),
                rules = parseRules(root.optJSONObject("rules")),
                updatePatches = parseUpdatePatches(root.optJSONArray("updatePatches")),
                securityUpdate = securityUpdate,
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

/**
 * Parses the signed `textOverrides` block defensively.
 *
 * The block is only allowed to replace *display text*, so it is bounded hard:
 * at most [MaxTextOverrideEntries] keys across locales, each locale tag, key and
 * value length-capped. Anything oversized is dropped rather than trusted, and a
 * malformed block simply yields no overrides instead of failing the config.
 */
internal fun parseTextOverrides(json: JSONObject?): Map<String, Map<String, String>> {
    if (json == null) return emptyMap()
    val result = LinkedHashMap<String, Map<String, String>>()
    var remaining = MaxTextOverrideEntries
    for (locale in json.keys()) {
        if (remaining <= 0) break
        val tag = locale.trim().take(MaxTextOverrideLocaleLength)
        if (tag.isBlank()) continue
        val entries = json.optJSONObject(locale) ?: continue
        val parsed = LinkedHashMap<String, String>()
        for (key in entries.keys()) {
            if (remaining <= 0) break
            val cleanKey = key.trim()
            if (cleanKey.isBlank() || cleanKey.length > MaxTextOverrideKeyLength) continue
            val value = entries.optString(key)
            if (value.isBlank() || value.length > MaxTextOverrideValueLength) continue
            parsed[cleanKey] = value
            remaining--
        }
        if (parsed.isNotEmpty()) result[tag] = parsed
    }
    return result
}

internal const val MaxTextOverrideEntries = 256
internal const val MaxTextOverrideLocaleLength = 24
internal const val MaxTextOverrideKeyLength = 120
internal const val MaxTextOverrideValueLength = 400

/**
 * Parses the signed `scripts` block defensively.
 *
 * Scripts run in a memory-limited JS sandbox, but the config still only gets a
 * fixed budget: a handful of hooks, each length-capped, with a hard total so a
 * compromised or oversized payload cannot turn into a large download.
 */
internal fun parseScripts(json: JSONObject?): Map<String, MeloXRemoteScript> {
    if (json == null) return emptyMap()
    val result = LinkedHashMap<String, MeloXRemoteScript>()
    var total = 0
    for (name in json.keys()) {
        if (result.size >= MaxHotScripts) break
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > MaxHotScriptNameLength) continue
        val entry = json.optJSONObject(name) ?: continue
        val source = entry.optString("source")
        if (source.isBlank() || source.length > MaxHotScriptSourceLength) continue
        if (total + source.length > MaxHotScriptTotalLength) break
        total += source.length
        result[cleanName] = MeloXRemoteScript(
            name = cleanName,
            revision = entry.optInt("revision", 1).coerceAtLeast(1),
            source = source,
        )
    }
    return result
}

/**
 * Parses the signed `colorOverrides` block.
 *
 * Only the Material roles the app actually consumes are accepted, values must be
 * `#RRGGBB` / `#AARRGGBB`, and the whole block is length-capped. An invalid entry
 * is dropped rather than failing the config, so a typo can never blank the UI.
 */
internal fun parseColorOverrides(json: JSONObject?): Map<String, Int> {
    if (json == null) return emptyMap()
    val result = LinkedHashMap<String, Int>()
    for (key in json.keys()) {
        if (result.size >= MaxColorOverrides) break
        val cleanKey = key.trim()
        if (cleanKey.length > MaxColorOverrideKeyLength) continue
        val role = cleanKey.substringAfterLast('.')
        if (role !in AllowedColorRoles) continue
        val prefix = cleanKey.substringBeforeLast('.', "")
        if (prefix.isNotEmpty() && prefix != "light" && prefix != "dark") continue
        val parsed = parseHexColor(json.optString(key)) ?: continue
        result[cleanKey] = parsed
    }
    return result
}

/** `#RRGGBB`, `#AARRGGBB`, or the same without the leading `#`. */
internal fun parseHexColor(value: String): Int? {
    val text = value.trim().removePrefix("#")
    if (text.length != 6 && text.length != 8) return null
    if (!text.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    val body = text.toLong(16)
    return if (text.length == 6) (0xFF000000L or body).toInt() else body.toInt()
}

internal val AllowedColorRoles = setOf(
    "primary",
    "onPrimary",
    "primaryContainer",
    "onPrimaryContainer",
    "secondary",
    "tertiary",
    "error",
    "background",
    "onBackground",
    "surface",
    "onSurface",
    "surfaceVariant",
    "onSurfaceVariant",
    "outline",
)

/**
 * Parses the signed `rules` block: matching tolerances and safe literal title
 * suffixes to strip before a provider search. Deliberately no regex from the
 * remote side — that is an unbounded CPU surface with no timeout.
 */
internal fun parseRules(json: JSONObject?): MeloXRemoteRules {
    val defaults = MeloXRemoteConfigDefaults.Rules
    if (json == null) return defaults
    val lyric = json.optJSONObject("lyricMatch")
    val suffixes = json.optJSONArray("searchTitleStripSuffixes")?.let { array ->
        buildList {
            for (index in 0 until array.length()) {
                if (size >= MaxTitleStripSuffixes) break
                val value = array.optString(index).trim()
                if (value.isNotEmpty() && value.length <= MaxTitleStripSuffixLength) add(value)
            }
        }.distinct()
    } ?: defaults.searchTitleStripSuffixes
    return MeloXRemoteRules(
        lyricMatchOriginalToleranceMs = lyric
            ?.optLong("originalToleranceMs", defaults.lyricMatchOriginalToleranceMs)
            ?.coerceIn(0L, MaxLyricMatchToleranceMs) ?: defaults.lyricMatchOriginalToleranceMs,
        lyricMatchVersionToleranceMs = lyric
            ?.optLong("versionToleranceMs", defaults.lyricMatchVersionToleranceMs)
            ?.coerceIn(0L, MaxLyricMatchToleranceMs) ?: defaults.lyricMatchVersionToleranceMs,
        searchTitleStripSuffixes = suffixes,
    )
}

/** Removes configured literal suffixes from the end of a search keyword. */
internal fun stripTitleSuffixes(title: String, suffixes: List<String>): String {
    var result = title.trim()
    var changed = true
    while (changed && result.isNotEmpty()) {
        changed = false
        for (suffix in suffixes) {
            if (suffix.isNotEmpty() && result.endsWith(suffix, ignoreCase = true) &&
                result.length > suffix.length
            ) {
                result = result.dropLast(suffix.length).trim()
                changed = true
            }
        }
    }
    return result.ifEmpty { title.trim() }
}

/**
 * Parses the signed `updatePatches` list.
 *
 * The patch URL must live under the project's own GitHub release path — the same
 * allow-list the full APK download uses — and every hash is length-checked, so a
 * payload can never point the updater at a foreign host or a truncated hash.
 */
internal fun parseUpdatePatches(json: org.json.JSONArray?): List<MeloXRemoteUpdatePatch> {
    if (json == null) return emptyList()
    val patches = ArrayList<MeloXRemoteUpdatePatch>()
    for (index in 0 until json.length()) {
        if (patches.size >= MaxUpdatePatches) break
        val entry = json.optJSONObject(index) ?: continue
        val from = entry.optString("fromApkSha256").lowercase()
        val apkSha = entry.optString("apkSha256").lowercase()
        val patchSha = entry.optString("patchSha256").lowercase()
        val toVersion = entry.optString("toVersion").trim()
        val url = entry.optString("patchUrl")
        val bytes = entry.optLong("patchBytes", -1L)
        if (!isSha256(from) || !isSha256(apkSha) || !isSha256(patchSha)) continue
        if (toVersion.isEmpty() || toVersion.length > 32) continue
        if (!url.startsWith(MeloXRemoteConfigDefaults.PinnedReleasePrefix)) continue
        if (bytes <= 0L || bytes > MaxUpdatePatchBytes) continue
        patches += MeloXRemoteUpdatePatch(from, toVersion, apkSha, url, patchSha, bytes)
    }
    return patches
}

private fun isSha256(value: String): Boolean =
    value.length == 64 && value.all { it.isDigit() || it in 'a'..'f' }

internal const val MaxUpdatePatches = 4
internal const val MaxUpdatePatchBytes = 64L * 1024 * 1024

internal const val MaxTitleStripSuffixes = 16
internal const val MaxTitleStripSuffixLength = 32
internal const val MaxLyricMatchToleranceMs = 10_000L

internal const val MaxColorOverrides = 48
internal const val MaxColorOverrideKeyLength = 48

internal const val MaxHotScripts = 8
internal const val MaxHotScriptNameLength = 64
internal const val MaxHotScriptSourceLength = 16_384
internal const val MaxHotScriptTotalLength = 32_768
