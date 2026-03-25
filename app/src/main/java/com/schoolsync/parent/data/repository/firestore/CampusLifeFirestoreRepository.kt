package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.BehaviorSummaryDoc
import com.schoolsync.parent.data.model.firestore.HostelAllocationDoc
import com.schoolsync.parent.data.model.firestore.IncidentDoc
import com.schoolsync.parent.data.model.firestore.LibraryBookDoc
import com.schoolsync.parent.data.model.firestore.LibraryFineDoc
import com.schoolsync.parent.data.model.firestore.LibraryIssueDoc
import com.schoolsync.parent.data.model.firestore.LostFoundDoc
import com.schoolsync.parent.data.model.firestore.MealMenuDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for campus life features: hostel, library, behavior tracking, and lost & found.
 *
 * Collections used:
 * - hostelAllocations: per-student hostel room assignments
 * - mealMenus: current meal schedules
 * - libraryBooks: book catalogue for search
 * - libraryIssues: books issued to students
 * - libraryFines: outstanding fines for overdue books
 * - behaviorSummary: aggregated behavior metrics per student
 * - incidents: individual behavior incidents
 * - lostFound: lost and found item listings
 */
@Singleton
class CampusLifeFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    // ── Hostel ──────────────────────────────────────────────────────────────

    /**
     * Fetch hostel allocation for a specific student.
     * Document ID pattern: {schoolId}_{studentId}
     */
    suspend fun getHostelAllocation(studentId: String): Result<HostelAllocationDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val docId = "${schoolCode}_${studentId}"
            val doc = firestoreService.getDocumentAs<HostelAllocationDoc>(
                Constants.Firestore.HOSTEL_ALLOCATIONS,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the current meal menu for the school.
     * Document ID: {schoolId}_current
     */
    suspend fun getMealMenu(): Result<MealMenuDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val docId = "${schoolCode}_current"
            val doc = firestoreService.getDocumentAs<MealMenuDoc>(
                Constants.Firestore.MEAL_MENU,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Library ─────────────────────────────────────────────────────────────

    /**
     * Search the library catalogue by title or author containing the query string.
     * Firestore does not support full-text search natively, so we query by a
     * searchable prefix field (searchTitle) using >= and < range.
     */
    suspend fun searchBooks(query: String): Result<List<LibraryBookDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val lowerQuery = query.lowercase().trim()
            val upperBound = lowerQuery + "\uf8ff"
            val books = firestoreService.queryDocumentsAs<LibraryBookDoc>(
                Constants.Firestore.LIBRARY_BOOKS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereGreaterThanOrEqualTo("searchTitle", lowerQuery)
                    .whereLessThanOrEqualTo("searchTitle", upperBound)
                    .limit(50)
            }
            Result.success(books)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all books currently issued to a student.
     * Query: schoolId + borrowerId + status = "issued"
     */
    suspend fun getMyIssuedBooks(studentId: String): Result<List<LibraryIssueDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val issues = firestoreService.queryDocumentsAs<LibraryIssueDoc>(
                Constants.Firestore.LIBRARY_ISSUES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("borrowerId", studentId)
                    .whereEqualTo("status", "issued")
            }
            Result.success(issues)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch outstanding library fines for a student.
     * Query: schoolId + studentId + paid = false
     */
    suspend fun getMyFines(studentId: String): Result<List<LibraryFineDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val fines = firestoreService.queryDocumentsAs<LibraryFineDoc>(
                Constants.Firestore.LIBRARY_FINES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .whereEqualTo("paid", false)
            }
            Result.success(fines)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Behavior ────────────────────────────────────────────────────────────

    /**
     * Fetch the aggregated behavior summary for a student.
     * Document ID pattern: {schoolId}_{session}_{studentId}
     */
    suspend fun getBehaviorSummary(studentId: String): Result<BehaviorSummaryDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val docId = "${schoolCode}_${session}_${studentId}"
            val doc = firestoreService.getDocumentAs<BehaviorSummaryDoc>(
                Constants.Firestore.BEHAVIOR_SUMMARY,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch behavior incidents for a student, ordered by most recent first.
     */
    suspend fun getIncidents(studentId: String): Result<List<IncidentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val incidents = firestoreService.queryDocumentsAs<IncidentDoc>(
                Constants.Firestore.INCIDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            }
            Result.success(incidents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Lost & Found ────────────────────────────────────────────────────────

    /**
     * Fetch all active lost and found items for the school.
     */
    suspend fun getLostFoundItems(): Result<List<LostFoundDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val items = firestoreService.queryDocumentsAs<LostFoundDoc>(
                Constants.Firestore.LOST_FOUND
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("resolved", false)
                    .orderBy("reportedAt", Query.Direction.DESCENDING)
                    .limit(100)
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Report a lost item. Returns the generated document ID.
     */
    suspend fun reportLostItem(
        description: String,
        category: String,
        location: String,
        photo: String
    ): Result<String> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))
        val userName = getUserName()

        val docId = "${schoolCode}_${System.currentTimeMillis()}"
        val data = mapOf(
            "schoolId" to schoolCode,
            "description" to description,
            "category" to category,
            "location" to location,
            "photo" to photo,
            "type" to "lost",
            "reportedBy" to userId,
            "reportedByName" to userName,
            "reportedByRole" to "parent",
            "resolved" to false,
            "reportedAt" to FieldValue.serverTimestamp()
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.LOST_FOUND,
                docId,
                data
            )
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.user.firstOrNull()?.session?.takeIf { it.isNotBlank() }
    }

    private suspend fun getUserId(): String? {
        return tokenManager.user.firstOrNull()?.userId?.takeIf { it.isNotBlank() }
    }

    private suspend fun getUserName(): String {
        return tokenManager.user.firstOrNull()?.name ?: ""
    }
}
