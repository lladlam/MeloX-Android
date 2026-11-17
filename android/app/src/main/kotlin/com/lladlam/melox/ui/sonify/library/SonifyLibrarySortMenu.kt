/*
 * Sonify library sort menu (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/LibrarySortMenu.kt (master @
 * 209e1aa). Sonify uses its liquid-glass dropdown; MeloX has no equivalent
 * public dropdown host, so the same menu items are presented through the
 * Material 3 DropdownMenu while preserving the item labels, ordering, and
 * default/name/song-count + ascending/descending semantics.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.glass.MeloXGlassSelectionItem
import com.lladlam.melox.ui.glass.MeloXGlassSelectionPopup
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.sonify.AlphabetIndex
import com.lladlam.melox.ui.sonify.IndexedListResult
import com.lladlam.melox.ui.sonify.isSonifyDark
import com.lladlam.melox.ui.sonify.withNight

internal enum class SonifyLibrarySort {
    Default,
    Name,
    SongCount,
}

internal data class SonifyMenuAction(
    val text: String,
    val onClick: () -> Unit,
)

/**
 * Library list top-right sort menu. Keeps Sonify's "⋯" trigger in the title
 * bar and the same sort dimensions.
 */
@Composable
internal fun SonifyLibrarySortMenuButton(
    selectedSort: () -> Int,
    onSelectSort: (Int) -> Unit,
    descending: () -> Boolean,
    onSelectDescending: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportsSongCount: Boolean = true,
    leadingAction: SonifyMenuAction? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf<IntRect?>(null) }
    Box(modifier.padding(end = 10.dp)) {
        Box(
            Modifier
                .size(38.dp)
                .onGloballyPositioned {
                    val bounds = it.boundsInWindow()
                    anchorBounds = IntRect(
                        left = bounds.left.toInt(),
                        top = bounds.top.toInt(),
                        right = bounds.right.toInt(),
                        bottom = bounds.bottom.toInt(),
                    )
                }
                .background(
                    (Color.White withNight Color(0xFF1C1C1E)).copy(alpha = 0.28f),
                    CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.Ellipsis,
                modifier = Modifier.size(22.dp),
                color = LocalContentColor.current,
            )
        }
        anchorBounds?.let { anchor ->
            MeloXGlassSelectionPopup(
                expanded = expanded,
                anchorBounds = anchor,
                items = buildList {
                    leadingAction?.let {
                        add(MeloXGlassSelectionItem(it.text, onClick = it.onClick))
                    }
                    add(MeloXGlassSelectionItem("默认排序", selectedSort() == SonifyLibrarySort.Default.ordinal) {
                        onSelectSort(SonifyLibrarySort.Default.ordinal)
                    })
                    add(MeloXGlassSelectionItem("按名称排序", selectedSort() == SonifyLibrarySort.Name.ordinal) {
                        onSelectSort(SonifyLibrarySort.Name.ordinal)
                    })
                    if (supportsSongCount) {
                        add(MeloXGlassSelectionItem("按歌曲数量排序", selectedSort() == SonifyLibrarySort.SongCount.ordinal) {
                            onSelectSort(SonifyLibrarySort.SongCount.ordinal)
                        })
                    }
                    add(MeloXGlassSelectionItem("升序", checked = !descending()) { onSelectDescending(false) })
                    add(MeloXGlassSelectionItem("降序", checked = descending()) { onSelectDescending(true) })
                },
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
private fun SonifyDropdownItem(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (selected) {
                    MeloXSymbolIcon(
                        symbol = MeloXSymbol.Check,
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                    )
                }
            }
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SonifySortMenuDivider() {
    Spacer(
        Modifier
            .padding(start = 22.dp, end = 14.dp)
            .padding(vertical = 5.dp)
            .height(0.65.dp)
            .fillMaxWidth()
            .background((Color.Black withNight Color.White).copy(alpha = 0.1f)),
    )
}

/** Detail-page sort menu content, used by album and playlist detail pages. */
@Composable
internal fun SonifyDetailSortMenuItems(
    sortOptions: List<Pair<Int, String>>,
    selectedSort: Int,
    onSelectSort: (Int) -> Unit,
    descending: Boolean,
    onSelectDescending: (Boolean) -> Unit,
) {
    sortOptions.forEach { (ordinal, label) ->
        SonifyDropdownItem(label, selectedSort == ordinal) { onSelectSort(ordinal) }
    }
    SonifySortMenuDivider()
    SonifyDropdownItem("升序", selected = !descending) { onSelectDescending(false) }
    SonifyDropdownItem("降序", selected = descending) { onSelectDescending(true) }
}

@Composable
internal fun SonifyDetailSortMenuButton(
    sortOptions: List<Pair<Int, String>>,
    selectedSort: Int,
    onSelectSort: (Int) -> Unit,
    descending: Boolean,
    onSelectDescending: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingActionText: String? = null,
    onLeadingAction: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf<IntRect?>(null) }
    Box(modifier.padding(end = 10.dp)) {
        Box(
            Modifier
                .size(38.dp)
                .onGloballyPositioned {
                    val bounds = it.boundsInWindow()
                    anchorBounds = IntRect(
                        left = bounds.left.toInt(),
                        top = bounds.top.toInt(),
                        right = bounds.right.toInt(),
                        bottom = bounds.bottom.toInt(),
                    )
                }
                .background(
                    (Color.White withNight Color(0xFF1C1C1E)).copy(alpha = 0.28f),
                    CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.Ellipsis,
                modifier = Modifier.size(22.dp),
                color = LocalContentColor.current,
            )
        }
        anchorBounds?.let { anchor ->
            MeloXGlassSelectionPopup(
                expanded = expanded,
                anchorBounds = anchor,
                items = buildList {
                    if (leadingActionText != null && onLeadingAction != null) {
                        add(MeloXGlassSelectionItem(leadingActionText, onClick = onLeadingAction))
                    }
                    sortOptions.forEach { (ordinal, label) ->
                        add(MeloXGlassSelectionItem(label, checked = selectedSort == ordinal) {
                            onSelectSort(ordinal)
                        })
                    }
                    add(MeloXGlassSelectionItem("升序", checked = !descending) { onSelectDescending(false) })
                    add(MeloXGlassSelectionItem("降序", checked = descending) { onSelectDescending(true) })
                },
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
internal fun SonifyDropdownMenuAction(
    text: String,
    onClick: () -> Unit,
) {
    SonifyDropdownItem(text, onClick = onClick)
}

/** Name sort + alphabet groups, mirroring upstream's helper. */
internal fun <T> sonifyAlphabetIndex(
    items: List<T>,
    descending: Boolean,
    isSearching: Boolean,
    keySelector: (T) -> String,
): IndexedListResult<T> {
    if (items.isEmpty()) return IndexedListResult(emptyList(), emptyList(), emptyList())
    val result = AlphabetIndex.build(items = items, descending = descending, nameSelector = keySelector)
    return if (isSearching || result.labels.size < 2) {
        IndexedListResult(result.sortedItems, emptyList(), emptyList())
    } else {
        result
    }
}

internal fun <T> sonifySortAndIndex(
    items: List<T>,
    sortOrdinal: Int,
    descending: Boolean,
    isSearching: Boolean,
    nameSelector: (T) -> String,
    songCountSelector: ((T) -> Int)? = null,
): IndexedListResult<T> {
    if (items.isEmpty()) return IndexedListResult(emptyList(), emptyList(), emptyList())
    return when (sortOrdinal) {
        SonifyLibrarySort.Name.ordinal -> sonifyAlphabetIndex(
            items = items,
            descending = descending,
            isSearching = isSearching,
            keySelector = nameSelector,
        )

        SonifyLibrarySort.SongCount.ordinal -> {
            val comparator = compareBy<T> { songCountSelector?.invoke(it) ?: 0 }
                .thenBy { AlphabetIndex.sortKeyOf(nameSelector(it)) }
            val sorted = if (descending) items.sortedWith(comparator.reversed()) else items.sortedWith(comparator)
            IndexedListResult(sorted, emptyList(), emptyList())
        }

        else -> {
            val sorted = if (descending) items.asReversed() else items
            IndexedListResult(sorted, emptyList(), emptyList())
        }
    }
}
