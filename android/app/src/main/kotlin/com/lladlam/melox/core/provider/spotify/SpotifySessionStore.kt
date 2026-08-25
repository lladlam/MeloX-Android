package com.lladlam.melox.core.provider.spotify

import android.content.Context

data class SpotifySession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAtEpochMs: Long = 0L,
    val accountId: String = "",
) {
    val isLoggedIn: Boolean get() = accessToken.isNotBlank() || refreshToken.isNotBlank()
    fun needsRefresh(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        accessToken.isBlank() || nowEpochMs >= expiresAtEpochMs - 60_000L

    override fun toString(): String =
        "SpotifySession(isLoggedIn=$isLoggedIn, expiresAtEpochMs=$expiresAtEpochMs, accountId=$accountId)"
}

data class SpotifyAuthorizationTransaction(
    val state: String,
    val codeVerifier: String,
    val createdAtEpochMs: Long,
)

object SpotifySessionStore {
    private const val PreferencesName = "melox_spotify_session"
    private const val AccessToken = "access_token"
    private const val RefreshToken = "refresh_token"
    private const val ExpiresAt = "expires_at"
    private const val AccountId = "account_id"
    private const val OAuthState = "oauth_state"
    private const val OAuthVerifier = "oauth_verifier"
    private const val OAuthCreatedAt = "oauth_created_at"
    private const val OAuthError = "oauth_error"

    fun read(context: Context): SpotifySession = preferences(context).let {
        SpotifySession(
            accessToken = it.getString(AccessToken, null).orEmpty(),
            refreshToken = it.getString(RefreshToken, null).orEmpty(),
            expiresAtEpochMs = it.getLong(ExpiresAt, 0L),
            accountId = it.getString(AccountId, null).orEmpty(),
        )
    }

    fun write(context: Context, session: SpotifySession) {
        preferences(context).edit()
            .putString(AccessToken, session.accessToken)
            .putString(RefreshToken, session.refreshToken)
            .putLong(ExpiresAt, session.expiresAtEpochMs)
            .putString(AccountId, session.accountId)
            .apply()
    }

    fun updateAccountId(context: Context, accountId: String) {
        preferences(context).edit().putString(AccountId, accountId).apply()
    }

    fun saveTransaction(context: Context, transaction: SpotifyAuthorizationTransaction) {
        preferences(context).edit()
            .putString(OAuthState, transaction.state)
            .putString(OAuthVerifier, transaction.codeVerifier)
            .putLong(OAuthCreatedAt, transaction.createdAtEpochMs)
            .remove(OAuthError)
            .apply()
    }

    fun transaction(context: Context): SpotifyAuthorizationTransaction? = preferences(context).let {
        val state = it.getString(OAuthState, null).orEmpty()
        val verifier = it.getString(OAuthVerifier, null).orEmpty()
        if (state.isBlank() || verifier.isBlank()) null else SpotifyAuthorizationTransaction(
            state = state,
            codeVerifier = verifier,
            createdAtEpochMs = it.getLong(OAuthCreatedAt, 0L),
        )
    }

    fun clearTransaction(context: Context) {
        preferences(context).edit().remove(OAuthState).remove(OAuthVerifier).remove(OAuthCreatedAt).apply()
    }

    fun setOAuthError(context: Context, message: String) {
        preferences(context).edit().putString(OAuthError, message).apply()
    }

    fun consumeOAuthError(context: Context): String? {
        val preferences = preferences(context)
        return preferences.getString(OAuthError, null)?.also { preferences.edit().remove(OAuthError).apply() }
    }

    fun clear(context: Context) {
        preferences(context).edit().clear().apply()
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
