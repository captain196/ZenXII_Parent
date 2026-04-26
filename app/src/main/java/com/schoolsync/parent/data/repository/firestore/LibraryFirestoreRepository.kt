package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.LibraryBookDoc
import com.schoolsync.parent.data.model.firestore.LibraryFineDoc
import com.schoolsync.parent.data.model.firestore.LibraryIssueDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for library features: issued books, book history, fines, and catalogue search.
 *
 * Collections used:
 * - libraryIssues: books issued to students
 * - libraryFines: outstanding fines for overdue books
 * - libraryBooks: book catalogue for search
 */
@Singleton
class LibraryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    // ── Issued Books ─────────────────────────────────────────────────────────

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

    // ── Book History ─────────────────────────────────────────────────────────

    /**
     * Fetch the full borrowing history for a student (all statuses).
     * Query: schoolId + borrowerId (includes issued, returned, overdue)
     */
    suspend fun getMyBookHistory(studentId: String): Result<List<LibraryIssueDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val issues = firestoreService.queryDocumentsAs<LibraryIssueDoc>(
                Constants.Firestore.LIBRARY_ISSUES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("borrowerId", studentId)
            }
            Result.success(issues)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Fines ────────────────────────────────────────────────────────────────

    /**
     * Fetch outstanding library fines for a student.
     * Query: schoolId + borrowerId + status = "pending"
     */
    suspend fun getMyFines(studentId: String): Result<List<LibraryFineDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val fines = firestoreService.queryDocumentsAs<LibraryFineDoc>(
                Constants.Firestore.LIBRARY_FINES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("borrowerId", studentId)
                    .whereEqualTo("status", "pending")
            }
            Result.success(fines)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Catalogue Search ─────────────────────────────────────────────────────

    /**
     * Search the library catalogue by title prefix.
     * Uses the searchTitle field for Firestore prefix range queries.
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }
}
