package com.lladlam.melox.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RetainedPageStateStoreTest {
    @Test
    fun backgroundRestoreReusesLoadedDataWithoutPuttingItInTheToken() {
        val store = RetainedPageStateStore()
        val songs = MutablePage((1..10_000).toList())
        val token = store.retain(songs, listOf("netease", "account-a"))
        assertSame(songs, store.restore(token, listOf("netease", "account-a")))
        assertEquals(36, token.length)
        // Another save of the same page must not consume another cache slot.
        assertEquals(token, store.retain(songs, listOf("netease", "account-a")))
        songs.selectedId = 1234
        assertEquals(1234, (store.restore(token, listOf("netease", "account-a")) as MutablePage).selectedId)
    }

    @Test
    fun restoredBundleCannotReuseAnotherAccountOrSource() {
        val store = RetainedPageStateStore()
        val token = store.retain(MutablePage(listOf(1)), listOf("qq", "account-a"))
        assertNull(store.restore(token, listOf("qq", "account-b")))
        assertNull(store.restore(token, listOf("netease", "account-a")))
    }

    @Test
    fun missingProcessCacheFallsBackInsteadOfRestoringInvalidData() {
        val token = RetainedPageStateStore().retain(MutablePage(listOf(1)), emptyList())
        assertNull(RetainedPageStateStore().restore(token, emptyList()))
    }

    @Test
    fun inactivePagesAreBoundedAndRecentlyRestoredPageSurvives() {
        val store = RetainedPageStateStore(capacity = 2)
        val first = MutablePage(listOf(1))
        val firstToken = store.retain(first, emptyList())
        val secondToken = store.retain(MutablePage(listOf(2)), emptyList())
        assertSame(first, store.restore(firstToken, emptyList()))
        store.retain(MutablePage(listOf(3)), emptyList())
        assertNull(store.restore(secondToken, emptyList()))
        assertSame(first, store.restore(firstToken, emptyList()))
    }

    private class MutablePage(val songs: List<Int>, var selectedId: Int = 0)
}
