package com.lladlam.melox.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.lladlam.melox.core.music.provider.MusicProviderRegistry
import com.lladlam.melox.core.plugin.PluginHttpBroker
import com.lladlam.melox.core.plugin.PluginMusicProvider
import java.io.IOException

internal data class PluginMediaRequest(val provider: PluginMusicProvider, val registry: MusicProviderRegistry, val generation: Long,
    val credentialRequested: Boolean = false, val reResolve: (() -> DataSpec)? = null)

/** Applies plugin network policy to actual Media3 socket opens, not just resolved URL strings. */
@androidx.annotation.OptIn(UnstableApi::class)
class PluginMediaDataSource(private val fallback: DataSource.Factory) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null
    private var broker: PluginHttpBroker? = null
    private var policy: PluginMediaRequest? = null
    private var transferred = 0L
    private var retrying = false

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener); delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(delegate == null)
        policy = dataSpec.customData as? PluginMediaRequest
        val request = policy
        try {
        val source = if (request != null) {
            checkEnabled(request)
            val secure = request.provider.mediaBroker()
            broker = secure
            secure.authorizeMediaUrl(dataSpec.uri.toString())
            OkHttpDataSource.Factory(secure.mediaClient(request.credentialRequested)).createDataSource()
        } else fallback.createDataSource()
        delegate = source
        listeners.forEach(source::addTransferListener)
            val size = source.open(dataSpec)
            if (request != null && size > MAX_MEDIA_BYTES) throw IOException("Plugin media exceeds quota")
            return size
        } catch (error: Throwable) {
            close()
            // Only playback opens are retried, once; never replay account/library writes.
            if (!retrying && request?.reResolve != null && error is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException &&
                error.responseCode in setOf(401, 403, 410)) {
                checkEnabled(request)
                retrying = true
                try { return open(request.reResolve.invoke()) } finally { retrying = false }
            }
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        policy?.let(::checkEnabled)
        val count = (delegate ?: error("data source not opened")).read(buffer, offset, length)
        if (count > 0 && policy != null) {
            transferred += count
            if (transferred > MAX_MEDIA_BYTES) throw IOException("Plugin media exceeds quota")
        }
        return count
    }

    override fun getUri(): Uri? = delegate?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()
    override fun close() {
        try { delegate?.close() } finally {
            delegate = null; broker?.close(); broker = null; policy = null; transferred = 0
        }
    }

    private fun checkEnabled(request: PluginMediaRequest) {
        if (request.registry.generation != request.generation || request.registry[request.provider.sourceId] !== request.provider)
            throw IOException("Plugin media source changed or disabled")
    }

    private companion object { const val MAX_MEDIA_BYTES = 512L * 1024 * 1024 }
}

/** Dynamic media bypasses the shared byte cache so disabling cannot be bypassed by a cache hit. */
@androidx.annotation.OptIn(UnstableApi::class)
class PluginCacheRoutingDataSource(
    private val cached: DataSource.Factory,
    private val uncached: DataSource.Factory,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null
    override fun addTransferListener(transferListener: TransferListener) { listeners.add(transferListener) }
    override fun open(dataSpec: DataSpec): Long {
        check(delegate == null)
        val uri = dataSpec.uri
        val identity = uri.pathSegments.firstOrNull()?.let(com.lladlam.melox.core.music.model.SourceId::parse)
        val dynamic = uri.scheme == "melox" && uri.host == "track" && identity?.builtIn == null
        val source = (if (dynamic) uncached else cached).createDataSource()
        delegate = source
        listeners.forEach(source::addTransferListener)
        return try { source.open(dataSpec) } catch (error: Throwable) { close(); throw error }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int) = (delegate ?: error("data source not opened")).read(buffer, offset, length)
    override fun getUri(): Uri? = delegate?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()
    override fun close() { try { delegate?.close() } finally { delegate = null } }
}
