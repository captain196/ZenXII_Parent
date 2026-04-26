package com.schoolsync.parent.ui.messages

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.schoolsync.parent.R
import com.schoolsync.parent.data.model.ChatMessage
import com.schoolsync.parent.data.model.InboxMessage
import com.schoolsync.parent.ui.components.staggerIn
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel = hiltViewModel(),
    /**
     * Notifies the parent navigation that we've entered/left the chat view
     * so it can hide the global bottom bar — otherwise the chat input is
     * covered by the floating nav bar.
     */
    onChatViewChange: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isInChatView) { onChatViewChange(uiState.isInChatView) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { onChatViewChange(false) }
    }

    if (uiState.isInChatView) {
        BackHandler { viewModel.goBackToList() }
        ChatView(
            conversation = uiState.selectedConversation,
            messages = uiState.chatMessages,
            messageText = uiState.messageText,
            isSending = uiState.isSending,
            isLoading = uiState.isLoading,
            currentUserId = uiState.currentUserId,
            onMessageTextChange = viewModel::onMessageTextChange,
            onSend = viewModel::sendMessage,
            onBack = viewModel::goBackToList,
            onDelete = { id -> viewModel.deleteConversation(id) },
            formatRelative = viewModel::formatTimestamp
        )
    } else {
        InboxListView(
            inbox = uiState.inbox,
            isLoading = uiState.isLoading,
            searchQuery = uiState.searchQuery,
            onSearchChange = viewModel::searchInbox,
            onMessageClick = viewModel::openConversation,
            onDeleteConversation = { id -> viewModel.deleteConversation(id) },
            errorMessage = uiState.errorMessage,
            formatRelative = viewModel::formatTimestamp
        )
    }
}

// ─── Inbox list ──────────────────────────────────────────────────────────────

@Composable
private fun InboxListView(
    inbox: List<InboxMessage>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onMessageClick: (InboxMessage) -> Unit,
    onDeleteConversation: (String) -> Unit,
    errorMessage: String?,
    formatRelative: (Long) -> String
) {
    val c = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.messages_title),
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (inbox.isNotEmpty()) {
                val totalUnread = inbox.sumOf { it.unreadCount }
                if (totalUnread > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.accent.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.messages_unread_badge_format, totalUnread),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(stringResource(R.string.messages_search_placeholder), color = c.textTertiary)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = c.textSecondary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_clear),
                            tint = c.textSecondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
                cursorColor = c.accent,
                focusedBorderColor = c.accent.copy(alpha = 0.6f),
                unfocusedBorderColor = c.glassBorder,
                focusedContainerColor = c.glass,
                unfocusedContainerColor = c.glass
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(12.dp))

        Crossfade(
            targetState = isLoading && inbox.isEmpty(),
            animationSpec = tween(220),
            label = "messages-loading"
        ) { loading ->
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = c.accent, modifier = Modifier.size(40.dp))
                }
                inbox.isEmpty() -> EmptyMessagesState(searchQuery.isNotBlank())
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = inbox,
                        key = { _, it -> it.messageId }
                    ) { index, message ->
                        Box(modifier = Modifier.staggerIn(index)) {
                            InboxItem(
                                message = message,
                                formatRelative = formatRelative,
                                onClick = { onMessageClick(message) },
                                onDelete = { onDeleteConversation(message.conversationId) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }

        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.errorBg)
                    .border(1.dp, c.error.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(text = error, color = c.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InboxItem(
    message: InboxMessage,
    formatRelative: (Long) -> String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalAppColors.current
    val unread = message.unreadCount > 0
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        DeleteConversationDialog(
            otherName = message.otherName,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(18.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar — profile pic if available, otherwise colored initials circle
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(c.accent.copy(alpha = 0.18f))
                .border(
                    width = if (unread) 2.dp else 0.dp,
                    color = if (unread) c.accent else c.glassBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (message.otherProfilePic.isNotBlank()) {
                AsyncImage(
                    model = message.otherProfilePic,
                    contentDescription = message.otherName,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(
                    text = message.initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.otherName.ifBlank { stringResource(R.string.generic_unknown) },
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatRelative(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unread) c.accent else c.textTertiary,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            if (message.studentName.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                val reText = if (message.studentClass.isNotBlank()) {
                    stringResource(
                        R.string.messages_re_with_class_format,
                        message.studentName,
                        message.studentClass
                    )
                } else {
                    stringResource(R.string.messages_re_format, message.studentName)
                }
                Text(
                    text = reText,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.lastMessage.ifBlank { stringResource(R.string.messages_tap_to_chat) },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unread) c.textPrimary else c.textSecondary,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (unread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .widthIn(min = 22.dp)
                            .height(22.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (message.unreadCount > 99) "99+" else "${message.unreadCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.pillText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Long-press menu — anchored to the right side of the row.
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Delete chat", color = c.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = c.error
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        confirmDelete = true
                    }
                )
            }
        }
    }
}

@Composable
private fun DeleteConversationDialog(
    otherName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete chat", color = c.textPrimary) },
        text = {
            Text(
                "Delete this conversation with ${otherName.ifBlank { "this contact" }}? " +
                    "It will be removed from your inbox only — the other person will still see it.",
                color = c.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = c.error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = c.textSecondary)
            }
        },
        containerColor = c.bgMid
    )
}

// ─── Chat view ───────────────────────────────────────────────────────────────

@Composable
private fun ChatView(
    conversation: InboxMessage?,
    messages: List<ChatMessage>,
    messageText: String,
    isSending: Boolean,
    isLoading: Boolean,
    currentUserId: String,
    onMessageTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    formatRelative: (Long) -> String
) {
    val c = LocalAppColors.current
    val listState = rememberLazyListState()
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && conversation != null) {
        DeleteConversationDialog(
            otherName = conversation.otherName,
            onConfirm = {
                confirmDelete = false
                onDelete(conversation.conversationId)
            },
            onDismiss = { confirmDelete = false }
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
    ) {
        // ── Chat header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .glassCard(22.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = c.textPrimary
                )
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(c.accent.copy(alpha = 0.18f))
                    .border(1.dp, c.glassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!conversation?.otherProfilePic.isNullOrBlank()) {
                    AsyncImage(
                        model = conversation!!.otherProfilePic,
                        contentDescription = conversation.otherName,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = conversation?.initials ?: "?",
                        style = MaterialTheme.typography.titleSmall,
                        color = c.accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val chatDefault = stringResource(R.string.chat_default_title)
                Text(
                    text = conversation?.otherName?.ifBlank { chatDefault } ?: chatDefault,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = listOfNotNull(
                    conversation?.studentName?.takeIf { it.isNotBlank() },
                    conversation?.studentClass?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Delete chat (per-user) button — only enabled when we have a
            // conversation reference to act on.
            IconButton(
                onClick = { confirmDelete = true },
                enabled = conversation != null
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete chat",
                    tint = c.textSecondary
                )
            }
        }

        // ── Messages list ──
        if (isLoading) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = c.accent, modifier = Modifier.size(32.dp))
            }
        } else if (messages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.chat_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textTertiary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                var lastDateKey: String? = null
                messages.forEachIndexed { index, msg ->
                    val dateKey = dayKey(msg.timestamp)
                    if (dateKey != lastDateKey) {
                        lastDateKey = dateKey
                        item(key = "date_$dateKey$index") {
                            DateSeparator(label = dayLabel(msg.timestamp))
                        }
                    }
                    val prev = messages.getOrNull(index - 1)
                    val showSenderName = msg.senderId != currentUserId &&
                        msg.senderName.isNotBlank() &&
                        (prev == null || prev.senderId != msg.senderId)

                    item(key = msg.messageId.ifBlank { "m$index" }) {
                        MessageBubble(
                            message = msg,
                            isSentByMe = msg.senderId == currentUserId,
                            showSenderName = showSenderName,
                            formatRelative = formatRelative
                        )
                    }
                }
            }
        }

        // ── Input bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .glassCard(28.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                placeholder = {
                    Text(stringResource(R.string.chat_input_placeholder), color = c.textTertiary)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = c.textPrimary,
                    unfocusedTextColor = c.textPrimary,
                    cursorColor = c.accent,
                    focusedBorderColor = c.glassBorder,
                    unfocusedBorderColor = c.glassBorder,
                    focusedContainerColor = c.glass.copy(alpha = 0.0f),
                    unfocusedContainerColor = c.glass.copy(alpha = 0.0f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(6.dp))

            val canSend = messageText.isNotBlank() && !isSending
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (canSend) c.accent else c.accent.copy(alpha = 0.35f))
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = c.pillText,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send),
                        tint = c.pillText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isSentByMe: Boolean,
    showSenderName: Boolean,
    formatRelative: (Long) -> String
) {
    val c = LocalAppColors.current
    val bubbleColor = if (isSentByMe) c.chatSent else c.chatReceived
    val textColor = if (isSentByMe) c.pillText else c.textPrimary
    val metaColor = if (isSentByMe) c.pillText.copy(alpha = 0.75f) else c.textTertiary

    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isSentByMe) 20.dp else 6.dp,
        bottomEnd = if (isSentByMe) 6.dp else 20.dp
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        contentAlignment = if (isSentByMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        val maxBubbleWidth = maxWidth * 0.80f

        Column(
            modifier = Modifier.widthIn(max = maxBubbleWidth),
            horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
        ) {
            // Sender name above the bubble (received messages only) so the
            // bubble itself stays a clean conversational unit.
            if (showSenderName && !isSentByMe) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(bubbleColor)
                    .border(
                        width = if (isSentByMe) 0.dp else 1.dp,
                        color = if (isSentByMe) bubbleColor else c.glassBorder.copy(alpha = 0.6f),
                        shape = shape
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = clockTime(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = metaColor,
                            fontSize = 10.sp
                        )
                        if (isSentByMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val isRead = message.readBy.any { (k, v) -> v && k != message.senderId }
                            Icon(
                                imageVector = if (isRead) Icons.Filled.DoneAll else Icons.Filled.Check,
                                contentDescription = stringResource(
                                    if (isRead) R.string.chat_status_read else R.string.chat_status_sent
                                ),
                                tint = if (isRead) c.success else metaColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSeparator(label: String) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(c.glass)
                .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun clockTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return try {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}

private fun dayKey(timestamp: Long): String {
    if (timestamp <= 0L) return "0"
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
}

/**
 * Localised day label for chat date separators. The composable wrapper
 * pulls the localised "Today" / "Yesterday" strings from resources.
 */
@Composable
private fun dayLabelLocalized(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val msg = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    val diffDays = ((now.timeInMillis - msg.timeInMillis) / (1000L * 60 * 60 * 24)).toInt()

    val today = stringResource(R.string.chat_day_today)
    val yesterday = stringResource(R.string.chat_day_yesterday)

    return when {
        msg.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            msg.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> today
        diffDays in 0..1 -> yesterday
        diffDays in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        msg.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

/** Non-composable wrapper kept for legacy callers — defaults to English. */
private fun dayLabel(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val msg = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    val diffDays = ((now.timeInMillis - msg.timeInMillis) / (1000L * 60 * 60 * 24)).toInt()

    return when {
        msg.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            msg.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Today"
        diffDays in 0..1 -> "Yesterday"
        diffDays in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        msg.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun EmptyMessagesState(isSearchEmpty: Boolean = false) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(c.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearchEmpty) Icons.Filled.Search else Icons.Filled.Chat,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(
                    if (isSearchEmpty) R.string.messages_search_empty_title
                    else R.string.messages_empty_title
                ),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (isSearchEmpty) R.string.messages_search_empty_subtitle
                    else R.string.messages_empty_subtitle
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
