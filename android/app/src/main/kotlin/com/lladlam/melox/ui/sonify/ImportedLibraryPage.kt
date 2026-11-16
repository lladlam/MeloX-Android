package com.lladlam.melox.ui.sonify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.MeloXBottomContentClearance

data class ImportedLibraryItem(
    val label: String,
    val subtitle: String? = null,
    val onClick: () -> Unit,
)

data class ImportedLibraryGroup(
    val title: String,
    val items: List<ImportedLibraryItem>,
)

/** Copied Sonify Library.kt structure, with MeloX data supplied as groups. */
@Composable
internal fun ImportedLibraryPage(
    title: String,
    groups: List<ImportedLibraryGroup>,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearance + 90.dp),
    ) {
        item("title") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 42.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text("•••", Modifier.clickable(onClick = onOpenSettings).padding(12.dp), fontSize = 20.sp)
            }
        }
        groups.forEach { group ->
            item("header-${group.title}") {
                Text(
                    group.title,
                    Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
                )
            }
            group.items.forEach { item ->
                item("item-${group.title}-${item.label}") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = item.onClick)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Text(item.label, fontSize = 20.sp)
                        item.subtitle?.let {
                            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
                        }
                    }
                }
            }
        }
    }
}
