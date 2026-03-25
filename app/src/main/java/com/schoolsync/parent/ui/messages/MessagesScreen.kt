package com.schoolsync.parent.ui.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.ChatMessage
import com.schoolsync.parent.data.model.InboxMessage
import com.schoolsync.parent.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            onBack = viewModel::goBackToList
        )
    } else {
        InboxListView(
            inbox = uiState.inbox,
            isLoading = uiState.isLoading,
            onMessageClick = viewModel::openConversation,
            errorMessage = uiState.errorMessage
        )
    }
}

@Composable
private fun InboxListView(
    inbox: List<InboxMessage>,
    isLoading: Boolean,
    onMessageClick: (InboxMessage) -> Unit,
    errorMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        Text(
            text = "Messages",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Teal, modifier = Modifier.size(40.dp))
            }
        } else if (inbox.isEmpty()) {
            EmptyMessagesState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(inbox, key = { it.messageId }) { message ->
                    InboxItem(
                        message = message,
                        onClick = { onMessageClick(message) }
                    )
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.3f))
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }

        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ErrorRedBg)
                    .padding(12.dp)
            ) {
                Text(text = error, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InboxItem(
    message: InboxMessage,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SlateBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.otherName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.otherName.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.lastMessage.ifBlank { message.studentName },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (message.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Teal),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${message.unreadCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

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
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive
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
            .imePadding()
    ) {
        // Chat header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(0.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SlateBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (conversation?.otherName?.take(1) ?: "?").uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = conversation?.otherName ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (conversation?.studentClass?.isNotBlank() == true) {
                    Text(
                        text = conversation.studentClass,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Messages list
        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Teal, modifier = Modifier.size(32.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.messageId }) { message ->
                    MessageBubble(
                        message = message,
                        isSentByMe = message.senderId == currentUserId
                    )
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                placeholder = {
                    Text("Type a message...", color = TextTertiary)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Teal,
                    focusedBorderColor = Teal.copy(alpha = 0.5f),
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassCard,
                    unfocusedContainerColor = GlassCard
                ),
                maxLines = 4,
                singleLine = false
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSend,
                enabled = messageText.isNotBlank() && !isSending,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (messageText.isNotBlank() && !isSending) Teal
                        else Teal.copy(alpha = 0.3f)
                    )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = TextPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = TextPrimary,
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
    isSentByMe: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isSentByMe) 16.dp else 4.dp,
                        bottomEnd = if (isSentByMe) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isSentByMe) ChatSent.copy(alpha = 0.85f) else ChatReceived
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                if (!isSentByMe && message.senderName.isNotBlank()) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TealLight,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSentByMe) TextPrimary.copy(alpha = 0.7f) else TextTertiary,
                    modifier = Modifier.align(Alignment.End),
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return try {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}

@Composable
private fun EmptyMessagesState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Chat,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Messages",
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Messages from teachers will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}
