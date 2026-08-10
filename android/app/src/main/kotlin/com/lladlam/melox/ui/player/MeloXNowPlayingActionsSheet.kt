package com.lladlam.melox.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.glass.meloXLiquidButton
import java.net.URLEncoder

private enum class ActionPage { Main, Sleep }

/**
 * Same-window Now Playing overlay.
 *
 * This intentionally does not use Dialog: a platform Dialog owns a separate
 * window and therefore cannot sample the Compose Backdrop recorded from the
 * player underneath it. SharedHost places this composable outside the recorded
 * Now Playing layer and provides that layer as LocalMeloXBackdrop, so the glass
 * sheet blurs the actual artwork/lyrics/queue page instead of the app behind it.
 */
@Composable
fun MeloXNowPlayingActionsSheet(
    state: MeloXPlaybackUiState,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var page by remember(state.mediaId) { mutableStateOf(ActionPage.Main) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = 520f)),
        exit = fadeOut(spring(stiffness = 620f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(horizontal = 18.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val sheetInteraction = remember { MutableInteractionSource() }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(30.dp),
                        tint = Color.White.copy(alpha = 0.08f),
                        surfaceColor = Color.Black.copy(alpha = 0.12f),
                        blurRadius = 14.dp,
                        lensRadius = 20.dp,
                        refractionHeight = 22.dp,
                    )
                    // Consume sheet taps so they never reach the dismiss scrim.
                    .clickable(
                        interactionSource = sheetInteraction,
                        indication = null,
                        onClick = {},
                    ),
                color = Color.Transparent,
                shape = RoundedCornerShape(30.dp),
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (fadeIn(spring(stiffness = 520f)) + scaleIn(initialScale = 0.96f)) togetherWith
                            (fadeOut(spring(stiffness = 620f)) + scaleOut(targetScale = 0.96f))
                    },
                    label = "now-playing-actions-page",
                ) { selected ->
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
                        when (selected) {
                            ActionPage.Main -> MainActions(state, context, onDismiss) {
                                page = ActionPage.Sleep
                            }
                            ActionPage.Sleep -> SleepActions(state, onDismiss) {
                                page = ActionPage.Main
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainActions(
    state: MeloXPlaybackUiState,
    context: Context,
    onDismiss: () -> Unit,
    onSleep: () -> Unit,
) {
    ActionHeader(state, "歌曲操作")
    ActionItem("定时关闭", "◷", onSleep)
    ActionItem("添加到播放队列", "+") { state.addCurrentToQueue(); onDismiss() }
    ActionItem("添加到歌单", "≡") { openSong(context, state); onDismiss() }
    ActionItem("收藏 / 取消收藏", "♥") { openSong(context, state); onDismiss() }
    ActionItem("分享", "↗") { shareSong(context, state); onDismiss() }
    ActionItem("评论", "◌") { openSong(context, state, "#comment-box"); onDismiss() }
    ActionItem("歌曲百科", "i") { openSong(context, state); onDismiss() }
    ActionItem("一起听", "◎") { openSong(context, state); onDismiss() }
    ActionItem("前往专辑", "▣") { openSearch(context, state.album, 10); onDismiss() }
    ActionItem("前往歌手", "♬") { openSearch(context, state.artist, 100); onDismiss() }
}

@Composable
private fun SleepActions(
    state: MeloXPlaybackUiState,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
) {
    ActionHeader(state, "定时关闭")
    listOf(15, 30, 45, 60).forEach { minutes ->
        ActionItem("$minutes 分钟后", "◷") {
            state.setSleepTimer(minutes)
            onDismiss()
        }
    }
    if (state.sleepTimerEndRealtimeMs > 0L) {
        ActionItem("取消定时", "×") {
            state.cancelSleepTimer()
            onDismiss()
        }
    }
    ActionItem("返回", "‹", onBack)
}

@Composable
private fun ActionHeader(state: MeloXPlaybackUiState, title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.58f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = state.title.ifBlank { "正在播放" },
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
    )
}

@Composable
private fun ActionItem(title: String, symbol: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
    }
}

private fun shareSong(context: Context, state: MeloXPlaybackUiState) {
    val url = "https://music.163.com/song?id=${state.mediaId.orEmpty()}"
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "${state.title} - ${state.artist}\n$url"),
            "分享歌曲",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun openSong(context: Context, state: MeloXPlaybackUiState, suffix: String = "") {
    openUrl(context, "https://music.163.com/#/song?id=${state.mediaId.orEmpty()}$suffix")
}

private fun openSearch(context: Context, query: String, type: Int) {
    if (query.isBlank()) return
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    openUrl(context, "https://music.163.com/#/search/m/?s=$encoded&type=$type")
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
