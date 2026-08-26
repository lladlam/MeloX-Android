package com.lladlam.melox.core.provider.kuwo

import android.content.Context

/**
 * 酷我音乐手机号登录态。
 *
 * @param token 登录令牌，用于需要鉴权的接口。
 * @param userId 酷我用户 ID，用于账号展示。
 * @param nickname 用户昵称，可选。
 */
data class KuwoSession(
    val token: String,
    val userId: String,
    val nickname: String,
) {
    val isLoggedIn: Boolean
        get() = token.isNotBlank() && userId.isNotBlank()
}

/** 酷我音乐本地登录态存储。 */
object KuwoSessionStore {
    private const val PreferencesName = "melox_kuwo_session"
    private const val PlaybackPreferencesName = "melox_kuwo_playback_session"
    private const val KeyToken = "token"
    private const val KeyUserId = "user_id"
    private const val KeyNickname = "nickname"

    fun read(context: Context, playback: Boolean = false): KuwoSession {
        val prefs = context.applicationContext.getSharedPreferences(
            if (playback) PlaybackPreferencesName else PreferencesName,
            Context.MODE_PRIVATE,
        )
        return KuwoSession(
            token = prefs.getString(KeyToken, "").orEmpty(),
            userId = prefs.getString(KeyUserId, "").orEmpty(),
            nickname = prefs.getString(KeyNickname, "").orEmpty(),
        )
    }

    fun write(context: Context, session: KuwoSession, playback: Boolean = false): KuwoSession {
        require(session.isLoggedIn) { "酷我登录态不完整" }
        context.applicationContext.getSharedPreferences(
            if (playback) PlaybackPreferencesName else PreferencesName,
            Context.MODE_PRIVATE,
        )
            .edit()
            .putString(KeyToken, session.token)
            .putString(KeyUserId, session.userId)
            .putString(KeyNickname, session.nickname)
            .apply()
        return session
    }

    fun clear(context: Context, playback: Boolean = false) {
        context.applicationContext.getSharedPreferences(
            if (playback) PlaybackPreferencesName else PreferencesName,
            Context.MODE_PRIVATE,
        )
            .edit()
            .clear()
            .apply()
    }
}
