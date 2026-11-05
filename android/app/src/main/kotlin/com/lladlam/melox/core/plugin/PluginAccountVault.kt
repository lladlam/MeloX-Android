package com.lladlam.melox.core.plugin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.lladlam.melox.core.music.model.SourceId
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Independent aliases and authenticated ciphertext per plugin/account; never resets shared keys. */
class PluginAccountVault(context: Context, private val source: SourceId) {
    init { require(source.value.startsWith("plugin:")) { "plugin account namespace required" } }
    data class Account(val id: String, val displayName: String)
    internal class Lease(val source: SourceId, val account: Account, val token: String, val policy: PluginAuthentication? = null,
        val refreshToken: String? = null, val expiresAt: Long? = null, val selectionStamp: String? = null)
    private val namespace = digest(source.value)
    private val root = File(context.applicationContext.noBackupFilesDir, "melox-plugin-accounts-v1/$namespace")
    private val selectedFile get() = AtomicFile(File(root, "selected"))
    private fun alias(id: String) = "melox.plugin.account.v1.$namespace.$id"
    private fun file(id: String): AtomicFile {
        require(id.matches(Regex("[a-f0-9-]{36}"))) { "invalid account id" }
        return AtomicFile(File(root, "$id.enc"))
    }
    private fun keys() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun importToken(displayName: String, token: String, policy: PluginAuthentication): Account = synchronized(lock) {
        require(policy.type == "apiToken" && policy.domains.isNotEmpty())
        create(displayName, token, policy, null, null)
    }

    internal fun importOAuth(displayName: String, tokens: PluginOAuth.Tokens, policy: PluginAuthentication): Account = synchronized(lock) {
        require(policy.type == "oauth2Pkce" && policy.oauth != null)
        create(displayName, tokens.accessToken, policy, tokens.refreshToken, tokens.expiresAt)
    }

    private fun create(displayName: String, token: String, policy: PluginAuthentication, refreshToken: String?, expiresAt: Long?): Account {
        require(displayName.isNotBlank() && displayName.length <= 120)
        require(token.isNotEmpty() && token.length <= 4096 && token.all { it.code in 33..126 }) { "invalid API token" }
        require(accounts().size < 16) { "account quota exceeded" }
        check(root.isDirectory || root.mkdirs())
        val account = Account(UUID.randomUUID().toString(), displayName)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias(account.id), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        val key = generator.generateKey()
        return try {
            encrypt(Lease(source, account, token, policy, refreshToken, expiresAt), key)
            account
        } catch (error: Throwable) {
            keys().deleteEntry(alias(account.id))
            throw error
        }
    }

    private fun encrypt(lease: Lease, key: SecretKey) {
        require(lease.source == source) { "credential source mismatch" }
        val policy = requireNotNull(lease.policy)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD("${source.value}:${lease.account.id}".toByteArray(Charsets.UTF_8))
        val auth = JSONObject().put("type", policy.type).put("header", policy.header).put("prefix", policy.prefix)
            .put("domains", org.json.JSONArray(policy.domains.toList()))
        policy.oauth?.let { auth.put("oauth", JSONObject().put("authorizationEndpoint", it.authorizationEndpoint)
            .put("tokenEndpoint", it.tokenEndpoint).put("clientId", it.clientId).put("scopes", org.json.JSONArray(it.scopes))) }
        val plaintext = JSONObject().put("id", lease.account.id).put("displayName", lease.account.displayName).put("token", lease.token)
            .put("policy", auth).put("refreshToken", lease.refreshToken).put("expiresAt", lease.expiresAt)
            .toString().toByteArray(Charsets.UTF_8)
        require(plaintext.size <= 15 * 1024) { "credential record too large" }
        val ciphertext = cipher.doFinal(plaintext)
        write(file(lease.account.id), byteArrayOf(1, cipher.iv.size.toByte()) + cipher.iv + ciphertext)
    }

    /** Compare-and-swap prevents a late refresh from resurrecting a deleted/logged-out account. */
    internal fun replaceTokens(previous: Lease, tokens: PluginOAuth.Tokens): Lease = synchronized(lock) {
        require(previous.source == source) { "credential source mismatch" }
        check(selected() == previous.account.id) { "account changed" }
        check(previous.selectionStamp != null && sessionStamp() == previous.selectionStamp) { "account session changed" }
        val current = decrypt(previous.account.id)
        check(current.token == previous.token && current.policy == previous.policy) { "account changed" }
        val updated = Lease(source, current.account, tokens.accessToken, current.policy, tokens.refreshToken ?: current.refreshToken, tokens.expiresAt, previous.selectionStamp)
        val key = keys().getKey(alias(current.account.id), null) as? SecretKey ?: error("account key unavailable")
        encrypt(updated, key)
        updated
    }

    fun accounts(): List<Account> = synchronized(lock) {
        root.listFiles().orEmpty().map { it.name.removeSuffix(".bak") }.distinct()
            .filter { it.matches(Regex("[a-f0-9-]{36}\\.enc")) }.map {
                val id = it.removeSuffix(".enc")
                runCatching { decrypt(id).account }.getOrElse { Account(id, "无法解密的账号 ${id.take(8)}（原数据已保留）") }
            }
    }
    fun selected(): String? = synchronized(lock) {
        sessionStamp()?.substringBefore('\n')?.takeIf { it.isNotEmpty() }
    }
    internal fun sessionStamp(): String? = synchronized(lock) {
        try { read(selectedFile, 128).toString(Charsets.UTF_8) }
        catch (_: FileNotFoundException) { null }
    }
    fun activate(id: String?) = synchronized(lock) {
        if (id != null) decrypt(id)
        check(root.isDirectory || root.mkdirs())
        write(selectedFile, (id.orEmpty() + "\n" + UUID.randomUUID().toString()).toByteArray(Charsets.UTF_8))
    }
    fun remove(id: String) = synchronized(lock) {
        if (selected() == id) activate(null)
        file(id).delete()
        keys().deleteEntry(alias(id))
    }
    fun clear() = synchronized(lock) {
        activate(null)
        val store = keys()
        store.aliases().toList().filter { it.startsWith("melox.plugin.account.v1.$namespace.") }.forEach(store::deleteEntry)
        check(!root.exists() || root.deleteRecursively()) { "cannot remove plugin credentials" }
    }
    internal fun lease(): Lease? = synchronized(lock) {
        val stamp = sessionStamp()
        selected()?.let { id -> decrypt(id).let { Lease(source, it.account, it.token, it.policy, it.refreshToken, it.expiresAt, stamp) } }
    }
    private fun decrypt(id: String): Lease {
        val data = read(file(id), 16 * 1024)
        require(data.size >= 30 && data[0] == 1.toByte() && data[1] == 12.toByte()) { "invalid encrypted account record" }
        // A missing/invalid key is an error. Preserve ciphertext and other accounts for recovery.
        val key = keys().getKey(alias(id), null) as? SecretKey ?: error("account key unavailable")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(2, 14)))
        cipher.updateAAD("${source.value}:$id".toByteArray(Charsets.UTF_8))
        val value = JSONObject(cipher.doFinal(data.copyOfRange(14, data.size)).toString(Charsets.UTF_8))
        require(value.getString("id") == id) { "account identity mismatch" }
        val policy = value.getJSONObject("policy")
        val domains = policy.getJSONArray("domains")
        val oauth = policy.optJSONObject("oauth")?.let {
            val scopes = it.getJSONArray("scopes")
            PluginOAuthConfiguration(it.getString("authorizationEndpoint"), it.getString("tokenEndpoint"), it.getString("clientId"),
                (0 until scopes.length()).map { index -> scopes.getString(index) })
        }
        return Lease(source, Account(id, value.getString("displayName")), value.getString("token"),
            PluginAuthentication(policy.getString("type"), policy.getString("header"), policy.getString("prefix"),
                (0 until domains.length()).map { domains.getString(it) }.toSet(), oauth),
            if (value.has("refreshToken")) value.getString("refreshToken") else null,
            if (value.has("expiresAt")) value.getLong("expiresAt") else null)
    }
    private fun write(file: AtomicFile, bytes: ByteArray) {
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) }
        catch (error: Throwable) { file.failWrite(out); throw error }
    }
    private fun read(file: AtomicFile, limit: Int): ByteArray = file.openRead().use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            require(out.size() + count <= limit) { "account record too large" }
            out.write(buffer, 0, count)
        }
        out.toByteArray()
    }
    private companion object {
        val lock = Any()
        fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
