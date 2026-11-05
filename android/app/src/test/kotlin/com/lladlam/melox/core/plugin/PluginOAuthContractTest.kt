package com.lladlam.melox.core.plugin

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PluginOAuthContractTest {
    @Test fun pkceMatchesRfc7636S256Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            PluginOAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun tokenParserRequiresBearerAndKeepsRefreshAndExpiryHostOnly() {
        val result = PluginOAuth.parseTokens(JSONObject("""{"access_token":"access","refresh_token":"refresh","token_type":"Bearer","expires_in":3600}"""), 1000)
        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
        assertEquals(3_601_000L, result.expiresAt)
        assertFalse(result.toString().contains("access"))
        assertThrows(IllegalArgumentException::class.java) {
            PluginOAuth.parseTokens(JSONObject("""{"access_token":"access","token_type":"MAC"}"""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PluginOAuth.parseTokens(JSONObject("""{"access_token":"access","token_type":"Bearer","expires_in":-1}"""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PluginOAuth.parseTokens(JSONObject("""{"access_token":"a\r\nb","token_type":"Bearer"}"""))
        }
    }

    @Test fun oauthManifestOnlyAllowsExplicitPublicClientHttpsEndpoints() {
        val manifest = """{"id":"org.example.oauth","name":"OAuth","version":"1.0.0","apiVersion":1,"entry":"index.js","capabilities":["authentication"],"networkDomains":["auth.example.com","api.example.com"],"authentication":{"type":"oauth2Pkce","authorizationEndpoint":"https://auth.example.com/authorize","tokenEndpoint":"https://auth.example.com/token","clientId":"public-client","scopes":["music.read"],"domains":["api.example.com"]}}"""
        val parsed = PluginManifestParser.parse(manifest)
        assertEquals("public-client", parsed.authentication?.oauth?.clientId)
        assertEquals(setOf("api.example.com"), parsed.authentication?.domains)
        for (invalid in listOf(manifest.replace("https://", "http://"), manifest.replace("auth.example.com/token", "other.example.com/token"),
            manifest.replace("/token\"", "/token?client_secret=secret\""), manifest.replace("music.read", "invalid scope"))) {
            assertThrows(IllegalArgumentException::class.java) { PluginManifestParser.parse(invalid) }
        }
    }
}
