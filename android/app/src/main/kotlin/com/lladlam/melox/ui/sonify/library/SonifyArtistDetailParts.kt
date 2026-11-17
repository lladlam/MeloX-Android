/*
 * Sonify artist-page parts (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/artists/ArtistDetailParts.kt (master @ 209e1aa).
 * The artist detail hero itself lives in ui/sonify/SonifyArtistDetailPage.kt
 * (the already-migrated foundation). This file keeps the small section/error
 * primitives used by the library artist lists and the artist detail loading
 * paths, without importing Sonify's data objects.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SonifyArtistSectionError(
    error: String,
    busy: Boolean = false,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
        Text(error, fontSize = 15.sp, modifier = Modifier.alpha(0.65f))
        Spacer(Modifier.height(8.dp))
        Text(
            text = "重试",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(enabled = !busy, role = Role.Button, onClick = onRetry)
                .padding(vertical = 6.dp)
                .alpha(if (busy) 0.45f else 1f),
        )
    }
}

@Composable
internal fun SonifyArtistListFooterLoading() {
    Text(
        text = "加载中…",
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .alpha(0.5f),
    )
}

@Composable
internal fun SonifyArtistSkeletonBlock(modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LocalContentColor.current.copy(alpha = 0.09f)),
    )
}

@Composable
internal fun SonifyArtistActionButton(
    content: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(LocalContentColor.current.copy(alpha = 0.18f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(content, fontSize = 18.sp)
    }
}
