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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.discovery.DiscoveryCollection
import com.lladlam.melox.ui.discovery.DiscoveryTrack
import com.lladlam.melox.ui.discovery.HomeAccountUi
import com.lladlam.melox.ui.discovery.HomeBlock
import com.lladlam.melox.ui.discovery.HomeQuickActions
import androidx.compose.ui.res.stringResource

/**
 * Sonify-style home. This is intentionally a separate page from MeloXHomeLayout:
 * it owns the large page title, the wide recommendation rail and the compact
 * grouped track cards, while all data and playback callbacks still come from
 * MeloX's existing HomeBlock pipeline.
 */
@Composable
internal fun SonifyHomePage(
    source: MusicSource,
    account: HomeAccountUi?,
    blocks: List<HomeBlock>?,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    activeAction: String?,
    onQuickAction: (String) -> Unit,
    onCollection: (DiscoveryCollection) -> Unit,
    onTrack: (List<DiscoveryTrack>, DiscoveryTrack) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 10.dp, bottom = MeloXBottomContentClearance + 90.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "sonify-home-title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tab_home),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.5).sp,
                )
                Spacer(Modifier.weight(1f))
                SonifyAccountButton(account)
            }
        }
        if (blocks == null) {
            item { Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator() else Text(error, color = MaterialTheme.colorScheme.error)
            } }
            return@LazyColumn
        }
        val hero = blocks.filterIsInstance<HomeBlock.Tracks>().firstOrNull()?.values?.firstOrNull()
        if (hero != null) {
            item(key = "sonify-home-hero") {
                SonifyContinueCard(hero) { onTrack(blocks.filterIsInstance<HomeBlock.Tracks>().firstOrNull()?.values.orEmpty(), hero) }
            }
        }
        blocks.forEach { block ->
            when (block) {
                HomeBlock.QuickActions -> Unit
                is HomeBlock.Collections -> {
                    item { SonifySectionHeader(block.title, block.trailing, onRefresh) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(block.values, key = { "sonify-collection-${it.key}" }) { collection ->
                                SonifyCollectionCard(collection) { onCollection(collection) }
                            }
                        }
                    }
                }
                is HomeBlock.Tracks -> {
                    item { SonifySectionHeader(block.title, block.trailing, onRefresh) }
                    item { SonifyTrackRail(block.values, onTrack) }
                }
                is HomeBlock.Podcasts -> {
                    item { SonifySectionHeader("播客", "", onRefresh) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(block.values, key = { "sonify-podcast-${it.programId ?: it.id}" }) { podcast ->
                                Column(Modifier.width(150.dp)) {
                                    AsyncImage(
                                        podcast.artworkUrl,
                                        null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(150.dp).clip(RoundedCornerShape(18.dp)),
                                    )
                                    Text(
                                        podcast.name,
                                        modifier = Modifier.padding(top = 8.dp),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        error?.let { message ->
            item { Text(message, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error) }
        }
        if (blocks.isEmpty() && error == null) {
            item { Text("当前没有可展示的首页内容", Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f)) }
        }
    }
}

@Composable
private fun SonifyContinueCard(track: DiscoveryTrack, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .10f))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            AsyncImage(
                track.artworkUrl,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .72f),
                    ),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                Text("继续收听", color = Color.White.copy(alpha = .72f), fontSize = 14.sp)
                Text(
                    track.title,
                    modifier = Modifier.padding(top = 4.dp),
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(track.artist, color = Color.White.copy(alpha = .72f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SonifyAccountButton(account: HomeAccountUi?) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .10f))
            .clickable(enabled = account?.onClick != null) { account?.onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        if (account?.avatarUrl.isNullOrBlank()) {
            Text(account?.name?.take(1).orEmpty().ifBlank { "M" }, fontWeight = FontWeight.Bold)
        } else {
            AsyncImage(
                account?.avatarUrl,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

@Composable
private fun SonifySectionHeader(title: String, trailing: String, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (trailing.isNotBlank()) {
            Text(
                trailing,
                modifier = Modifier.clickable(onClick = onRefresh),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
            )
        }
    }
}

@Composable
private fun SonifyCollectionCard(collection: DiscoveryCollection, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(278.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .10f))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            AsyncImage(
                collection.artworkUrl,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .62f),
                    ),
                ),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Text(
                collection.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (collection.creatorName.isNotBlank()) {
                Text(
                    collection.creatorName,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
                )
            }
        }
    }
}

@Composable
private fun SonifyTrackRail(
    tracks: List<DiscoveryTrack>,
    onTrack: (List<DiscoveryTrack>, DiscoveryTrack) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(tracks.chunked(3), key = { index, group -> "sonify-tracks-$index-${group.first().key}" }) { _, group ->
            Column(
                modifier = Modifier.width(354.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.forEach { track ->
                    SonifyTrackRow(track) { onTrack(tracks, track) }
                }
            }
        }
    }
}

@Composable
private fun SonifyTrackRow(track: DiscoveryTrack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .07f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            track.artworkUrl,
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
    }
}
