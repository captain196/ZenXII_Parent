package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.StudentDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading student data from Firestore.
 * Collection: students/{studentId}
 */
@Singleton
class StudentFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch a single student by their document ID.
     */
    suspend fun getStudent(studentId: String): Result<StudentDoc> {
        return try {
            val doc = firestoreService.getDocumentAs<StudentDoc>(
                Constants.Firestore.STUDENTS,
                studentId
            )
            if (doc != null) {
                Result.success(doc)
            } else {
                Result.failure(Exception("Student not found: $studentId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all students belonging to a specific class and section within the current school.
     * Uses compound query: schoolId == schoolCode AND className == [className] AND section == [section].
     */
    suspend fun getStudentsByClass(className: String, section: String): Result<List<StudentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val students = firestoreService.queryDocumentsAs<StudentDoc>(
                Constants.Firestore.STUDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("className", className)
                    .whereEqualTo("section", section)
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all students in the current school.
     * Uses query: schoolId == schoolCode.
     */
    suspend fun getStudentsBySchool(): Result<List<StudentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val students = firestoreService.queryDocumentsAs<StudentDoc>(
                Constants.Firestore.STUDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search students by name prefix within the current school.
     * Uses Firestore range query with Unicode upper bound for prefix matching.
     */
    suspend fun searchStudentsByName(query: String): Result<List<StudentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        if (query.isBlank()) {
            return Result.success(emptyList())
        }

        return try {
            val students = firestoreService.queryDocumentsAs<StudentDoc>(
                Constants.Firestore.STUDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereGreaterThanOrEqualTo("name", query)
                    .whereLessThanOrEqualTo("name", query + "\uf8ff")
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe a student document for real-time changes.
     * Emits `null` when the document does not exist.
     */
    fun observeStudent(studentId: String): Flow<StudentDoc?> {
        return firestoreService.observeDocumentAs<StudentDoc>(
            Constants.Firestore.STUDENTS,
            studentId
        )
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
    }
}
