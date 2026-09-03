package com.schoolsync.parent.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.R
import com.schoolsync.parent.data.model.firestore.SupportTicketDoc
import com.schoolsync.parent.ui.theme.*

/**
 * The parent's own tickets.
 *
 * Deliberately distinguishes three states that are easy to collapse into one:
 * loading, genuinely empty, and failed. An error rendered as an empty list is
 * the read-side version of reporting a failure as success — and "you have no
 * tickets" reads as good news, so it is the more dangerous of the two.
 */
@Composable
fun SupportListScreen(
    onBack: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onCompose: () -> Unit,
    viewModel: SupportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = c.textPrimary)
                }
                Text(
                    text = stringResource(R.string.support_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            uiState.errorMessage?.let { msg ->
                ErrorBanner(msg, c) { viewModel.clearError() }
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = c.accent)
                }

                // Only an EMPTY list with NO error is genuinely empty.
                uiState.tickets.isEmpty() && uiState.errorMessage == null -> EmptyState(c)

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.tickets, key = { it.ticketId }) { t ->
                        TicketRow(t, c, viewModel) { onOpenTicket(t.ticketId) }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCompose,
            containerColor = c.accent,
            contentColor = c.textPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .navigationBarsPadding(),
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(stringResource(R.string.support_new_ticket)) }
        )
    }
}

@Composable
private fun EmptyState(c: AppColors) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.support_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.support_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary
        )
    }
}

@Composable
private fun ErrorBanner(msg: String, c: AppColors, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.errorBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(msg, color = c.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.support_retry), color = c.error)
        }
    }
}

@Composable
private fun TicketRow(
    t: SupportTicketDoc,
    c: AppColors,
    vm: SupportViewModel,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ticketNo arrives a moment after creation, from a Cloud Function.
            // Until then it is 0 and we simply omit it — the ticket is fully
            // usable without a display number.
            if (t.ticketNo > 0) {
                Text(
                    stringResource(R.string.support_ticket_number, t.ticketNo),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary
                )
                Spacer(Modifier.width(8.dp))
            }
            StatusChip(t.status, c)
            Spacer(Modifier.weight(1f))
            if (isAwaitingSchool(t)) {
                Text(
                    stringResource(R.string.support_awaiting_school),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.warning
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            t.subject.ifBlank { vm.categoryLabelLocalized(t.category) },
            style = MaterialTheme.typography.bodyLarge,
            color = c.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        // The category line is redundant when the subject IS the category: a
        // blank subject is backfilled with the category label on create (see
        // SupportViewModel), deliberately, so the panel's triage queue has no
        // blank rows. That leaves the card printing the same words twice.
        val categoryLabel = vm.categoryLabelLocalized(t.category)
        // Compare against the ENGLISH label, not the localized one: the subject
        // was backfilled with the English label at create time and is stored
        // that way. Comparing against the translation never matches, so the
        // card would print the same category twice — once as the stored English
        // subject and once as the Tamil label.
        if (!t.subject.equals(vm.categoryLabel(t.category), ignoreCase = true)) {
            Spacer(Modifier.height(2.dp))
            Text(
                categoryLabel,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
        }
    }
}

/**
 * True when the parent replied more recently than the school did.
 *
 * Reads the two denormalised timestamps rather than the thread, which is why
 * they are stored separately in the first place.
 */
private fun isAwaitingSchool(t: SupportTicketDoc): Boolean {
    // A resolved or closed ticket is not waiting on anybody. Without this the
    // card told a parent the school still owed them a reply on a ticket that
    // had already been closed — the timestamps alone cannot say that, because
    // the parent legitimately spoke last. Caught on device UAT 2026-08-29.
    if (t.status == "closed" || t.status == "resolved") return false
    val parent = millisOf(t.lastParentReplyAt) ?: return false
    val staff = millisOf(t.lastStaffReplyAt)
    return staff == null || parent > staff
}

/** Firestore timestamps arrive in several shapes depending on the read path. */
private fun millisOf(v: Any?): Long? = when (v) {
    null -> null
    is com.google.firebase.Timestamp -> v.toDate().time
    is java.util.Date -> v.time
    is Long -> v
    is String -> runCatching { java.time.Instant.parse(v).toEpochMilli() }.getOrNull()
    else -> null
}

@Composable
private fun StatusChip(status: String, c: AppColors) {
    val (label, fg, bg) = when (status) {
        "open" -> Triple(stringResource(R.string.support_status_open), c.warning, c.warningBg)
        "assigned" -> Triple(stringResource(R.string.support_status_assigned), c.accent, c.accentBg)
        "reopened" -> Triple(stringResource(R.string.support_status_reopened), c.accent, c.accentBg)
        "resolved" -> Triple(stringResource(R.string.support_status_resolved), c.success, c.successBg)
        else -> Triple(stringResource(R.string.support_status_closed), c.textSecondary, c.surfaceDark)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
