package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.SourceId
import org.junit.Assert.*
import org.junit.Test

class PluginHardeningPolicyTest {
    private val manifest = PluginManifest(SourceId.parse("plugin:org.example.secure")!!, "Secure", "1.0.0", 1,
        "index.js", setOf(PluginCapability.Search), setOf("images.example.com"))

    @Test fun ipcRejectsStaleTokensUnboundedCallsAndUndeclaredMethods() {
        assertTrue(PluginIpcPolicy.validToken("unique-invocation", "unique-invocation"))
        assertFalse(PluginIpcPolicy.validToken("unique-invocation", "previous-invocation"))
        assertFalse(PluginIpcPolicy.validToken("unique-invocation", null))
        assertTrue(PluginIpcPolicy.validHostCall(32, 32, 48 * 1024))
        assertFalse(PluginIpcPolicy.validHostCall(33, 32, 1))
        assertFalse(PluginIpcPolicy.validHostCall(1, 33, 1))
        assertFalse(PluginIpcPolicy.validHostCall(1, 1, 48 * 1024 + 1))
        assertTrue(PluginIpcPolicy.methodAllowed(manifest, "search"))
        assertFalse(PluginIpcPolicy.methodAllowed(manifest, "resolvePlayback"))
        assertFalse(PluginIpcPolicy.methodAllowed(manifest, "__proto__"))
    }

    @Test fun ipcErrorsNeverForwardArbitraryScriptTextOrCredentials() {
        assertEquals("LOGIN_REQUIRED", PluginIpcPolicy.errorCode("""{"code":"LOGIN_REQUIRED","message":"secret-token"}"""))
        for (raw in listOf("secret-token", """{"code":"Bearer secret-token"}""", "x".repeat(2048))) {
            assertEquals("PLUGIN_EXECUTION_FAILED", PluginIpcPolicy.errorCode(raw))
        }
    }

    @Test fun artworkRetainsProvenanceAndNeverAcceptsLocalOrUndeclaredUrls() {
        val upstream = "https://images.example.com/cover.png?version=a%2Fb"
        val wrapped = PluginArtwork.wrap(manifest, upstream)!!
        val restored = PluginArtwork.parse(wrapped)
        assertEquals(manifest.sourceId, restored.source)
        assertEquals(upstream, restored.url)
        for (url in listOf("file:///data/user/0/private", "content://private/image", "http://images.example.com/a", "https://other.example.com/a", "https://user@images.example.com/a")) {
            assertThrows(IllegalArgumentException::class.java) { PluginArtwork.wrap(manifest, url) }
        }
        assertThrows(IllegalArgumentException::class.java) { PluginArtwork.parse(wrapped + "&source=netease") }
        assertThrows(IllegalArgumentException::class.java) { PluginArtwork.parse(wrapped.replace("plugin%3Aorg.example.secure", "netease")) }
    }

    @Test fun permissionExpansionIncludesAuthenticationAndCapabilitiesButNotDescriptions() {
        assertFalse(PluginPermissions.expands(manifest, manifest.copy(version = "2.0.0", author = "Author")))
        assertTrue(PluginPermissions.expands(manifest, manifest.copy(networkDomains = manifest.networkDomains + "cdn.example.com")))
        assertTrue(PluginPermissions.expands(manifest, manifest.copy(capabilities = manifest.capabilities + PluginCapability.Download)))
        assertTrue(PluginPermissions.expands(manifest, manifest.copy(authentication = PluginAuthentication("none"))))
    }

    @Test fun oauthCallbackRequiresExactTargetUniqueStateAndExclusiveCodeOrError() {
        val state = "a".repeat(43)
        val valid = "com.lladlam.melox.plugin:/oauth2redirect?state=$state&code=authorized-code"
        assertEquals("authorized-code", PluginOAuthCallback.parse(valid).code)
        assertTrue(PluginOAuthCallback.parse("com.lladlam.melox.plugin:/oauth2redirect?state=$state&error=access_denied").declined)
        for (invalid in listOf(valid.replace(":/oauth2redirect", "://attacker/oauth2redirect"), valid + "&state=$state",
            valid + "&code=another", valid + "#fragment", valid.replace("/oauth2redirect", "/other"),
            valid + "&error=access_denied", valid.replace("authorized-code", "%0d%0a"))) {
            assertThrows(IllegalArgumentException::class.java) { PluginOAuthCallback.parse(invalid) }
        }
    }
}
