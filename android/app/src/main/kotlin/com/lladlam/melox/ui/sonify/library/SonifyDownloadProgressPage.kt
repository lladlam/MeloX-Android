package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.download.MeloXActiveDownload
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.ui.sonify.Title

/** Only in-flight/paused downloads. Completed items live in the local library. */
@Composable
internal fun SonifyDownloadProgressPage(
    downloads: MeloXDownloadStore,
    onBack: () -> Unit,
) {
    val active = downloads.activeDownloads.values.toList()
    val paused = downloads.pausedDownloads.values.toList()
    Title(title = "下载", onBack = onBack) {
        if (active.isEmpty() && paused.isEmpty()) {
            item("empty") {
                Text(
                    "没有正在下载的歌曲",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                )
            }
        } else {
            if (active.isNotEmpty()) {
                item("active-header") {
                    Text(
                        "正在下载 · ${active.size} 首",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                    )
                }
                items(active.size, key = { "active-${active[it].song.id}" }) { index ->
                    DownloadProgressRow(
                        item = active[index],
                        paused = false,
                        onPause = { downloads.pause(active[index].song.id) },
                        onResume = null,
                        onCancel = { downloads.cancel(active[index].song.id) },
                    )
                }
            }
            if (paused.isNotEmpty()) {
                item("paused-header") {
                    Text(
                        "已暂停 · ${paused.size} 首",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                    )
                }
                items(paused.size, key = { "paused-${paused[it].song.id}" }) { index ->
                    DownloadProgressRow(
                        item = paused[index],
                        paused = true,
                        onPause = null,
                        onResume = { downloads.resume(paused[index].song.id) },
                        onCancel = { downloads.cancel(paused[index].song.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressRow(
    item: MeloXActiveDownload,
    paused: Boolean,
    onPause: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.song.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                item.song.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Text(
                item.song.artists,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                fontSize = 12.sp,
            )
            val fraction = item.fractionCompleted
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                )
                Text(
                    "${(fraction * 100).toInt()}% · ${formatBytes(item.bytesPerSecond)}/s",
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (onPause != null) Text(
                    if (paused) "继续" else "暂停",
                    modifier = Modifier.padding(top = 3.dp).clickable(enabled = !paused) { onPause?.invoke() },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (onResume != null) Text(
                    "继续",
                    modifier = Modifier.padding(top = 3.dp).clickable { onResume.invoke() },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "取消",
                    modifier = Modifier.padding(top = 3.dp).clickable(onClick = onCancel),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun formatBytes(value: Long): String = when {
    value >= 1_048_576 -> "%.1f MB".format(value / 1_048_576f)
    value >= 1_024 -> "%.0f KB".format(value / 1_024f)
    else -> "$value B"
}
