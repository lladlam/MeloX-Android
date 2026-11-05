package com.lladlam.melox.core.plugin

import android.app.Instrumentation
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lladlam.melox.core.music.model.*
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.playback.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Public synthetic fixtures only; opt in with -e online true. No credentials, platform accounts or music. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object PluginRcOnlineChecks {
    fun run(test: Instrumentation, evidence: MutableList<String>) {
        val context = test.targetContext
        val source = SourceId.parse("plugin:org.melox.rc.online${UUID.randomUUID().toString().replace("-", "")}")!!
        val manifest = PluginManifest(source, "RC synthetic media", "1.0.0", 1, "index.js", setOf(PluginCapability.Playback), setOf("httpbingo.org"))
        val wav = ByteBuffer.allocate(2044).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(2036); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(8000); putInt(8000); putShort(1); putShort(8)
            put("data".toByteArray()); putInt(2000)
            repeat(2000) { put((128 + 24 * kotlin.math.sin(2 * Math.PI * 440 * it / 8000)).toInt().toByte()) }
        }.array()
        val url = fixtureUrl(wav, "audio/wav")
        val script = "globalThis.meloxPlugin={resolvePlayback:async()=>({type:'playable',format:'wav',url:${JSONObject.quote(url)}})};"
        val registry = MeloXMusicProviders.create(context)
        PluginRuntime(context, PluginArchiveReader.Package(manifest, script)).use { runtime ->
            val provider = PluginMusicProvider(manifest, runtime)
            registry.registerDynamic(provider, enabled = true)
            try {
                artwork(test, manifest, evidence)
                val resolver = ProviderPlaybackResolver(NeteasePlaybackResolver(), registry)
                val secure = androidx.media3.datasource.DataSource.Factory {
                    PluginMediaDataSource(androidx.media3.datasource.DefaultDataSource.Factory(context))
                }
                val factory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    androidx.media3.datasource.ResolvingDataSource.Factory(secure, resolver))
                val done = CountDownLatch(1)
                val error = AtomicReference<Throwable?>()
                var player: ExoPlayer? = null
                var transitions = 0
                var sought = false
                try {
                    test.runOnMainSync {
                        player = ExoPlayer.Builder(context).setMediaSourceFactory(factory).build().apply {
                            volume = 0f
                            addListener(object : Player.Listener {
                                override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) { transitions++ }
                                override fun onPlaybackStateChanged(state: Int) {
                                    if (state == Player.STATE_READY && !sought) { sought = true; seekTo(100) }
                                    if (state == Player.STATE_ENDED) done.countDown()
                                }
                                override fun onPlayerError(failure: androidx.media3.common.PlaybackException) { error.set(failure); done.countDown() }
                            })
                            setMediaItems((1..2).map { ProviderPlaybackCommands.mediaItemFor(MusicTrack(MusicResourceId(source, "tone-$it"), "CC0 tone", listOf(MusicArtistRef(name = "MeloX RC")))) })
                            playWhenReady = true; prepare()
                        }
                    }
                    check(done.await(40, TimeUnit.SECONDS)) { "Media3 online fixture timeout" }
                    error.get()?.let { throw it }
                    check(sought && transitions >= 2) { "Media3 seek/transition was not observed" }
                    evidence += "PASS Media3 real QuickJS resolve -> dedicated Broker -> WAV decode/playback, seek and two-track transition (muted test player, not background service)"
                } finally { test.runOnMainSync { player?.release() } }
            } finally { registry.unregisterDynamic(source) }
            val model = PluginArtwork.wrap(manifest, fixtureUrl(png(1), "image/png"))
            val unavailable = runBlocking { coil3.SingletonImageLoader.get(context).execute(coil3.request.ImageRequest.Builder(context).data(model).build()) }
            check(unavailable is coil3.request.ErrorResult) { "disabled source artwork was served" }
            evidence += "PASS Coil denies source after unregister (no image cache bypass)"
        }
    }

    private fun artwork(test: Instrumentation, manifest: PluginManifest, evidence: MutableList<String>) = runBlocking {
        val loader = coil3.SingletonImageLoader.get(test.targetContext)
        val small = PluginArtwork.wrap(manifest, fixtureUrl(png(1), "image/png"))
        val result = loader.execute(coil3.request.ImageRequest.Builder(test.targetContext).data(small).build())
        check(result is coil3.request.SuccessResult && result.image.width == 1 && result.image.height == 1) { "Coil artwork decode failed" }
        val excessive = PluginArtwork.wrap(manifest, fixtureUrl(png(4097), "image/png"))
        val rejected = loader.execute(coil3.request.ImageRequest.Builder(test.targetContext).data(excessive).build())
        check(rejected is coil3.request.ErrorResult) { "oversized artwork dimensions accepted" }
        evidence += "PASS Coil real controlled PNG decode and 4097-pixel dimension rejection"
    }
    private fun png(width: Int): ByteArray {
        val image = Bitmap.createBitmap(width, 1, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().also { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray() }
        finally { image.recycle() }
    }
    private fun fixtureUrl(bytes: ByteArray, mime: String): String = "https://httpbingo.org/base64/" +
        Uri.encode(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)) + "?content-type=" + Uri.encode(mime)
}
