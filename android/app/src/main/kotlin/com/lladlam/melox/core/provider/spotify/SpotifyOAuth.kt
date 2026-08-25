package com.lladlam.melox.core.provider.spotify

import android.content.Context
import android.net.Uri
import java.io.IOException
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
    fun authorizationUri(): Uri {
        require(clientId.isNotBlank()) { "未配置 Spotify Client ID；请设置 Gradle property meloxSpotifyClientId" }
        val transaction = SpotifyAuthorizationTransaction(
            state = SpotifyOAuthLogic.randomUrlSafe(24),
            codeVerifier = SpotifyOAuthLogic.randomUrlSafe(64),
            createdAtEpochMs = System.currentTimeMillis(),
        )
        SpotifySessionStore.saveTransaction(context, transaction)
        return Uri.parse(AuthorizeEndpoint).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", RedirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", SpotifyOAuthLogic.codeChallenge(transaction.codeVerifier))
            .appendQueryParameter("state", transaction.state)
            .appendQueryParameter("scope", Scopes.joinToString(" "))
            .build()
    }

    suspend fun handleCallback(uri: Uri): SpotifySession = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "Spotify Client ID 未配置" }
        if (uri.scheme != RedirectScheme || uri.host != RedirectHost ||
            uri.port != -1 || uri.path.orEmpty().isNotEmpty() || uri.userInfo != null
        ) {
            throw IOException("无效的 Spotify 登录回调")
        }
        val transaction = SpotifySessionStore.transaction(context)
            ?: throw IOException("Spotify 登录事务不存在或已失效，请重新登录")
        if (!SpotifyOAuthLogic.transactionIsFresh(
                transaction.createdAtEpochMs,
                System.currentTimeMillis(),
                TransactionTtlMs,
            )
        ) {
            SpotifySessionStore.clearTransaction(context)
            throw IOException("Spotify 登录已超时，请重新登录")
        }
        if (!SpotifyOAuthLogic.stateMatches(transaction.state, uri.getQueryParameter("state"))) {
            SpotifySessionStore.clearTransaction(context)
            throw IOException("Spotify OAuth state 校验失败")
        }
        uri.getQueryParameter("error")?.takeIf(String::isNotBlank)?.let {
            SpotifySessionStore.clearTransaction(context)
            throw IOException("Spotify 授权失败: $it")
        }
        try {
            val code = uri.getQueryParameter("code")?.takeIf(String::isNotBlank)
                ?: throw IOException("Spotify 授权回调缺少 code")
            val token = requestToken(
                FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", RedirectUri)
                    .add("code_verifier", transaction.codeVerifier)
                    .build(),
            )
            SpotifySession(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken.orEmpty(),
                expiresAtEpochMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(token.expiresInSeconds),
            ).also { SpotifySessionStore.write(context, it) }
        } finally {
            SpotifySessionStore.clearTransaction(context)
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
        session.copy(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken ?: session.refreshToken,
            expiresAtEpochMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(token.expiresInSeconds),
        ).also { SpotifySessionStore.write(context, it) }
    }

    private fun requestToken(body: FormBody): SpotifyTokenResponse {
        val request = Request.Builder().url(TokenEndpoint).post(body).header("Accept", "application/json").build()
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
        const val RedirectUri = "com.lladlam.melox.android://spotify-callback"
        const val RedirectScheme = "com.lladlam.melox.android"
        const val RedirectHost = "spotify-callback"
        const val AuthorizeEndpoint = "https://accounts.spotify.com/authorize"
        const val TokenEndpoint = "https://accounts.spotify.com/api/token"
        private const val TransactionTtlMs = 10 * 60 * 1_000L
        val Scopes = listOf(
            "user-read-private",
            "playlist-read-private",
            "playlist-read-collaborative",
            "playlist-modify-private",
            "playlist-modify-public",
            "user-library-modify",
        )
    }
}
