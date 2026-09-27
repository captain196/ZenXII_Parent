package com.schoolsync.parent.data.repository.firestore

import android.util.Log
import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.LeaveApplicationDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for student leave application operations.
 *
 * Phase 9a (2026-04-09): submit + cancel wrote to RTDB because Firestore
 * security rules blocked client-side writes (same PERMISSION_DENIED issue as
 * userDevices/pushRequests). Phase 10 fixed the rules — Firestore writes have
 * been primary and working ever since.
 *
 * Phase 3 RTDB removal (2026-08-13, PARENT_APP_FIRESTORE_ONLY_HARDENING_REPORT.md
 * §7/§8): the RTDB write/read fallbacks that survived Phase 10 as a safety net
 * are REMOVED. They were not actually safe: the Admin Panel reads
 * `leaveApplications` from Firestore only (confirmed — application/controllers/
 * Attendance.php never reads RTDB for this feature), so a leave application
 * that silently fell back to RTDB on a Firestore write failure would appear
 * "submitted" to the parent while being invisible to the school forever. A
 * clean Firestore-only failure that surfaces to the UI is strictly safer than
 * a fallback that succeeds into a store nobody reads.
 */
@Singleton
class LeaveFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "LeaveFirestoreRepo"
    }

    /**
     * Submit a leave application for the current child.
     * Phase 10: Firestore-first (rules now allow client writes).
     */
    suspend fun submitLeave(
        studentId: String,
        studentName: String,
        className: String,
        section: String,
        leaveType: String,
        startDate: String,
        endDate: String,
        numberOfDays: Int,
        reason: String
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        val leaveId = "${schoolCode}_${studentId}_${System.currentTimeMillis()}"

        val data = mapOf(
            "schoolId" to schoolCode,
            "leaveId" to leaveId,
            "applicantType" to "student",
            "applicantId" to studentId,
            "applicantName" to studentName,
            "className" to Constants.Firebase.classKey(className),
            "section" to Constants.Firebase.sectionKey(section),
            "sectionKey" to "${Constants.Firebase.classKey(className)}/${Constants.Firebase.sectionKey(section)}",
            "leaveType" to leaveType,
            "startDate" to startDate,
            "endDate" to endDate,
            "numberOfDays" to numberOfDays,
            "reason" to reason,
            "status" to "pending",
            "attendanceStamped" to false,
            "appliedAt" to firestoreService.serverTimestamp(),
            "approvedBy" to "",
            "remarks" to ""
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.LEAVE_APPLICATIONS,
                leaveId,
                data
            )
            Log.w(TAG, "Leave submitted to Firestore: $leaveId")
            Result.success(leaveId)
        } catch (e: Exception) {
            Log.e(TAG, "Leave submit failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch leave history — Firestore only. A successful query (even EMPTY)
     * is authoritative; an empty history must NOT be treated as a failure.
     */
    suspend fun getLeaveHistory(studentId: String): Result<List<LeaveApplicationDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val leaves = firestoreService.queryDocumentsAs<LeaveApplicationDoc>(
                Constants.Firestore.LEAVE_APPLICATIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("applicantId", studentId)
                    .whereEqualTo("applicantType", "student")
            }
            // Fix (MEDIUM): sort newest-first (parity with Teacher app).
            val sorted = leaves.sortedWith(
                compareByDescending<LeaveApplicationDoc> { appliedAtMillis(it.appliedAt) }
                    .thenByDescending { it.startDate }
            )
            Result.success(sorted)
        } catch (e: Exception) {
            Log.w(TAG, "Firestore leave query failed", e)
            Result.failure(e)
        }
    }

    /**
     * Cancel a pending leave application. Firestore-first.
     */
    suspend fun cancelLeave(leaveId: String): Result<Unit> {
        return try {
            // Read current status from Firestore
            val doc = firestoreService.getDocumentAs<LeaveApplicationDoc>(
                Constants.Firestore.LEAVE_APPLICATIONS,
                leaveId
            ) ?: return Result.failure(Exception("Leave application not found"))

            // Fix (case-insensitive): accept legacy CapitalCase ("Pending").
            if (!doc.status.equals("pending", ignoreCase = true)) {
                return Result.failure(IllegalStateException("Cannot cancel leave with status '${doc.status}'"))
            }

            firestoreService.updateDocument(
                Constants.Firestore.LEAVE_APPLICATIONS,
                leaveId,
                mapOf("status" to "cancelled")
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe a specific leave application for real-time status updates.
     */
    fun observeLeaveStatus(leaveId: String): Flow<LeaveApplicationDoc?> {
        return firestoreService.observeDocumentAs<LeaveApplicationDoc>(
            Constants.Firestore.LEAVE_APPLICATIONS,
            leaveId
        )
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }

    /** Normalize a Firestore Timestamp / RTDB numeric appliedAt to epoch millis for sorting. */
    private fun appliedAtMillis(raw: Any?): Long = when (raw) {
        is com.google.firebase.Timestamp -> raw.toDate().time
        is java.util.Date -> raw.time
        is Number -> raw.toLong()
        else -> 0L
    }
}
