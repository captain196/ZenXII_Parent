package com.schoolsync.parent.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.R
import com.schoolsync.parent.data.model.firestore.SupportMessageDoc
import com.schoolsync.parent.ui.theme.*

/**
 * One ticket thread.
 *
 * Replying to a RESOLVED ticket reopens it — but the app never says so by
 * writing `status`. A Cloud Function makes that transition when it sees a
 * parent message on a resolved ticket inside the reopen window. The parent has
 * no update path on a ticket at all (`allow update: if false`), which is what
 * removed a whole class of bug: a rules allowlist constrains WHICH keys change,
 * not what values they take, so a client-side transition let a parent write
 * "closed" and bury their own ticket.
 *
 * The banner tells them what replying will do, so the behaviour is not a
 * surprise.
 */
@Composable
fun SupportThreadScreen(
    ticketId: String,
    onBack: () -> Unit,
    viewModel: SupportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(ticketId) { viewModel.openTicket(ticketId) }

    // Keep the newest message in view as the thread grows.
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    val ticket = uiState.activeTicket
    val closed = ticket?.status == "closed"
    val resolved = ticket?.status == "resolved"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = c.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        ticket?.subject?.ifBlank { null }
                            ?: stringResource(R.string.support_thread_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    ticket?.let {
                        // Drop the category when it merely repeats the title: a
                        // blank subject is backfilled with the category label on
                        // create, so the header otherwise reads the same words
                        // twice. The ticket number is still worth showing.
                        val cat = viewModel.categoryLabelLocalized(it.category)
                        val num = if (it.ticketNo > 0) "#${it.ticketNo}" else ""
                        val line = if (it.subject.equals(cat, ignoreCase = true)) num
                                   else if (num.isEmpty()) cat
                                   else "$cat  ·  $num"
                        if (line.isNotEmpty()) {
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                        }
                    }
                }
            }

            uiState.errorMessage?.let { msg ->
                Banner(msg, c.error, c.errorBg)
            }
            uiState.infoMessage?.let { msg ->
                Banner(msg, c.success, c.successBg)
            }
            if (resolved) Banner(stringResource(R.string.support_resolved_banner), c.success, c.successBg)
            if (closed) Banner(stringResource(R.string.support_closed_banner), c.textSecondary, c.surfaceDark)

            // What the parent themselves attached. Rendered from the TICKET, which
            // is where the filenames live — not from the messages. Before this the
            // sender had no way to see their own evidence had gone anywhere, and
            // that is precisely what hid the upload being dead for the whole life
            // of the module: a working upload and a broken one looked identical
            // from this screen.
            if (uiState.attachmentUrls.isNotEmpty()) {
                AttachmentStrip(uiState.attachmentUrls, c)
            }

            when {
                uiState.isThreadLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = c.accent)
                }
                uiState.messages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        stringResource(R.string.support_no_messages),
                        color = c.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { m -> MessageBubble(m, c) }
                }
            }

            // No composer on a closed ticket: the server refuses the write, and
            // offering a box that always fails is worse than offering none.
            if (!closed) {
                Surface(color = c.surfaceElevated, tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = uiState.replyBody,
                            onValueChange = viewModel::updateReply,
                            placeholder = { Text(stringResource(R.string.support_reply_hint)) },
                            maxLines = 4,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.sendReply(ticketId) },
                            enabled = uiState.replyBody.isNotBlank() && !uiState.isSendingReply
                        ) {
                            if (uiState.isSendingReply) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                    color = c.accent
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    stringResource(R.string.support_send_reply),
                                    tint = c.accent
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
private fun Banner(text: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = fg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(12.dp)
    )
}

@Composable
private fun MessageBubble(m: SupportMessageDoc, c: AppColors) {
    // A system message is bookkeeping — an assignment, a return to the queue.
    // It is centred and muted so it never reads as somebody talking.
    if (m.isSystem) {
        Text(
            text = m.body,
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        )
        return
    }

    val mine = m.isFromParent
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Text(
            text = if (mine) stringResource(R.string.support_from_you)
                   else m.senderName.ifBlank { stringResource(R.string.support_from_school) },
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = m.body,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (mine) c.accentBg else c.surfaceElevated)
                .padding(12.dp)
        )
    }
}

/**
 * The parent's own attachments, as tappable thumbnails.
 *
 * Deliberately NOT lazy-loaded. The panel's copy of this strip was invisible for
 * exactly that reason — `loading="lazy"` on images written into a container that
 * was still hidden, which Chrome never schedules. There are at most three of
 * these and they sit at the top of the thread, so laziness buys nothing and has
 * already cost a release.
 */
@Composable
private fun AttachmentStrip(urls: List<String>, c: AppColors) {
    var zoomed by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        urls.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.support_attachment_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceDark)
                    .clickable { zoomed = url }
            )
        }
    }

    zoomed?.let { url ->
        Dialog(onDismissRequest = { zoomed = null }) {
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.support_attachment_cd),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surfaceElevated)
                    .clickable { zoomed = null }
            )
        }
    }
}
