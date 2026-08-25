package com.lladlam.melox.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXMeiScreenMigrationTest {
    @Test
    fun albumAndArtistRoutesUseMigratedScreens() {
        val activity = File("src/main/kotlin/com/lladlam/melox/ui/collection/MeloXCollectionDetailActivity.kt").readText()
        val library = File("src/main/kotlin/com/lladlam/melox/ui/library/LibraryScreen.kt").readText()

        assertTrue(activity.contains("MeloXUnifiedAlbumDetailScreen(id, ::finish)"))
        assertTrue(activity.contains("MeloXArtistDetailScreen(id, ::finish)"))
        assertTrue(library.contains("internal fun MeloXUnifiedProviderAlbumDetailScreen"))
    }

    @Test
    fun searchSongRowsUsePlaylistSwipeActions() {
        val search = File("src/main/kotlin/com/lladlam/melox/ui/search/SearchScreen.kt").readText()

        assertTrue(search.contains("private fun SearchSwipeSongRow"))
        assertTrue(search.contains("MeloXSwipeAction(\"下一首播放\""))
        assertTrue(search.contains("MeloXSwipeAction(\"稍后播放\""))
        assertTrue(search.contains("MeloXSwipeAction(\"添加到资料库\""))
    }

    @Test
    fun songWikiAccountAndArtistPagesAreWired() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val actions = File("src/main/kotlin/com/lladlam/melox/ui/player/MeloXSongActionsOverlay.kt").readText()
        val account = File("src/main/kotlin/com/lladlam/melox/ui/account/MeloXAccountActivity.kt").readText()
        val artist = File("src/main/kotlin/com/lladlam/melox/ui/collection/MeloXArtistDetailScreen.kt").readText()

        assertTrue(manifest.contains(".ui.song.MeloXSongWikiActivity"))
        assertTrue(actions.contains("MeloXSongWikiActivity.launch(context, song)"))
        assertTrue(account.contains("MeloXGlassSegmentedControl"))
        assertTrue(account.contains("AccountHero"))
        assertTrue(artist.contains("ArtistHero"))
        assertTrue(artist.contains("LazyRow"))
    }

    @Test
    fun podcastUsesMeiLayoutAndSharedDirectRoute() {
        val podcast = File("src/main/kotlin/com/lladlam/melox/ui/podcast/MeloXPodcastScreens.kt").readText()
        val activity = File("src/main/kotlin/com/lladlam/melox/ui/collection/MeloXCollectionDetailActivity.kt").readText()

        assertTrue(podcast.contains("initialPodcastId: Long? = null"))
        assertTrue(podcast.contains("PodcastCategoryButton"))
        assertTrue(podcast.contains("MeloXPinnedListPage("))
        assertTrue(podcast.contains("PlaybackCommands.playQueue(context, songs, selected.id)"))
        assertTrue(activity.contains("MeloXPodcastScreen("))
    }

    @Test
    fun themeProvidesDarkAwareRootContentColor() {
        val theme = File("src/main/kotlin/com/lladlam/melox/ui/theme/MeloXTheme.kt").readText()

        assertTrue(theme.contains("onSurfaceVariant = Color(0xFFB8B8C0)"))
        assertTrue(theme.contains("contentColor = MaterialTheme.colorScheme.onBackground"))
    }
}
