package com.lladlam.melox.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.lyrics.LyricLine

@Composable
internal fun MeloXUnsynchronizedLyrics(lines: List<LyricLine>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 32.dp)) {
        item {
            Text("歌词暂无时间轴 · 可手动滚动", color = Color.White.copy(alpha = .55f),
                fontSize = 14.sp, modifier = Modifier.padding(bottom = 24.dp))
        }
        items(lines) { line ->
            Text(line.text, color = Color.White.copy(alpha = .85f), fontSize = 25.sp,
                lineHeight = 36.sp, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}
