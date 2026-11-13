package com.lladlam.melox.core.provider.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class SpotifyTokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
)

object SpotifyOAuthLogic {
    private val secureRandom = SecureRandom()

    fun randomUrlSafe(bytes: Int): String = ByteArray(bytes).also(secureRandom::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    fun codeChallenge(verifier: String): String = MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.US_ASCII))
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    fun stateMatches(expected: String, actual: String?): Boolean = actual != null &&
        MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())

    fun transactionIsFresh(createdAtEpochMs: Long, nowEpochMs: Long, ttlMs: Long): Boolean =
        nowEpochMs - createdAtEpochMs in 0..ttlMs

    fun parseToken(body: String): SpotifyTokenResponse {
        val json = JSONObject(body)
        val accessToken = json.optString("access_token").takeIf(String::isNotBlank)
            ?: throw IOException("Spotify token 响应缺少 access_token")
        val expiresIn = json.optLong("expires_in").takeIf { it > 0L }
            ?: throw IOException("Spotify token 响应缺少有效 expires_in")
        return SpotifyTokenResponse(
            accessToken = accessToken,
            refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank),
            expiresInSeconds = expiresIn,
        )
    }
}

class SpotifyOAuth(
    private val context: Context,
    private val clientId: String,
    private val httpClient: OkHttpClient,
) {
    /**
     * Opens the system browser and waits for Spotify to redirect to the loopback
     * listener. The keymaster client only accepts `http://127.0.0.1:5588/login`,
     * so a custom scheme is rejected and never returns a usable code.
     */
    suspend fun authorize(doneMessage: String): SpotifySession = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "未配置 Spotify Client ID；请设置 Gradle property meloxSpotifyClientId" }
        val transaction = SpotifyAuthorizationTransaction(
            state = SpotifyOAuthLogic.randomUrlSafe(24),
            codeVerifier = SpotifyOAuthLogic.randomUrlSafe(64),
            createdAtEpochMs = System.currentTimeMillis(),
        )
        SpotifyLoopbackReceiver(RedirectPort, doneMessage).use { receiver ->
            val redirectUri = receiver.redirectUri
            val authUri = Uri.parse(AuthorizeEndpoint).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", SpotifyOAuthLogic.codeChallenge(transaction.codeVerifier))
                .appendQueryParameter("state", transaction.state)
                .appendQueryParameter("scope", Scopes.joinToString(" "))
                .build()
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, authUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            val callback = try {
                receiver.awaitRedirect()
            } catch (_: SocketTimeoutException) {
                throw IOException("Spotify 登录已超时，请重新登录")
            }
            if (!SpotifyOAuthLogic.stateMatches(transaction.state, callback.state)) {
                throw IOException("Spotify OAuth state 校验失败")
            }
            callback.error?.takeIf(String::isNotBlank)?.let {
                throw IOException("Spotify 授权失败: $it")
            }
            val code = callback.code?.takeIf(String::isNotBlank)
                ?: throw IOException("Spotify 授权回调缺少 code")
            val token = requestToken(
                FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", redirectUri)
                    .add("code_verifier", transaction.codeVerifier)
                    .build(),
            )
            SpotifySession(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken.orEmpty(),
                expiresAtEpochMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(token.expiresInSeconds),
            ).also { SpotifySessionStore.write(context, it) }
        }
    }

    suspend fun refresh(session: SpotifySession): SpotifySession = withContext(Dispatchers.IO) {
        if (session.refreshToken.isBlank()) throw IOException("Spotify 登录已过期，请重新登录")
        val token = requestToken(
            FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", session.refreshToken)
                .build(),
        )
        val refreshed = session.copy(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken ?: session.refreshToken,
            expiresAtEpochMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(token.expiresInSeconds),
        )
        // A refresh started for account A must not overwrite a newer login as B.
        val current = SpotifySessionStore.read(context)
        if (current == session) SpotifySessionStore.write(context, refreshed)
        refreshed
    }

    private fun requestToken(body: FormBody): SpotifyTokenResponse {
        val request = Request.Builder().url(TokenEndpoint).post(body)
            .header("Accept", "application/json")
            .header(com.lladlam.melox.core.network.RetryInterceptor.NO_RETRY_HEADER, "true")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(responseBody).optString("error_description") }.getOrNull()
                    ?.takeIf(String::isNotBlank) ?: "HTTP ${response.code}"
                throw IOException("Spotify token 请求失败: $message")
            }
            return SpotifyOAuthLogic.parseToken(responseBody)
        }
    }

    companion object {
        /** Port registered for librespot's keymaster client. Spotify matches it literally. */
        const val RedirectPort = 5588
        const val RedirectUri = "http://127.0.0.1:$RedirectPort/login"
        const val AuthorizeEndpoint = "https://accounts.spotify.com/authorize"
        const val TokenEndpoint = "https://accounts.spotify.com/api/token"
        val Scopes = listOf(
            "app-remote-control",
            "playlist-modify",
            "playlist-modify-private",
            "playlist-modify-public",
            "playlist-read",
            "playlist-read-collaborative",
            "playlist-read-private",
            "streaming",
            "user-follow-modify",
            "user-follow-read",
            "user-library-modify",
            "user-library-read",
            "user-read-currently-playing",
            "user-read-email",
            "user-read-playback-state",
            "user-read-private",
            "user-read-recently-played",
        )
    }
}

/**
 * One-shot listener for Spotify's loopback redirect.
 * Bound to IPv4 explicitly: Android's default loopback is `::1`, and a browser
 * following `http://127.0.0.1` would then get connection refused.
 */
private class SpotifyLoopbackReceiver(
    port: Int,
    doneMessage: String,
) : AutoCloseable {
    data class Callback(val code: String?, val state: String?, val error: String?)

    private val server = ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).apply {
        soTimeout = RedirectTimeoutMs
    }
    val redirectUri: String = "http://${server.inetAddress.hostAddress}:${server.localPort}/login"
    private val response: String = buildString {
        val page = "<html><body><h2>$doneMessage</h2></body></html>"
        append("HTTP/1.1 200 OK\r\n")
        append("Content-Type: text/html; charset=utf-8\r\n")
        append("Content-Length: ${page.toByteArray(Charsets.UTF_8).size}\r\n")
        append("Connection: close\r\n\r\n")
        append(page)
    }

    fun awaitRedirect(): Callback {
        server.accept().use { socket ->
            val requestLine = socket.getInputStream().bufferedReader().readLine()
                ?: throw IOException("Spotify 授权回调为空")
            val target = requestLine.split(' ').getOrNull(1)
                ?: throw IOException("Spotify 授权回调无效")
            val uri = Uri.parse("http://127.0.0.1$target")
            socket.getOutputStream().writer().apply {
                write(response)
                flush()
            }
            return Callback(
                code = uri.getQueryParameter("code"),
                state = uri.getQueryParameter("state"),
                error = uri.getQueryParameter("error"),
            )
        }
    }

    override fun close() {
        runCatching { server.close() }
    }

    private companion object {
        const val RedirectTimeoutMs = 5 * 60 * 1000
    }
}
