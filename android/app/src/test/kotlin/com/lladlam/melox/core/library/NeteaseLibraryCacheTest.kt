package com.lladlam.melox.core.library

import com.lladlam.melox.core.model.SearchSong
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 回归测试：修复「我喜欢的音乐」收藏过段时间消失。
 *
 * 根因是 [NeteaseLibraryCache.pruneCache] 与瞬态缓存（home / explore / playlist）
 * 共用一个目录，且按 lastModified 倒序、越过 MAX_CACHE_FILES 即删。library 快照
 * 只在冷启动刷新或点赞切换时落盘，几乎是目录里最旧的文件 ⇒ 一旦瞬态文件累计超过
 * 128 个，library 快照第一个被清掉，收藏列表随之空白。本测试精确复现这一场景，并
 * 断言修复后 library 快照在任何情况下都不会被淘汰。
 */
class NeteaseLibraryCacheTest {

    private fun tempDir(): File {
        val dir = File.createTempFile("netease_cache_", "")
        dir.delete()
        dir.mkdirs()
        return dir
    }

    private fun song(id: Long) = SearchSong(
        id = id,
        name = "song-$id",
        artists = "artist-$id",
        album = "album-$id",
        artworkUrl = null,
    )

    /**
     * 复现 bug：library 快照最旧 + 瞬态文件超过 MAX_CACHE_FILES。
     * 旧实现会把 library 快照删掉；修复后它受保护，必须保留且数据完好。
     */
    @Test
    fun librarySnapshotSurvivesTransientCachePressure() = runBlocking {
        val dir = tempDir()
        val cache = NeteaseLibraryCache(dir)
        val userId = 12345L

        // 1) 落盘一份「我喜欢的音乐」快照（含 specialType == 5 的系统歌单）。
        val likedPlaylist = NeteasePlaylistSummary(
            id = 999L,
            name = "我喜欢的音乐",
            coverUrl = null,
            trackCount = 3,
            specialType = 5,
            creatorName = "me",
        )
        val snapshot = NeteaseLibrarySnapshot(
            playlists = listOf(likedPlaylist),
            likedSongs = listOf(song(1), song(2), song(3)),
            recentSongs = emptyList(),
            likedPlaylistId = 999L,
        )
        cache.saveSnapshot(userId, snapshot)

        // 2) 把 library 文件改成「比瞬态文件旧」——这正是旧实现会优先删掉它的条件。
        //    注意必须是「30 天以内」的过去时间：readJson 自身对超过 30 天的文件会按
        //    过期删除（那是正常的缓存失效，不是本 bug）。生产里 library 文件的 mtime
        //    就是这种「相对较旧但远没到 30 天」的状态。
        val libraryFile = File(dir, "library_$userId.json")
        assertTrue("library file should exist after save", libraryFile.isFile)
        libraryFile.setLastModified(System.currentTimeMillis() - 2L * 60L * 60L * 1000L)

        // 3) 写入远超 MAX_CACHE_FILES 的瞬态文件（home/explore 这一类），且都比 library 新。
        val transientCount = 200
        repeat(transientCount) { index ->
            val f = File(dir, "home_transient_$index.json")
            f.writeText("{}")
            f.setLastModified(System.currentTimeMillis() + index)
        }
        assertEquals(transientCount, dir.listFiles()!!.count { it.name.startsWith("home_transient_") })

        // 4) 触发与真实写入相同的清理逻辑。
        cache.pruneCache()

        // 5) 断言：library 快照毫发无伤，且瞬态文件被收敛到上限以内。
        val reloaded = cache.loadSnapshot(userId)
        assertNotNull("library snapshot must survive transient cache pressure", reloaded)
        assertEquals(3, reloaded!!.likedSongs.size)
        assertEquals(999L, reloaded.likedPlaylistId)
        // specialType 必须经过 JSON 落盘往返后仍然保留。
        assertEquals(5, reloaded.playlists.first().specialType)

        val remainingTransient = dir.listFiles()!!.count { it.name.startsWith("home_transient_") }
        assertTrue(
            "transient files should be evicted down to the cap (was $transientCount, now $remainingTransient)",
            remainingTransient <= MAX_CACHE_FILES_FOR_TEST,
        )
    }

    /**
     * 直接验证：即便目录里只有 library 这一个文件，pruneCache 也不能误删它
     * （旧实现下 index 0 永不删，但明确锁定行为）。
     */
    @Test
    fun pruneNeverDeletesLibrarySnapshot() = runBlocking {
        val dir = tempDir()
        val cache = NeteaseLibraryCache(dir)
        cache.saveSnapshot(777L, NeteaseLibrarySnapshot(emptyList(), listOf(song(10)), emptyList(), null))
        cache.pruneCache()
        assertTrue(File(dir, "library_777.json").isFile)
        assertNotNull(cache.loadSnapshot(777L))
    }

    /**
     * specialType 字段在快照落盘往返中不能丢失：reconcileLikedSongs 现在依赖它
     * 来定位「我喜欢的音乐」，丢了就会退回不可靠的 firstOrNull()。
     */
    @Test
    fun specialTypeRoundTripsThroughSnapshotCache() = runBlocking {
        val dir = tempDir()
        val cache = NeteaseLibraryCache(dir)
        val summary = NeteasePlaylistSummary(
            id = 1L,
            name = "liked",
            coverUrl = null,
            trackCount = 1,
            specialType = 5,
            creatorName = "x",
        )
        cache.saveSnapshot(1L, NeteaseLibrarySnapshot(listOf(summary), listOf(song(1)), emptyList(), 1L))
        val loaded = cache.loadSnapshot(1L)!!
        assertEquals(5, loaded.playlists.first().specialType)
    }

    private companion object {
        // 与 NeteaseLibraryCache.MAX_CACHE_FILES 保持一致（避免从主源 import 私有常量）。
        const val MAX_CACHE_FILES_FOR_TEST = 128
    }
}
