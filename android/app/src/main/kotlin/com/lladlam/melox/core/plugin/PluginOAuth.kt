package com.lladlam.melox.core.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import okio.ByteString.Companion.toByteString
import java.security.MessageDigest
import java.security.SecureRandom
import com.lladlam.melox.core.music.model.SourceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

/** Public-client authorization-code flow. No embedded user agent, implicit flow or client secret. */
object PluginOAuth {
    const val REDIRECT_URI = "com.lladlam.melox.plugin:/oauth2redirect"
    internal class Tokens(val accessToken: String, val refreshToken: String?, val expiresAt: Long?)
    private class Pending(val manifest: PluginManifest, val label: String, val state: String, val verifier: String, val started: Long,
        val replacement: PluginAccountVault.Lease? = null, var exchanging: Boolean = false, var broker: PluginHttpBroker? = null)
    private val lock = Any()
    private val pending = mutableMapOf<SourceId, Pending>()
    private val messages = MutableStateFlow<Map<SourceId, String>>(emptyMap())
    val status = messages.asStateFlow()

    fun begin(context: Context, manifest: PluginManifest, label: String) = begin(context, manifest, label, null)

    internal fun beginReplacement(context: Context, manifest: PluginManifest, previous: PluginAccountVault.Lease) {
        require(previous.source == manifest.sourceId && previous.selectionStamp != null)
        begin(context, manifest, previous.account.displayName, previous)
    }

    private fun begin(context: Context, manifest: PluginManifest, label: String, replacement: PluginAccountVault.Lease?) {
        require(label.isNotBlank() && label.length <= 120)
        val configuration = requireNotNull(manifest.authentication?.oauth)
        val attempt = Pending(manifest, label, random(), random(), SystemClock.elapsedRealtime(), replacement)
        val url = configuration.authorizationEndpoint.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code").addQueryParameter("client_id", configuration.clientId)
            .addQueryParameter("redirect_uri", REDIRECT_URI).addQueryParameter("scope", configuration.scopes.joinToString(" "))
            .addQueryParameter("state", attempt.state).addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("code_challenge", challenge(attempt.verifier)).build()
        synchronized(lock) {
            pending.put(manifest.sourceId, attempt)?.broker?.close()
            messages.value = messages.value + (manifest.sourceId to "等待浏览器授权，可在此取消")
        }
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (error: Exception) { cancel(manifest.sourceId); throw error }
    }

    fun cancel(source: SourceId) = synchronized(lock) {
        pending.remove(source)?.broker?.close()
        messages.value = messages.value + (source to "登录已取消")
    }

    /** Called only on a host IO dispatcher. An unknown/replayed/expired callback never exchanges a code. */
    internal fun complete(context: Context, raw: String) {
        val callback = PluginOAuthCallback.parse(raw)
        val attempt = synchronized(lock) { pending.values.firstOrNull { constantTimeEquals(it.state, callback.state) } } ?: error("unknown OAuth state")
        val source = attempt.manifest.sourceId
        synchronized(lock) {
            check(pending[source] === attempt && !attempt.exchanging) { "OAuth login cancelled or replayed" }
            attempt.exchanging = true
        }
        var committed = false
        try {
            check(SystemClock.elapsedRealtime() - attempt.started in 0..600_000L) { "OAuth login expired" }
            require(!callback.declined) { "OAuth authorization declined" }
            val code = requireNotNull(callback.code)
            val manager = MeloXPlugins.get(context)
            val current = manager.plugins.value[source]
            check(current?.status == PluginManager.Status.Enabled && current.manifest == attempt.manifest) { "plugin changed during authorization" }
            val config = requireNotNull(attempt.manifest.authentication?.oauth)
            // Claim the state before network I/O; keep it cancellable until credential commit.
            synchronized(lock) { check(pending[source] === attempt) { "OAuth login cancelled" } }
            val tokens = PluginHttpBroker(attempt.manifest, accountCurrent = { synchronized(lock) { pending[source] === attempt } }).use { broker ->
                synchronized(lock) {
                    check(pending[source] === attempt) { "OAuth login cancelled" }
                    attempt.broker = broker
                }
                parseTokens(broker.tokenRequest(config.tokenEndpoint, mapOf(
                "grant_type" to "authorization_code", "code" to code, "client_id" to config.clientId,
                "redirect_uri" to REDIRECT_URI, "code_verifier" to attempt.verifier))) }
            // Hold the lifecycle gate over the final identity check and commit.
            manager.commitAccount(source, attempt.manifest) {
                synchronized(lock) {
                check(pending[source] === attempt) { "OAuth login cancelled" }
                val vault = PluginAccountVault(context, source)
                val policy = requireNotNull(attempt.manifest.authentication)
                if (attempt.replacement != null) {
                    vault.reauthorize(attempt.replacement, tokens, policy)
                } else {
                    vault.importOAuthAndActivate(attempt.label, tokens, policy)
                }
                committed = true
                pending.remove(source)
                }
            }
            synchronized(lock) { if (source !in pending) messages.value = messages.value + (source to "登录成功") }
        } catch (error: Exception) {
            synchronized(lock) {
                if (committed && source !in pending) messages.value = messages.value + (source to "授权已保存，但插件恢复失败，请重新启用插件")
                else if (pending[source] === attempt) messages.value = messages.value + (source to "登录未完成，请重试")
            }
            throw error
        } finally { synchronized(lock) { if (pending[source] === attempt) pending.remove(source) } }
    }

    internal fun refresh(manifest: PluginManifest, lease: PluginAccountVault.Lease, broker: PluginHttpBroker): Tokens {
        require(lease.source == manifest.sourceId) { "credential source mismatch" }
        require(lease.policy == manifest.authentication) { "credential policy changed" }
        val config = requireNotNull(manifest.authentication?.oauth)
        val refresh = lease.refreshToken ?: error("AUTH_EXPIRED")
        return parseTokens(broker.tokenRequest(config.tokenEndpoint, mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to config.clientId)))
    }

    internal fun parseTokens(value: JSONObject, now: Long = System.currentTimeMillis()): Tokens {
        fun token(name: String): String {
            require(value.opt(name) is String)
            return value.getString(name).also { text -> require(text.length in 1..4096 && text.all { it.code in 33..126 }) }
        }
        require(value.opt("token_type") is String && value.getString("token_type").equals("Bearer", true)) { "unsupported token type" }
        val expiry = if (value.has("expires_in")) {
            require(value.opt("expires_in") is Number)
            val seconds = value.getDouble("expires_in")
            require(seconds.isFinite() && seconds >= 1 && seconds <= 315_360_000 && seconds == kotlin.math.floor(seconds))
            now + seconds.toLong() * 1000
        } else null
        return Tokens(token("access_token"), if (value.has("refresh_token")) token("refresh_token") else null, expiry)
    }
    internal fun challenge(verifier: String): String = encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    private fun random(): String = encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
    private fun encode(bytes: ByteArray): String = bytes.toByteString().base64Url().trimEnd('=')
    private fun constantTimeEquals(first: String, second: String) = second.length <= 128 &&
        MessageDigest.isEqual(first.toByteArray(Charsets.US_ASCII), second.toByteArray(Charsets.US_ASCII))
}
