package com.lladlam.melox.ui.player

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.ui.glass.meloXLiquidButton
import kotlinx.coroutines.launch

@Composable
internal fun MeloXLyricShareDialog(
    state: MeloXPlaybackUiState,
    lines: List<LyricLine>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val foreground = MaterialTheme.colorScheme.onBackground
    val surfaceForeground = MaterialTheme.colorScheme.onSurface
    val scope = rememberCoroutineScope()
    var selected by remember(lines, initialIndex) {
        mutableStateOf(if (lines.isEmpty()) emptySet() else setOf(initialIndex.coerceIn(lines.indices)))
    }
    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("取消", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                Text("分享歌词", Modifier.weight(1f), color = foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("全选", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { selected = lines.indices.toSet() }.padding(8.dp))
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(state.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(state.title, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(state.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = foreground.copy(alpha = .58f))
                    Text("已选择 ${selected.size} 行 · 无字符数量限制", fontSize = 12.sp, color = foreground.copy(alpha = .48f), modifier = Modifier.padding(top = 4.dp))
                }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(lines, key = { index, line -> "share-$index-${line.timeMs}" }) { index, line ->
                    val chosen = index in selected
                    val shape = RoundedCornerShape(when {
                        !chosen -> 14.dp
                        index - 1 !in selected && index + 1 in selected -> 14.dp
                        index - 1 in selected && index + 1 !in selected -> 14.dp
                        else -> 5.dp
                    })
                    Box(
                        Modifier.fillMaxWidth().clip(shape)
                            .background(if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else MaterialTheme.colorScheme.surface)
                            .clickable {
                                selected = if (chosen) selected - index else selected + index
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(line.text, color = surfaceForeground, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShareButton("分享文本", Modifier.weight(1f), selected.isNotEmpty() && !generating) {
                    val chosen = selected.sorted().mapNotNull(lines::getOrNull)
                    val text = buildString {
                        append(chosen.joinToString("\n") { it.text })
                        append("\n——《${state.title}》 · ${state.artist}")
                    }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "分享歌词"))
                }
                ShareButton(if (generating) "生成中…" else "生成图片", Modifier.weight(1f), selected.isNotEmpty() && !generating) {
                    generating = true; error = null
                    scope.launch {
                        runCatching { shareLyricImage(context, state, selected.sorted().mapNotNull(lines::getOrNull)) }
                            .onFailure { error = it.message ?: "歌词图片生成失败" }
                        generating = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareButton(text: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.height(50.dp).meloXLiquidButton(
            shape = RoundedCornerShape(25.dp),
            enabled = enabled,
            surfaceColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
        ).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .35f), fontWeight = FontWeight.SemiBold)
    }
}
