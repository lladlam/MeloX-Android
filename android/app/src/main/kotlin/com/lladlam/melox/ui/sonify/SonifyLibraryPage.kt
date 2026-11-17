/*
 * Sonify Library hub (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/pages/library/Library.kt (master @ 209e1aa).
 * The original two-section flat list (local music + online music) is kept
 * pixel-for-pixel: ListHeader / SmallLabelItem / LibraryDivider / GroupSpacer,
 * 54dp rows, 18dp leading icon inset, 21dp chevron inset.
 *
 * Navigation replacement:
 *   navController.toUI(UI.*) + LibraryObject
 *     -> SonifyLibraryEntry callbacks supplied by MeloX LibraryScreen.
 *   SettingsLibrary.ShowLocalLibrary
 *     -> MeloX source availability via showLocal.
 */
package com.lladlam.melox.ui.sonify

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lladlam.melox.R
import com.lladlam.melox.ui.glass.MeloXSymbol

data class SonifyLibraryEntry(
    val label: String,
    val subtitle: String? = null,
    val icon: MeloXSymbol,
    val onClick: () -> Unit,
)

@Composable
fun SonifyLibraryPage(
    localEntries: List<SonifyLibraryEntry>,
    onlineEntries: List<SonifyLibraryEntry>,
    onOpenSettings: () -> Unit = {},
    showLocal: Boolean = SonifySettings.ShowLocalLibrary,
) {
    Column(Modifier.fillMaxSize()) {
        Title(
            title = "音乐库",
            extraTopPadding = 57.dp,
        ) {
            item("Library") {
                Column(Modifier.fillMaxSize()) {
                    if (showLocal) {
                        SonifyListHeader(
                            content = stringResource(id = R.string.sonify_library_local),
                            horizontalPadding = 20.dp,
                        )
                        localEntries.forEachIndexed { index, entry ->
                            SonifySmallLabelItem(
                                icon = entry.icon,
                                label = entry.label,
                                subtitle = entry.subtitle,
                                iconTint = Color(0xFFF54047),
                                onClick = entry.onClick,
                            )
                            if (index != localEntries.lastIndex) SonifyLibraryDivider()
                        }
                    }

                    SonifyGroupSpacer()
                    SonifyListHeader(
                        content = stringResource(id = R.string.sonify_library_online),
                        horizontalPadding = 20.dp,
                    )
                    onlineEntries.forEachIndexed { index, entry ->
                        SonifySmallLabelItem(
                            icon = entry.icon,
                            label = entry.label,
                            subtitle = entry.subtitle,
                            iconTint = Color(0xFFF54047),
                            onClick = entry.onClick,
                        )
                        if (index != onlineEntries.lastIndex) SonifyLibraryDivider()
                    }
                }
            }
        }
    }
}
