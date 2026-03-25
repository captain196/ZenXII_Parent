package com.schoolsync.parent.ui.fees

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.ClearanceStatus
import com.schoolsync.parent.data.model.FeeHead
import com.schoolsync.parent.data.model.FeePayment
import com.schoolsync.parent.data.model.HostelFee
import com.schoolsync.parent.data.model.LibraryFine
import com.schoolsync.parent.data.model.PendingMonth
import com.schoolsync.parent.data.model.TransportFee
import com.schoolsync.parent.ui.theme.AppColors
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

@Composable
fun FeesScreen(
    viewModel: FeesViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val overview = uiState.overview

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // Header
        Text(
            text = "Fees",
            style = MaterialTheme.typography.headlineLarge,
            color = c.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        // Total Pending Banner
        if (overview.pendingFees.totalPending > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.warningBg)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = c.warning,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Pending",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.warning
                    )
                    Text(
                        text = "Rs. ${"%,.0f".format(overview.pendingFees.totalPending)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.warning,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Tab Row
        ScrollableTabRow(
            selectedTabIndex = FeesTab.entries.indexOf(uiState.selectedTab),
            containerColor = c.bgStart,
            contentColor = c.textPrimary,
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        tabPositions[FeesTab.entries.indexOf(uiState.selectedTab)]
                    ),
                    color = c.accent
                )
            },
            divider = { HorizontalDivider(color = c.glassBorder) }
        ) {
            FeesTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (uiState.selectedTab == tab) c.accent else c.textSecondary,
                            fontWeight = if (uiState.selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Content
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = c.accent, modifier = Modifier.size(40.dp))
            }
        } else {
            when (uiState.selectedTab) {
                FeesTab.STRUCTURE -> FeeStructureContent(overview.feeStructure.feeHeads)
                FeesTab.PENDING -> PendingFeesContent(
                    pendingMonths = overview.pendingFees.pendingMonths,
                    paymentInProgress = uiState.paymentInProgress,
                    onPayNow = { months ->
                        viewModel.initiatePayment(months)
                    }
                )
                FeesTab.HISTORY -> PaymentHistoryContent(overview.paymentHistory)
                FeesTab.MODULE_FEES -> ModuleFeesContent(
                    transportFee = overview.transportFee,
                    hostelFee = overview.hostelFee,
                    libraryFines = overview.libraryFines
                )
                FeesTab.CLEARANCE -> ClearanceContent(overview.clearanceStatus)
            }
        }
    }
}

@Composable
private fun FeeStructureContent(items: List<FeeHead>) {
    if (items.isEmpty()) {
        EmptyFeesState(message = "Fee structure not available")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items) { item ->
            FeeStructureCard(item)
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun FeeStructureCard(item: FeeHead) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(12.dp)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CreditCard,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                if (item.frequency.isNotBlank()) {
                    Text(
                        text = item.frequency,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary
                    )
                }
            }
        }
        Text(
            text = "Rs. ${"%,.0f".format(item.amount)}",
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PendingFeesContent(
    pendingMonths: List<PendingMonth>,
    paymentInProgress: Boolean = false,
    onPayNow: (List<String>) -> Unit = {}
) {
    val c = LocalAppColors.current
    if (pendingMonths.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = c.success,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "All Clear!",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.success,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No pending fees at the moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(pendingMonths) { month ->
            PendingFeeCard(month)
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val allMonths = pendingMonths
                        .filter { it.status.equals("Pending", true) || it.status.equals("Overdue", true) }
                        .map { it.month }
                    onPayNow(allMonths)
                },
                enabled = !paymentInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.accent,
                    disabledContainerColor = c.accent.copy(alpha = 0.4f)
                )
            ) {
                if (paymentInProgress) {
                    CircularProgressIndicator(
                        color = c.textPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Processing...",
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Payment,
                        contentDescription = null,
                        tint = c.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pay Now",
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PendingFeeCard(month: PendingMonth) {
    val c = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    val statusColor = if (month.status.equals("Overdue", true)) c.error else c.warning

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .clickable { expanded = !expanded }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = month.month,
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = month.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Rs. ${"%,.0f".format(month.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = c.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                if (month.dueDate.isNotBlank()) {
                    DetailRow("Due Date", month.dueDate, c)
                }
                DetailRow("Amount", "Rs. ${"%,.0f".format(month.amount)}", c)
                DetailRow("Status", month.status, c)
            }
        }
    }
}

@Composable
private fun PaymentHistoryContent(history: List<FeePayment>) {
    if (history.isEmpty()) {
        EmptyFeesState(message = "No payment history found")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(history) { record ->
            PaymentRecordCard(record)
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun PaymentRecordCard(record: FeePayment) {
    val c = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .clickable { expanded = !expanded }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.successBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Receipt,
                    contentDescription = null,
                    tint = c.success,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.month.ifBlank { "Payment" },
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = record.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary
                )
            }
            Text(
                text = "Rs. ${"%,.0f".format(record.amount)}",
                style = MaterialTheme.typography.titleSmall,
                color = c.success,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                DetailRow("Amount", "Rs. ${"%,.0f".format(record.amount)}", c)
                DetailRow("Date", record.date, c)
                if (record.mode.isNotBlank()) DetailRow("Payment Mode", record.mode, c)
                if (record.receiptNo.isNotBlank()) DetailRow("Receipt No.", record.receiptNo, c)
                if (record.remarks.isNotBlank()) DetailRow("Remarks", record.remarks, c)
            }
        }
    }
}

// -- Module Fees Tab ----------------------------------------------------------

@Composable
private fun ModuleFeesContent(
    transportFee: TransportFee?,
    hostelFee: HostelFee?,
    libraryFines: List<LibraryFine>
) {
    val c = LocalAppColors.current
    val hasContent = transportFee != null || hostelFee != null || libraryFines.isNotEmpty()
    if (!hasContent) {
        EmptyFeesState(message = "No module fees applicable")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Transport fee
        if (transportFee != null) {
            item {
                ModuleFeeCard(
                    icon = Icons.Filled.DirectionsBus,
                    iconBg = c.infoBg,
                    iconTint = c.info,
                    title = "Transport Fee",
                    subtitle = transportFee.routeName.ifBlank { "Route: ${transportFee.routeId}" },
                    amount = transportFee.amount,
                    detail = transportFee.period,
                    status = transportFee.status
                )
            }
        }

        // Hostel fee
        if (hostelFee != null) {
            item {
                ModuleFeeCard(
                    icon = Icons.Filled.Home,
                    iconBg = c.purpleBg,
                    iconTint = c.purple,
                    title = "Hostel Fee",
                    subtitle = "${hostelFee.building} - Room ${hostelFee.room}",
                    amount = hostelFee.amount + hostelFee.messCharges,
                    detail = if (hostelFee.messCharges > 0)
                        "Room: Rs. ${"%,.0f".format(hostelFee.amount)} + Mess: Rs. ${"%,.0f".format(hostelFee.messCharges)}"
                    else hostelFee.period,
                    status = hostelFee.status
                )
            }
        }

        // Library fines
        if (libraryFines.isNotEmpty()) {
            item {
                Text(
                    text = "Library Fines",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
            items(libraryFines) { fine ->
                ModuleFeeCard(
                    icon = Icons.Filled.LibraryBooks,
                    iconBg = c.coralBg,
                    iconTint = c.coral,
                    title = fine.bookTitle.ifBlank { fine.bookId },
                    subtitle = if (fine.dueDate.isNotBlank()) "Due: ${fine.dueDate}" else "",
                    amount = fine.fineAmount,
                    detail = "Fine",
                    status = "pending"
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun ModuleFeeCard(
    icon: ImageVector,
    iconBg: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    amount: Double,
    detail: String,
    status: String
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(12.dp)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary
                    )
                }
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Rs. ${"%,.0f".format(amount)}",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = status.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = if (status.equals("active", true)) c.success else c.warning
            )
        }
    }
}

// -- Clearance Tab ------------------------------------------------------------

@Composable
private fun ClearanceContent(clearanceStatus: ClearanceStatus?) {
    val c = LocalAppColors.current
    if (clearanceStatus == null) {
        EmptyFeesState(message = "Clearance status not available")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Overall status banner
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (clearanceStatus.allClear) c.successBg else c.errorBg)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (clearanceStatus.allClear) Icons.Filled.VerifiedUser else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (clearanceStatus.allClear) c.success else c.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (clearanceStatus.allClear) "All Clear" else "Dues Pending",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (clearanceStatus.allClear) c.success else c.error,
                        fontWeight = FontWeight.Bold
                    )
                    if (!clearanceStatus.allClear) {
                        Text(
                            text = "Total Dues: Rs. ${"%,.0f".format(clearanceStatus.totalDues)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.error
                        )
                    }
                    if (clearanceStatus.checkedAt.isNotBlank()) {
                        Text(
                            text = "Checked: ${clearanceStatus.checkedAt}",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary
                        )
                    }
                }
            }
        }

        // Per-module breakdown
        item {
            ClearanceItemRow(
                label = "Tuition Fees",
                isClear = clearanceStatus.feesClear,
                dues = clearanceStatus.feesDues
            )
        }
        item {
            ClearanceItemRow(
                label = "Library",
                isClear = clearanceStatus.libraryClear,
                dues = clearanceStatus.libraryDues,
                extra = if (clearanceStatus.libraryUnreturnedBooks > 0)
                    "${clearanceStatus.libraryUnreturnedBooks} unreturned book(s)"
                else null
            )
        }
        item {
            ClearanceItemRow(
                label = "Hostel",
                isClear = clearanceStatus.hostelClear,
                dues = clearanceStatus.hostelDues
            )
        }
        item {
            ClearanceItemRow(
                label = "Transport",
                isClear = clearanceStatus.transportClear,
                dues = clearanceStatus.transportDues
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun ClearanceItemRow(
    label: String,
    isClear: Boolean,
    dues: Double,
    extra: String? = null
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(12.dp)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isClear) c.successBg else c.errorBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isClear) Icons.Filled.CheckCircle else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (isClear) c.success else c.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isClear) "Cleared" else "Dues: Rs. ${"%,.0f".format(dues)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isClear) c.success else c.error
                )
                if (extra != null) {
                    Text(
                        text = extra,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.warning
                    )
                }
            }
        }
        if (isClear) {
            Text(
                text = "Clear",
                style = MaterialTheme.typography.labelLarge,
                color = c.success,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                text = "Rs. ${"%,.0f".format(dues)}",
                style = MaterialTheme.typography.titleMedium,
                color = c.error,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyFeesState(message: String) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, c: AppColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = c.textTertiary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = c.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
