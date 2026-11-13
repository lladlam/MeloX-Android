package com.lladlam.melox.core.provider.youtubemusic

import com.lladlam.melox.core.music.model.MusicPage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Adapts opaque YouTube cursors to the shared UI's numbered pages. */
internal class YouTubeCursorPager<T>(private val identity: (T) -> String) {
    data class Batch<T>(val items: List<T>, val continuation: String?)
    private class Entry<T>(val items: MutableList<T>, var cursor: String?) {
        val visited = mutableSetOf<String>()
    }
    private val mutex = Mutex()
    private val entries = linkedMapOf<String, Entry<T>>()

    suspend fun page(
        key: String,
        page: Int,
        pageSize: Int,
        first: suspend () -> Batch<T>,
        next: suspend (String) -> Batch<T>,
    ): MusicPage<T> = mutex.withLock {
        require(page in 1..100 && pageSize in 1..1000)
        val entry = if (page == 1 || key !in entries) {
            val batch = first()
            Entry(batch.items.distinctBy(identity).toMutableList(), batch.continuation).also {
                entries.remove(key)
                entries[key] = it
                if (entries.size > 24) entries.remove(entries.keys.first())
            }
        } else entries.getValue(key)
        val offset = (page - 1) * pageSize
        val end = offset + pageSize
        while (entry.items.size < end) {
            val cursor = entry.cursor ?: break
            if (cursor in entry.visited || entry.visited.size >= 100) {
                entry.cursor = null
                break
            }
            val batch = next(cursor)
            entry.visited += cursor
            val known = entry.items.mapTo(hashSetOf(), identity)
            val additions = batch.items.filter { known.add(identity(it)) }
            entry.items += additions
            // A fully duplicated page can still advance to new items. The visited
            // cursor set above is the loop guard; additions are not a stop signal.
            entry.cursor = batch.continuation
        }
        MusicPage(
            items = entry.items.drop(offset).take(pageSize),
            page = page,
            pageSize = pageSize,
            total = entry.items.size.toLong().takeIf { entry.cursor == null },
            hasMore = entry.items.size > end || entry.cursor != null,
        )
    }
}
