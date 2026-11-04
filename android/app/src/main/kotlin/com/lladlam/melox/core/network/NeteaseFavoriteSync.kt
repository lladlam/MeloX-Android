package com.lladlam.melox.core.network

import android.content.Context
import com.lladlam.melox.core.account.NeteaseSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes each song's writes and keeps them alive when the player UI is closed. */
internal object NeteaseFavoriteSync {
    private class Entry(val mutex: Mutex = Mutex(), var revision: Long = 0)
    private val entries = mutableMapOf<Long, Entry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun submit(context: Context, songId: Long, liked: Boolean, onResult: (Result<Unit>) -> Unit) {
        val app = context.applicationContext
        val (entry, revision) = synchronized(entries) {
            val entry = entries.getOrPut(songId) { Entry() }
            entry.revision++
            entry to entry.revision
        }
        scope.launch {
            delay(350)
            entry.mutex.withLock {
                if (synchronized(entries) { entry.revision != revision }) return@withLock
                val result = runCatching {
                    NeteaseMusicOperationsClient({ NeteaseSessionStore.readCookie(app) }).setSongLiked(songId, liked)
                }
                val latest = synchronized(entries) {
                    if (entry.revision == revision) {
                        entries.remove(songId)
                        true
                    } else false
                }
                if (latest) onResult(result)
            }
        }
    }
}
