package com.lladlam.melox.ui.sonify.library

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.download.MeloXActiveDownload
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXPinnedListPage

/**
 * Settings-style download page. Only in-flight/paused tasks live here; completed
 * downloads are represented by the local library section.
 */
@Composable
internal fun SonifyDownloadProgressPage(
    downloads: MeloXDownloadStore,
    onBack: () -> Unit,
) {
    val active = downloads.activeDownloads.values.toList()
    val paused = downloads.pausedDownloads.values.toList()
    MeloXPinnedListPage(
        title = "下载",
        onNavigateBack = onBack,
        bottomPadding = MeloXBottomContentClearance,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (active.isEmpty() && paused.isEmpty()) {
            item("empty") {
                MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
                    Text(
                        "没有正在下载的歌曲",
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                        fontSize = 15.sp,
                    )
                }
            }
            return@MeloXPinnedListPage
        }
        if (active.isNotEmpty()) {
            item("active-header") {
                SectionLabel("正在下载 · ${active.size} 首")
            }
            item("active-group") {
                MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
                    active.forEachIndexed { index, item ->
                        DownloadProgressSection(
                            item = item,
                            paused = false,
                            showSeparator = index > 0,
                            onPause = { downloads.pause(item.song.id) },
                            onResume = null,
                            onCancel = { downloads.cancel(item.song.id) },
                        )
                    }
                }
            }
        }
        if (paused.isNotEmpty()) {
            item("paused-header") { SectionLabel("已暂停 · ${paused.size} 首") }
            item("paused-group") {
                MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
                    paused.forEachIndexed { index, item ->
                        DownloadProgressSection(
                            item = item,
                            paused = true,
                            showSeparator = index > 0,
                            onPause = null,
                            onResume = { downloads.resume(item.song.id) },
                            onCancel = { downloads.cancel(item.song.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun DownloadProgressSection(
    item: MeloXActiveDownload,
    paused: Boolean,
    showSeparator: Boolean,
    onPause: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    val separator = MaterialTheme.colorScheme.onSurface.copy(alpha = .10f)
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (showSeparator) Modifier.drawTopSeparator(separator) else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.song.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(7.dp)),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    item.song.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    item.song.artists,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                    fontSize = 12.sp,
                )
            }
            val fraction = item.fractionCompleted
            Text(
                if (fraction == null) "…" else "${(fraction * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        AppleProgressBar(
            fraction = item.fractionCompleted,
            paused = paused,
            modifier = Modifier.padding(start = 58.dp, end = 1.dp, top = 9.dp),
        )
        Row(
            Modifier.padding(start = 58.dp, top = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildString {
                    append(formatBytes(item.bytesPerSecond))
                    append("/s")
                    item.expectedByteCount?.let {
                        append(" · ")
                        append(formatBytes(it))
                    }
                },
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f),
                fontSize = 10.sp,
            )
            if (onPause != null) ActionText(if (paused) "继续" else "暂停", MaterialTheme.colorScheme.primary, onPause)
            if (onResume != null) ActionText("继续", MaterialTheme.colorScheme.primary, onResume)
            ActionText("取消", MaterialTheme.colorScheme.error, onCancel)
        }
    }
}

@Composable
private fun ActionText(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.clickable(onClick = onClick).padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** Thin, capsule-shaped Apple-style progress bar (no Material track/stop indicator). */
@Composable
private fun AppleProgressBar(
    fraction: Float?,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val target = fraction?.coerceIn(0f, 1f) ?: 0f
    val animated by animateFloatAsState(target, tween(260), label = "download-progress")
    val infinite = rememberInfiniteTransition(label = "download-indeterminate")
    val indeterminate by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "download-indeterminate-offset",
    )
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = .13f)
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = if (paused) .35f else .78f)
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        if (fraction != null) {
            if (animated > .001f) {
                Box(
                    Modifier
                        .fillMaxWidth(animated)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(fill),
                )
            }
        } else {
            Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                val segment = size.width * .28f
                val x = (size.width + segment) * indeterminate - segment
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(x, 0f),
                    size = Size(segment, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
        }
    }
}

private fun Modifier.drawTopSeparator(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(
        Modifier.drawBehind {
            drawLine(
                color = color,
                start = Offset(16.dp.toPx(), 0f),
                end = Offset(size.width - 16.dp.toPx(), 0f),
                strokeWidth = 1.dp.toPx(),
            )
        },
    )

private fun formatBytes(value: Long): String = when {
    value >= 1_048_576 -> "%.1f MB".format(value / 1_048_576f)
    value >= 1_024 -> "%.0f KB".format(value / 1_024f)
    else -> "$value B"
}
