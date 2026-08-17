package com.schoolsync.parent.ui.leave

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.schoolsync.parent.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.firestore.LeaveApplicationDoc
import com.schoolsync.parent.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Single source of truth for leave-type code -> label.
 *
 * The CODE ("CL"/"SL"/"EL") is the wire value written to Firestore and matched
 * back from history — it never changes. The label is a @StringRes id resolved
 * at the render site: this is a top-level val, evaluated outside composition,
 * so a String captured here would freeze the launch language.
 */
private val LEAVE_TYPES = listOf(
    "CL" to R.string.leave_type_casual,
    "SL" to R.string.leave_type_sick,
    "EL" to R.string.leave_type_emergency
)
private val LEAVE_TYPE_LABELS = LEAVE_TYPES.toMap()

/** A [SelectableDates] that only permits dates on/after [min] (UTC comparison, DatePicker's basis). */
@OptIn(ExperimentalMaterial3Api::class)
private fun minSelectableDates(min: LocalDate): SelectableDates {
    val minMillis = min.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    return object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= minMillis
        override fun isSelectableYear(year: Int): Boolean = year >= min.year
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LeaveScreen(
    onBack: () -> Unit,
    viewModel: LeaveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }
    // Phase 9b: use theme-aware colors instead of hardcoded DarkColors constants
    val c = LocalAppColors.current
    val Accent = c.accent
    val TextPrimary = c.textPrimary
    val TextSecondary = c.textSecondary

    // Snackbar for success
    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = TextPrimary
                    )
                }
                Text(
                    text = stringResource(R.string.leave_application),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Apply form (collapsible)
            AnimatedVisibility(
                visible = uiState.showApplyForm,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                LeaveApplyForm(
                    uiState = uiState,
                    dateFormatter = dateFormatter,
                    onLeaveTypeChanged = viewModel::updateLeaveType,
                    onStartDateChanged = viewModel::updateStartDate,
                    onEndDateChanged = viewModel::updateEndDate,
                    onReasonChanged = viewModel::updateReason,
                    onSubmit = viewModel::submitLeave,
                    onCancel = viewModel::toggleApplyForm
                )
            }

            // Leave history
            com.schoolsync.parent.ui.common.PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.pullRefresh() }
            ) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Accent)
                    }
                } else if (uiState.leaveHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = TextSecondary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.leave_none_yet),
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.leaveHistory, key = { it.id }) { leave ->
                            LeaveHistoryCard(
                                leave = leave,
                                dateFormatter = dateFormatter,
                                cancelling = uiState.cancellingId == leave.id,
                                onCancel = { viewModel.cancelLeave(leave.id) }
                            )
                        }
                    }
                }
            }
        }

        // FAB to apply leave
        if (!uiState.showApplyForm) {
            FloatingActionButton(
                onClick = viewModel::toggleApplyForm,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = Accent
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.leave_apply),
                    tint = Color.White
                )
            }
        }

        // Single snackbar slot — error takes priority so success + error never stack.
        val error = uiState.errorMessage
        when {
            error != null -> {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = c.error,
                    action = {
                        TextButton(onClick = viewModel::clearError) {
                            Text(stringResource(R.string.common_dismiss), color = Color.White)
                        }
                    }
                ) {
                    Text(error)
                }
            }
            uiState.submitSuccess -> {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = c.success
                ) {
                    Text(stringResource(R.string.leave_submitted))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LeaveApplyForm(
    uiState: LeaveUiState,
    dateFormatter: DateTimeFormatter,
    onLeaveTypeChanged: (String) -> Unit,
    onStartDateChanged: (LocalDate) -> Unit,
    onEndDateChanged: (LocalDate) -> Unit,
    onReasonChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    // Phase 9b: theme-aware colors (same pattern as LeaveScreen + LeaveHistoryCard)
    val c = LocalAppColors.current
    val Accent = c.accent
    val TextPrimary = c.textPrimary
    val TextSecondary = c.textSecondary

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val leaveTypes = LEAVE_TYPES

    // Fix H12: form must scroll & fit small screens AND landscape. Cap height
    // screen-relative, scroll the body internally, and pad for the IME so the
    // Submit button is never clipped or covered by the keyboard.
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val maxFormHeight = (configuration.screenHeightDp * 0.82f).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(16.dp)
            .heightIn(max = maxFormHeight)
            .clip(RoundedCornerShape(16.dp))
            .glassCard()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.leave_apply_for),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            // Fix (a11y): keep a >=48dp touch target (was forced to 24dp).
            IconButton(
                onClick = onCancel,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.leave_close_form), tint = TextSecondary)
            }
        }

        // Leave type chips
        Text(stringResource(R.string.leave_type), color = TextSecondary, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leaveTypes.forEach { (code, labelRes) ->
                val isSelected = uiState.selectedLeaveType == code
                // Hoisted: Modifier.semantics {} below is not a composable scope.
                val label = stringResource(labelRes)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        // Fix (a11y): >=48dp target + radio-button semantics.
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Accent else Color.Transparent)
                        .border(1.dp, if (isSelected) Accent else TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .clickable { onLeaveTypeChanged(code) }
                        .semantics {
                            role = Role.RadioButton
                            selected = isSelected
                            contentDescription = label
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Date selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Start date
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.field_from), color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                val startLabel = uiState.startDate?.format(dateFormatter) ?: stringResource(R.string.leave_select_date)
                // Hoisted: Modifier.semantics {} is not a composable scope.
                val startCd = stringResource(R.string.leave_start_date_fmt, startLabel)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { showStartDatePicker = true }
                        .semantics { contentDescription = startCd }
                        .padding(12.dp)
                ) {
                    Text(
                        text = startLabel,
                        color = if (uiState.startDate != null) TextPrimary else TextSecondary.copy(alpha = 0.5f)
                    )
                }
            }

            // End date
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.field_to), color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                val endLabel = uiState.endDate?.format(dateFormatter) ?: stringResource(R.string.leave_select_date)
                val endCd = stringResource(R.string.leave_end_date_fmt, endLabel)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { showEndDatePicker = true }
                        .semantics { contentDescription = endCd }
                        .padding(12.dp)
                ) {
                    Text(
                        text = endLabel,
                        color = if (uiState.endDate != null) TextPrimary else TextSecondary.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Number of days indicator
        if (uiState.startDate != null && uiState.endDate != null) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(uiState.startDate, uiState.endDate) + 1
            Text(
                text = pluralStringResource(R.plurals.leave_days_count, days.toInt(), days.toInt()),
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Reason
        OutlinedTextField(
            value = uiState.reason,
            onValueChange = onReasonChanged,
            label = { Text(stringResource(R.string.field_reason)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                focusedLabelColor = Accent,
                unfocusedLabelColor = TextSecondary,
                cursorColor = Accent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        // Submit button
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.leave_submit))
            }
        }
    }

    // Date Pickers
    if (showStartDatePicker) {
        // Fix (validation): disallow past start dates in the picker itself.
        val today = remember { LocalDate.now() }
        val datePickerState = rememberDatePickerState(
            selectableDates = minSelectableDates(today)
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        onStartDateChanged(date)
                    }
                    showStartDatePicker = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        // Fix (validation): End picker constrained to >= Start (else today).
        val minEnd = uiState.startDate ?: LocalDate.now()
        val datePickerState = rememberDatePickerState(
            selectableDates = minSelectableDates(minEnd)
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        onEndDateChanged(date)
                    }
                    showEndDatePicker = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun LeaveHistoryCard(
    leave: LeaveApplicationDoc,
    dateFormatter: DateTimeFormatter,
    cancelling: Boolean,
    onCancel: () -> Unit
) {
    val c = LocalAppColors.current
    val TextPrimary = c.textPrimary
    val TextSecondary = c.textSecondary
    val Accent = c.accent
    // Fix (case-insensitive): tolerate legacy CapitalCase statuses on read.
    val status = leave.status.lowercase()
    val statusColor = when (status) {
        "approved" -> c.success
        "rejected" -> c.error
        "cancelled" -> c.textSecondary
        else -> c.warning  // pending
    }

    val statusIcon = when (status) {
        "approved" -> Icons.Filled.CheckCircle
        "rejected" -> Icons.Filled.Cancel
        "cancelled" -> Icons.Filled.Close
        else -> Icons.Filled.HourglassEmpty
    }

    var showCancelConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .glassCard()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leave type badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Accent.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    // Fix (LOW): show the human label (e.g. "Casual Leave"), not the raw "CL" code.
                    text = LEAVE_TYPE_LABELS[leave.leaveType]?.let { stringResource(it) }
                        ?: leave.leaveType.ifEmpty { stringResource(R.string.leave_generic) },
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Status badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = status,
                    modifier = Modifier.size(16.dp),
                    tint = statusColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = status.replaceFirstChar { it.uppercase() },
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dates
        Text(
            text = "${leave.startDate}  to  ${leave.endDate}",
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )

        if (leave.numberOfDays > 0) {
            Text(
                text = pluralStringResource(R.plurals.leave_days_count, leave.numberOfDays, leave.numberOfDays),
                color = TextSecondary,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Reason
        Text(
            text = leave.reason,
            color = TextSecondary,
            fontSize = 13.sp,
            maxLines = 2
        )

        // Remarks from approver
        if (leave.remarks.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.leave_remark_fmt, leave.remarks),
                color = TextSecondary.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Cancel button (only for pending)
        if (status == "pending") {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                // Fix (LOW): confirm first + disable while a cancel is in flight.
                onClick = { showCancelConfirm = true },
                enabled = !cancelling,
                colors = ButtonDefaults.textButtonColors(contentColor = c.error)
            ) {
                if (cancelling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = c.error,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (cancelling) "Cancelling..." else stringResource(R.string.leave_cancel_application), fontSize = 13.sp)
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(stringResource(R.string.leave_cancel_confirm)) },
            text = { Text(stringResource(R.string.leave_withdraw_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onCancel()
                }) { Text(stringResource(R.string.leave_yes_cancel), color = c.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text(stringResource(R.string.common_keep)) }
            }
        )
    }
}
