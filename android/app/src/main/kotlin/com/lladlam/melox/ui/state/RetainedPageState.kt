package com.lladlam.melox.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.UUID

/**
 * Loaded page data and model-valued navigation survive a stopped Activity being
 * destroyed while its process remains alive. Only an opaque key goes in the
 * instance-state Bundle, never an entire playlist/search result or a Context.
 *
 * Use rememberSaveable directly for small routes/IDs/input. After process death
 * this store is empty, so [init] runs and the page's normal loader reloads data.
 * Do not retain Views, Activities, coroutines or callbacks here.
 */
@Composable
internal fun <T> rememberRetainedPageState(
    vararg inputs: Any?,
    init: () -> MutableState<T>,
): MutableState<T> {
    val keys = inputs.toList()
    val saver = Saver<MutableState<T>, String>(
        save = { PageStates.retain(it, keys) },
        restore = { token ->
            @Suppress("UNCHECKED_CAST")
            (PageStates.restore(token, keys) as? MutableState<T>)
        },
    )
    return rememberSaveable(*inputs, saver = saver, init = init)
}

/** Bounded process-local cache; input matching prevents cross-account/source restores. */
internal class RetainedPageStateStore(private val capacity: Int = 256) {
    private data class Entry(val value: Any, val inputs: List<Any?>)
    private val entries = LinkedHashMap<String, Entry>(16, .75f, true)

    init { require(capacity > 0) }

    @Synchronized
    fun retain(value: Any, inputs: List<Any?>): String {
        val token = entries.entries.firstOrNull { it.value.value === value && it.value.inputs == inputs }?.key
            ?: UUID.randomUUID().toString()
        entries[token] = Entry(value, inputs.toList())
        while (entries.size > capacity) entries.remove(entries.keys.first())
        return token
    }

    @Synchronized
    fun restore(token: String, inputs: List<Any?>): Any? =
        entries[token]?.takeIf { it.inputs == inputs }?.value
}

private val PageStates = RetainedPageStateStore()
