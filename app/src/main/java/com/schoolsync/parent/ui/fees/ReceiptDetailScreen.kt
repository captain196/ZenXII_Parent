package com.schoolsync.parent.ui.fees

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.firestore.FeeReceiptDoc
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ReceiptDetailScreen(
    onBack: () -> Unit,
    viewModel: ReceiptDetailViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().gradientBackground().statusBarsPadding()
    ) {
        // ── Top bar ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Payment Receipt",
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            uiState.receipt?.let { receipt ->
                ReceiptActionButtons(
                    receipt = receipt,
                    schoolName = uiState.user?.schoolDisplayName.orEmpty(),
                    schoolMeta = uiState.schoolMeta
                )
            }
        }

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent, modifier = Modifier.size(42.dp))
                }
            }
            uiState.errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage ?: "Error", color = c.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            uiState.receipt != null -> {
                ReceiptBody(
                    receipt = uiState.receipt!!,
                    schoolName = uiState.user?.schoolDisplayName.orEmpty(),
                    allocations = uiState.allocations,
                    headAllocations = uiState.headAllocations,
                    isPartial = uiState.isPartial
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Top-bar actions: Download + Share. Extracted so the top bar
//  composable stays compact and the PDF-build closure has a clear
//  single-responsibility scope.
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun ReceiptActionButtons(
    receipt: FeeReceiptDoc,
    schoolName: String,
    schoolMeta: com.schoolsync.parent.ui.fees.SchoolMetaUi
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val coScope = rememberCoroutineScope()

    val buildPdf: suspend () -> java.io.File = build@{
        val logoBitmap: android.graphics.Bitmap? = if (schoolMeta.logoUrl.isNotBlank()) {
            try {
                val request = coil.request.ImageRequest.Builder(context)
                    .data(schoolMeta.logoUrl)
                    .allowHardware(false)
                    .size(160)
                    .build()
                val result = coil.ImageLoader(context).execute(request)
                (result as? coil.request.SuccessResult)?.drawable
                    ?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap }
            } catch (_: Exception) { null }
        } else null
        val pdf = com.schoolsync.parent.util.ReceiptPdfGenerator.generate(
            context = context,
            receipt = receipt,
            schoolName = schoolName,
            meta = com.schoolsync.parent.util.PdfSchoolMeta(
                name    = schoolMeta.name.ifBlank { schoolName },
                address = schoolMeta.address,
                phone   = schoolMeta.phone,
                email   = schoolMeta.email,
                gstin   = schoolMeta.gstin,
                logo    = logoBitmap
            )
        )
        com.schoolsync.parent.util.ReceiptPdfGenerator.cleanupOldReceipts(context)
        return@build pdf
    }

    IconButton(onClick = {
        coScope.launch {
            try {
                val pdf = buildPdf()
                val savedPath = com.schoolsync.parent.util.ReceiptPdfGenerator.saveToDownloads(
                    context, pdf, "Receipt_${receipt.receiptNo.ifBlank { receipt.receiptKey }}.pdf"
                )
                Toast.makeText(context, "Saved to $savedPath", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't save: ${e.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }) {
        Icon(Icons.Filled.Download, contentDescription = "Download to device", tint = c.accent)
    }

    IconButton(onClick = {
        coScope.launch {
            try {
                val pdf = buildPdf()
                com.schoolsync.parent.util.ReceiptPdfGenerator.sharePdf(
                    context, pdf, "Share Receipt #${receipt.receiptNo}"
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't share receipt: ${e.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }) {
        Icon(Icons.Filled.Share, contentDescription = "Share", tint = c.accent)
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Receipt body — UPI-app-style hierarchy:
//    1. HERO: green check + amount + "Paid" — the most prominent thing
//    2. PAID FOR: months + per-head breakdown with subtotal
//    3. STUDENT: name + class + father (small card)
//    4. RECEIPT INFO: receipt #, txn ID, payment mode, timestamp
//    5. Footer: school-verified badge
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun ReceiptBody(
    receipt: FeeReceiptDoc,
    schoolName: String,
    allocations: List<MonthAllocation>,
    headAllocations: List<HeadAllocation>,
    isPartial: Boolean
) {
    val c = LocalAppColors.current
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. HERO (with partial/full badge) ────────────────────
        item { HeroCard(receipt, schoolName, isPartial) }

        // ── 2. PAID FOR (months breakdown) ───────────────────────
        item { PaidForCard(receipt, allocations) }

        // ── 3. FEE HEADS (per-head paid / total) ─────────────────
        item { FeeHeadBreakdownCard(receipt, headAllocations) }

        // ── 4. STUDENT ──────────────────────────────────────────
        item { StudentCard(receipt) }

        // ── 5. RECEIPT INFO ────────────────────────────────────
        item { ReceiptInfoCard(receipt) }

        // ── 6. School-verified badge ───────────────────────────
        item { VerifiedBadge() }
    }
}

@Composable
private fun HeroCard(
    receipt: FeeReceiptDoc,
    schoolName: String,
    isPartial: Boolean
) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(c.accent, c.accentSecondary)))
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // Big check icon in a glassy circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(c.onBanner.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CheckCircle, null, tint = c.onBanner,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                schoolName.ifBlank { "School" },
                style = MaterialTheme.typography.labelMedium,
                color = c.onBannerMuted,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            // Refunds and payments flow through the same detail screen;
            // the banner just needs to label itself honestly. Detection
            // uses paymentMode rather than amount sign so that any
            // future "ledger credit" with a negative amount doesn't
            // misclassify as a refund.
            val isRefund = receipt.paymentMode.startsWith("Refund", ignoreCase = true)
            Text(
                text  = if (isRefund) "Refund Receipt" else "Payment Receipt",
                style = MaterialTheme.typography.titleMedium,
                color = c.onBanner,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Rs ${"%,.0f".format(receipt.netAmount.takeIf { it > 0 } ?: receipt.amount)}",
                style = MaterialTheme.typography.displaySmall,
                color = c.onBanner,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp
            )
            Spacer(Modifier.height(2.dp))
            // Pill: "Full Payment" green-ish · "Partial Payment" amber-ish
            // · "Refund Processed" for refund vouchers.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.onBanner.copy(alpha = 0.22f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when {
                        isRefund   -> "REFUND PROCESSED"
                        isPartial  -> "PARTIAL PAYMENT"
                        else       -> "FULL PAYMENT"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = c.onBanner,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            if (receipt.feeMonths.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        isRefund  -> "Refund of ${receipt.feeMonths.joinToString(", ")}"
                        isPartial -> "for ${receipt.feeMonths.joinToString(", ")}"
                        else      -> "${receipt.feeMonths.joinToString(", ")} fee cleared"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = c.onBanner,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Per-fee-head card. Shows for each head:
 *   "Tuition Fee   Rs 0 / Rs 2,000"
 *   "Computer Fee  Rs 500 / Rs 500   (cleared green tag)"
 *
 * Critical for partial-payment trust — without this card the parent
 * sees the receipt amount (Rs 500) but no indication of which head
 * the money landed on, or what's still owed per head.
 */
@Composable
private fun FeeHeadBreakdownCard(receipt: FeeReceiptDoc, heads: List<HeadAllocation>) {
    if (heads.isEmpty()) return
    val c = LocalAppColors.current
    // Historical carry-forward path: when a legacy receipt has
    // advance_credit > 0 the parent overpaid — every demand they
    // selected was fully cleared (otherwise the leftover wouldn't
    // exist). A head with allocated < total is NOT "still due" in that
    // case; the gap was absorbed elsewhere, so show a softer label.
    // Overpayment is rejected upstream for new receipts, so this branch
    // only fires on pre-cutover history.
    val hasCarryForward = receipt.advanceCredit > 0.005
    Column(modifier = Modifier.fillMaxWidth().glassCard(16.dp).padding(16.dp)) {
        SectionHeader("FEE BREAKDOWN")
        Spacer(Modifier.height(10.dp))
        heads.forEach { h ->
            val cleared = h.totalAmount > 0 && h.allocatedThisReceipt + 0.005 >= h.totalAmount
            val paidLabel = "Rs ${"%,.2f".format(h.allocatedThisReceipt)} / Rs ${"%,.2f".format(h.totalAmount)}"
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        h.head,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (h.allocatedThisReceipt <= 0.005) {
                        Text(
                            "Not paid in this receipt",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary
                        )
                    } else if (cleared) {
                        Text(
                            "Cleared in this receipt",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.success,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (hasCarryForward) {
                        // Legacy overpayment scenario — gap was absorbed
                        // elsewhere (prior receipts / carry-forward),
                        // not unpaid.
                        Text(
                            "Partially applied this receipt — no balance remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.success,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        // We used to say "Rs X still due on this head" here,
                        // but the widget only knows about THIS receipt and
                        // can't see whether a prior receipt already closed
                        // the gap (e.g. F7 pays ₹150 of Library's ₹300, F8
                        // pays the remaining ₹150 — F8 would still falsely
                        // show "Rs 150 still due" because its allocation
                        // alone doesn't cover the head's full rate). The
                        // paidLabel column next to this already shows
                        // "Rs X / Rs Y"; a status hint is all we should
                        // add here — no unverifiable balance claim.
                        Text(
                            "Partially applied this receipt",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.warning,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    paidLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (h.allocatedThisReceipt <= 0.005) c.textTertiary else c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Divider()
        Spacer(Modifier.height(10.dp))
        if (receipt.discount > 0) AmountRow("Discount", -receipt.discount, c.success)
        if (receipt.fine > 0)     AmountRow("Fine", receipt.fine, c.error)
        AmountRow(
            "Total Paid",
            receipt.netAmount.takeIf { it > 0 } ?: receipt.amount,
            c.accent,
            bold = true
        )
    }
}

@Composable
private fun PaidForCard(receipt: FeeReceiptDoc, allocations: List<MonthAllocation>) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().glassCard(16.dp).padding(16.dp)) {
        SectionHeader("PAID FOR")
        Spacer(Modifier.height(10.dp))

        if (allocations.isNotEmpty()) {
            // Allocation-aware month rows: show paid / remaining per month
            // so the user understands exactly what cleared and what's
            // left. Falls back to feeMonths line below when allocation
            // data is missing.
            allocations.forEach { alloc ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            alloc.month,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (alloc.remainingAfter > 0.005)
                                "Paid Rs ${"%,.0f".format(alloc.paidThisReceipt)} · Rs ${"%,.0f".format(alloc.remainingAfter)} remaining"
                            else
                                "Cleared",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (alloc.remainingAfter > 0.005) c.warning else c.success,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "Rs ${"%,.0f".format(alloc.paidThisReceipt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(10.dp))
        } else if (receipt.feeMonths.isNotEmpty()) {
            Text(
                receipt.feeMonths.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
        }

        // Per-fee-head breakdown + Total are rendered by
        // FeeHeadBreakdownCard immediately below this card so we don't
        // duplicate them here.
    }
}

@Composable
private fun StudentCard(receipt: FeeReceiptDoc) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().glassCard(16.dp).padding(16.dp)) {
        SectionHeader("STUDENT")
        Spacer(Modifier.height(10.dp))
        Text(
            receipt.studentName.ifBlank { receipt.studentId.ifBlank { "Student" } },
            style = MaterialTheme.typography.titleSmall,
            color = c.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        // Strip any leading "Class "/"Section " the server may already
        // include so we never render e.g. "Class Class 10th".
        val classBare = receipt.className.trim()
            .removePrefix("Class").removePrefix("class").trim()
        val secBare = receipt.section.trim()
            .removePrefix("Section").removePrefix("section")
            .removePrefix("Sec").removePrefix("sec").trim()
        val classSec = listOfNotNull(
            classBare.takeIf { it.isNotBlank() }?.let { "Class $it" },
            secBare.takeIf { it.isNotBlank() }?.let { "Sec $it" }
        ).joinToString(" · ")
        if (classSec.isNotBlank()) {
            Text(
                classSec,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
        }
        if (receipt.fatherName.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Father: ${receipt.fatherName}",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
        }
    }
}

@Composable
private fun ReceiptInfoCard(receipt: FeeReceiptDoc) {
    val c = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth().glassCard(16.dp).padding(16.dp)) {
        SectionHeader("RECEIPT INFO")
        Spacer(Modifier.height(10.dp))
        InfoRow(
            label = "Receipt No.",
            value = "#${receipt.receiptNo.ifBlank { receipt.receiptKey }}",
            copyable = true,
            onCopy = {
                clipboard.setText(AnnotatedString(receipt.receiptKey.ifBlank { receipt.receiptNo }))
                Toast.makeText(context, "Receipt number copied", Toast.LENGTH_SHORT).show()
            }
        )
        if (receipt.txnId.isNotBlank()) {
            InfoRow(
                label = "Transaction ID",
                value = receipt.txnId,
                monospace = true,
                copyable = true,
                onCopy = {
                    clipboard.setText(AnnotatedString(receipt.txnId))
                    Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show()
                }
            )
        }
        InfoRow(label = "Payment Mode", value = receipt.paymentMode.ifBlank { "—" })
        InfoRow(label = "Paid On", value = formatDate(receipt.createdAt))
        if (receipt.remarks.isNotBlank()) {
            InfoRow(label = "Remarks", value = receipt.remarks)
        }
    }
}

@Composable
private fun VerifiedBadge() {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.success.copy(alpha = 0.10f))
            .border(1.dp, c.success.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.VerifiedUser, null, tint = c.success, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Verified and recorded by the school",
            style = MaterialTheme.typography.labelMedium,
            color = c.success,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Small reusable bits
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    val c = LocalAppColors.current
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = c.textTertiary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun Divider() {
    val c = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(c.glassBorder))
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    copyable: Boolean = false,
    onCopy: () -> Unit = {}
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (copyable) Modifier.clickable(onClick = onCopy) else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (monospace) FontFamily.Monospace else null
            ),
            color = c.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun AmountRow(label: String, amount: Double, color: Color, bold: Boolean = false) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (bold) c.textPrimary else c.textSecondary,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
        Text(
            "Rs ${"%,.0f".format(amount)}",
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

private fun formatDate(value: Any?): String {
    val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
    }
    return when (value) {
        null -> ""
        is com.google.firebase.Timestamp -> fmt.format(value.toDate())
        is java.util.Date -> fmt.format(value)
        is Number -> fmt.format(java.util.Date(value.toLong()))
        is String -> {
            val raw = value.trim()
            if (raw.isEmpty()) return ""
            val iso = runCatching { java.time.OffsetDateTime.parse(raw) }.getOrNull()
            if (iso != null) {
                val ist = iso.atZoneSameInstant(java.time.ZoneId.of("Asia/Kolkata"))
                val p = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
                return ist.format(p)
            }
            runCatching {
                val short = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).apply {
                    timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                }
                SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).apply {
                    timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                }.format(short.parse(raw)!!)
            }.getOrDefault(raw)
        }
        is Map<*, *> -> {
            val seconds = (value["seconds"] as? Number)?.toLong() ?: 0L
            if (seconds > 0) fmt.format(java.util.Date(seconds * 1000)) else ""
        }
        else -> value.toString()
    }
}
