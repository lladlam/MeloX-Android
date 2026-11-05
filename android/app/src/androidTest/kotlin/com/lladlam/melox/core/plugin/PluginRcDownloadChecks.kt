package com.lladlam.melox.core.plugin

import android.app.Instrumentation
import com.lladlam.melox.core.download.PluginDownloadTransfer
import com.lladlam.melox.core.download.ProviderDownloadTaskStore
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.SourceId
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.util.UUID

/** Actual HTTPS bytes + private filesystem; not playback or proof of audio codec validity. */
internal object PluginRcDownloadChecks {
    fun run(test: Instrumentation, originText: String, evidence: MutableList<String>) {
        val origin = originText.toHttpUrl()
        val base = origin.toString().removeSuffix("/")
        val root = File(test.targetContext.cacheDir, "rc-download-${UUID.randomUUID()}").apply { mkdirs() }
        val source = requireNotNull(SourceId.parse("plugin:org.melox.rc.download"))
        val manifest = PluginManifestParser.parse("""{"id":"org.melox.rc.download","name":"Download fixture","version":"1.0.0","apiVersion":1,"entry":"index.js","capabilities":["download"],"networkDomains":["${origin.host}"]}""")
        val destination = File(root, "fixture.audio")
        var scope = "opaque-initial-scope"
        fun transfer(query: String) = PluginHttpBroker(manifest).use { broker ->
            PluginDownloadTransfer.run(broker.mediaClient(), Request.Builder().url("$base/fault/progressive?$query").build(), destination,
                scope = { scope }, active = { true }, discard = { false })
        }
        fun interrupted() {
            check(runCatching { transfer("cut=1") }.exceptionOrNull() is java.io.IOException)
            check(!destination.exists() && File(destination.path + ".part").length() == 16384L)
        }
        fun complete(version: Int = 1) {
            val expected = ByteArray(512 * 1024) { if (version == 1) (it % 256).toByte() else (255 - it % 256).toByte() }
            check(destination.readBytes().contentEquals(expected)) { "download bytes mixed or truncated" }
            check(!File(destination.path + ".part").exists() && !File(destination.path + ".resume.json").exists())
            destination.delete()
        }
        try {
            interrupted(); transfer("cut=1"); complete()
            evidence += "PASS actual truncated HTTP body retains validated partial; subsequent Range/If-Range resumes exact bytes"
            interrupted(); transfer("mode=ignore&v=2"); complete(2)
            interrupted(); transfer("v=2"); complete(2)
            evidence += "PASS ignored Range and changed ETag replace partial bytes rather than concatenating representations"
            interrupted()
            check(runCatching { transfer("mode=bad-range") }.exceptionOrNull() is IllegalArgumentException)
            check(!destination.exists() && !File(destination.path + ".part").exists())
            evidence += "PASS malformed Content-Range rejected and partial invalidated"
            interrupted(); scope = "changed-account-or-package"
            check(runCatching { transfer("cut=1") }.exceptionOrNull() is java.io.IOException)
            check(File(destination.path + ".part").length() == 16384L) // Reset, not old+new bytes.
            PluginDownloadTransfer.clear(destination)
            check(runCatching { transfer("mode=weak&cut=1") }.exceptionOrNull() is java.io.IOException)
            check(!File(destination.path + ".part").exists())
            evidence += "PASS scope change restarts full request; weak validators cannot retain resumable bytes"

            val track = MusicTrack(MusicResourceId(source, "opaque"), "Retry fixture", listOf(MusicArtistRef(name = "Synthetic")))
            val first = ProviderDownloadTaskStore.Task(track, AudioQualityTier.Lossless, UUID.randomUUID().toString(), accountScope = "a".repeat(64))
            ProviderDownloadTaskStore(root).save(first)
            val restored = ProviderDownloadTaskStore(root).load().single()
            check(restored.track.id == track.id && restored.quality == first.quality && restored.errorCode == "DOWNLOAD_INTERRUPTED")
            check(restored.accountScope == first.accountScope)
            val newer = first.copy(revision = UUID.randomUUID().toString(), errorCode = "SOURCE_UNAVAILABLE")
            ProviderDownloadTaskStore(root).save(newer)
            ProviderDownloadTaskStore(root).remove(track.id, first.revision)
            check(ProviderDownloadTaskStore(root).load().single().revision == newer.revision)
            ProviderDownloadTaskStore(root).remove(track.id, newer.revision)
            check(ProviderDownloadTaskStore(root).load().isEmpty())
            evidence += "PASS actual AtomicFile retry-task restart, SourceId/quality preservation and stale cancellation revision isolation"
        } finally { root.deleteRecursively() }
    }
}
