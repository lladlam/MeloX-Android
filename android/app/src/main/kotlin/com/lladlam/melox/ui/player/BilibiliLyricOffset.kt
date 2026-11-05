package com.lladlam.melox.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.provider.bilibili.BilibiliLyricOffsetStore
import com.lladlam.melox.playback.PlaybackTrackIdentity

@Composable
internal fun rememberBilibiliLyricOffset(mediaId: String?): Int {
    val context = LocalContext.current.applicationContext
    val identity = remember(mediaId) { mediaId?.let(PlaybackTrackIdentity::decode) }
    if (identity?.sourceId?.builtIn != MusicSource.Bilibili) return 0
    val offset by BilibiliLyricOffsetStore.state(context, identity.value)
    return offset
}

internal fun effectiveBilibiliLyricAdvance(globalAdvanceMs: Int, trackOffsetMs: Int): Long =
    BilibiliLyricOffsetStore.effectiveAdvance(globalAdvanceMs, trackOffsetMs)

internal fun isBilibiliMediaId(mediaId: String?): Boolean =
    mediaId?.let(PlaybackTrackIdentity::decode)?.sourceId?.builtIn == MusicSource.Bilibili
