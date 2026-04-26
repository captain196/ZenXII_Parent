package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.FeeDataState
import com.schoolsync.parent.data.model.firestore.FeeCarryForwardDoc
import com.schoolsync.parent.data.model.firestore.FeeDemandDoc
import com.schoolsync.parent.data.model.firestore.FeeDefaulterDoc
import com.schoolsync.parent.data.model.firestore.FeeOnlineOrderDoc
import com.schoolsync.parent.data.model.firestore.FeeReceiptDoc
import com.schoolsync.parent.data.model.firestore.FeeRefundVoucherDoc
import com.schoolsync.parent.data.model.firestore.FeeStructureDoc
import com.schoolsync.parent.data.model.firestore.ScholarshipAwardDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading fee-related data from Firestore (parent-side).
 *
 * Provides access to fee structures, demands, defaulter status, receipts,
 * and the ability to create payment intents for online payments.
 *
 * Collections used:
 * - feeStructures: class/section fee breakdown per session
 * - feeDemands: monthly fee demands per student
 * - feeDefaulters: defaulter flags per student
 * - feeReceipts: payment receipts per student
 * - paymentIntents: online payment requests initiated by parents
 */
@Singleton
class FeeFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the fee structure for a specific class and section.
     * Doc ID pattern: `{schoolId}_{session}_{className}_{section}`
     */
    suspend fun getFeeStructure(
        className: String,
        section: String
    ): Result<FeeStructureDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        val docId = "${schoolCode}_${session}_${Constants.Firebase.classKey(className)}_${Constants.Firebase.sectionKey(section)}"

        return try {
            val doc = firestoreService.getDocumentAs<FeeStructureDoc>(
                Constants.Firestore.FEE_STRUCTURES,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all fee demands for a student in the current session,
     * ordered by month.
     */
    suspend fun getFeeDemands(studentId: String): Result<List<FeeDemandDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val demands = firestoreService.queryDocumentsAs<FeeDemandDoc>(
                Constants.Firestore.FEE_DEMANDS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("studentId", studentId)
                // No orderBy: Firestore drops docs missing the indexed field,
                // and alphabetical month order is meaningless. The VM
                // re-sorts by academic order (April → March, Yearly last).
            }
            Result.success(demands)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch only pending (non-paid) fee demands for a student in the current session.
     * Filters out demands with status "paid".
     */
    suspend fun getPendingDemands(studentId: String): Result<List<FeeDemandDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val demands = firestoreService.queryDocumentsAs<FeeDemandDoc>(
                Constants.Firestore.FEE_DEMANDS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("studentId", studentId)
                // No orderBy — see getDemands() for the rationale.
            }
            val pending = demands.filter { it.status != "paid" }
            Result.success(pending)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the defaulter status for a student.
     * Doc ID pattern: `{schoolId}_{session}_{studentId}`
     */
    suspend fun getDefaulterStatus(studentId: String): Result<FeeDefaulterDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        val docId = "${schoolCode}_${session}_${studentId}"

        return try {
            val doc = firestoreService.getDocumentAs<FeeDefaulterDoc>(
                Constants.Firestore.FEE_DEFAULTERS,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all fee receipts for a student (across all sessions).
     * Uses only schoolId + studentId to avoid composite index requirements.
     */
    suspend fun getPaymentHistory(studentId: String): Result<List<FeeReceiptDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val receipts = firestoreService.queryDocumentsAs<FeeReceiptDoc>(
                Constants.Firestore.FEE_RECEIPTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
            }
            Result.success(receipts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch a single fee receipt by its Firestore doc id (`{schoolId}_{receiptKey}`).
     */
    suspend fun getReceipt(receiptId: String): Result<FeeReceiptDoc?> {
        return try {
            val doc = firestoreService.getDocumentAs<FeeReceiptDoc>(
                Constants.Firestore.FEE_RECEIPTS, receiptId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a payment intent for online fee payment.
     * Writes a new document to the paymentIntents collection and returns the intent ID.
     */
    suspend fun createPaymentIntent(
        studentId: String,
        studentName: String,
        amount: Double,
        feeMonths: List<String>
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        val intentId = UUID.randomUUID().toString()

        val data = hashMapOf(
            "schoolId" to schoolCode,
            "session" to session,
            "studentId" to studentId,
            "studentName" to studentName,
            "amount" to amount,
            "feeMonths" to feeMonths,
            "status" to "requested",
            "gatewayOrderId" to "",
            "gatewayPaymentId" to "",
            "createdAt" to firestoreService.serverTimestamp(),
            "completedAt" to null,
            "receiptId" to ""
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.PAYMENT_INTENTS,
                intentId,
                data
            )
            Result.success(intentId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe fee demands for a student in real time.
     * Reacts to user profile changes (school code) via [flatMapLatest].
     * Emits an empty list when identifiers are unavailable.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFeeDemands(studentId: String): Flow<FeeDataState<List<FeeDemandDoc>>> {
        return tokenManager.user
            .map { user ->
                val code = user.schoolCode.takeIf { it.isNotBlank() }
                val sess = user.session.takeIf { it.isNotBlank() }
                if (code != null && sess != null) Pair(code, sess) else null
            }
            .flatMapLatest { pair ->
                if (pair == null) {
                    flowOf<FeeDataState<List<FeeDemandDoc>>>(FeeDataState.Data(emptyList()))
                } else {
                    val (schoolCode, session) = pair
                    firestoreService.observeQuery(
                        Constants.Firestore.FEE_DEMANDS
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("session", session)
                            .whereEqualTo("studentId", studentId)
                    }.map { snapshot ->
                        val list = snapshot.documents.mapNotNull { doc ->
                            try { doc.toObject(FeeDemandDoc::class.java) } catch (_: Exception) { null }
                        }
                        FeeDataState.Data(list) as FeeDataState<List<FeeDemandDoc>>
                    }
                }
            }
            .onStart { emit(FeeDataState.Loading) }
            .catch { e ->
                android.util.Log.w("FeeRepo", "observeFeeDemands failed", e)
                emit(FeeDataState.Error(e))
            }
    }

    /**
     * Fetch carry-forward dues from the previous session.
     */
    suspend fun getCarryForward(studentId: String): Result<FeeCarryForwardDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))
        val docId = "${schoolCode}_${session}_${studentId}"
        return try {
            val doc = firestoreService.getDocumentAs<FeeCarryForwardDoc>(
                Constants.Firestore.FEE_CARRY_FORWARD, docId
            )
            Result.success(doc)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Fetch active scholarship awards for a student.
     */
    suspend fun getScholarshipAwards(studentId: String): Result<List<ScholarshipAwardDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        return try {
            val awards = firestoreService.queryDocumentsAs<ScholarshipAwardDoc>(
                Constants.Firestore.SCHOLARSHIP_AWARDS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .whereEqualTo("status", "active")
            }
            Result.success(awards)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Fetch online payment orders for a student (most recent first).
     */
    suspend fun getOnlineOrders(studentId: String): Result<List<FeeOnlineOrderDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        return try {
            val orders = firestoreService.queryDocumentsAs<FeeOnlineOrderDoc>(
                Constants.Firestore.FEE_ONLINE_ORDERS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            }
            Result.success(orders)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Observe the defaulter status in real time.
     * Doc ID pattern: `{schoolId}_{session}_{studentId}` (canonical).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDefaulterStatus(studentId: String): Flow<FeeDataState<FeeDefaulterDoc?>> {
        return tokenManager.user
            .map { user ->
                val code = user.schoolId.takeIf { it.isNotBlank() }
                val sess = user.session.takeIf { it.isNotBlank() }
                if (code != null && sess != null) Pair(code, sess) else null
            }
            .flatMapLatest { pair ->
                if (pair == null) flowOf<FeeDataState<FeeDefaulterDoc?>>(FeeDataState.Data(null))
                else {
                    val (schoolCode, session) = pair
                    val docId = "${schoolCode}_${session}_${studentId}"
                    firestoreService.observeDocument(
                        Constants.Firestore.FEE_DEFAULTERS, docId
                    ).map { snap ->
                        val d = try { snap?.toObject(FeeDefaulterDoc::class.java) } catch (_: Exception) { null }
                        FeeDataState.Data(d) as FeeDataState<FeeDefaulterDoc?>
                    }
                }
            }
            .onStart { emit(FeeDataState.Loading) }
            .catch { e ->
                android.util.Log.w("FeeRepo", "observeDefaulterStatus failed", e)
                emit(FeeDataState.Error(e))
            }
    }

    /**
     * Observe the student's payment history in real time.
     *
     * NOTE: deliberately no `orderBy` or `limit` on the Firestore query.
     * Adding an orderBy on a field other than the where-clause fields
     * forces a composite index — and if that index isn't deployed yet
     * the snapshot listener returns FAILED_PRECONDITION and the flow
     * silently dies, wiping the Payments tab (we hit this). Sorting
     * and capping newest-first is done client-side in the VM mapper
     * (`mapReceiptsToPayments`). A student with thousands of receipts
     * across a single session is pathological — the ViewModel cap +
     * client-side sort handle realistic fee volumes comfortably.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePaymentHistory(studentId: String): Flow<FeeDataState<List<FeeReceiptDoc>>> {
        return tokenManager.user
            .map { user -> user.schoolId.takeIf { it.isNotBlank() } }
            .flatMapLatest { schoolCode ->
                com.schoolsync.parent.util.debugLog(
                    "PaymentHistory query: studentId='$studentId' schoolCode='$schoolCode'"
                )
                if (schoolCode == null) flowOf<FeeDataState<List<FeeReceiptDoc>>>(FeeDataState.Data(emptyList()))
                else {
                    firestoreService.observeQuery(
                        Constants.Firestore.FEE_RECEIPTS
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("studentId", studentId)
                    }.map { snap ->
                        val parsed = snap.documents.mapNotNull {
                            try { it.toObject(FeeReceiptDoc::class.java) } catch (_: Exception) { null }
                        }
                        FeeDataState.Data(parsed) as FeeDataState<List<FeeReceiptDoc>>
                    }
                }
            }
            .onStart { emit(FeeDataState.Loading) }
            .catch { e ->
                android.util.Log.w("FeeRepo", "observePaymentHistory failed", e)
                com.schoolsync.parent.util.debugLog("PaymentHistory stream crashed: ${e.message}")
                emit(FeeDataState.Error(e))
            }
    }

    /**
     * Fetch refund vouchers for a student (one-shot). Admin-processed
     * refunds land in feeRefundVouchers with amount stored as a
     * negative value so they render as ledger debits alongside normal
     * receipts. No composite index required — studentId filter only.
     */
    suspend fun getRefundVouchers(studentId: String): Result<List<FeeRefundVoucherDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            // Both filters on server — the schoolId filter is REQUIRED
            // for Firestore to accept the query against the
            // feeRefundVouchers rule (`isSameSchool()` checks
            // resource.data.schoolId == token.school_id; queries must
            // include a matching where() or the rule engine rejects the
            // read as potentially over-permissioned).
            val refunds = firestoreService.queryDocumentsAs<FeeRefundVoucherDoc>(
                Constants.Firestore.FEE_REFUND_VOUCHERS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
            }
            Result.success(refunds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch a single refund voucher by its Firestore doc ID
     * (`{schoolId}_{session}_REFUND_XXX`). Used by ReceiptDetailScreen
     * when the user taps a refund row in the Payments list.
     */
    suspend fun getRefundVoucher(voucherId: String): Result<FeeRefundVoucherDoc?> {
        return try {
            val doc = firestoreService.getDocumentAs<FeeRefundVoucherDoc>(
                Constants.Firestore.FEE_REFUND_VOUCHERS, voucherId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Realtime stream of refund vouchers. Mirrors observePaymentHistory's
     * shape so the Fees screen can merge the two flows into one ledger.
     *
     * Wrapped with `onStart(emit empty)` + `catch(emit empty)` because
     * FirestoreService.observeQuery CANCELS on Firestore errors (e.g.
     * PERMISSION_DENIED when the collection has no rules, or absent
     * collection). The cancellation propagates up through combine() in
     * FeesViewModel — which silently blocks the whole payment-history
     * stream because combine waits for every source to emit at least
     * once. Emitting empty on error keeps the combine alive so the
     * receipt list still renders.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRefundVouchers(studentId: String): Flow<List<FeeRefundVoucherDoc>> {
        return tokenManager.user
            .map { user -> user.schoolId.takeIf { it.isNotBlank() } }
            .flatMapLatest { schoolCode ->
                if (schoolCode == null) flowOf(emptyList())
                else {
                    firestoreService.observeQuery(
                        Constants.Firestore.FEE_REFUND_VOUCHERS
                    ) { ref ->
                        // Both server-side filters: schoolId is REQUIRED
                        // to satisfy the isSameSchool() rule constraint
                        // (see matching comment in getRefundVouchers()).
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("studentId", studentId)
                    }.map { snap ->
                        snap.documents.mapNotNull {
                            it.toObject(FeeRefundVoucherDoc::class.java)
                        }
                    }.onStart {
                        // Emit empty IMMEDIATELY so combine() has both
                        // sides and can proceed; the real snapshot (if
                        // any) will follow shortly and overwrite.
                        emit(emptyList())
                    }.catch { e ->
                        // Surface the underlying Firestore code so the
                        // debug log is actually useful. The outer wrapper
                        // uses `cancel(msg, cause)` so the cause is
                        // attached.
                        val cause = e.cause
                        val code = if (cause is com.google.firebase.firestore.FirebaseFirestoreException) {
                            cause.code.name
                        } else "unknown"
                        com.schoolsync.parent.util.debugLog(
                            "observeRefundVouchers: stream errored — code=$code msg=${e.message} causeMsg=${cause?.message}"
                        )
                        emit(emptyList())
                    }
                }
            }
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.user.firstOrNull()?.session?.takeIf { it.isNotBlank() }
    }
}
