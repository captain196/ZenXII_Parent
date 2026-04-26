package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.AdmissionApplicationDoc
import com.schoolsync.parent.data.model.firestore.AdmissionConfigDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for admission-related features: configuration, applications, and submissions.
 *
 * Collections used:
 * - admissionConfig: school-level admission settings (dates, classes, eligibility)
 * - admissionApplications: individual admission applications submitted by parents
 */
@Singleton
class AdmissionFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the admission configuration for the current school.
     * Document ID: {schoolId}_{session}
     */
    suspend fun getAdmissionConfig(): Result<AdmissionConfigDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val docId = "${schoolCode}_${session}"
            val doc = firestoreService.getDocumentAs<AdmissionConfigDoc>(
                Constants.Firestore.ADMISSION_CONFIG,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all admission applications submitted by the current parent.
     * Query matches by parentPhone or parentEmail from the user profile.
     */
    suspend fun getMyApplications(): Result<List<AdmissionApplicationDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))

        return try {
            val applications = firestoreService.queryDocumentsAs<AdmissionApplicationDoc>(
                Constants.Firestore.ADMISSION_APPLICATIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("parentId", userId)
                    .orderBy("submittedAt", Query.Direction.DESCENDING)
            }
            Result.success(applications)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit a new admission application. Returns the generated document ID.
     * Enriches the document with schoolId, parentId, timestamps, and initial status.
     */
    suspend fun submitApplication(data: AdmissionApplicationDoc): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))

        val docId = "${schoolCode}_${System.currentTimeMillis()}"
        val enrichedData = mapOf(
            "schoolId" to schoolCode,
            "parentId" to userId,
            "applicantName" to data.applicantName,
            "dob" to data.dob,
            "gender" to data.gender,
            "applyingForClass" to data.applyingForClass,
            "parentName" to data.parentName,
            "parentPhone" to data.parentPhone,
            "parentEmail" to data.parentEmail,
            "address" to data.address,
            "documents" to data.documents,
            "status" to "submitted",
            "submittedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.ADMISSION_APPLICATIONS,
                docId,
                enrichedData
            )
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.user.firstOrNull()?.session?.takeIf { it.isNotBlank() }
    }

    private suspend fun getUserId(): String? {
        return tokenManager.user.firstOrNull()?.userId?.takeIf { it.isNotBlank() }
    }
}
