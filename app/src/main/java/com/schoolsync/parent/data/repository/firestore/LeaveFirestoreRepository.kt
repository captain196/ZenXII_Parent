package com.schoolsync.parent.data.repository.firestore

import android.util.Log
import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirebaseService
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
 * Phase 9a (2026-04-09): submit + cancel write to RTDB because
 * Firestore security rules block client-side writes (same
 * PERMISSION_DENIED issue as userDevices/pushRequests). The admin
 * panel reads from RTDB and mirrors to Firestore on approval.
 *
 * Read (getLeaveHistory) is still Firestore-first with RTDB fallback
 * per the read contract.
 */
@Singleton
class LeaveFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val firebaseService: FirebaseService,
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
            // Firestore FIRST (canonical — rules now allow leaveApplications writes)
            firestoreService.setDocument(
                Constants.Firestore.LEAVE_APPLICATIONS,
                leaveId,
                data
            )
            Log.w(TAG, "Leave submitted to Firestore: $leaveId")
            Result.success(leaveId)
        } catch (e: Exception) {
            Log.e(TAG, "Leave Firestore submit failed, trying RTDB fallback", e)
            // RTDB fallback
            try {
                firebaseService.writeValue(
                    "Schools/$schoolCode/LeaveApplications/$leaveId",
                    data
                )
                Log.w(TAG, "Leave submitted via RTDB fallback: $leaveId")
                Result.success(leaveId)
            } catch (e2: Exception) {
                Log.e(TAG, "Leave submit failed on both stores", e2)
                Result.failure(e2)
            }
        }
    }

    /**
     * Fetch leave history — Firestore first, RTDB fallback.
     */
    suspend fun getLeaveHistory(studentId: String): Result<List<LeaveApplicationDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        // Firestore first
        try {
            val leaves = firestoreService.queryDocumentsAs<LeaveApplicationDoc>(
                Constants.Firestore.LEAVE_APPLICATIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("applicantId", studentId)
                    .whereEqualTo("applicantType", "student")
            }
            if (leaves.isNotEmpty()) return Result.success(leaves)
        } catch (e: Exception) {
            Log.w(TAG, "Firestore leave query failed, falling back to RTDB", e)
        }

        // RTDB fallback
        return try {
            val path = "Schools/$schoolCode/LeaveApplications"
            val allLeaves = firebaseService.readMap(path)
            val result = mutableListOf<LeaveApplicationDoc>()
            for ((id, value) in allLeaves) {
                val map = value as? Map<*, *> ?: continue
                if (map["applicantId"]?.toString() != studentId) continue
                if (map["applicantType"]?.toString() != "student") continue
                result.add(
                    LeaveApplicationDoc(
                        id = id,
                        schoolId = map["schoolId"]?.toString() ?: "",
                        applicantType = "student",
                        applicantId = studentId,
                        applicantName = map["applicantName"]?.toString() ?: "",
                        sectionKey = map["sectionKey"]?.toString() ?: "",
                        leaveType = map["leaveType"]?.toString() ?: "",
                        startDate = map["startDate"]?.toString() ?: "",
                        endDate = map["endDate"]?.toString() ?: "",
                        numberOfDays = (map["numberOfDays"] as? Number)?.toInt() ?: 0,
                        reason = map["reason"]?.toString() ?: "",
                        status = map["status"]?.toString() ?: "pending",
                        appliedAt = map["appliedAt"],
                        approvedBy = map["approvedBy"]?.toString() ?: "",
                        remarks = map["remarks"]?.toString() ?: ""
                    )
                )
            }
            result.sortByDescending { it.appliedAt?.toString() ?: "" }
            Result.success(result)
        } catch (e: Exception) {
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

            if (doc.status != "pending") {
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
}
