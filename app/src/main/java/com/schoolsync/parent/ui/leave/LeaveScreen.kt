package com.schoolsync.parent.ui.leave

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.format.DateTimeFormatter

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
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Leave Application",
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
                                text = "No leave applications yet",
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
                    contentDescription = "Apply Leave",
                    tint = Color.White
                )
            }
        }

        // Success snackbar
        if (uiState.submitSuccess) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = c.success
            ) {
                Text("Leave application submitted successfully!")
            }
        }

        // Error snackbar
        uiState.errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = c.error,
                action = {
                    TextButton(onClick = viewModel::clearError) {
                        Text("Dismiss", color = Color.White)
                    }
                }
            ) {
                Text(error)
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

    val leaveTypes = listOf("CL" to "Casual Leave", "SL" to "Sick Leave", "EL" to "Emergency Leave")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .glassCard()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Apply for Leave",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        // Leave type chips
        Text("Leave Type", color = TextSecondary, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leaveTypes.forEach { (code, label) ->
                val selected = uiState.selectedLeaveType == code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Accent else Color.Transparent)
                        .border(1.dp, if (selected) Accent else TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .clickable { onLeaveTypeChanged(code) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.White else TextSecondary,
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
                Text("From", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { showStartDatePicker = true }
                        .padding(12.dp)
                ) {
                    Text(
                        text = uiState.startDate?.format(dateFormatter) ?: "Select date",
                        color = if (uiState.startDate != null) TextPrimary else TextSecondary.copy(alpha = 0.5f)
                    )
                }
            }

            // End date
            Column(modifier = Modifier.weight(1f)) {
                Text("To", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { showEndDatePicker = true }
                        .padding(12.dp)
                ) {
                    Text(
                        text = uiState.endDate?.format(dateFormatter) ?: "Select date",
                        color = if (uiState.endDate != null) TextPrimary else TextSecondary.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Number of days indicator
        if (uiState.startDate != null && uiState.endDate != null) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(uiState.startDate, uiState.endDate) + 1
            Text(
                text = "$days day${if (days > 1) "s" else ""}",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Reason
        OutlinedTextField(
            value = uiState.reason,
            onValueChange = onReasonChanged,
            label = { Text("Reason") },
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
                Text("Submit Application")
            }
        }
    }

    // Date Pickers
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        onStartDateChanged(date)
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        onEndDateChanged(date)
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
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
    onCancel: () -> Unit
) {
    val c = LocalAppColors.current
    val TextPrimary = c.textPrimary
    val TextSecondary = c.textSecondary
    val Accent = c.accent
    val statusColor = when (leave.status) {
        "approved" -> c.success
        "rejected" -> c.error
        "cancelled" -> c.textSecondary
        else -> c.warning  // pending
    }

    val statusIcon = when (leave.status) {
        "approved" -> Icons.Filled.CheckCircle
        "rejected" -> Icons.Filled.Cancel
        "cancelled" -> Icons.Filled.Close
        else -> Icons.Filled.HourglassEmpty
    }

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
                    text = leave.leaveType.ifEmpty { "Leave" },
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Status badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = leave.status,
                    modifier = Modifier.size(16.dp),
                    tint = statusColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = leave.status.replaceFirstChar { it.uppercase() },
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
                text = "${leave.numberOfDays} day${if (leave.numberOfDays > 1) "s" else ""}",
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
                text = "Remark: ${leave.remarks}",
                color = TextSecondary.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Cancel button (only for pending)
        if (leave.status == "pending") {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = c.error)
            ) {
                Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel Application", fontSize = 13.sp)
            }
        }
    }
}
