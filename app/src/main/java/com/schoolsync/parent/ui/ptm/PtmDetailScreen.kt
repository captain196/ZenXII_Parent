package com.schoolsync.parent.ui.ptm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

@Composable
fun PtmDetailScreen(
    ptmEventId: String,
    onBack: () -> Unit,
    viewModel: PtmDetailViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(ptmEventId) {
        viewModel.load(ptmEventId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = c.textPrimary
                )
            }
            Text(
                "Parent-Teacher Meeting",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent)
                }
            }
            state.ptm == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        state.errorMessage ?: "PTM not found.",
                        color = c.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            else -> {
                val ptm = state.ptm!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ── Header card: title + date + window + location + description ─
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(18.dp)
                                .padding(16.dp)
                        ) {
                            Text(
                                ptm.title.ifBlank { "Parent-Teacher Meeting" },
                                style = MaterialTheme.typography.titleLarge,
                                color = c.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            MetaRow(Icons.Filled.CalendarMonth, formatPtmDate(ptm.date), c)
                            // Time window — prefer root startTime/endTime; fall back
                            // to legacy slot range so old PTMs still render correctly.
                            // Display in IST 12-hour AM/PM via to12hDetail().
                            val window = remember(ptm) {
                                val s = ptm.startTime.ifBlank {
                                    ptm.slots.firstOrNull()?.startTime.orEmpty()
                                }
                                val e = ptm.endTime.ifBlank {
                                    ptm.slots.lastOrNull()?.endTime.orEmpty()
                                }
                                if (s.isNotBlank() && e.isNotBlank())
                                    "${to12hDetail(s)} – ${to12hDetail(e)}"
                                else ""
                            }
                            if (window.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                MetaRow(Icons.Filled.Schedule, window, c)
                            }
                            if (ptm.location.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                MetaRow(Icons.Filled.LocationOn, ptm.location, c)
                            }
                            if (ptm.description.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    ptm.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textSecondary
                                )
                            }
                        }
                    }

                    // ── Section + class teacher card ────────────────────
                    item {
                        AssignmentCard(state, c)
                    }

                    // ── Status / action card ───────────────────────────
                    when {
                        state.isApplied -> item { AppliedCard(state, c, viewModel) }
                        state.isDeclined -> item { DeclinedCard(state, c, viewModel) }
                        state.isDelivered -> item { DeliveredCard(state, c) }
                        state.isFresh -> {
                            item {
                                Text(
                                    "Add a note (optional)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = c.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = state.note,
                                    onValueChange = viewModel::onNoteChange,
                                    placeholder = { Text("e.g. Topics I'd like to discuss", style = TextStyle(fontSize = 13.sp)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = c.textPrimary,
                                        unfocusedTextColor = c.textPrimary,
                                        cursorColor = c.accent,
                                        focusedBorderColor = c.accent,
                                        unfocusedBorderColor = c.glassBorder,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    )
                                )
                            }
                            item { ActionRow(state, c, viewModel) }
                        }
                    }

                    state.errorMessage?.let {
                        item {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = c.error)
                        }
                    }
                    state.successMessage?.let {
                        item {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.success,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, c: com.schoolsync.parent.ui.theme.AppColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
    }
}

@Composable
private fun AssignmentCard(state: PtmDetailUiState, c: com.schoolsync.parent.ui.theme.AppColors) {
    val a = state.assignment
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .padding(16.dp)
    ) {
        Text(
            "Your section's class teacher",
            style = MaterialTheme.typography.labelMedium,
            color = c.textTertiary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        if (a == null) {
            Text(
                "This PTM doesn't include your section.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.error
            )
        } else if (a.classTeacherId.isBlank() || a.classTeacherName.isBlank()) {
            Text(
                "No class teacher set for ${a.sectionKey}. Please ask the school office.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.error
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, null, tint = c.accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        a.classTeacherName,
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        a.sectionKey,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AppliedCard(
    state: PtmDetailUiState,
    c: com.schoolsync.parent.ui.theme.AppColors,
    viewModel: PtmDetailViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.accent.copy(alpha = 0.12f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "You've applied for this PTM",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                val q = state.queueNumber
                val sub = if (q != null && q > 0) "Queue number: #$q"
                          else "You're in the queue."
                Text(sub, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
            if (state.queueNumber != null && state.queueNumber > 0) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(c.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "#${state.queueNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (c.isDark) c.bgStart else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::enterEditMode,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                enabled = !state.isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.glass,
                    contentColor = c.textPrimary
                ),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("Change my response", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DeclinedCard(
    state: PtmDetailUiState,
    c: com.schoolsync.parent.ui.theme.AppColors,
    viewModel: PtmDetailViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.errorBg)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Close, null, tint = c.error, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "You've declined this PTM",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "We've let the school know you can't make it.",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = viewModel::enterEditMode,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            enabled = !state.isSubmitting,
            colors = ButtonDefaults.buttonColors(
                containerColor = c.glass,
                contentColor = c.textPrimary
            ),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text("Change my response", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DeliveredCard(state: PtmDetailUiState, c: com.schoolsync.parent.ui.theme.AppColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF22C55E).copy(alpha = 0.12f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Meeting completed",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Your class teacher has marked your meeting delivered.",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    state: PtmDetailUiState,
    c: com.schoolsync.parent.ui.theme.AppColors,
    viewModel: PtmDetailViewModel
) {
    val canApply = state.assignment != null && state.assignment.classTeacherId.isNotBlank()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = viewModel::decline,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50),
            enabled = !state.isSubmitting,
            colors = ButtonDefaults.buttonColors(
                containerColor = c.errorBg,
                contentColor = c.error
            ),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Can't make it", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = viewModel::apply,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50),
            enabled = !state.isSubmitting && canApply,
            colors = ButtonDefaults.buttonColors(
                containerColor = c.accent,
                contentColor = if (c.isDark) c.bgStart else Color.White
            ),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = if (c.isDark) c.bgStart else Color.White
                )
            } else {
                Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apply for PTM", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatPtmDate(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val out = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault())
        parser.parse(iso)?.let { out.format(it) } ?: iso
    } catch (_: Exception) {
        iso
    }
}

// remember helper
@Composable
private fun <T> remember(key: Any?, calc: () -> T): T = androidx.compose.runtime.remember(key) { calc() }

/**
 * "HH:MM" (24h, what's stored) → "h:mm AM/PM" for display in IST. Pure
 * formatting — wall-clock strings, no timezone conversion. Named with
 * the `Detail` suffix to avoid clashing with the same helper in
 * PtmListScreen.kt (both files are top-level in the same package).
 */
private fun to12hDetail(hm: String): String {
    val m = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(hm.trim()) ?: return hm
    val h24 = m.groupValues[1].toIntOrNull() ?: return hm
    val ap  = if (h24 >= 12) "PM" else "AM"
    val h12 = (h24 % 12).let { if (it == 0) 12 else it }
    return "$h12:${m.groupValues[2]} $ap"
}
