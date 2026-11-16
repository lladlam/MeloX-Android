package com.lladlam.melox.ui.sonify

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.ui.MeloXBottomContentClearance

/** Sonify Home.kt layout: a large page title plus the fixed-width recommendation pager. */
@Composable
internal fun ImportedHomePage(
    tracks: List<MusicTrack>,
    onPlay: (MusicTrack) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 44.dp, bottom = MeloXBottomContentClearance + 90.dp),
    ) {
        item("title") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.tab_home), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("•••", Modifier.clickable(onClick = onOpenSettings).padding(12.dp), fontSize = 20.sp)
            }
        }
        item("RecommendCard") {
            RecommendCard(tracks, onPlay)
        }
    }
}

@Composable
private fun RecommendCard(tracks: List<MusicTrack>, onPlay: (MusicTrack) -> Unit) {
    if (tracks.isEmpty()) return
    val items = remember(tracks) { tracks.shuffled().take(5) }
    val pagerState = rememberPagerState(pageCount = { items.size })
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            "继续收听",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(278.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 136.dp),
            key = { items[it].id.value },
            beyondViewportPageCount = 1,
        ) { page ->
            RecommendCardItem(items[page]) { onPlay(items[page]) }
        }
    }
}

@Composable
private fun RecommendCardItem(music: MusicTrack, onClick: () -> Unit) {
    Column(Modifier.width(268.dp)) {
        Column(
            Modifier
                .height(354.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                music.artworkUrl,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(268.dp),
            )
            Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = .30f), Color.Black)),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(music.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(music.artistText, fontSize = 13.sp, color = Color.White.copy(alpha = .62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(music.album?.name.orEmpty(), fontSize = 13.sp, color = Color.White.copy(alpha = .62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
