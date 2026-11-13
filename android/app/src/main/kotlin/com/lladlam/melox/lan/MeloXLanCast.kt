package com.lladlam.melox.lan

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MeloXCast"

/**
 * Process-wide tap into Media3's decoded PCM. Attached only to the main
 * playback player. During casting, PCM is sent before silencing local output.
 */
internal object MeloXCastAudioTap {
    @Volatile var inputEncoding: Int = C.ENCODING_PCM_16BIT
    @Volatile var inputChannels: Int = 2
    @Volatile private var listener: ((ByteArray) -> Unit)? = null
    @Volatile private var formatListener: ((MeloXLanCastFormat) -> Unit)? = null
    @Volatile var muteLocalOutput: Boolean = false
    @Volatile var format: MeloXLanCastFormat = MeloXLanCastFormat()
        private set

    fun attach(listener: (ByteArray) -> Unit) {
        this.listener = listener
    }

    fun detach(listener: (ByteArray) -> Unit) {
        if (this.listener === listener) this.listener = null
    }

    fun isActive(): Boolean = listener != null

    fun onFormatChanged(format: MeloXLanCastFormat) {
        if (this.format == format) return
        this.format = format
        formatListener?.invoke(format)
    }

    fun setFormatListener(listener: ((MeloXLanCastFormat) -> Unit)?) {
        formatListener = listener
    }

    fun clearFormatListener(listener: (MeloXLanCastFormat) -> Unit) {
        if (formatListener === listener) formatListener = null
    }

    internal fun offer(bytes: ByteArray) {
        listener?.invoke(bytes)
    }

    /** USB exclusive output may own the mute; only release it when no USB session is active. */
    internal fun restoreLocalOutput() {
        if (!com.lladlam.melox.usb.MeloXUsbAudioSession.active) muteLocalOutput = false
    }
}

@UnstableApi
internal class MeloXCastAudioProcessor(private val tapAudio: Boolean = true) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (tapAudio) {
            MeloXCastAudioTap.inputEncoding = inputAudioFormat.encoding
            MeloXCastAudioTap.inputChannels = inputAudioFormat.channelCount
        }
        if (tapAudio && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            MeloXCastAudioTap.onFormatChanged(
                MeloXLanCastFormat(
                    sampleRate = inputAudioFormat.sampleRate,
                    channels = inputAudioFormat.channelCount.coerceIn(1, 2),
                ),
            )
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return
        val output = replaceOutputBuffer(remaining)
        if (tapAudio && MeloXCastAudioTap.isActive()) {
            val data = ByteArray(remaining)
            inputBuffer.duplicate().get(data)
            inputBuffer.position(inputBuffer.limit())
            MeloXCastAudioTap.offer(data)
            if (MeloXCastAudioTap.muteLocalOutput) output.put(ByteArray(remaining)) else output.put(data)
        } else {
            if (MeloXCastAudioTap.muteLocalOutput) {
                output.put(ByteArray(remaining))
                inputBuffer.position(inputBuffer.limit())
            } else output.put(inputBuffer)
        }
        output.flip()
    }
}

internal class MeloXLanCastReceiver(
    private val scope: CoroutineScope,
) {
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    @Volatile private var clientSocket: Socket? = null
    @Volatile private var audioTrack: AudioTrack? = null

    fun start(format: MeloXLanCastFormat): MeloXLanCastHandshake? {
        stop()
        if (format.codec != "pcm_s16le") return null
        val token = java.security.SecureRandom().let { random ->
            ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        }
        val preparedTrack = buildTrack(format) ?: return null
        val socket = runCatching { ServerSocket(0) }.getOrElse {
            preparedTrack.release()
            Log.w(TAG, "Unable to open cast receiver port", it)
            return null
        }
        serverSocket = socket
        audioTrack = preparedTrack
        job = scope.launch(Dispatchers.IO) {
            try {
                val client = socket.accept()
                clientSocket = client
                client.soTimeout = 5_000
                val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                val presented = runCatching {
                    val length = input.readInt()
                    if (length !in 1..4096) return@runCatching null
                    ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
                }.getOrNull()
                if (presented != token) {
                    Log.w(TAG, "Cast audio connection rejected: bad token")
                    client.close()
                    return@launch
                }
                client.soTimeout = 0
                Log.i(TAG, "Cast audio connection accepted: ${client.inetAddress.hostAddress}")
                client.use { receive(it, format, input) }
            } catch (error: Exception) {
                if (serverSocket != null) Log.w(TAG, "Cast receiver stopped", error)
            } finally {
                releaseTrack()
            }
        }
        return MeloXLanCastHandshake(socket.localPort, format, token)
    }

    private fun receive(socket: Socket, format: MeloXLanCastFormat, input: DataInputStream) {
        val track = audioTrack ?: return
        track.play()
        var received = 0L
        while (scope.isActive && serverSocket != null) {
            val length = input.readInt()
            if (length <= 0 || length > 64 * 1024) break
            val data = ByteArray(length)
            input.readFully(data)
            var offset = 0
            while (offset < data.size) {
                val count = track.write(data, offset, data.size - offset, AudioTrack.WRITE_BLOCKING)
                check(count > 0) { "AudioTrack write failed: $count" }
                offset += count
            }
            if (received == 0L) Log.i(TAG, "First cast PCM received: ${data.size} bytes, $format")
            received += length
        }
    }

    private fun buildTrack(format: MeloXLanCastFormat): AudioTrack? = runCatching {
        val channelMask = if (format.channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(format.sampleRate)
            .setChannelMask(channelMask)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(
            format.sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(format.sampleRate * format.channels * 2 / 5)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }.onFailure { Log.w(TAG, "Unable to create cast AudioTrack", it) }.getOrNull()

    fun stop() {
        val socket = serverSocket
        serverSocket = null
        runCatching { socket?.close() }
        // Interrupt the blocking write first; the owning IO coroutine releases
        // the AudioTrack in its finally block, avoiding a cross-thread race.
        runCatching { clientSocket?.close() }
        clientSocket = null
        job?.cancel()
        job = null
    }

    private fun releaseTrack() {
        val track = audioTrack ?: return
        audioTrack = null
        runCatching { track.stop() }
        runCatching { track.release() }
    }
}

internal class MeloXLanCastSender(
    private val device: MeloXLanDevice,
    private val handshake: MeloXLanCastHandshake,
    private val scope: CoroutineScope,
    private val onStopped: (reason: String?) -> Unit = {},
) {
    private var socket: Socket? = null
    private var job: Job? = null
    private val stopped = java.util.concurrent.atomic.AtomicBoolean(false)
    private val queue = ArrayBlockingQueue<ByteArray>(12)
    private val tapListener: (ByteArray) -> Unit = { bytes -> offer(bytes) }
    private val formatListener: (MeloXLanCastFormat) -> Unit = { newFormat ->
        if (newFormat != handshake.format) stop("format_changed")
    }

    fun start(): Boolean {
        val target = runCatching {
            Socket().apply { tcpNoDelay = true; connect(InetSocketAddress(device.address, handshake.port), 3_000) }
        }.getOrElse {
            onStopped(it.message ?: "connect_failed")
            return false
        }
        socket = target
        MeloXCastAudioTap.attach(tapListener)
        MeloXCastAudioTap.setFormatListener(formatListener)
        job = scope.launch(Dispatchers.IO) {
            try {
                val output = DataOutputStream(target.getOutputStream())
                val tokenBytes = handshake.token.toByteArray(Charsets.UTF_8)
                output.writeInt(tokenBytes.size)
                output.write(tokenBytes)
                output.flush()
                var sent = 0L
                var emptySince = 0L
                while (scope.isActive && !stopped.get()) {
                    val packet = queue.poll(200, TimeUnit.MILLISECONDS)
                    if (packet == null) {
                        val state = MeloXLanRuntime.snapshot()
                        if (state?.playing == true && state.ready) {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (emptySince == 0L) emptySince = now
                            if (now - emptySince > 5_000L) {
                                Log.w(TAG, "Cast has no decoded PCM for 5s while player is READY; sent=$sent")
                                stop("no_decoded_pcm")
                            }
                        } else emptySince = 0L
                        continue
                    }
                    emptySince = 0L
                    output.writeInt(packet.size)
                    output.write(packet)
                    output.flush()
                    if (sent == 0L) Log.i(TAG, "First cast PCM sent: ${packet.size} bytes to ${device.address}:${handshake.port}")
                    sent += packet.size
                }
            } catch (error: Exception) {
                if (socket != null) onStopped(error.message)
            } finally {
                closeSocket()
            }
        }
        return true
    }

    private fun offer(bytes: ByteArray) {
        // Decoder buffers can exceed the wire's 64 KiB maximum. Send ~20 ms frames.
        val packetSize = (handshake.format.sampleRate / 50).coerceAtLeast(1) * handshake.format.channels * 2
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + packetSize, bytes.size)
            val packet = bytes.copyOfRange(offset, end)
            if (!queue.offer(packet)) {
                queue.poll()
                queue.offer(packet)
            }
            offset = end
        }
    }

    fun stop(reason: String? = null) {
        if (!stopped.compareAndSet(false, true)) return
        val wasRunning = socket != null || job != null
        closeSocket()
        if (wasRunning) onStopped(reason)
    }

    private fun closeSocket() {
        MeloXCastAudioTap.detach(tapListener)
        MeloXCastAudioTap.clearFormatListener(formatListener)
        runCatching { socket?.close() }
        socket = null
        job?.cancel()
        job = null
        queue.clear()
    }
}
