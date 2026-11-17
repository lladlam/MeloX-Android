package com.lladlam.melox.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXTypography

/**
 * Apple Music "New" feed: every discovery category is a section with a
 * horizontal rail of large editorial cards and a chevron that opens the full
 * category grid.
 */
@Composable
internal fun SonifyExploreFeed(
    categories: List<String>,
    load: suspend (String) -> List<NeteasePlaylistSummary>,
    onCollection: (DiscoveryCollection) -> Unit,
) {
    val data = remember { mutableStateMapOf<String, List<NeteasePlaylistSummary>>() }
    val loading = remember { mutableStateMapOf<String, Boolean>() }
    val errors = remember { mutableStateMapOf<String, String?>() }
    var moreCategory by rememberSaveable { mutableStateOf<String?>(null) }

    if (moreCategory != null) {
        val category = moreCategory!!
        SonifyExploreCategoryPage(
            category = category,
            items = data[category].orEmpty(),
            loading = loading[category] == true,
            error = errors[category],
            onBack = { moreCategory = null },
            onCollection = onCollection,
            load = load,
            onLoaded = { data[category] = it },
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance + 90.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item("ExploreTitle") {
            Text(
                "新发现",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.2).sp,
            )
        }
        categories.forEach { category ->
            item("section-$category") {
                ExploreCategorySection(
                    category = category,
                    items = data[category],
                    loading = loading[category] == true,
                    error = errors[category],
                    onCollection = onCollection,
                    onMore = { moreCategory = category },
                    onLoad = {
                        if (data.containsKey(category) || loading[category] == true) return@ExploreCategorySection
                        loading[category] = true
                        errors.remove(category)
                        runCatching { load(category) }
                            .onSuccess { data[category] = it }
                            .onFailure { errors[category] = it.message ?: "加载失败" }
                        loading[category] = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ExploreCategorySection(
    category: String,
    items: List<NeteasePlaylistSummary>?,
    loading: Boolean,
    error: String?,
    onCollection: (DiscoveryCollection) -> Unit,
    onMore: () -> Unit,
    onLoad: suspend () -> Unit,
) {
    LaunchedEffect(category) { onLoad() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable(onClick = onMore),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            category.removeSuffix("歌单"),
            fontSize = 23.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        MeloXSymbolIcon(
            symbol = MeloXSymbol.ChevronRight,
            modifier = Modifier.height(20.dp).width(20.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .35f),
        )
    }
    Spacer(Modifier.height(10.dp))
    when {
        !items.isNullOrEmpty() -> {
            val pager = rememberPagerState(pageCount = { items.size })
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fill,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 12.dp,
                key = { items[it].id },
            ) { page ->
                ExploreHeroCard(items[page], category, onCollection)
            }
        }
        loading -> Box(
            Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)) }
        error != null -> Text(error, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
    }
}

@Composable
private fun ExploreHeroCard(
    playlist: NeteasePlaylistSummary,
    category: String,
    onCollection: (DiscoveryCollection) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(356.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCollection(DiscoveryCollection.Netease(playlist)) },
    ) {
        AsyncImage(
            model = playlist.coverUrl,
            contentDescription = playlist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.35f to Color.Transparent,
                    1f to Color.Black.copy(alpha = .92f),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                category.removeSuffix("歌单").uppercase(),
                color = Color.White.copy(alpha = .68f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .6.sp,
            )
            Text(
                playlist.name,
                color = Color.White,
                fontSize = 21.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                playlist.creatorName.ifBlank { "Apple Music" },
                color = Color.White.copy(alpha = .62f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SonifyExploreCategoryPage(
    category: String,
    items: List<NeteasePlaylistSummary>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onCollection: (DiscoveryCollection) -> Unit,
    load: suspend (String) -> List<NeteasePlaylistSummary>,
    onLoaded: (List<NeteasePlaylistSummary>) -> Unit,
) {
    LaunchedEffect(category) {
        if (items.isEmpty()) {
            runCatching { load(category) }.onSuccess(onLoaded)
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                MeloXSymbolIcon(
                    MeloXSymbol.ChevronLeft,
                    Modifier.size(22.dp),
                    MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(category.removeSuffix("歌单"), fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
        if (items.isEmpty() && loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (items.isEmpty()) {
            Text(error ?: "暂无内容", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, MeloXBottomContentClearance),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items, key = { it.id }) { playlist ->
                    Column(
                        Modifier.clickable { onCollection(DiscoveryCollection.Netease(playlist)) },
                    ) {
                        AsyncImage(
                            model = playlist.coverUrl,
                            contentDescription = playlist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
                        )
                        Text(
                            playlist.name,
                            modifier = Modifier.padding(top = 6.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            playlist.creatorName,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
