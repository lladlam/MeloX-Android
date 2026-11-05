package com.lladlam.melox.core.plugin

import org.junit.Assert.*
import org.junit.Test

class PluginCredentialPolicyTest {
    private val base = """{"id":"org.example.auth","name":"Auth","version":"1.0.0","apiVersion":1,"entry":"index.js","capabilities":["authentication"],"networkDomains":["api.example.com","public.example.com"],"authentication":{"type":"apiToken","header":"Authorization","prefix":"Bearer ","domains":["api.example.com"]}}"""

    @Test fun credentialRequestsCannotEscapeTheirSubsetOfNetworkPermissions() {
        val manifest = PluginManifestParser.parse(base)
        val lease = PluginAccountVault.Lease(manifest.sourceId, PluginAccountVault.Account("opaque", "Test"), "secret-not-logged", manifest.authentication)
        PluginHttpBroker(manifest, lease).use { broker ->
            assertThrows(IllegalArgumentException::class.java) {
                broker.request("""{"url":"https://public.example.com/","credential":"active"}""")
            }
            assertThrows(IllegalArgumentException::class.java) {
                broker.request("""{"url":"http://api.example.com/","credential":"active"}""")
            }
            assertThrows(IllegalArgumentException::class.java) {
                broker.request("""{"url":"https://api.example.com/","credential":"another-plugin"}""")
            }
        }
        assertFalse(lease.toString().contains("secret-not-logged"))
    }

    @Test fun missingAccountAndUnsupportedModesFailBeforeMakingRequests() {
        val manifest = PluginManifestParser.parse(base)
        PluginHttpBroker(manifest).use { broker ->
            assertThrows(IllegalStateException::class.java) {
                broker.request("""{"url":"https://api.example.com/","credential":"active"}""")
            }
        }
        assertThrows(IllegalStateException::class.java) { PluginManifestParser.parse(base.replace("apiToken", "webviewCookie")) }
        assertThrows(IllegalArgumentException::class.java) { PluginManifestParser.parse(base.replace("\"Authorization\"", "\"Host\"")) }
    }

    @Test fun updateCannotReuseAnAccountWithExpandedCredentialPermissions() {
        val oldManifest = PluginManifestParser.parse(base)
        val updated = oldManifest.copy(authentication = oldManifest.authentication!!.copy(domains = oldManifest.networkDomains))
        val lease = PluginAccountVault.Lease(oldManifest.sourceId, PluginAccountVault.Account("opaque", "Test"), "secret", oldManifest.authentication)
        PluginHttpBroker(updated, lease).use { broker ->
            assertThrows(IllegalArgumentException::class.java) {
                broker.request("""{"url":"https://public.example.com/","credential":"active"}""")
            }
        }
    }

    @Test fun identicalAuthPoliciesDoNotAllowCrossPluginCredentialLeases() {
        val first = PluginManifestParser.parse(base)
        val second = PluginManifestParser.parse(base.replace("org.example.auth", "org.example.other"))
        val lease = PluginAccountVault.Lease(first.sourceId, PluginAccountVault.Account("opaque", "Test"), "synthetic", first.authentication)
        assertThrows(IllegalArgumentException::class.java) { PluginHttpBroker(second, lease) }
    }
}
