package com.lladlam.melox.core.recognition

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Capture 8 kHz mono playback audio through a user-authorized MediaProjection. */
internal object PlaybackAudioCapture {
    suspend fun capture(
        context: Context,
        resultCode: Int,
        data: Intent,
        durationSeconds: Int,
    ): FloatArray = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IOException("系统音频识别需要 Android 10 或更高版本")
        }
        val app = context.applicationContext
        val serviceIntent = Intent(app, MeloXRecognitionCaptureService::class.java)
        ContextCompat.startForegroundService(app, serviceIntent)
        // Give the mediaProjection FGS a chance to enter the foreground before
        // getMediaProjection(), as required by Android 10+.
        delay(180)
        val manager = app.getSystemService(MediaProjectionManager::class.java)
            ?: throw IOException("系统不支持屏幕录制")
        val projection = manager.getMediaProjection(resultCode, data)
            ?: throw IOException("无法创建屏幕录制会话")
        var recorder: AudioRecord? = null
        try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SongRecognitionClient.SampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val minimum = AudioRecord.getMinBufferSize(
                SongRecognitionClient.SampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minimum <= 0) throw IOException("设备不支持系统音频录制")
            recorder = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(max(minimum * 2, 4096))
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw IOException("系统音频录制初始化失败")
            }
            val target = durationSeconds * SongRecognitionClient.SampleRate
            val pcm = ShortArray(target)
            var offset = 0
            recorder.startRecording()
            while (offset < target) {
                val count = recorder.read(pcm, offset, minOf(2048, target - offset), AudioRecord.READ_BLOCKING)
                if (count < 0) throw IOException("系统音频读取失败：$count")
                if (count == 0) continue
                offset += count
            }
            if (offset < SongRecognitionClient.SampleRate) {
                throw IOException("没有收到足够的系统音频")
            }
            FloatArray(offset) { pcm[it] / 32768f }
        } finally {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { projection.stop() }
            runCatching { app.stopService(serviceIntent) }
        }
    }
}
