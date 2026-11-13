package com.lladlam.melox.ui.messages

import androidx.compose.runtime.saveable.rememberSaveable
import com.lladlam.melox.ui.state.rememberRetainedPageState

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.shapes.Capsule
import com.lladlam.melox.R
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.network.MeloXMessageContact
import com.lladlam.melox.core.network.MeloXPrivateMessage
import com.lladlam.melox.core.network.MeloXPrivateMessageResource
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow
import com.lladlam.melox.ui.glass.meloXLiquidButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun MessagesScreen(onBack: () -> Unit) {
    var selectedContact by rememberRetainedPageState { mutableStateOf<MeloXMessageContact?>(null) }
    var selectingContact by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selectedContact != null || selectingContact) {
        if (selectedContact != null) selectedContact = null else selectingContact = false
    }

    if (selectedContact != null) {
        MessagesDetailScreen(
            contact = selectedContact!!,
            onBack = { selectedContact = null },
        )
    } else if (selectingContact) {
        MessageContactsScreen(
            onBack = { selectingContact = false },
            onContactSelected = {
                selectingContact = false
                selectedContact = it
            },
        )
    } else {
        MessagesConversationList(
            onContactSelected = { selectedContact = it },
            onStartMessage = { selectingContact = true },
            onBack = onBack,
        )
    }
}

@Composable
private fun MessagesConversationList(
    onContactSelected: (MeloXMessageContact) -> Unit,
    onStartMessage: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val ops = remember(context) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    val account = remember(context) {
        NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }

    var contacts by rememberRetainedPageState { mutableStateOf<List<MeloXMessageContact>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        runCatching {
            val profile = account.accountProfile()
            val recent = ops.privateMessageConversations(profile.userId, limit = 50)
            contacts = recent
                .filter { it.id != profile.userId }
                .distinctBy(MeloXMessageContact::id)
        }.onFailure { error = it.message ?: context.getString(R.string.message_dm_failed) }
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ⚠ 2026-10-17 修「消息标题不居中」：左侧返回键 44dp、右侧两枚按钮 ≈96dp，
            //   原 `weight(1f)` 只在**两侧剩余空间内**居中 ⇒ 标题实际偏左 ~26dp。
            //   改为 Box 叠层：标题对**整行**居中，按钮两端对齐 —— 与两侧宽度无关。
            Box(
                Modifier.fillMaxWidth().height(58.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(44.dp)
                        .meloXLiquidButton(
                            shape = CircleShape,
                            surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .10f),
                        )
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    MeloXSymbolIcon(
                        symbol = MeloXSymbol.ChevronLeft,
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = context.getString(R.string.tab_messages),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Row(
                    Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MessageGlassIconButton(onClick = onStartMessage) {
                        MeloXSymbolIcon(MeloXSymbol.Person, Modifier.size(21.dp), MaterialTheme.colorScheme.primary)
                    }
                    MessageGlassIconButton(onClick = { reload++ }) {
                        MeloXSymbolIcon(MeloXSymbol.Refresh, Modifier.size(21.dp), MaterialTheme.colorScheme.primary)
                    }
                }
            }

            when {
                loading && contacts.isEmpty() -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
                error != null && contacts.isEmpty() -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: stringResource(R.string.message_load_failed), color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .height(44.dp)
                                .meloXLiquidButton(
                                    shape = Capsule(),
                                    surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .10f),
                                )
                                .clickable { reload++ }
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(context.getString(R.string.action_refresh), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "private-conversation-group") {
                        MeloXIosGroupedList {
                            contacts.forEachIndexed { index, contact ->
                                MessagesConversationItem(
                                    contact = contact,
                                    showTopSeparator = index > 0,
                                    onClick = { onContactSelected(contact) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageGlassIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .meloXLiquidButton(
                shape = CircleShape,
                surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .10f),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun MessageContactsScreen(
    onBack: () -> Unit,
    onContactSelected: (MeloXMessageContact) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val ops = remember(context) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    val account = remember(context) {
        NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    var contacts by rememberRetainedPageState { mutableStateOf<List<MeloXMessageContact>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        runCatching {
            val profile = account.accountProfile()
            contacts = ops.messageContacts(profile.userId, limit = 1_000)
                .filter { it.id != profile.userId }
                .distinctBy(MeloXMessageContact::id)
        }.onFailure { error = it.message ?: context.getString(R.string.message_contacts_failed) }
        loading = false
    }
    val filtered = remember(query, contacts) {
        val keyword = query.trim()
        if (keyword.isBlank()) contacts else contacts.filter {
            it.name.contains(keyword, ignoreCase = true) || it.signature.contains(keyword, ignoreCase = true)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MessageGlassIconButton(onBack) {
                MeloXSymbolIcon(MeloXSymbol.ChevronLeft, Modifier.size(22.dp), MaterialTheme.colorScheme.primary)
            }
            Text(
                stringResource(R.string.message_new),
                Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            MessageGlassIconButton({ reload++ }) {
                MeloXSymbolIcon(MeloXSymbol.Refresh, Modifier.size(21.dp), MaterialTheme.colorScheme.primary)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .meloXLiquidButton(
                    shape = Capsule(),
                    surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .10f),
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeloXSymbolIcon(MeloXSymbol.Search, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                ),
                decorationBox = { inner ->
                    Box {
                        if (query.isBlank()) Text(stringResource(R.string.message_search_contacts), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        when {
            loading && contacts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
            error != null && contacts.isEmpty() -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            filtered.isEmpty() -> Text(stringResource(R.string.message_no_contacts), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "message-contact-group") {
                    MeloXIosGroupedList {
                        filtered.forEachIndexed { index, contact ->
                            MeloXIosListRow(
                                title = contact.name,
                                subtitle = contact.signature.takeIf(String::isNotBlank),
                                leading = {
                                    AsyncImage(
                                        model = contact.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp).clip(CircleShape),
                                    )
                                },
                                showTopSeparator = index > 0,
                                onClick = { onContactSelected(contact) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagesConversationItem(
    contact: MeloXMessageContact,
    showTopSeparator: Boolean,
    onClick: () -> Unit,
) {
    MeloXIosListRow(
        title = contact.name,
        subtitle = contact.latestMessage ?: contact.signature.ifBlank { stringResource(R.string.message_none) },
        leading = {
            AsyncImage(
                model = contact.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
            )
        },
        trailing = if (contact.unreadCount > 0) {
            {
                Box(
                    Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        contact.unreadCount.coerceAtMost(99).toString(),
                        fontSize = 11.sp,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
        } else null,
        showTopSeparator = showTopSeparator,
        onClick = onClick,
    )
}

@Composable
private fun MessagesDetailScreen(
    contact: MeloXMessageContact,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val ops = remember(context) {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }
    val account = remember(context) {
        NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie(context) })
    }

    var messages by rememberRetainedPageState(contact.id) { mutableStateOf<List<MeloXPrivateMessage>>(emptyList()) }
    var currentUserId by rememberSaveable { mutableStateOf(0L) }
    var draft by rememberSaveable(contact.id) { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val messageListState = rememberLazyListState()
    val messagesWithTime = remember(messages) {
        val labels = mutableSetOf<String>()
        messages.map { message ->
            val label = formatFullMessageTime(message.timeMs)
            message to label.takeIf(labels::add)
        }
    }

    LaunchedEffect(contact.id, reload) {
        loading = true
        error = null
        runCatching {
            val profile = account.accountProfile()
            currentUserId = profile.userId
            messages = ops.privateMessageHistory(contact.id)
        }.onFailure { error = it.message ?: context.getString(R.string.message_dm_failed) }
        loading = false
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.id) {
        if (!loading && messages.isNotEmpty()) {
            messageListState.animateScrollToItem(messages.lastIndex)
        }
    }
    val send = {
        val text = draft.trim()
        if (text.isNotBlank() && !busy) {
            busy = true
            scope.launch {
                // The history refresh must stay inside the try block: an
                // exception thrown from the old onSuccess lambda escaped the
                // coroutine, skipped `busy = false` and left the send button
                // permanently disabled (or crashed the screen).
                try {
                    ops.sendPrivateText(text, contact.id)
                    draft = ""
                    messages = ops.privateMessageHistory(contact.id)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: context.getString(R.string.message_send_failed)
                } finally {
                    busy = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .meloXLiquidButton(
                        shape = CircleShape,
                        surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .10f),
                    )
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.ChevronLeft,
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = contact.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                )
                Text(
                    text = contact.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(44.dp))
        }

        if (loading && messages.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                state = messageListState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 78.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messagesWithTime, key = { it.first.id }) { (message, timeLabel) ->
                    val outgoing = message.fromUserId == currentUserId
                    MessageBubble(message, outgoing, timeLabel)
                }
            }
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .meloXLiquidButton(
                        shape = Capsule(),
                        surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .10f),
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isBlank()) Text(
                                stringResource(R.string.message_input),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                fontSize = 15.sp,
                            )
                            inner()
                        }
                    },
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .meloXLiquidButton(
                        shape = CircleShape,
                        enabled = !busy && draft.isNotBlank(),
                        surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .10f),
                    )
                    .clickable(enabled = !busy && draft.isNotBlank()) { send() },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) Text("…", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                else MeloXSymbolIcon(
                    symbol = MeloXSymbol.ArrowUp,
                    modifier = Modifier.size(21.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MeloXPrivateMessage,
    outgoing: Boolean,
    timeLabel: String?,
) {
    val context = LocalContext.current
    val bubbleColor = if (outgoing) Color(0xFF269DFD) else MaterialTheme.colorScheme.onBackground.copy(alpha = .09f)
    val contentColor = if (outgoing) Color.White else MaterialTheme.colorScheme.onBackground
    var menuExpanded by remember(message.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        timeLabel?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
        }
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .drawBehind {
                        val tailWidth = 24.dp.toPx()
                        val tailHeight = 14.dp.toPx()
                        val bodyHeight = (size.height - tailHeight).coerceAtLeast(0f)
                        val bodyLeft = if (outgoing) 0f else tailWidth
                        drawRoundRect(
                            color = bubbleColor,
                            topLeft = androidx.compose.ui.geometry.Offset(bodyLeft, 0f),
                            size = androidx.compose.ui.geometry.Size(size.width - tailWidth, bodyHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                        )
                        val path = Path().apply {
                            if (outgoing) {
                                moveTo(size.width - 23.dp.toPx(), bodyHeight - 5.dp.toPx())
                                cubicTo(size.width - 18.dp.toPx(), bodyHeight + 5.dp.toPx(), size.width - 12.dp.toPx(), size.height - 2.dp.toPx(), size.width, size.height)
                                cubicTo(size.width - 10.dp.toPx(), size.height - 1.dp.toPx(), size.width - 15.dp.toPx(), bodyHeight + 1.dp.toPx(), size.width - 21.dp.toPx(), bodyHeight - 3.dp.toPx())
                            } else {
                                moveTo(23.dp.toPx(), bodyHeight - 5.dp.toPx())
                                cubicTo(18.dp.toPx(), bodyHeight + 5.dp.toPx(), 12.dp.toPx(), size.height - 2.dp.toPx(), 0f, size.height)
                                cubicTo(10.dp.toPx(), size.height - 1.dp.toPx(), 15.dp.toPx(), bodyHeight + 1.dp.toPx(), 21.dp.toPx(), bodyHeight - 3.dp.toPx())
                            }
                            close()
                        }
                        drawPath(path, bubbleColor)
                    }
                    .combinedClickable(onClick = {}, onLongClick = { menuExpanded = true })
                    .padding(start = if (outgoing) 13.dp else 37.dp, end = if (outgoing) 37.dp else 13.dp)
                    .padding(top = 9.dp, bottom = 23.dp),
            ) {
                message.resource?.let { MessageResourceCard(it, outgoing) }
                if (message.text.isNotBlank()) Text(
                    message.text,
                    color = contentColor,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = if (message.resource == null) 0.dp else 8.dp),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.message_copy)) },
                    onClick = {
                        val summary = message.text.ifBlank { message.resource?.title.orEmpty() }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.message_clipboard_label), summary))
                        menuExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageResourceCard(resource: MeloXPrivateMessageResource, outgoing: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = resource.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
            Text(resource.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                resource.subtitle,
                color = if (outgoing) Color.White.copy(alpha = .76f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MeloXSymbolIcon(
            MeloXSymbol.ChevronRight,
            Modifier.size(15.dp),
            if (outgoing) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatFullMessageTime(timeMs: Long): String {
    val normalized = if (timeMs in 1 until 100_000_000_000L) timeMs * 1_000L else timeMs
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(normalized))
}

private fun formatMessageTime(timeMs: Long): String {
    val normalizedTimeMs = if (timeMs in 1 until 100_000_000_000L) timeMs * 1_000L else timeMs
    val date = Date(normalizedTimeMs)
    val now = Calendar.getInstance()
    val messageCal = Calendar.getInstance().apply { time = date }
    return when {
        now.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == messageCal.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        now.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - messageCal.get(Calendar.DAY_OF_YEAR) == 1 ->
            "Yesterday"
        else -> SimpleDateFormat("M/d/yy", Locale.getDefault()).format(date)
    }
}
