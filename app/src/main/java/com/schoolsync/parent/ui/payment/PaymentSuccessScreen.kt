package com.schoolsync.parent.ui.payment

import androidx.compose.ui.res.stringResource
import com.schoolsync.parent.R
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.parent.data.payment.PaymentSession
import com.schoolsync.parent.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.schoolsync.parent.util.DisplayFormat
import com.schoolsync.parent.util.localizedString

/**
 * Full-screen success state shown by [PaymentFlowOverlay] when
 * [PaymentSession.State.Success] becomes active.
 *
 * Defensive rendering rules (per user spec "no crash if receipt data
 * is delayed or null"):
 *  - If `amount == 0` (Firestore confirm timed out), the rupee figure
 *    is hidden — better than rendering "Rs 0".
 *  - If `receiptNo` is blank, the receipt-row and the View Receipt
 *    button are hidden.
 *  - If `confirmedFromBackend == false`, a softer subtitle explains
 *    the receipt is still being prepared on the server.
 *  - Transaction ID and timestamp ALWAYS appear — those are the bits
 *    the user / school office care about for proving the payment was
 *    made even if Firestore propagation is slow.
 *
 * Done button calls `onAcknowledge` which transitions PaymentSession
 * back to Idle, dismissing the overlay.
 */
@Composable
fun PaymentSuccessScreen(
    details: PaymentSession.SuccessDetails,
    onViewReceipt: (receiptDocId: String) -> Unit,
    onAcknowledge: () -> Unit
) {
    val c = LocalAppColors.current
    val clipboard = LocalClipboardManager.current

    val title = when {
        details.alreadyPaid -> stringResource(R.string.pay_already_paid)
        details.isPartial -> stringResource(R.string.pay_partial_received)
        else -> stringResource(R.string.pay_successful)
    }
    val timestampStr = remember(details.timestamp) {
        com.schoolsync.parent.util.DisplayFormat.dateTime(Date(details.timestamp))
    }

    // Touch-blocking Box: a Compose Box with just a background colour
    // does NOT consume touches; taps that miss our buttons fall through
    // to the Fees screen underneath and can accidentally open whichever
    // receipt row sits at the cursor location. The empty .clickable
    // modifier (no ripple, no interaction source) makes the entire
    // overlay surface absorb taps so this can't happen.
    val blockerInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgStart)
            .clickable(
                interactionSource = blockerInteraction,
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Big check icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(c.success.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = c.success,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Amount — only when we know it. "Rs 0" looks like a bug.
            if (details.amount > 0) {
                Text(
                    DisplayFormat.currencyWhole(details.amount),
                    style = MaterialTheme.typography.displaySmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Subtitle: which months + cleared/partial status
            //
            // The "cleared" / "remaining" copy must be honest — saying
            // "January fee cleared" after a Rs 500 partial against Rs
            // 2,800 dues is misleading. PaymentSession reads the
            // allocation doc to compute isPartial + remainingByMonth so
            // we can render accurate per-month status here.
            // joinToString's transform is not a composable scope, so the
            // per-month line resolves through the Context instead.
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val subtitle = when {
                details.alreadyPaid ->
                    stringResource(R.string.pay_already_verified)
                !details.confirmedFromBackend ->
                    stringResource(R.string.pay_receipt_preparing)
                details.isPartial && details.remainingByMonth.isNotEmpty() -> {
                    val parts = details.remainingByMonth.entries.joinToString(" · ") { (m, bal) ->
                        ctx.localizedString(R.string.pay_remaining_fmt, m, DisplayFormat.currencyWhole(bal))
                    }
                    parts
                }
                details.isPartial -> stringResource(R.string.pay_balance_remaining)
                details.months.isNotEmpty() ->
                    stringResource(R.string.pay_fee_cleared_fmt, details.months.joinToString(", "))
                else -> stringResource(R.string.pay_fees_cleared)
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Detail card with receipt + transaction + timestamp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.glass)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                    .padding(vertical = 8.dp)
            ) {
                if (details.receiptNo.isNotBlank()) {
                    DetailRow(
                        label = stringResource(R.string.pay_receipt_no),
                        value = "#${details.receiptKey}",
                        copyable = true,
                        onCopy = {
                            clipboard.setText(AnnotatedString(details.receiptKey))
                        }
                    )
                }
                DetailRow(
                    label = stringResource(R.string.fees_txn_id),
                    value = details.transactionId,
                    copyable = true,
                    monospace = true,
                    onCopy = {
                        clipboard.setText(AnnotatedString(details.transactionId))
                    }
                )
                DetailRow(
                    label = stringResource(R.string.pay_paid_at),
                    value = timestampStr
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action buttons — View Receipt only when we have a doc to show
            if (details.receiptDocId.isNotBlank()) {
                Button(
                    onClick = {
                        Log.i(
                            "PaymentSuccess",  // i18n-ignore: log tag
                            "[TAP View Receipt] receiptDocId=${details.receiptDocId} receiptKey=${details.receiptKey}"
                        )
                        onViewReceipt(details.receiptDocId)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accent,
                        contentColor = if (c.isDark) c.bgStart else Color.White
                    )
                ) {
                    Icon(Icons.Filled.Receipt, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.pay_view_receipt),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            OutlinedButton(
                onClick = {
                    Log.i("PaymentSuccess", "[TAP Done] dismissing overlay only — no navigation")
                    onAcknowledge()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    stringResource(R.string.common_done),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    copyable: Boolean = false,
    monospace: Boolean = false,
    onCopy: () -> Unit = {}
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (copyable) Modifier.clickable(onClick = onCopy) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (monospace) FontFamily.Monospace else null
            ),
            color = c.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
