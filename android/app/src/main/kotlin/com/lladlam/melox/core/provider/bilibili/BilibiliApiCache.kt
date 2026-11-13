package com.lladlam.melox.core.provider.bilibili

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class BilibiliApiCache private constructor(context: Context) {
    data class Policy(val memoryTtlMs: Long, val diskTtlMs: Long? = null)
    private data class Entry(val value: String, val storedAtMs: Long, val expiresAtMs: Long)
    private data class MixinEntry(val value: String, val expiresAtMs: Long)

    private val directory = File(context.applicationContext.cacheDir, "bilibili_metadata")
    private val lock = Any()
    private val memory = object : LinkedHashMap<String, Entry>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MaxMemoryEntries
    }
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<JSONObject>>()
    private val mixinKeys = ConcurrentHashMap<String, MixinEntry>()

    fun getOrLoad(key: String, policy: Policy, loader: () -> JSONObject): JSONObject {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            memory[key]?.takeIf { now < it.expiresAtMs }?.let { return JSONObject(it.value) }
            memory.remove(key)
        }
        policy.diskTtlMs?.let { diskTtl ->
            readDisk(key, now, diskTtl)?.let { value ->
                synchronized(lock) { memory[key] = Entry(value, now, now + policy.memoryTtlMs) }
                return JSONObject(value)
            }
        }
        val pending = CompletableFuture<JSONObject>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return JSONObject(existing.get().toString())
        return try {
            val loaded = loader()
            require(loaded.optInt("code", -1) == 0) { "Only successful Bilibili responses are cacheable" }
            val value = loaded.toString()
            synchronized(lock) { memory[key] = Entry(value, now, now + policy.memoryTtlMs) }
            policy.diskTtlMs?.let { writeDisk(key, value, now) }
            JSONObject(value).also { pending.complete(JSONObject(value)) }
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    fun mixinKey(scope: String, loader: () -> String): String {
        val now = System.currentTimeMillis()
        mixinKeys[scope]?.takeIf { now < it.expiresAtMs }?.let { return it.value }
        return synchronized(mixinKeys) {
            mixinKeys[scope]?.takeIf { now < it.expiresAtMs }?.value ?: loader().also {
                mixinKeys[scope] = MixinEntry(it, now + MixinTtlMs)
            }
        }
    }

    fun invalidateMixin(scope: String) { mixinKeys.remove(scope) }

    private fun readDisk(key: String, now: Long, ttlMs: Long): String? = runCatching {
        val json = JSONObject(fileFor(key).readText())
        val storedAt = json.getLong("storedAtMs")
        if (now - storedAt > ttlMs) return@runCatching null
        json.getJSONObject("payload").toString()
    }.getOrNull()

    private fun writeDisk(key: String, value: String, now: Long) = runCatching {
        directory.mkdirs()
        val target = fileFor(key)
        val temp = File.createTempFile("${target.name}.", ".tmp", directory)
        try {
            temp.writeText(JSONObject().put("storedAtMs", now).put("payload", JSONObject(value)).toString())
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } finally {
            temp.delete()
        }
    }

    private fun fileFor(key: String) = File(directory, sha256(key) + ".json")

    companion object {
        val Account = Policy(5 * 60_000L, 30 * 60_000L)
        val Folders = Policy(2 * 60_000L, 15 * 60_000L)
        val FolderDetail = Policy(2 * 60_000L, 10 * 60_000L)
        val View = Policy(6 * 60 * 60_000L, 24 * 60 * 60_000L)
        val Search = Policy(3 * 60_000L)
        private const val MaxMemoryEntries = 128
        private const val MixinTtlMs = 6 * 60 * 60_000L
        @Volatile private var instance: BilibiliApiCache? = null

        fun shared(context: Context): BilibiliApiCache = instance ?: synchronized(this) {
            instance ?: BilibiliApiCache(context.applicationContext).also { instance = it }
        }

        fun cacheKey(scope: String, operation: String, params: Map<String, String>): String =
            "$scope|$operation|" + params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }

        fun normalizeSearchQuery(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

        fun isFresh(storedAtMs: Long, ttlMs: Long, nowMs: Long): Boolean =
            ttlMs > 0 && nowMs >= storedAtMs && nowMs - storedAtMs < ttlMs

        fun playbackExpiry(url: String, nowMs: Long = System.currentTimeMillis()): Long {
            val deadline = Regex("(?:[?&](?:deadline|expires)=)(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
            val parsed = deadline?.times(1_000L)?.minus(30_000L)
            return parsed?.takeIf { it > nowMs } ?: nowMs + 90_000L
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
