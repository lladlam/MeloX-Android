package com.lladlam.melox.playback
import android.content.Context
import android.util.Log
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.network.NeteaseAuthenticatedEapi
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
internal class MeloXPlaybackHistoryReporter(context: Context) {
    private val app = context.applicationContext; private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO); private val mutex = Mutex(); private val eapi = NeteaseAuthenticatedEapi(cookieProvider = { NeteaseSessionStore.readCookie(app) })
    fun recordStart(songId: Long, sourceId: Long = 0L) { if (songId > 0L) submit("startplay", JSONObject().put("id", songId.toString()).put("type", "song").put("mainsite", "1").put("mainsiteWeb", "1").put("content", "id=$sourceId")) }
    fun recordDuration(songId: Long, sourceId: Long = 0L, elapsedMs: Long, durationMs: Long? = null) { if (songId <= 0L) return; val elapsed = (elapsedMs.coerceAtLeast(0L) / 1000L).toInt(); val duration = durationMs?.takeIf { it > 0L }?.let { (it / 1000L).toInt() }; val seconds = duration?.let { minOf(elapsed, it) } ?: elapsed; if (seconds <= 0) return; submit("play", JSONObject().put("download", 0).put("end", "playend").put("id", songId.toString()).put("sourceId", sourceId.toString()).put("time", seconds.toString()).put("type", "song").put("wifi", 0).put("source", "list").put("mainsite", "1").put("mainsiteWeb", "1").put("content", "id=$sourceId")) }
    fun close() { scope.launch { delay(1_500L); scope.cancel() } }
    private fun submit(action: String, fields: JSONObject) { if (!NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie(app))) return; scope.launch { mutex.withLock { runCatching { val logs = JSONArray().put(JSONObject().put("action", action).put("json", fields)); eapi.post("/api/feedback/weblog", JSONObject().put("logs", logs.toString()), domain = "https://clientlog.music.163.com", cookieOs = "osx") }.onFailure { Log.w("MeloXHistory", "NetEase playback history upload failed", it) } } } }
}
