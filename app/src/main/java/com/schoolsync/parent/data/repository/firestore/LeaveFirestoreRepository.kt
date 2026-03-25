package com.schoolsync.parent.data.repository.firestore

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
 * Repository for student leave application operations in Firestore (parent-side).
 *
 * Collection: leaveApplications
 * Parent submits leave with applicantType = "student".
 * Teacher/admin approves or rejects from their app.
 */
@Singleton
class LeaveFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Submit a leave application for the current child.
     *
     * @return the auto-generated document ID (leaveId)
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

        val sectionKey = "${Constants.Firebase.classKey(className)}/${Constants.Firebase.sectionKey(section)}"
        val leaveId = "${schoolCode}_${studentId}_${System.currentTimeMillis()}"

        val data = hashMapOf(
            "schoolId" to schoolCode,
            "applicantType" to "student",
            "applicantId" to studentId,
            "applicantName" to studentName,
            "sectionKey" to sectionKey,
            "className" to className,
            "section" to section,
            "leaveType" to leaveType,
            "startDate" to startDate,
            "endDate" to endDate,
            "numberOfDays" to numberOfDays,
            "reason" to reason,
            "attachments" to emptyList<String>(),
            "status" to "pending",
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
            Result.success(leaveId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch leave history for a specific student, ordered by appliedAt descending.
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
                    .orderBy("appliedAt", Query.Direction.DESCENDING)
            }
            Result.success(leaves)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancel a pending leave application.
     */
    suspend fun cancelLeave(leaveId: String): Result<Unit> {
        return try {
            val doc = firestoreService.getDocumentAs<LeaveApplicationDoc>(
                Constants.Firestore.LEAVE_APPLICATIONS,
                leaveId
            ) ?: return Result.failure(Exception("Leave application not found"))

            if (doc.status != "pending") {
                return Result.failure(
                    IllegalStateException("Cannot cancel leave with status '${doc.status}'")
                )
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
        return tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
    }
}
