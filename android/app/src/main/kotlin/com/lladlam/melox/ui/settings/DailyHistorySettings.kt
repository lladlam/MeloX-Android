package com.lladlam.melox.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.dailyhistory.DailyHistoryEntry
import com.lladlam.melox.core.dailyhistory.DailyHistoryStore
import com.lladlam.melox.core.dailyhistory.EmptyState
import com.lladlam.melox.core.dailyhistory.MeloXDailyClock
import com.lladlam.melox.core.dailyhistory.accountKeyOf
import com.lladlam.melox.core.library.NeteasePlaylistSummary
import com.lladlam.melox.playback.PlaybackCommands
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassIconButton
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow
import com.lladlam.melox.ui.glass.MeloXPinnedListPage
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSystemColors
import com.lladlam.melox.ui.library.MeloXUnifiedSongListDetailScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * History of daily recommendations. Switches between the calendar page and the
 * full-screen playlist detail without leaving the settings detail Activity.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun DailyHistoryRoute(title: String, onBack: () -> Unit) {
    var playlistEntry by remember { mutableStateOf<DailyHistoryEntry?>(null) }
    val entry = playlistEntry
    // Keep the system back gesture inside the history feature: first return to the
    // calendar, only the next one exits the settings detail Activity.
    BackHandler(enabled = entry != null) { playlistEntry = null }
    if (entry != null) {
        // Synthetic negative id: supplied song lists must never hit the disk/network
        // playlist cache (same convention as HomeSongListDetail).
        val playlistName = stringResource(R.string.daily_history_playlist_name, entry.date)
        val playlistCreator = stringResource(R.string.daily_history_playlist_creator)
        val playlist = remember(entry.date, entry.songs.size, playlistName, playlistCreator) {
            NeteasePlaylistSummary(
                id = -(("历史日推 ${entry.date}").hashCode().toLong() and 0x7fff_ffffL) - 1L,
                name = playlistName,
                coverUrl = entry.songs.firstOrNull()?.artworkUrl,
                trackCount = entry.songs.size,
                creatorName = playlistCreator,
            )
        }
        MeloXUnifiedSongListDetailScreen(
            playlist = playlist,
            songs = entry.songs,
            onBack = { playlistEntry = null },
        )
    } else {
        DailyHistoryPage(
            title = title,
            onBack = onBack,
            onOpenAsPlaylist = { playlistEntry = it },
        )
    }
}

@Composable
private fun DailyHistoryPage(
    title: String,
    onBack: () -> Unit,
    onOpenAsPlaylist: (DailyHistoryEntry) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()

    val initialToday = remember { MeloXDailyClock.today() }
    var year by rememberSaveable { mutableIntStateOf(initialToday.year) }
    var monthValue by rememberSaveable { mutableIntStateOf(initialToday.monthValue) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(MeloXDailyClock.archiveKeyFor(initialToday)) }
    var dateKeys by remember { mutableStateOf(emptyList<String>()) }
    var accountKey by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf<DailyHistoryEntry?>(null) }
    var entryLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }

    val month = YearMonth.of(year, monthValue)
    val today = MeloXDailyClock.today()
    val marked = remember(dateKeys) { MeloXDailyClock.markedDates(dateKeys) }
    val cells = remember(month) { MeloXDailyClock.monthCells(month) }
    val contentState = MeloXDailyClock.isEmptyState(dateKeys, selectedKey)

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            loading = true
            error = null
            val key = withContext(Dispatchers.IO) {
                accountKeyOf(NeteaseSessionStore.readCookie(app))
            }
            accountKey = key
            dateKeys = runCatching {
                withContext(Dispatchers.IO) { DailyHistoryStore.listDateKeys(app, key) }
            }.onFailure {
                error = it.message ?: "历史日推读取失败"
            }.getOrDefault(emptyList())
            loading = false
        }
    }
    val latestRefresh by rememberUpdatedState(::refresh)
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latestRefresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(accountKey, selectedKey, dateKeys) {
        val key = accountKey ?: return@LaunchedEffect
        val requestedKey = selectedKey
        entry = null
        error = null
        entryLoading = requestedKey != null && requestedKey in dateKeys
        entry = if (requestedKey == null || requestedKey !in dateKeys) {
            null
        } else {
            runCatching {
                withContext(Dispatchers.IO) { DailyHistoryStore.read(app, key, requestedKey) }
            }.onFailure {
                error = it.message ?: "历史日推读取失败"
            }.getOrNull()
        }
        entryLoading = false
    }

    fun showMonth(target: YearMonth) {
        year = target.year
        monthValue = target.monthValue
        selectedKey = MeloXDailyClock.selectionForMonth(
            target = target,
            selectedKey = selectedKey,
            dateKeys = dateKeys,
        )
    }

    MeloXPinnedListPage(
        title = title,
        onNavigateBack = onBack,
        bottomPadding = MeloXBottomContentClearance,
    ) {
        item(key = "history-month-header") {
            MonthHeader(
                month = month,
                onPrevious = { showMonth(month.minusMonths(1)) },
                onNext = { showMonth(month.plusMonths(1)) },
                onToday = {
                    showMonth(YearMonth.from(MeloXDailyClock.today()))
                },
            )
        }

        item(key = "history-calendar") {
            MonthGrid(
                cells = cells,
                marked = marked,
                today = today,
                selected = selectedKey?.let(MeloXDailyClock::parseKey),
                onSelect = { date -> selectedKey = MeloXDailyClock.archiveKeyFor(date) },
            )
        }

        item(key = "history-content-state") {
            when {
                loading && dateKeys.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MeloXSystemColors.Red)
                    }
                }
                entryLoading -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MeloXSystemColors.Red)
                    }
                }
                error != null -> {
                    EmptyHint(error.orEmpty())
                }
                contentState == EmptyState.NO_HISTORY -> {
                    EmptyHint(stringResource(R.string.daily_history_empty_all))
                }
                contentState == EmptyState.NO_SELECTION -> {
                    EmptyHint(stringResource(R.string.daily_history_select_day))
                }
                contentState == EmptyState.NO_RECORD_THAT_DAY -> {
                    EmptyHint(stringResource(R.string.daily_history_empty_day))
                }
                entry == null -> {
                    EmptyHint(stringResource(R.string.daily_history_empty_day))
                }
                else -> {
                    val current = entry!!
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            current.date,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        )
                        Text(
                            stringResource(R.string.daily_history_song_count, current.songs.size),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        MeloXGlassButton(
                            onClick = { onOpenAsPlaylist(current) },
                            modifier = Modifier.fillMaxWidth(),
                            style = MeloXGlassButtonStyle.BorderedProminent,
                        ) {
                            Text(stringResource(R.string.daily_history_open_playlist))
                        }
                    }
                }
            }
        }

        // One grouped card for all songs: avoids 100 independent list items and keeps
        // the standard separator rendering. A plain Column inside a single LazyColumn
        // item is safe (no nested scrolling / infinite height).
        if (contentState == EmptyState.HAS_RECORD && entry != null) {
            item(key = "history-songs:$selectedKey") {
                MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
                    val songs = entry!!.songs
                    songs.forEachIndexed { index, song ->
                        MeloXIosListRow(
                            title = song.name,
                            subtitle = listOf(song.artists, song.album)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            detail = "${index + 1}",
                            showTopSeparator = index > 0,
                            onClick = {
                                PlaybackCommands.playQueue(
                                    context = context,
                                    songs = songs,
                                    selectedSongId = song.id,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MeloXGlassIconButton(
            symbol = MeloXSymbol.ChevronLeft,
            onClick = onPrevious,
            contentDescription = stringResource(R.string.daily_history_previous_month),
        )
        Text(
            stringResource(R.string.daily_history_month, month.year, month.monthValue),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeloXGlassButton(onClick = onToday) {
                Text(stringResource(R.string.daily_history_today), fontSize = 13.sp)
            }
            MeloXGlassIconButton(
                symbol = MeloXSymbol.ChevronRight,
                onClick = onNext,
                contentDescription = stringResource(R.string.daily_history_next_month),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    cells: List<LocalDate?>,
    marked: Set<LocalDate>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val weekdays = remember {
        (0 until 7).map { offset ->
            DayOfWeek.MONDAY.plus(offset.toLong())
                .getDisplayName(TextStyle.NARROW, Locale.getDefault())
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val date = cells[row * 7 + column]
                    DayCell(
                        date = date,
                        isToday = date == today,
                        isSelected = date != null && date == selected,
                        hasRecord = date != null && date in marked,
                        onClick = { if (date != null) onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isToday: Boolean,
    isSelected: Boolean,
    hasRecord: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        isSelected -> MeloXSystemColors.Red.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .height(46.dp)
            .padding(2.dp)
            .clip(shape)
            .background(background)
            .then(
                if (isToday) Modifier.border(1.dp, MeloXSystemColors.Red, shape)
                else Modifier,
            )
            .then(if (date != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${date.dayOfMonth}",
                    fontSize = 14.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected -> MeloXSystemColors.Red
                        isToday -> MeloXSystemColors.Red
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasRecord) MeloXSystemColors.Red
                            else Color.Transparent,
                        ),
                )
            }
        }
    }
}
