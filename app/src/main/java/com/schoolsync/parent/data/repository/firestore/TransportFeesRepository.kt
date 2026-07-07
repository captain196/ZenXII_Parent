package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.model.firestore.FeeDemandDoc
import com.schoolsync.parent.data.model.firestore.FeeReceiptDoc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F10 (2026-07-07) — thin filter over the existing FeeFirestoreRepository
 * that surfaces ONLY transport-category fee demands + receipts to the
 * Parent App Transport Fees screen (LC-21 P-G..H).
 *
 * Refinement 4 (2026-07-07) — F10-and-later admin writes normalise
 * category='transport' (lowercase). For backwards-compat with historical
 * demands written by earlier revisions (mixed-case 'Transport' or no
 * category field at all), the filter is case-insensitive AND falls back
 * to a feeHead string-contains check. No admin-side data migration is
 * required.
 *
 * Payment flow — the Parent App REUSES the existing Fees Razorpay
 * pipeline (`FeesViewModel._checkoutRequests` Channel) rather than
 * introducing a parallel payment flow. This repo returns filtered
 * lists; the calling ViewModel forwards the selected demand to
 * `FeeFirestoreRepository.createPaymentIntent()` and lets the existing
 * checkout journey handle the rest.
 */
@Singleton
class TransportFeesRepository @Inject constructor(
    private val feeRepository: FeeFirestoreRepository
) {

    /**
     * Fetch transport-category pending demands for a student. Applies
     * client-side filter (server queries pending demands broadly; we
     * narrow to transport here). Returns empty on any repo failure so
     * the UI can render a graceful empty state.
     */
    suspend fun getPendingTransportDemands(studentId: String): Result<List<FeeDemandDoc>> {
        return try {
            val all = feeRepository.getPendingDemands(studentId).getOrDefault(emptyList())
            Result.success(all.filter(::isTransportDemand))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Payment history for transport-category receipts. Filters the
     * general payment history; case-insensitive on category with a
     * feeMonths/feeBreakdown fallback for pre-F10 receipts that don't
     * carry the category tag on the receipt itself.
     */
    suspend fun getTransportReceipts(studentId: String): Result<List<FeeReceiptDoc>> {
        return try {
            val all = feeRepository.getPaymentHistory(studentId).getOrDefault(emptyList())
            Result.success(all.filter(::isTransportReceipt))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Case-insensitive category match + feeHead fallback. */
    private fun isTransportDemand(demand: FeeDemandDoc): Boolean {
        val cat = demand.category.trim()
        if (cat.equals("transport", ignoreCase = true)) return true
        val head = demand.feeHead.lowercase()
        return head.contains("transport") || head.contains("bus")
    }

    /**
     * Receipts often don't carry a top-level category; fall back to
     * scanning the feeBreakdown items for the transport head/category.
     * Best-effort — this is a read-only presentation filter.
     */
    private fun isTransportReceipt(receipt: FeeReceiptDoc): Boolean {
        // Prefer explicit top-level category if the field is populated
        val breakdown = receipt.feeBreakdown
        for (item in breakdown) {
            val cat = (item["category"] as? String)?.trim().orEmpty()
            if (cat.equals("transport", ignoreCase = true)) return true
            val head = (item["feeHead"] as? String)?.trim().orEmpty().lowercase()
            if (head.contains("transport") || head.contains("bus")) return true
        }
        return false
    }
}
