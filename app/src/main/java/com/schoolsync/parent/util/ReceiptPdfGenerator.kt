package com.schoolsync.parent.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.schoolsync.parent.data.model.firestore.FeeReceiptDoc
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Optional school details rendered in the PDF header / footer. Pass
 * null fields to skip them. The caller is responsible for loading the
 * logo bitmap via Coil/etc. before invoking generate().
 */
data class PdfSchoolMeta(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val gstin: String = "",
    val logo: Bitmap? = null
)

/**
 * Renders a [FeeReceiptDoc] to a single-page A4-ish PDF (595×842 pt)
 * using the native Android [PdfDocument] API — no external deps.
 * The resulting file is dropped in the app's cache and handed to a
 * standard share Intent via [sharePdf].
 */
object ReceiptPdfGenerator {

    private const val PAGE_WIDTH  = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    /**
     * Create a PDF file for the receipt and return the File reference.
     * Filename includes an epoch suffix so concurrent share intents
     * don't fight over the same file (Android stale-URI bug).
     *
     * @param meta  Optional school header details (logo, address, GSTIN).
     */
    fun generate(
        context: Context,
        receipt: FeeReceiptDoc,
        schoolName: String,
        meta: PdfSchoolMeta? = null
    ): File {
        val dir = File(context.cacheDir, "receipts").apply { if (!exists()) mkdirs() }
        val rkey = receipt.receiptNo.ifBlank { receipt.receiptKey.ifBlank { "R" } }
        val stamp = (System.currentTimeMillis() / 1000)
        val file = File(dir, "Receipt_${rkey}_${stamp}.pdf")

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val title = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#0F766E")
            textSize = 22f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val subtitle = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#334155")
            textSize = 12f
        }
        val h2 = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#0F172A")
            textSize = 14f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val body = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#1F2937")
            textSize = 11f
        }
        val muted = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#64748B")
            textSize = 10f
        }
        val accent = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#0F766E")
            textSize = 12f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val line = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.6f
        }

        var y = MARGIN + 10f

        // ── Header: optional logo (left) + school name + address + GSTIN ──
        val effectiveSchoolName = meta?.name?.takeIf { it.isNotBlank() } ?: schoolName.ifBlank { "School" }
        val logo = meta?.logo
        if (logo != null) {
            // Draw logo at 56dp (~56pt) high, preserve aspect ratio.
            val logoH = 56f
            val ratio = logo.width.toFloat() / logo.height.toFloat()
            val logoW = (logoH * ratio).coerceAtMost(80f)
            val logoRect = android.graphics.RectF(MARGIN, y - 14f, MARGIN + logoW, y - 14f + logoH)
            canvas.drawBitmap(logo, null, logoRect, null)
            // Shift name+address right of the logo.
            val textX = MARGIN + logoW + 14f
            canvas.drawText(effectiveSchoolName, textX, y, title)
            y += 16f
            meta?.address?.takeIf { it.isNotBlank() }?.let {
                canvas.drawText(it, textX, y, subtitle); y += 12f
            }
            val contactBits = listOfNotNull(
                meta?.phone?.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
                meta?.email?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (contactBits.isNotBlank()) {
                canvas.drawText(contactBits, textX, y, muted); y += 12f
            }
            meta?.gstin?.takeIf { it.isNotBlank() }?.let {
                canvas.drawText("GSTIN: $it", textX, y, muted); y += 12f
            }
            // Make sure y is at least below the logo bottom.
            y = maxOf(y, logoRect.bottom + 8f)
        } else {
            canvas.drawText(effectiveSchoolName, MARGIN, y, title)
            y += 18f
            meta?.address?.takeIf { it.isNotBlank() }?.let {
                canvas.drawText(it, MARGIN, y, subtitle); y += 12f
            }
            val contactBits = listOfNotNull(
                meta?.phone?.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
                meta?.email?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (contactBits.isNotBlank()) {
                canvas.drawText(contactBits, MARGIN, y, muted); y += 12f
            }
            meta?.gstin?.takeIf { it.isNotBlank() }?.let {
                canvas.drawText("GSTIN: $it", MARGIN, y, muted); y += 12f
            }
        }
        canvas.drawText("Fee Payment Receipt", MARGIN, y, subtitle)
        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, line)
        y += 18f

        // Receipt + date row
        val receiptNoLabel = "Receipt #: ${receipt.receiptNo.ifBlank { receipt.receiptKey }}"
        val dateStr = when (val c = receipt.createdAt) {
            is com.google.firebase.Timestamp -> SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(c.toDate())
            is String -> c
            else -> ""
        }
        canvas.drawText(receiptNoLabel, MARGIN, y, h2)
        if (dateStr.isNotBlank()) {
            val width = body.measureText(dateStr)
            canvas.drawText(dateStr, PAGE_WIDTH - MARGIN - width, y, body)
        }
        y += 22f

        // Student info block
        canvas.drawText("Student Details", MARGIN, y, accent)
        y += 14f
        drawKv(canvas, "Name", receipt.studentName, MARGIN, y, muted, body); y += 16f
        drawKv(canvas, "Student ID", receipt.studentId, MARGIN, y, muted, body); y += 16f
        val classSec = listOfNotNull(
            receipt.className.takeIf { it.isNotBlank() }?.let { formatClass(it) },
            receipt.section.takeIf { it.isNotBlank() }?.let { formatSection(it) }
        ).joinToString(" · ")
        if (classSec.isNotBlank()) {
            drawKv(canvas, "Class", classSec, MARGIN, y, muted, body); y += 16f
        }
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, line)
        y += 18f

        // Breakdown table
        canvas.drawText("Fee Breakdown", MARGIN, y, accent)
        y += 14f
        // Table header
        val col1 = MARGIN
        val col2 = PAGE_WIDTH - MARGIN - 80f
        canvas.drawText("Head", col1, y, muted)
        val amtHeader = "Amount"
        canvas.drawText(amtHeader, PAGE_WIDTH - MARGIN - body.measureText(amtHeader), y, muted)
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, line)
        y += 14f

        var breakdownTotal = 0.0
        receipt.feeBreakdown.forEach { row ->
            val head = (row["head"] as? String) ?: ""
            val amt = ((row["amount"] as? String)?.toDoubleOrNull()
                ?: (row["amount"] as? Number)?.toDouble()) ?: 0.0
            val freq = (row["frequency"] as? String) ?: ""
            if (head.isNotBlank()) {
                val label = if (freq.isNotBlank()) "$head  ·  ${freq.replaceFirstChar { it.uppercase() }}" else head
                canvas.drawText(label, col1, y, body)
                val amtStr = "Rs. ${"%,.2f".format(amt)}"
                canvas.drawText(amtStr, PAGE_WIDTH - MARGIN - body.measureText(amtStr), y, body)
                y += 16f
                breakdownTotal += amt
            }
        }

        if (receipt.feeBreakdown.isEmpty()) {
            // Fallback: list months
            receipt.feeMonths.forEach { m ->
                canvas.drawText(m, col1, y, body); y += 16f
            }
        }

        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, line)
        y += 16f

        // Totals
        fun totalRow(label: String, amount: Double, emphasis: Boolean = false) {
            val p = if (emphasis) h2 else body
            canvas.drawText(label, col1, y, p)
            val s = "Rs. ${"%,.2f".format(amount)}"
            canvas.drawText(s, PAGE_WIDTH - MARGIN - p.measureText(s), y, p)
            y += if (emphasis) 20f else 16f
        }
        if (receipt.discount > 0) totalRow("Discount", -receipt.discount)
        if (receipt.fine > 0) totalRow("Fine", receipt.fine)
        totalRow("Net Paid", receipt.netAmount, emphasis = true)

        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, line)
        y += 18f

        // Payment mode + months
        canvas.drawText("Payment", MARGIN, y, accent); y += 14f
        drawKv(canvas, "Mode", receipt.paymentMode.ifBlank { "—" }, MARGIN, y, muted, body); y += 16f
        if (receipt.feeMonths.isNotEmpty()) {
            drawKv(canvas, "Months", receipt.feeMonths.joinToString(", "), MARGIN, y, muted, body)
            y += 16f
        }
        if (receipt.remarks.isNotBlank()) {
            drawKv(canvas, "Remarks", receipt.remarks, MARGIN, y, muted, body)
            y += 16f
        }

        // ── Signature row (3 boxes) — anchored bottom of page ──
        // Sits ~140pt above page bottom so it never collides with
        // dynamically growing content above (breakdown / remarks).
        val sigY = PAGE_HEIGHT - MARGIN - 110f
        // Only render the signature row if we still have room — i.e.
        // the content above hasn't already pushed past it. Otherwise
        // skip cleanly so we don't corrupt the page.
        if (y < sigY - 10f) {
            val colW = (PAGE_WIDTH - 2 * MARGIN - 20f) / 3f
            val sigBoxY = sigY + 24f
            val labels = listOf("Cashier", "Accountant", "Principal")
            for (i in 0..2) {
                val xStart = MARGIN + (colW + 10f) * i
                // Signature line
                canvas.drawLine(xStart, sigBoxY, xStart + colW, sigBoxY, line)
                // Label centred under the line
                val label = labels[i]
                val lblW = body.measureText(label)
                canvas.drawText(label, xStart + (colW - lblW) / 2f, sigBoxY + 14f, body)
            }
        }

        // Footer
        val footerY = PAGE_HEIGHT - MARGIN
        canvas.drawLine(MARGIN, footerY - 30f, PAGE_WIDTH - MARGIN, footerY - 30f, line)
        canvas.drawText("This is a computer-generated receipt — verify against the school's official records.", MARGIN, footerY - 16f, muted)
        val gen = "Generated: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(java.util.Date())}"
        canvas.drawText(gen, MARGIN, footerY - 4f, muted)

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /**
     * Save the generated PDF to the device's public Downloads folder
     * so the parent has an offline copy outside the app's cache.
     *
     * Android 10+ uses MediaStore (no permission needed). Older
     * devices fall back to writing into Environment Downloads dir
     * (the app already has implicit access via FileProvider). Returns
     * the public URI / path used so the caller can show a toast.
     *
     * @param sourcePdf  the file produced by [generate]
     * @param fileName   final user-visible name (e.g. "Receipt_F6.pdf")
     */
    fun saveToDownloads(context: Context, sourcePdf: File, fileName: String): String {
        val target = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // MediaStore — Android 10+. No WRITE_EXTERNAL_STORAGE perm required.
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, target)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS + "/SchoolSync"
                )
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw java.io.IOException("Could not create download entry.")
            resolver.openOutputStream(uri)?.use { out ->
                sourcePdf.inputStream().use { it.copyTo(out) }
            } ?: throw java.io.IOException("Could not open output stream.")
            values.clear()
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Downloads/SchoolSync/$target"
        } else {
            // Pre-Android-10: write directly to public Downloads dir.
            @Suppress("DEPRECATION")
            val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val sub = File(downloads, "SchoolSync").apply { if (!exists()) mkdirs() }
            val outFile = File(sub, target)
            sourcePdf.inputStream().use { input ->
                FileOutputStream(outFile).use { input.copyTo(it) }
            }
            // Ask MediaScanner to index the new file so it shows in the gallery/Files apps.
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(outFile.absolutePath), arrayOf("application/pdf"), null
            )
            return outFile.absolutePath
        }
    }

    /**
     * Best-effort cleanup of older receipt PDFs we generated. Called
     * from app startup or before generating a new one to keep the
     * cache from growing unbounded.
     */
    fun cleanupOldReceipts(context: Context, keepLatest: Int = 5) {
        val dir = File(context.cacheDir, "receipts")
        if (!dir.exists()) return
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("Receipt_") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(keepLatest).forEach { runCatching { it.delete() } }
    }

    /** Launch the standard Android share sheet for the given PDF file. */
    fun sharePdf(context: Context, file: File, label: String = "Share Receipt") {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, label))
    }

    private fun drawKv(
        canvas: android.graphics.Canvas,
        key: String,
        value: String,
        x: Float,
        y: Float,
        keyPaint: Paint,
        valuePaint: Paint
    ) {
        canvas.drawText("$key:", x, y, keyPaint)
        val keyWidth = keyPaint.measureText("$key: ")
        canvas.drawText(value, x + keyWidth + 4f, y, valuePaint)
    }

    // Avoid unused-import warnings when building
    @Suppress("unused") private val _r = Rect()

    /**
     * Canonical class label. Server may store `"Class 10th"` (prefixed)
     * or `"10th"` (bare); we always render `"Class <N>"` exactly once.
     */
    private fun formatClass(raw: String): String {
        val bare = raw.trim()
            .removePrefix("Class").removePrefix("class")
            .trim()
        return if (bare.isBlank()) raw.trim() else "Class $bare"
    }

    /**
     * Canonical section label: never "Sec Section A" — strip any
     * leading "Section"/"Sec" before prepending "Sec".
     */
    private fun formatSection(raw: String): String {
        val bare = raw.trim()
            .removePrefix("Section").removePrefix("section")
            .removePrefix("Sec").removePrefix("sec")
            .trim()
        return if (bare.isBlank()) raw.trim() else "Sec $bare"
    }
}
