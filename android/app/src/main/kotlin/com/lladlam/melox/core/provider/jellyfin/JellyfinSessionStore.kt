package com.lladlam.melox.core.provider.jellyfin

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Stores the single Jellyfin session encrypted with an Android Keystore key. */
object JellyfinSessionStore {
    private const val PreferencesName = "melox_jellyfin_session"
    private const val SessionKey = "encrypted_session"
    private const val KeyAlias = "melox_jellyfin_session_key"

    fun read(context: Context): JellyfinSession {
        val encoded = preferences(context).getString(SessionKey, null) ?: return JellyfinSession()
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, 12)
            val encrypted = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(encrypted), StandardCharsets.UTF_8))
            JellyfinSession(
                serverUrl = json.optString("serverUrl"),
                accessToken = json.optString("accessToken"),
                userId = json.optString("userId"),
                serverId = json.optString("serverId"),
                userName = json.optString("userName"),
            )
        }.getOrDefault(JellyfinSession())
    }

    fun write(context: Context, session: JellyfinSession) {
        val json = JSONObject()
            .put("serverUrl", session.serverUrl)
            .put("accessToken", session.accessToken)
            .put("userId", session.userId)
            .put("serverId", session.serverId)
            .put("userName", session.userName)
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val packed = iv + cipher.doFinal(json.toString().toByteArray(StandardCharsets.UTF_8))
        preferences(context).edit()
            .putString(SessionKey, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    fun clear(context: Context) {
        preferences(context).edit().clear().apply()
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                KeyAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }
}
