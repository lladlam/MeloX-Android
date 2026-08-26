package com.lladlam.melox.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class MeloXGitHubRoutingTest {
    private val original = MeloXGitHubRouting.UpdateManifestUrl

    @Test
    fun githubApiCanUseConfiguredRoutes() {
        val api = "https://api.github.com/repos/lladlam/MeloX-Android/releases"
        assertEquals(api, MeloXGitHubRouting.routedUrlFor(MeloXGitHubSource.GitHubDoh, api))
        assertEquals("https://ghfast.top/$api", MeloXGitHubRouting.routedUrlFor(MeloXGitHubSource.GhFast, api))
    }

    @Test
    fun concreteSourcesUseExpectedTrustedRoutes() {
        assertEquals(original, MeloXGitHubRouting.routedUrlFor(MeloXGitHubSource.GitHubDoh, original))
        assertEquals("https://ghfast.top/$original", MeloXGitHubRouting.routedUrlFor(MeloXGitHubSource.GhFast, original))
        assertEquals("https://ghproxy.net/$original", MeloXGitHubRouting.routedUrlFor(MeloXGitHubSource.GhProxy, original))
        assertEquals("https://gh-proxy.org/$original", MeloXGitHubRouting.routedUrlFor(MeloXGitHubSource.GhProxyOrg, original))
    }

    @Test
    fun oldJsDelivrSelectionMigratesToAuto() {
        assertEquals(MeloXGitHubSource.Auto, MeloXGitHubRouting.parseSource(null, "JsDelivr"))
        assertEquals(MeloXGitHubSource.GitHubDoh, MeloXGitHubRouting.parseSource(null, "GitHub"))
    }
}
