package com.lladlam.melox.core.plugin

import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import com.lladlam.melox.core.music.model.SourceId
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real HTTPS token exchange/Keystore/manager, simulated external browser consent.
 * Public HTTPS fixture origin required. This does not claim browser UI/custom-scheme OS delivery PASS.
 */
internal object PluginRcOAuthChecks {
    fun run(test: Instrumentation, originText: String, evidence: MutableList<String>) {
        val origin = originText.toHttpUrl()
        require(origin.scheme == "https" && origin.port == 443 && origin.encodedPath == "/" && origin.query == null && origin.fragment == null && origin.username.isEmpty() && origin.password.isEmpty())
        val source = SourceId.parse("plugin:org.melox.rc.oauth${UUID.randomUUID().toString().replace("-", "")}")!!
        val base = origin.toString().removeSuffix("/")
        val manifestJson = JSONObject().put("id", source.value.removePrefix("plugin:")).put("name", "Synthetic OAuth RC")
            .put("version", "1.0.0").put("apiVersion", 1).put("entry", "index.js")
            .put("capabilities", org.json.JSONArray().put("authentication"))
            .put("networkDomains", org.json.JSONArray().put(origin.host))
            .put("authentication", JSONObject().put("type", "oauth2Pkce").put("domains", org.json.JSONArray().put(origin.host))
                .put("authorizationEndpoint", "$base/authorize").put("tokenEndpoint", "$base/token").put("clientId", "melox-rc-public")
                .put("scopes", org.json.JSONArray().put("music.read")))
        val manifest = PluginManifestParser.parse(manifestJson.toString())
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            mapOf("manifest.json" to manifestJson.toString(), "index.js" to "globalThis.meloxPlugin={authenticationStatus:async(host)=>host.account?{type:'signedIn',account:host.account}:{type:'signedOut'}};")
                .forEach { (name, value) -> zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry() }
        }
        val manager = MeloXPlugins.get(test.targetContext)
        val vault = PluginAccountVault(test.targetContext, source)
        val browser = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(10, TimeUnit.SECONDS).build()
        var captured: String? = null
        val context = object : ContextWrapper(test.targetContext) {
            override fun startActivity(intent: Intent) { captured = requireNotNull(intent.data).toString() }
        }
        fun begin(account: String, allow: Boolean = true): String {
            captured = null
            PluginOAuth.begin(context, manifest, "Synthetic $account")
            val authorization = requireNotNull(captured).toHttpUrl()
            check(authorization.queryParameter("code_challenge_method") == "S256")
            check(authorization.queryParameter("redirect_uri") == PluginOAuth.REDIRECT_URI)
            val cookie = browser.newCall(Request.Builder().url(authorization).build()).execute().use { response ->
                check(response.code == 200); requireNotNull(response.header("Set-Cookie")).substringBefore(';')
            }
            val form = FormBody.Builder()
            listOf("client_id", "redirect_uri", "response_type", "code_challenge_method", "code_challenge", "state").forEach {
                form.add(it, requireNotNull(authorization.queryParameter(it)))
            }
            form.add("account", account).add("decision", if (allow) "allow" else "deny")
            return browser.newCall(Request.Builder().url("$base/consent").header("Cookie", cookie).post(form.build()).build()).execute().use {
                check(it.code == 303); requireNotNull(it.header("Location"))
            }
        }
        fun profile(lease: PluginAccountVault.Lease): String = PluginHttpBroker(manifest, lease).use { broker ->
            val response = JSONObject(broker.request(JSONObject().put("url", "$base/api/me").put("credential", "active").toString()))
            check(response.getInt("status") == 200)
            JSONObject(response.getString("body")).getString("id")
        }
        try {
            manager.install(out.toByteArray()); manager.enable(source)
            val alphaCallback = begin("alpha")
            check(runCatching { PluginOAuth.complete(test.targetContext, alphaCallback.replace("state=", "state=invalid")) }.isFailure)
            PluginOAuth.complete(test.targetContext, alphaCallback)
            check(runCatching { PluginOAuth.complete(test.targetContext, alphaCallback) }.isFailure)
            val alpha = requireNotNull(vault.lease()); check(profile(alpha) == "alpha")
            val staleStamp = vault.sessionStamp()
            PluginHttpBroker(manifest, alpha) { vault.sessionStamp() == staleStamp }.use { stale ->
                PluginOAuth.complete(test.targetContext, begin("beta"))
                val beta = requireNotNull(vault.lease()); check(beta.account.id != alpha.account.id && profile(beta) == "beta")
                check(runCatching { stale.request(JSONObject().put("url", "$base/api/me").put("credential", "active").toString()) }.isFailure)
            }
            check(vault.accounts().size == 2)
            vault.activate(alpha.account.id); manager.accountChanged(source)
            check(vault.sessionStamp() != staleStamp)
            val oldLease = requireNotNull(vault.lease())
            val refreshed = PluginHttpBroker(manifest, oldLease).use { PluginOAuth.refresh(manifest, oldLease, it) }
            val rotated = vault.replaceTokens(oldLease, refreshed)
            check(profile(rotated) == "alpha")
            check(runCatching { PluginHttpBroker(manifest, oldLease).use { PluginOAuth.refresh(manifest, oldLease, it) } }.isFailure)
            check(vault.lease()?.token == rotated.token) // Failed refresh/replay never clears the good account.
            val cancelled = begin("alpha"); PluginOAuth.cancel(source)
            check(runCatching { PluginOAuth.complete(test.targetContext, cancelled) }.isFailure)
            val denied = begin("beta", allow = false)
            check(runCatching { PluginOAuth.complete(test.targetContext, denied) }.isFailure)
            check(vault.accounts().size == 2)
            evidence += "PASS actual OAuth HTTPS S256/code exchange, state/code replay rejection, encrypted accounts, switch/stale lease, refresh rotation/failure and cancel/deny (simulated browser)"
            val secretFile = File(test.targetContext.cacheDir, "plugin-rc-oauth-admin")
            if (secretFile.isFile) {
                val secret = secretFile.readText().trim()
                fun control(action: String) {
                    val body = FormBody.Builder().add("action", action).add("account", "alpha").build()
                    browser.newCall(Request.Builder().url("$base/admin/control").header("X-RC-Admin", secret).post(body).build()).execute().use { check(it.code == 200) }
                }
                control("fail-refresh"); control("expire")
                check(runCatching { PluginHttpBroker(manifest, rotated).use { PluginOAuth.refresh(manifest, rotated, it) } }.isFailure)
                check(vault.lease()?.token == rotated.token)
                control("allow-refresh")
                val events = browser.newCall(Request.Builder().url("$base/admin/events").header("X-RC-Admin", secret).build()).execute().use {
                    check(it.code == 200); org.json.JSONArray(requireNotNull(it.body).string())
                }
                for (i in 0 until events.length()) {
                    val event = events.getJSONObject(i)
                    if (event.getString("kind") in setOf("token", "api")) check(!event.getBoolean("cookiePresent"))
                }
                evidence += "PASS controlled refresh failure preserves account; server audit reports no browser Cookie on token/API calls"
            } else evidence += "NOT_RUN OAuth admin expiry/failure/Cookie audit (no disposable admin secret)"
        } finally {
            PluginOAuth.cancel(source)
            if (source in manager.plugins.value) manager.uninstall(source)
            PluginStorage.clear(test.targetContext, source, vault.accounts().map { it.id }); vault.clear()
            File(test.targetContext.cacheDir, "plugin-rc-oauth-admin").delete()
            browser.connectionPool.evictAll(); browser.dispatcher.executorService.shutdown()
        }
    }
}
