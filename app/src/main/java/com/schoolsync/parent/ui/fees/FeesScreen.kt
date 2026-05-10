package com.schoolsync.parent.ui.fees

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsBasketball
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
import androidx.compose.material3.TextButton
import android.app.Activity
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.pullrefresh.pullRefresh
import com.razorpay.Checkout
import org.json.JSONObject
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

/**
 * Smart rupee formatter — prints whole rupees without trailing zeros
 * ("Rs 2,000") and paise only when the amount actually has them
 * ("Rs 1,999.99"). Replaces the blanket "%,.0f" which silently
 * rounded paise away (₹149.99 → "Rs 150") and the overly chatty
 * "%,.2f" which littered integer amounts with ".00".
 *
 * Called from money displays throughout this screen via `fmtRupee(x)`.
 * Tolerant of negative values and zero.
 */
private fun fmtRupee(value: Double): String {
    val hasPaise = kotlin.math.abs(value * 100 - kotlin.math.round(value * 100)) > 0.005 ||
                   kotlin.math.round(value * 100).toLong() % 100L != 0L
    return if (hasPaise) "%,.2f".format(value) else "%,d".format(kotlin.math.round(value).toLong())
}

/**
 * Material 2 pull-refresh wrapper — Material3 1.2 (our BoM) doesn't
 * yet ship PullToRefreshBox so we use the legacy `pullRefresh`
 * modifier which coexists with material3 cleanly.
 */
@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
private fun PullToRefreshWrapper(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = androidx.compose.material.pullrefresh.rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh
    )
    val c = LocalAppColors.current
    // fillMaxSize is REQUIRED — without it the Box wraps content size
    // and the pullRefresh gesture has no surface to detect against.
    // That's why earlier the swipe-down on Pending Dues did nothing.
    Box(modifier = Modifier.fillMaxSize().pullRefresh(state)) {
        content()
        androidx.compose.material.pullrefresh.PullRefreshIndicator(
            refreshing = isRefreshing,
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = c.bgStart,
            contentColor = c.accent
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FeesScreen(
    onOpenReceipt: (String) -> Unit = {},
    viewModel: FeesViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val overview = uiState.overview
    val context = LocalContext.current
    // Single SnackbarHost for the whole Fees screen — replaces the
    // older dual Toast LaunchedEffects which could overlap and stack
    // on rapid status changes (Creating → Opening → Verifying → ...).
    val snackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }

    // Launch Razorpay checkout whenever the ViewModel emits a request.
    LaunchedEffect(Unit) {
        viewModel.checkoutRequests.collect { req ->
            val activity = context as? Activity ?: return@collect
            // Razorpay Standard Checkout options. Schema reference:
            //   https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/build-integration/
            //
            // What was wrong before: I had `put("method", "upi")` inside
            // prefill AND `put("method", JSONObject(...))` at top level
            // — the second overwrote the first via JSONObject's same-key
            // collision. Plus a stray `put("upi", JSONObject(...))` at
            // top level that isn't a valid Standard Checkout field.
            // Cleaned up below.
            val options = JSONObject().apply {
                put("name", req.name)
                put("description", req.description)
                put("currency", req.currency)
                put("amount", req.amountPaise)
                put("order_id", req.orderId)
                put("theme", JSONObject().put("color", req.themeColor))
                put("prefill", JSONObject().apply {
                    if (req.prefillEmail.isNotBlank())  put("email",   req.prefillEmail)
                    if (req.prefillContact.isNotBlank()) put("contact", req.prefillContact)
                    // pre-select the UPI tab on the checkout sheet
                    put("method", "upi")
                })
                put("retry", JSONObject().put("enabled", true).put("max_count", 2))
                // Explicitly enable UPI + alternates. method.upi=true is
                // the supported Standard Checkout flag for showing the
                // UPI block. UPI must ALSO be enabled in your Razorpay
                // dashboard (Settings → Payment Methods); test-mode
                // accounts have it on by default but Live mode requires
                // KYC + activation.
                put("method", JSONObject().apply {
                    put("upi", true)
                    put("card", true)
                    put("netbanking", true)
                    put("wallet", true)
                })
                // upi_flow="intent" → on Android, picks the system
                // intent flow so GPay / PhonePe / Paytm appear as
                // direct buttons instead of a single "Pay via any UPI
                // app" collect-VPA prompt.
                put("upi_flow", "intent")
            }
            try {
                Checkout().apply { setKeyID(req.apiKey) }.open(activity, options)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Unable to open checkout: ${e.message}")
            }
        }
    }

    // One Snackbar pipeline for status + error messages. The host
    // queues messages instead of stacking them as the old toast
    // pattern did — and dismissing one auto-flows to the next.
    val status = uiState.paymentStatus
    LaunchedEffect(status) {
        if (!status.isNullOrBlank()) {
            snackbarHostState.showSnackbar(status)
        }
    }
    // Critical errors are shown as a persistent banner above the tabs
    // (rendered below) instead of a transient snackbar — those auto-
    // dismissed before parents on slow devices could read them.
    val error = uiState.errorMessage

    // Structured failure AlertDialog — surfaces the Razorpay code and
    // description with a one-tap Retry.
    uiState.paymentFailure?.let { failure ->
        PaymentFailureDialog(
            failure = failure,
            onRetry = { viewModel.retryLastFailedPayment() },
            onDismiss = { viewModel.dismissFailure() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // ── Rich Summary Header ──
        Text(
            text = "Fees",
            style = MaterialTheme.typography.headlineLarge,
            color = c.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Summary stats row
        // "Total Fees" is computed from actual demands (Paid + Due), not
        // from the fee chart rollup. That way it reads 0 when no demands
        // have been generated yet, matching Paid=0 / Due=0, and always
        // stays consistent (Total Fees = Paid + Due).
        val totalFees = overview.totalPaid + overview.pendingFees.totalPending
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeeSummaryChip(
                label = "Total Fees",
                value = "Rs. ${fmtRupee(totalFees)}",
                color = c.accent,
                modifier = Modifier.weight(1f)
            )
            FeeSummaryChip(
                label = "Paid",
                value = "Rs. ${fmtRupee(overview.totalPaid)}",
                color = c.success,
                modifier = Modifier.weight(1f)
            )
            FeeSummaryChip(
                label = "Due",
                value = "Rs. ${fmtRupee(overview.pendingFees.totalPending)}",
                color = if (overview.pendingFees.totalPending > 0) c.error else c.success,
                modifier = Modifier.weight(1f)
            )
        }

        if (overview.scholarshipAmount > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeeSummaryChip(
                    label = "Scholarship",
                    value = "Rs. ${"%,.0f".format(overview.scholarshipAmount)}",
                    color = Color(0xFF9C27B0),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Persistent error banner — renders above the tabs and stays
        // until the parent taps Dismiss. The previous snackbar approach
        // auto-dismissed too quickly (Long duration ≈ 10 s) which hid
        // important messages like "Razorpay not configured" before they
        // could be read.
        if (!error.isNullOrBlank()) {
            FeesErrorBanner(
                message = error,
                onDismiss = { viewModel.clearStatusMessage() }
            )
        }

        // Soft "syncing" banner — shown when the gateway captured a
        // payment but the server is still writing the receipt
        // (admin reconciliation cron will replay it). Calm tone so the
        // parent doesn't panic and re-pay.
        val pendingMsg = uiState.pendingSyncMessage
        if (!pendingMsg.isNullOrBlank()) {
            FeesPendingSyncBanner(
                message = pendingMsg,
                onDismiss = { viewModel.dismissPendingSyncMessage() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Tab Row + HorizontalPager ──
        // Using foundation.pager.HorizontalPager so users can swipe
        // left/right between tabs in addition to tapping. The pager
        // and tab row stay synced both ways.
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = FeesTab.entries.indexOf(uiState.selectedTab).coerceAtLeast(0),
            pageCount = { FeesTab.entries.size }
        )
        // Tap a tab → animate the pager.
        LaunchedEffect(uiState.selectedTab) {
            val idx = FeesTab.entries.indexOf(uiState.selectedTab)
            if (idx >= 0 && pagerState.currentPage != idx) {
                pagerState.animateScrollToPage(idx)
            }
        }
        // Swipe → mirror back to selectedTab so other consumers (e.g.
        // bottom buttons that depend on which tab is active) stay in sync.
        LaunchedEffect(pagerState.currentPage) {
            val tab = FeesTab.entries.getOrNull(pagerState.currentPage)
            if (tab != null && tab != uiState.selectedTab) viewModel.selectTab(tab)
        }

        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = c.bgStart,
            contentColor = c.textPrimary,
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = c.accent
                    )
                }
            },
            divider = { HorizontalDivider(color = c.glassBorder) }
        ) {
            FeesTab.entries.forEachIndexed { idx, tab ->
                val active = pagerState.currentPage == idx
                Tab(
                    selected = active,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) c.accent else c.textSecondary,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // ── Pager content ──
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val tab = FeesTab.entries[pageIndex]
            if (uiState.isLoading) {
                when (tab) {
                    FeesTab.PENDING  -> PendingFeesSkeleton()
                    FeesTab.PAYMENTS -> PaymentsSkeleton()
                    else -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = c.accent, modifier = Modifier.size(40.dp))
                    }
                }
            } else {
                when (tab) {
                    FeesTab.PENDING -> PullToRefreshWrapper(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.pullRefresh() }
                    ) {
                        if (uiState.demandsError != null) {
                            FeeSectionError(
                                title = "Couldn't load your pending fees",
                                message = uiState.demandsError!!,
                                onRetry = { viewModel.pullRefresh() }
                            )
                        } else {
                            PendingFeesContent(
                                pendingMonths = overview.pendingFees.pendingMonths,
                                paymentInProgress = uiState.paymentInProgress,
                                isPaymentOverlayActive = uiState.isPaymentOverlayActive,
                                selectedMonthsState = uiState.selectedMonths,
                                pendingConfirmMonths = uiState.pendingConfirmMonths,
                                onToggleMonth = { m, list -> viewModel.toggleMonth(m, list) },
                                onSelectAll  = { list -> viewModel.selectAllMonths(list) },
                                onClearSelection = { viewModel.clearSelection() },
                                onPayNow = { months -> viewModel.initiatePayment(months) },
                                onPayCustom = { month, amount ->
                                    viewModel.initiatePayment(listOf(month), amountOverride = amount)
                                }
                            )
                        }
                    }
                    FeesTab.PAYMENTS -> PullToRefreshWrapper(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.pullRefresh() }
                    ) {
                        if (uiState.paymentsError != null) {
                            FeeSectionError(
                                title = "Couldn't load payment history",
                                message = uiState.paymentsError!!,
                                onRetry = { viewModel.pullRefresh() }
                            )
                        } else {
                            PaymentHistoryContent(
                                payments = overview.paymentHistory,
                                onOpenReceipt = onOpenReceipt
                            )
                        }
                    }
                    FeesTab.DISCOUNTS -> PullToRefreshWrapper(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.pullRefresh() }
                    ) {
                        DiscountsContent(
                            scholarshipAmount = overview.scholarshipAmount,
                            carryForwardDues = overview.carryForwardDues
                        )
                    }
                    FeesTab.STRUCTURE -> PullToRefreshWrapper(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.pullRefresh() }
                    ) {
                        FeeStructureContent(overview.feeStructure.feeHeads)
                    }
                }
            }
        }
    }
        // Snackbar host overlays the whole Fees screen, anchored
        // bottom-center so it sits above the bottom nav bar.
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Soft "payment received, syncing receipt" banner — calm amber tone
 * that distinguishes it from a hard error. Used when Razorpay has
 * captured the money but the server-side receipt write is deferred
 * to the admin reconciliation cron.
 */
@Composable
private fun FeesPendingSyncBanner(
    message: String,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.warning.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Sync,
            contentDescription = null,
            tint = c.warning,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = c.textPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onDismiss,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "OK",
                style = MaterialTheme.typography.labelMedium,
                color = c.warning,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Persistent error banner — shown at the top of the Fees screen until
 * the user dismisses it. Replaces transient snackbar errors that often
 * disappeared before being read.
 */
@Composable
private fun FeesErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.errorBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = c.error,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = c.error,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onDismiss,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "Dismiss",
                style = MaterialTheme.typography.labelMedium,
                color = c.error,
                fontWeight = FontWeight.SemiBold
            )
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
        items(items, key = { it.name }) { item ->
            FeeStructureCard(item)
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun FeeStructureCard(item: FeeHead) {
    val c = LocalAppColors.current
    val nameLower = item.name.lowercase()
    val (icon, iconColor) = when {
        nameLower.contains("tuition") || nameLower.contains("school") ->
            Icons.Filled.School to Color(0xFF1565C0)
        nameLower.contains("computer") || nameLower.contains("lab") ->
            Icons.Filled.Computer to Color(0xFF00897B)
        nameLower.contains("library") || nameLower.contains("book") ->
            Icons.Filled.MenuBook to Color(0xFF6A1B9A)
        nameLower.contains("transport") || nameLower.contains("bus") ->
            Icons.Filled.DirectionsBus to Color(0xFFE65100)
        nameLower.contains("sport") || nameLower.contains("game") ->
            Icons.Filled.SportsBasketball to Color(0xFF2E7D32)
        nameLower.contains("exam") || nameLower.contains("test") ->
            Icons.Filled.Assignment to Color(0xFFC62828)
        nameLower.contains("hostel") || nameLower.contains("boarding") ->
            Icons.Filled.Hotel to Color(0xFF4527A0)
        nameLower.contains("annual") || nameLower.contains("admission") ->
            Icons.Filled.CalendarMonth to Color(0xFF00838F)
        nameLower.contains("fine") || nameLower.contains("late") ->
            Icons.Filled.Warning to Color(0xFFE65100)
        else -> Icons.Filled.Receipt to c.accent
    }

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
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
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
                Text(
                    text = if (item.frequency.equals("annual", true) || item.frequency.equals("yearly", true))
                        "Annual" else "Monthly",
                    style = MaterialTheme.typography.labelSmall,
                    color = iconColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
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
    /** True while the global PaymentFlowOverlay is covering the screen
     *  (PaymentSession Verifying / Confirming). The Pay button stays
     *  disabled — the overlay is the canonical processing surface, so
     *  we suppress this row's redundant in-button spinner + dots. */
    isPaymentOverlayActive: Boolean = false,
    selectedMonthsState: List<String> = emptyList(),
    /** Months that just completed Razorpay but whose server-side
     *  pipeline is still running. Drawn with a "Processing…" chip so the
     *  UI feels immediate on slow networks. Cleared by the demand
     *  listener once the server commit lands. */
    pendingConfirmMonths: List<String> = emptyList(),
    onToggleMonth: (String, List<String>) -> Unit = { _, _ -> },
    onSelectAll: (List<String>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onPayNow: (List<String>) -> Unit = {},
    onPayCustom: (String, Double) -> Unit = { _, _ -> }
) {
    val c = LocalAppColors.current

    if (pendingMonths.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.CheckCircle, null, tint = c.success, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("All Fees Paid!", style = MaterialTheme.typography.titleLarge, color = c.success, fontWeight = FontWeight.Bold)
                Text("No pending fees.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        return
    }

    val unpaidMonths = pendingMonths.filter { it.status != "Paid" }
    val paidMonths = pendingMonths.filter { it.status == "Paid" }
    val unpaidMonthNames = unpaidMonths.map { it.month }
    // Selection driven from VM state — survives recomposition AND can
    // be cleared by `pullRefresh()`.
    val selectedMonths = selectedMonthsState
    val selectedTotal = unpaidMonths.filter { it.month in selectedMonths }.sumOf { it.balanceAmount }
    var showCustomPayDialog by remember { mutableStateOf(false) }
    // Pay-Full-Year confirm — only triggers for high totals (> ₹10k).
    // Prevents accidental taps from charging the entire year at once.
    // Low totals (≤ ₹10k) skip the confirm so the common-case remains
    // one-tap.
    var showPayFullYearConfirm by remember { mutableStateOf(false) }
    val fullYearTotal = unpaidMonths.sumOf { it.balanceAmount }
    val PAY_FULL_YEAR_CONFIRM_THRESHOLD = 10_000.0

    Box(modifier = Modifier.fillMaxSize()) {
        // Bottom padding must clear the full pay-action bar (summary
        // card + button row + "Pay a custom amount" link). ~230dp is a
        // safe ceiling.
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 240.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Quick action buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSelectAll(unpaidMonthNames) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, c.accent)
                    ) {
                        Text("Select All", color = c.accent, style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { onClearSelection() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, c.textTertiary)
                    ) {
                        Text("Clear", color = c.textSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Section: Unpaid months
            if (unpaidMonths.isNotEmpty()) {
                item {
                    Text(
                        text = "UNPAID (${unpaidMonths.size} months)",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // Stable key=month-name prevents Compose slot-table
                // mismatch when the demand listener pushes a fresh
                // list and a row moves from unpaid → paid section
                // (different items() block). Without keys, positional
                // remapping caused
                //   ClassCastException: $9$1$3$1 cannot be cast to
                //   RecomposeScopeImpl
                // on rapid tab switches.
                items(unpaidMonths, key = { it.month }) { month ->
                    PendingFeeCard(
                        month = month,
                        isSelected = month.month in selectedMonths,
                        showCheckbox = true,
                        isProcessing = month.month in pendingConfirmMonths,
                        onToggle = { onToggleMonth(month.month, unpaidMonthNames) }
                    )
                }
            }

            // Section: Paid months
            if (paidMonths.isNotEmpty()) {
                item(key = "paid_header") {
                    Text(
                        text = "PAID (${paidMonths.size} months)",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.success,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(paidMonths, key = { "paid_${it.month}" }) { month ->
                    PendingFeeCard(month = month, showCheckbox = false)
                }
            }
        }

        // ── Bottom payment action bar ──
        if (unpaidMonths.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Solid background with a short fade at the top so
                    // scrolling content doesn't bleed THROUGH the action
                    // bar (previous transparent-start gradient caused
                    // the month-row text to ghost behind the summary
                    // card — see DOWNLOADS/www.jpeg).
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(c.bgStart.copy(alpha = 0f), c.bgStart),
                            startY = 0f,
                            endY = 24f
                        )
                    )
                    .background(c.bgStart)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Prominent summary card — replaces the tiny caption
                if (selectedMonths.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            // Solid surface layered on the accent tint
                            // so the card is fully opaque regardless of
                            // what sits behind it in the list.
                            .background(c.bgStart)
                            .background(c.accent.copy(alpha = 0.12f))
                            .border(
                                BorderStroke(1.dp, c.accent.copy(alpha = 0.35f)),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Receipt,
                                    null,
                                    tint = c.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (selectedMonths.size == 1)
                                        "1 month selected"
                                    else
                                        "${selectedMonths.size} months selected",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Total Payable",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.textTertiary
                                )
                            }
                        }
                        Text(
                            text = "Rs. ${fmtRupee(selectedTotal)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = c.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onPayNow(selectedMonths.toList()) },
                        enabled = selectedMonths.isNotEmpty() && !paymentInProgress,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent)
                    ) {
                        // While the full-screen PaymentVerifyScreen is up
                        // (Verifying / Confirming), the overlay owns the
                        // processing UX — no need to also animate a
                        // spinner + dots here behind it. The button
                        // remains disabled either way via paymentInProgress.
                        val showInButtonSpinner = paymentInProgress && !isPaymentOverlayActive
                        if (showInButtonSpinner) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Processing${rememberAnimatedDots()}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(Icons.Filled.Payment, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Pay Selected",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            // Confirm before committing to a high-value
                            // full-year payment — easy to tap by accident
                            // right next to "Pay Selected".
                            if (fullYearTotal > PAY_FULL_YEAR_CONFIRM_THRESHOLD) {
                                showPayFullYearConfirm = true
                            } else {
                                onSelectAll(unpaidMonthNames)
                                onPayNow(unpaidMonthNames)
                            }
                        },
                        enabled = !paymentInProgress,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, c.accent)
                    ) {
                        Text(
                            "Pay Full Year",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // "Pay custom amount" — enabled only for a single-month
                // selection. Partial payments are allocated server-side
                // (oldest demand first) by the existing verify flow.
                if (selectedMonths.size == 1) {
                    val only = selectedMonths.first()
                    val monthBalance = unpaidMonths.find { it.month == only }?.balanceAmount ?: 0.0
                    TextButton(
                        onClick = { showCustomPayDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !paymentInProgress && monthBalance > 0.0
                    ) {
                        Text(
                            "Pay a custom amount (≤ Rs. ${fmtRupee(monthBalance)})",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    if (showCustomPayDialog && selectedMonths.size == 1) {
        val only = selectedMonths.first()
        val monthBalance = unpaidMonths.find { it.month == only }?.balanceAmount ?: 0.0
        PartialPaymentDialog(
            month = only,
            maxAmount = monthBalance,
            onDismiss = { showCustomPayDialog = false },
            onConfirm = { amount ->
                showCustomPayDialog = false
                onPayCustom(only, amount)
            }
        )
    }

    // Pay-Full-Year confirmation — one-tap safety net for a big charge.
    if (showPayFullYearConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPayFullYearConfirm = false },
            title = {
                Text(
                    "Pay full year?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "This will charge ALL ${unpaidMonths.size} unpaid month(s) in one payment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.accent.copy(alpha = 0.12f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total Payable",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary
                        )
                        Text(
                            "Rs. ${fmtRupee(fullYearTotal)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = c.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You can cancel anytime before completing the payment.",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showPayFullYearConfirm = false
                        onSelectAll(unpaidMonthNames)
                        onPayNow(unpaidMonthNames)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.accent)
                ) {
                    Text(
                        "Yes, pay full year",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showPayFullYearConfirm = false }
                ) {
                    Text("Cancel", color = c.textSecondary)
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

/**
 * Cycles "" → "." → ".." → "..." every 400ms. Use as a live suffix
 * on a "Processing" label so users see progress while the verify
 * round-trip is in flight (Razorpay → backend → Firestore).
 */
@Composable
private fun rememberAnimatedDots(): String {
    var dots by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dots = (dots + 1) % 4
        }
    }
    return ".".repeat(dots)
}

@Composable
private fun PendingFeeCard(
    month: PendingMonth,
    isSelected: Boolean = false,
    showCheckbox: Boolean = false,
    /** Optimistic "server is still writing" flag — overrides the
     *  visual status until the Firestore listener confirms. */
    isProcessing: Boolean = false,
    onToggle: () -> Unit = {}
) {
    val c = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    val isPaid = month.status.equals("Paid", true)
    val isPartial = month.status.equals("Partial", true)
    val statusColor = when {
        isProcessing -> c.accent
        isPaid -> c.success
        isPartial -> Color(0xFFE67E22)
        else -> c.warning
    }
    val statusIcon = when {
        isProcessing -> Icons.Filled.Sync
        isPaid -> Icons.Filled.CheckCircle
        isPartial -> Icons.Filled.Info
        else -> Icons.Filled.Warning
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .then(
                if (isSelected) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable {
                if (showCheckbox) onToggle()
                else expanded = !expanded
            }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCheckbox) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = c.accent,
                        uncheckedColor = c.textTertiary
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    val iconModifier = if (isProcessing) {
                        val infiniteTransition =
                            rememberInfiniteTransition(label = "fee-sync")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "fee-sync-rotation"
                        )
                        Modifier.size(18.dp).rotate(rotation)
                    } else {
                        Modifier.size(18.dp)
                    }
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = iconModifier
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    // Yearly demands surface as "Annual Fee" with a
                    // "(One-time)" subtitle so parents don't read it as a
                    // 13th month. The underlying month value stays
                    // "Yearly Fees" so selection/allocation logic is
                    // unaffected — this is purely a label.
                    val isYearly = month.month == "Yearly Fees"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isYearly) "Annual Fee" else month.month,
                            style = MaterialTheme.typography.titleSmall,
                            color = c.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Overdue chip — shown only when VM computed a
                        // positive overdueDays (i.e. period past grace
                        // window AND still not fully paid). Defaults to 0
                        // for paid / future / not-yet-due months so the
                        // chip is absent when it shouldn't urge anyone.
                        if (!isPaid && !isProcessing && month.overdueDays > 0) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.error.copy(alpha = 0.14f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (month.overdueDays == 1) "Overdue · 1d"
                                           else "Overdue · ${month.overdueDays}d",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    val statusLabel = when {
                        isProcessing -> "Processing${rememberAnimatedDots()}"
                        isPaid -> "Paid"
                        isPartial -> "Partially Paid"
                        isYearly -> "One-time · Due"
                        else -> "Due"
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                // Top-right shows the BALANCE prominently (what user
                // owes right now), not the total — they care more
                // about what's left to pay than the original amount.
                val displayAmount = if (isPaid) month.totalAmount else month.balanceAmount
                Text(
                    text = "Rs. ${fmtRupee(displayAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPaid) c.textPrimary else statusColor,
                    fontWeight = FontWeight.Bold
                )
                if (!isPaid) {
                    Text(
                        text = if (isPartial) "remaining" else "due",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { expanded = !expanded }
            )
        }

        // ── Progress bar + paid/total split ───────────────────────
        // Hidden for fully-paid months (no useful info there) and
        // for fully-unpaid months (progress would be 0 — we show the
        // info via the "Due Rs X" label only). Shown for partials and
        // for any month with paidAmount > 0.
        if (!isPaid && month.totalAmount > 0 && month.paidAmount > 0.005) {
            Spacer(modifier = Modifier.height(10.dp))
            val progress = (month.paidAmount / month.totalAmount).toFloat().coerceIn(0f, 1f)
            val pctInt = (progress * 100).toInt()
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = c.accent,
                trackColor = c.glassBorder,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Rs ${fmtRupee(month.paidAmount)} paid of Rs ${fmtRupee(month.totalAmount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary
                )
                Text(
                    "$pctInt%",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Expandable fee-head breakdown
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                HorizontalDivider(color = c.divider, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                month.feeHeads.forEach { head ->
                    val headColor = when (head.status) {
                        "paid" -> c.success
                        "partial" -> Color(0xFFE67E22)
                        else -> c.textSecondary
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = head.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Rs. ${"%,.0f".format(head.netAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = headColor
                        )
                    }
                }
                if (month.paidAmount > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = c.divider, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Paid", style = MaterialTheme.typography.labelMedium, color = c.success)
                        Text(
                            "Rs. ${"%,.0f".format(month.paidAmount)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = c.success
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentHistoryContent(
    payments: List<FeePayment>,
    onOpenReceipt: (String) -> Unit = {}
) {
    if (payments.isEmpty()) {
        EmptyFeesState(message = "No payment history found")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(payments, key = { it.paymentId }) { record ->
            PaymentRecordCard(record, onOpenReceipt = onOpenReceipt)
        }
        item(key = "payments_footer") { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun PaymentRecordCard(
    record: FeePayment,
    onOpenReceipt: (String) -> Unit = {}
) {
    val c = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .clickable { onOpenReceipt(record.paymentId) }
            .padding(14.dp)
    ) {
        // Header row: receipt icon + receipt# + date | amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.success.copy(alpha = 0.12f)),
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
                    text = "Receipt #${record.receiptNo}",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary
                    )
                    if (record.mode.isNotBlank()) {
                        Text(
                            text = " · ${record.mode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Rs. ${"%,.0f".format(record.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.success,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Paid",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.success,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open receipt",
                tint = c.textTertiary,
                modifier = Modifier.size(18.dp)
            )
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

/**
 * Section-level error banner. Rendered in place of normal tab content
 * when a Firestore listener emits FeeDataState.Error.
 *
 * The content sits inside a verticalScroll Column sized to fillMaxSize —
 * that gives the parent PullToRefreshWrapper a proper NestedScroll
 * surface to intercept the downward drag, without the janky
 * LazyColumn-center rendering issues.
 */
@Composable
private fun FeeSectionError(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    val c = LocalAppColors.current
    val scroll = androidx.compose.foundation.rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = c.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = c.accent),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Pull down to refresh also works.",
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            textAlign = TextAlign.Center,
        )
        // Bottom spacer keeps content tall enough that pull-refresh has
        // comfortable counter-drag even on short phones.
        Spacer(Modifier.height(200.dp))
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
                imageVector = Icons.Filled.Payment,
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

@Composable
private fun FeeSummaryChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Dialogs: partial payment + payment failure
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun PartialPaymentDialog(
    month: String,
    maxAmount: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val c = LocalAppColors.current
    var input by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val parsed = input.toDoubleOrNull()
    val valid = parsed != null && parsed > 0.0 && parsed <= maxAmount + 0.005
    val remainingAfter = (maxAmount - (parsed ?: 0.0)).coerceAtLeast(0.0)
    val focusRequester = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Custom Dialog (not AlertDialog) so we control the entire surface
    // — the default AlertDialog has cramped padding and a cluttered
    // button row that looked unprofessional.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.bgStart)
                    .border(BorderStroke(1.dp, c.glassBorder), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                // ── Header ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Payment, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Pay a custom amount",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "$month  ·  Balance Rs. ${"%,.0f".format(maxAmount)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── Amount input — currency prefix + big number ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.glass)
                        .border(
                            BorderStroke(
                                1.5.dp,
                                if (input.isNotEmpty() && !valid) c.error
                                else c.accent.copy(alpha = 0.4f)
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "₹",
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = input,
                        onValueChange = { new ->
                            if (new.matches(Regex("^\\d*\\.?\\d{0,2}$"))) input = new
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { if (valid) parsed?.let(onConfirm) }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text(
                                    "0",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textTertiary
                                    )
                                )
                            }
                            inner()
                        }
                    )
                }

                if (input.isNotEmpty() && !valid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Enter an amount between Rs. 1 and Rs. ${"%,.0f".format(maxAmount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.error
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ── Quick-amount chips ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "25%" to maxAmount * 0.25,
                        "50%" to maxAmount * 0.50,
                        "75%" to maxAmount * 0.75,
                        "Full" to maxAmount
                    ).forEach { (label, value) ->
                        val rounded = (value).let { kotlin.math.round(it).toLong() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.accent.copy(alpha = 0.10f))
                                .border(BorderStroke(1.dp, c.accent.copy(alpha = 0.30f)), RoundedCornerShape(10.dp))
                                .clickable { input = rounded.toString() }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = c.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // ── Live breakdown ──
                if (parsed != null && parsed > 0) {
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.glass)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BreakdownRow("Paying now", "Rs. ${"%,.0f".format(parsed)}", c.accent, bold = true)
                        BreakdownRow("Remaining due", "Rs. ${"%,.0f".format(remainingAfter)}",
                            if (remainingAfter > 0) c.warning else c.success)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Action row — large buttons, properly spaced ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, c.textTertiary.copy(alpha = 0.5f))
                    ) {
                        Text("Cancel", color = c.textSecondary, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = { parsed?.let(onConfirm) },
                        enabled = valid,
                        modifier = Modifier.weight(1.4f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent)
                    ) {
                        Icon(Icons.Filled.Payment, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (parsed != null && valid) "Pay Rs. ${"%,.0f".format(parsed)}"
                            else "Enter amount",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, accent: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium
        )
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = accent,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun PaymentFailureDialog(
    failure: PaymentFailure,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    // Razorpay code 0 = cancelled by user, 2 = network, 3 = invalid options
    val isCancelled = failure.code == com.razorpay.Checkout.PAYMENT_CANCELED
    val headline = if (isCancelled) "Payment cancelled" else "Payment failed"
    val body = when {
        isCancelled -> "You cancelled the payment. No money was deducted."
        failure.description.isNotBlank() -> failure.description
        else -> "Something went wrong while processing the payment. No money was deducted."
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (isCancelled) Icons.Filled.Info else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (isCancelled) c.warning else c.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text(headline, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                if (!isCancelled && failure.code != 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Error code: ${failure.code}",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary
                    )
                }
                if (failure.failedMonths.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Months: ${failure.failedMonths.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onRetry) {
                Text("Try again", fontWeight = FontWeight.Bold, color = c.accent)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// Discounts tab — scholarships + carry-forward + active discounts
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun DiscountsContent(
    scholarshipAmount: Double,
    carryForwardDues: Double
) {
    val c = LocalAppColors.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item("section_header") {
            Text(
                "DISCOUNTS & ADJUSTMENTS",
                style = MaterialTheme.typography.labelMedium,
                color = c.textTertiary,
                fontWeight = FontWeight.Bold
            )
        }

        if (scholarshipAmount > 0) {
            item("scholarship") {
                DiscountTile(
                    icon = Icons.Filled.School,
                    iconColor = c.success,
                    title = "Scholarship",
                    subtitle = "Awarded for the current session",
                    amountText = "−Rs. ${"%,.0f".format(scholarshipAmount)}",
                    amountColor = c.success
                )
            }
        }

        if (carryForwardDues > 0) {
            item("carry_forward") {
                DiscountTile(
                    icon = Icons.Filled.Warning,
                    iconColor = c.warning,
                    title = "Carry-forward dues",
                    subtitle = "Outstanding fees rolled over from last session",
                    amountText = "+Rs. ${"%,.0f".format(carryForwardDues)}",
                    amountColor = c.warning
                )
            }
        }

        if (scholarshipAmount <= 0 && carryForwardDues <= 0) {
            item("empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(14.dp)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Info,
                            null,
                            tint = c.textTertiary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No discounts or adjustments",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary
                        )
                        Text(
                            "Scholarships, fee concessions and previous-year balances will appear here when applicable.",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        item("info_card") {
            // Helpful note explaining where to ask for a discount
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.accent.copy(alpha = 0.08f))
                    .padding(14.dp)
            ) {
                Text(
                    "Need a fee concession?",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Sibling discounts, hardship concessions and government scholarships are managed by the school office. Visit them with the supporting documents to apply.",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary
                )
            }
        }
    }
}

@Composable
private fun DiscountTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    amountText: String,
    amountColor: androidx.compose.ui.graphics.Color
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary
            )
        }
        Text(
            amountText,
            style = MaterialTheme.typography.titleSmall,
            color = amountColor,
            fontWeight = FontWeight.Bold
        )
    }
}
