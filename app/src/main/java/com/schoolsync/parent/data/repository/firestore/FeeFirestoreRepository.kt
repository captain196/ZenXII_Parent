package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.FeeDemandDoc
import com.schoolsync.parent.data.model.firestore.FeeDefaulterDoc
import com.schoolsync.parent.data.model.firestore.FeeReceiptDoc
import com.schoolsync.parent.data.model.firestore.FeeStructureDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
                    .orderBy("month")
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
                    .orderBy("month")
            }
            val pending = demands.filter { it.status != "paid" }
            Result.success(pending)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the defaulter status for a student.
     * Doc ID pattern: `{schoolId}_{studentId}`
     */
    suspend fun getDefaulterStatus(studentId: String): Result<FeeDefaulterDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        val docId = "${schoolCode}_${studentId}"

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
     * Fetch all fee receipts for a student in the current session.
     */
    suspend fun getPaymentHistory(studentId: String): Result<List<FeeReceiptDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val receipts = firestoreService.queryDocumentsAs<FeeReceiptDoc>(
                Constants.Firestore.FEE_RECEIPTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("studentId", studentId)
            }
            Result.success(receipts)
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
    fun observeFeeDemands(studentId: String): Flow<List<FeeDemandDoc>> {
        return tokenManager.user
            .map { user ->
                val code = user.schoolCode.takeIf { it.isNotBlank() }
                val sess = user.session.takeIf { it.isNotBlank() }
                if (code != null && sess != null) Pair(code, sess) else null
            }
            .flatMapLatest { pair ->
                if (pair == null) {
                    flowOf(emptyList())
                } else {
                    val (schoolCode, session) = pair
                    firestoreService.observeQuery(
                        Constants.Firestore.FEE_DEMANDS
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("session", session)
                            .whereEqualTo("studentId", studentId)
                            .orderBy("month")
                    }.map { snapshot ->
                        snapshot.documents.mapNotNull { doc ->
                            doc.toObject(FeeDemandDoc::class.java)
                        }
                    }
                }
            }
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.user.firstOrNull()?.session?.takeIf { it.isNotBlank() }
    }
}
