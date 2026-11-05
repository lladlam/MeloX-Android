package com.lladlam.melox.core.plugin

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

/** Uses the deployed synthetic HTTPS server through the unchanged production broker. */
internal object PluginRcNetworkFaultChecks {
    fun run(originText: String, evidence: MutableList<String>) {
        val origin = originText.toHttpUrl()
        require(origin.scheme == "https" && origin.port == 443 && origin.encodedPath == "/" && origin.query == null && origin.fragment == null && origin.username.isEmpty() && origin.password.isEmpty())
        val base = origin.toString().removeSuffix("/")
        val manifest = PluginManifestParser.parse(JSONObject().put("id", "org.melox.rc.network-faults").put("name", "Network faults")
            .put("version", "1.0.0").put("apiVersion", 1).put("entry", "index.js")
            .put("capabilities", org.json.JSONArray()).put("networkDomains", org.json.JSONArray().put(origin.host)).toString())
        // Health must pass first: a TLS/DNS/deployment outage is not a successful policy rejection.
        PluginHttpBroker(manifest).use { broker ->
            val response = JSONObject(broker.request(JSONObject().put("url", "$base/health").toString()))
            check(response.getInt("status") == 200 && JSONObject(response.getString("body")).getString("fixture") == "melox-oauth-v1")
        }
        for (endpoint in listOf("redirect-outside", "redirect-loop", "large", "gzip-large")) {
            PluginHttpBroker(manifest).use { broker ->
                val error = runCatching { broker.request(JSONObject().put("url", "$base/fault/$endpoint").toString()) }.exceptionOrNull()
                check(error is IllegalArgumentException) { "Expected an explicit broker policy failure at $endpoint, got ${error?.javaClass?.simpleName}" }
            }
            evidence += "PASS live HTTPS broker policy: $endpoint rejected"
        }
        PluginHttpBroker(manifest).use { broker ->
            val error = runCatching {
                broker.mediaClient().newCall(Request.Builder().url("$base/fault/adaptive").build()).execute().use { error("adaptive response accepted") }
            }.exceptionOrNull()
            check(error is java.io.IOException && error.message == "UNSUPPORTED_MEDIA_FORMAT")
        }
        evidence += "PASS live media adaptive MIME rejected before decode"
        PluginHttpBroker(manifest).use { broker ->
            broker.mediaClient().newCall(Request.Builder().url("$base/fault/redirect-outside").build()).execute().use {
                check(it.code == 302 && it.request.url.host == origin.host) { "media followed a redirect" }
            }
        }
        evidence += "PASS live media redirect not followed"
        var current = true
        PluginHttpBroker(manifest, accountCurrent = { current }).use { broker ->
            val client = broker.mediaClient()
            current = false
            check(runCatching { client.newCall(Request.Builder().url("$base/health").build()).execute().use { } }.isFailure)
        }
        evidence += "PASS revoked media client rejects a subsequent request"
    }
}
