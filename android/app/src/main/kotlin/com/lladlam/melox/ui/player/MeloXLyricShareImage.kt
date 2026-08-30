package com.lladlam.melox.ui.player

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Path
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.ui.settings.MeloXPlayerBackgroundMode
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toArgb

internal suspend fun shareLyricImage(
    context: Context,
    state: MeloXPlaybackUiState,
    lines: List<LyricLine>,
) {
    val uri = withContext(Dispatchers.IO) {
        require(lines.isNotEmpty()) { "请至少选择一行歌词" }
    val width = 1080
    val headerHeight = 340
    val lineHeight = 86
    val height = (headerHeight + lines.size * lineHeight + 140).coerceAtMost(4_096)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cover = ArtworkDynamicPaletteProvider.bitmapFor(context, state.artworkUrl)
    val palette = ArtworkDynamicPaletteProvider.paletteFor(context, state.artworkUrl)
    when (MeloXSettingsRuntime.playerBackgroundMode) {
        MeloXPlayerBackgroundMode.FlowingLight -> {
            canvas.drawColor(palette.average.toArgb())
            val colors = palette.cells.ifEmpty { listOf(palette.average) }
            colors.forEachIndexed { index, color ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                    alpha = 135
                }
                val x = width * ((index % 3) + .5f) / 3f
                val y = height * ((index / 3) + .5f) / 3f
                canvas.drawCircle(x, y, width * .55f, paint)
            }
        }
        MeloXPlayerBackgroundMode.AppleLyrics -> {
            canvas.drawColor(palette.average.toArgb())
            cover?.let { source ->
                val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader; alpha = 120 }
                canvas.save(); canvas.rotate(-12f, width / 2f, height / 2f); canvas.drawRect(-180f, -180f, width + 180f, height + 180f, paint); canvas.restore()
            }
        }
        MeloXPlayerBackgroundMode.BlurredArtwork -> {
            canvas.drawColor(Color.rgb(28, 28, 30))
            cover?.let { source -> drawCenterCrop(canvas, source, RectF(0f, 0f, width.toFloat(), height.toFloat()), 145) }
        }
        MeloXPlayerBackgroundMode.MeiMesh -> {
            canvas.drawColor(palette.average.toArgb())
            palette.cells.take(4).forEachIndexed { index, color ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                    alpha = 120
                }
                val x = width * ((index % 2) + .5f) / 2f
                val y = height * ((index / 2) + .5f) / 2f
                canvas.drawCircle(x, y, width * .62f, paint)
            }
        }
    }
    val overlay = Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), Color.argb(25, 255, 255, 255), Color.argb(185, 0, 0, 0), android.graphics.Shader.TileMode.CLAMP) }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlay)

    val coverRect = RectF(72f, 70f, 292f, 290f)
    val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(55, 255, 255, 255) }
    canvas.drawRoundRect(coverRect, 34f, 34f, coverPaint)
    cover?.let { source ->
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(coverRect, 34f, 34f, Path.Direction.CW) })
        drawCenterCrop(canvas, source, coverRect, 255)
        canvas.restore()
    }
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 48f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255); textSize = 31f }
    canvas.drawText(state.title.ifBlank { "正在播放" }.take(24), 340f, 155f, title)
    canvas.drawText(state.artist.take(34), 340f, 215f, subtitle)
    canvas.drawText("MeloX Lyrics", 340f, 270f, subtitle)

    val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 40f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    lines.forEachIndexed { index, line ->
        canvas.drawText(line.text.take(36), 78f, headerHeight + index * lineHeight + 55f, lyricPaint)
    }

        val fileName = "MeloX-Lyrics-${System.currentTimeMillis()}.png"
        val sharedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MeloX")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val value = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建系统图片")
            runCatching {
                context.contentResolver.openOutputStream(value)?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "歌词图片编码失败" }
                } ?: error("无法写入系统图片")
                context.contentResolver.update(
                    value,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
                value
            }.getOrElse { error ->
                context.contentResolver.delete(value, null, null)
                throw error
            }
        } else {
            val folder = File(context.cacheDir, "lyric_share").apply { mkdirs() }
            val file = File(folder, fileName)
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "歌词图片编码失败" }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        bitmap.recycle()
        cover?.recycle()
        sharedUri
    }
    withContext(Dispatchers.Main.immediate) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setDataAndType(uri, "image/*")
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${state.title} - ${state.artist}")
            clipData = ClipData.newUri(context.contentResolver, "MeloX lyric image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        }
        context.packageManager.queryIntentActivities(intent, 0).forEach { target ->
            context.grantUriPermission(
                target.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val chooser = Intent.createChooser(intent, "分享歌词图片").apply {
            clipData = intent.clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, target: RectF, alpha: Int) {
    val scale = maxOf(target.width() / bitmap.width, target.height() / bitmap.height)
    val sourceWidth = target.width() / scale
    val sourceHeight = target.height() / scale
    val left = (bitmap.width - sourceWidth) / 2f
    val top = (bitmap.height - sourceHeight) / 2f
    canvas.drawBitmap(
        bitmap,
        Rect(left.toInt(), top.toInt(), (left + sourceWidth).toInt(), (top + sourceHeight).toInt()),
        target,
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha },
    )
}
