/*
 * Sonify detail-page search state (GPL-3.0), adapted for MeloX.
 *
 * Upstream: ui/pages/library/playlists/PageSearchState.kt (master @ 209e1aa).
 * The debounce, IME composition gate, focus hand-off and collapse behaviour are
 * preserved. MeloX uses the same Compose focus APIs, so only package/imports
 * change.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

@Composable
internal fun rememberSonifyPageSearch(
    resetKey: Any?,
    enabled: Boolean = true,
): SonifyPageSearchState {
    val state = remember { SonifyPageSearchState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val morph = animateFloatAsState(
        targetValue = if (state.expanded) 1f else 0f,
        animationSpec = SonifySearchMorphSpec,
        label = "sonifyPageSearchMorph",
    )
    LaunchedEffect(Unit) {
        snapshotFlow { state.expanded }.drop(1).collect { expanded ->
            if (expanded) {
                for (attempt in 0 until 3) {
                    withFrameNanos { }
                    if (runCatching { state.fieldFocus.requestFocus() }.isSuccess) break
                }
                keyboard?.show()
            } else {
                snapshotFlow { morph.value }.first { it <= 0.005f }
                state.query = TextFieldValue("")
                state.keyword = ""
                runCatching { focusManager.clearFocus(force = true) }
                keyboard?.hide()
                runCatching { state.buttonFocus.requestFocus() }
            }
        }
    }
    LaunchedEffect(state.query.text, state.query.composition) {
        val composing = state.query.composition?.let { !it.collapsed } == true
        if (composing) return@LaunchedEffect
        val keyword = state.query.text.trim()
        if (keyword == state.keyword) return@LaunchedEffect
        if (keyword.isEmpty()) {
            state.keyword = ""
            return@LaunchedEffect
        }
        delay(250)
        state.keyword = keyword
    }
    LaunchedEffect(resetKey) { state.collapse() }
    LaunchedEffect(enabled) { if (!enabled) state.collapse() }
    state.morph = morph
    return state
}

internal class SonifyPageSearchState {
    var expanded by mutableStateOf(false)
        private set
    var query by mutableStateOf(TextFieldValue(""))
    var keyword by mutableStateOf("")
    val fieldFocus = FocusRequester()
    val buttonFocus = FocusRequester()
    var morph: State<Float> = mutableStateOf(0f)
        internal set
    val filtering: Boolean get() = keyword.isNotEmpty()

    fun open() {
        expanded = true
    }

    fun dismiss() {
        if (!expanded) return
        expanded = false
    }

    fun clearQuery() {
        query = TextFieldValue("")
        keyword = ""
    }

    fun collapse() {
        expanded = false
        query = TextFieldValue("")
        keyword = ""
    }
}
