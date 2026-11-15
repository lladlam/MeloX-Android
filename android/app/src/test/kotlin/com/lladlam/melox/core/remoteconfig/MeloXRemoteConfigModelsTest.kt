package com.lladlam.melox.core.remoteconfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXRemoteConfigModelsTest {
    @Test
    fun unknownOptionalValuesFallBackWithoutExpandingRemoteAuthority() {
        val config = MeloXRemoteConfig.parse(
            """
            {
              "schemaVersion": 1,
              "configVersion": 8,
              "issuedAtEpochSeconds": 1,
              "minVersionCode": 10,
              "maxVersionCode": 20,
              "disabledCapabilities": ["qq_playback", "unknown_capability"],
              "strategies": {
                "netease_playback": "unknown",
                "qq_playback": "v2",
                "kugou_playback": "v1"
              },
              "crossProviderFallback": {
                "enabled": true,
                "order": ["unknown", "qq_music"],
                "disabledProviders": ["bilibili", "unknown"],
                "timeoutMs": 99999
              },
              "notice": null
            }
            """.trimIndent().toByteArray(),
        )

        assertEquals(setOf("qq_playback"), config.disabledCapabilities)
        assertEquals("v1", config.strategies["netease_playback"])
        assertEquals("v1", config.strategies["qq_playback"])
        assertEquals(
            listOf(
                "qq_music",
                "bilibili",
                "youtube_music",
                "netease",
                "kugou_lite",
                "qishui",
                "kuwo",
                "jellyfin",
            ),
            config.fallback.order,
        )
        assertEquals(setOf("bilibili"), config.fallback.disabledProviders)
        assertEquals(10_000, config.fallback.timeoutMs)
        assertTrue(config.appliesTo(11))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSchema() {
        MeloXRemoteConfig.parse(
            """{"schemaVersion":2,"configVersion":1}""".toByteArray(),
        )
    }

    @Test
    fun textOverridesAreParsedPerLocale() {
        val config = MeloXRemoteConfig.parse(
            """
            {
              "schemaVersion": 1,
              "configVersion": 9,
              "issuedAtEpochSeconds": 1,
              "minVersionCode": 1,
              "maxVersionCode": 100,
              "disabledCapabilities": [],
              "strategies": {},
              "crossProviderFallback": {
                "enabled": true,
                "order": ["qq_music", "kugou", "bilibili"],
                "disabledProviders": [],
                "timeoutMs": 6000
              },
              "textOverrides": {
                "zh-CN": { "about.cat_title": "新的标题" },
                "default": { "about.cat_body": "fallback body" }
              }
            }
            """.trimIndent().toByteArray(),
        )

        assertEquals("新的标题", config.textOverrides["zh-CN"]?.get("about.cat_title"))
        assertEquals("fallback body", config.textOverrides["default"]?.get("about.cat_body"))
    }

    @Test
    fun oversizedTextOverridesAreDropped() {
        val longValue = "x".repeat(MaxTextOverrideValueLength + 1)
        val longKey = "k".repeat(MaxTextOverrideKeyLength + 1)
        val parsed = parseTextOverrides(
            org.json.JSONObject(
                """
                {
                  "zh-CN": {
                    "ok": "fine",
                    "longkey$longKey": "v",
                    "toobig": "$longValue"
                  }
                }
                """.trimIndent(),
            ),
        )
        val locale = parsed["zh-CN"].orEmpty()
        assertEquals(setOf("ok"), locale.keys)
    }

    @Test
    fun missingTextOverridesYieldEmptyMap() {
        assertTrue(parseTextOverrides(null).isEmpty())
    }

    @Test
    fun colorOverridesAcceptHexAndDropUnknownRoles() {
        val parsed = parseColorOverrides(
            org.json.JSONObject(
                """
                {
                  "primary": "#FF8800",
                  "dark.background": "#0B0B0D",
                  "light.primary": "#007AFF",
                  "unknownRole": "#FFFFFF",
                  "bad": "not-a-colour",
                  "short": "#12345"
                }
                """.trimIndent(),
            ),
        )

        assertEquals(setOf("primary", "dark.background", "light.primary"), parsed.keys)
        assertEquals(0xFFFF8800.toInt(), parsed.getValue("primary"))
        assertEquals(0xFF0B0B0D.toInt(), parsed.getValue("dark.background"))
        org.junit.Assert.assertNull(parseHexColor("not-a-colour"))
        org.junit.Assert.assertNull(parseHexColor("#12345"))
        org.junit.Assert.assertEquals(0x80123456.toInt(), parseHexColor("#80123456"))
    }

    @Test
    fun rulesParseTolerancesAndTitleSuffixes() {
        val tooLong = "y".repeat(MaxTitleStripSuffixLength + 1)
        val rules = parseRules(
            org.json.JSONObject(
                """
                {
                  "lyricMatch": { "originalToleranceMs": 99999, "versionToleranceMs": 500 },
                  "searchTitleStripSuffixes": ["(Live)", " - Live", "", "$tooLong"]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(MaxLyricMatchToleranceMs, rules.lyricMatchOriginalToleranceMs)
        assertEquals(500L, rules.lyricMatchVersionToleranceMs)
        // Suffixes are trimmed at parse time; the matcher keeps working because
        // the stripped title is trimmed again afterwards.
        assertEquals(listOf("(Live)", "- Live"), rules.searchTitleStripSuffixes)
    }

    @Test
    fun titleSuffixStrippingIsRepeatedAndNeverEmptiesTheTitle() {
        val suffixes = listOf("(Live)", " - Live")
        assertEquals("夜曲", stripTitleSuffixes("夜曲(Live) - Live", suffixes))
        assertEquals("夜曲", stripTitleSuffixes("夜曲(LIVE)", suffixes))
        assertEquals("(Live)", stripTitleSuffixes("(Live)", suffixes))
        assertEquals("夜曲", stripTitleSuffixes("夜曲", suffixes))
    }

    @Test
    fun compatStrategyIsAcceptedAndUnknownFallsBack() {
        val compat = MeloXRemoteConfig.parse(
            defaultPayload().replace("\"netease_playback\": \"v1\"", "\"netease_playback\": \"compat\"").toByteArray(),
        )
        assertEquals("compat", compat.strategies["netease_playback"])

        val bogus = MeloXRemoteConfig.parse(
            defaultPayload().replace("\"netease_playback\": \"v1\"", "\"netease_playback\": \"v9\"").toByteArray(),
        )
        assertEquals("v1", bogus.strategies["netease_playback"])
    }


    @Test
    fun securityUpdateGateIsParsedAndDefaultsOff() {
        val enabled = MeloXRemoteConfig.parse(
            org.json.JSONObject(defaultPayload())
                .put("securityUpdate", org.json.JSONObject().put("enabled", true).put("message", "critical"))
                .toString().toByteArray(),
        )
        assertTrue(enabled.securityUpdate.enabled)
        assertEquals("critical", enabled.securityUpdate.message)

        val missing = MeloXRemoteConfig.parse(defaultPayload().toByteArray())
        org.junit.Assert.assertFalse(missing.securityUpdate.enabled)
    }

    private fun defaultPayload(): String = """
        {
          "schemaVersion": 1,
          "configVersion": 1,
          "issuedAtEpochSeconds": 1,
          "minVersionCode": 1,
          "maxVersionCode": 100,
          "disabledCapabilities": [],
          "strategies": { "netease_playback": "v1", "qq_playback": "v1", "kugou_playback": "v1" },
          "crossProviderFallback": {
            "enabled": true,
            "order": ["qq_music", "kugou", "bilibili"],
            "disabledProviders": [],
            "timeoutMs": 6000
          }
        }
    """.trimIndent()

    @Test
    fun updatePatchesRequirePinnedHostAndValidHashes() {
        val good = "a".repeat(64)
        val patch = parseUpdatePatches(
            org.json.JSONArray(
                """
                [
                  {
                    "fromApkSha256": "$good",
                    "toVersion": "0.6.6",
                    "apkSha256": "$good",
                    "patchUrl": "https://github.com/lladlam/MeloX-Android/releases/download/0.6.6/MeloX-0.6.5-0.6.6.patch",
                    "patchSha256": "$good",
                    "patchBytes": 123456
                  },
                  {
                    "fromApkSha256": "$good",
                    "toVersion": "0.6.7",
                    "apkSha256": "$good",
                    "patchUrl": "https://evil.example/0.6.7.patch",
                    "patchSha256": "$good",
                    "patchBytes": 123456
                  }
                ]
                """.trimIndent(),
            ),
        )

        assertEquals(1, patch.size)
        assertEquals("0.6.6", patch.first().toVersion)
    }

    @Test
    fun scriptsAreParsedWithRevisionAndSource() {
        val scripts = parseScripts(
            org.json.JSONObject(
                """
                {
                  "search.keywords": {
                    "revision": 3,
                    "source": "function run(input){ return input; }"
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals(setOf("search.keywords"), scripts.keys)
        assertEquals(3, scripts.getValue("search.keywords").revision)
        assertTrue(scripts.getValue("search.keywords").source.contains("function run"))
    }

    @Test
    fun oversizedAndExcessScriptsAreDropped() {
        val huge = "s".repeat(MaxHotScriptSourceLength + 1)
        val text = buildString {
            append("{")
            append("\"too.long\": {\"source\": \"").append(huge).append("\"},")
            repeat(MaxHotScripts + 2) { index ->
                append("\"hook.$index\": {\"source\": \"function run(i){ return i; }\"}")
                if (index <= MaxHotScripts) append(",")
            }
            append("}")
        }
        val scripts = parseScripts(org.json.JSONObject(text))

        assertTrue(scripts.size <= MaxHotScripts)
        assertTrue("too.long" !in scripts)
    }
}
