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
     * Fetch a single student. Admin writes use the compound docId
     * `{schoolId}_{studentId}` (see Firestore_service::docId). We try
     * that first, falling back to the plain studentId for any legacy
     * docs written without the prefix.
     */
    suspend fun getStudent(studentId: String): Result<StudentDoc> {
        return try {
            val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
                ?: tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
            var doc: StudentDoc? = null
            if (schoolCode != null) {
                doc = firestoreService.getDocumentAs<StudentDoc>(
                    Constants.Firestore.STUDENTS,
                    "${schoolCode}_$studentId"
                )
            }
            if (doc == null) {
                doc = firestoreService.getDocumentAs<StudentDoc>(
                    Constants.Firestore.STUDENTS,
                    studentId
                )
            }
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
                    .whereEqualTo("className", Constants.Firebase.classKey(className))
                    .whereEqualTo("section", Constants.Firebase.sectionKey(section))
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
     * Find siblings of the given primary student — i.e. other active
     * students in the same school whose parent details match.
     *
     * Match strategy (strictest first):
     *   1. Same `parentDbKey` if non-blank
     *   2. Same `fatherName` AND same `motherName` AND non-blank
     *   3. Same `fatherPhone` (or `phone`) AND non-blank
     *
     * The primary student is excluded from the returned list. If no
     * siblings are found, returns an empty list (not an error).
     */
    suspend fun findSiblings(primary: StudentDoc): Result<List<StudentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        return try {
            val fetched = firestoreService.queryDocumentsAs<StudentDoc>(
                Constants.Firestore.STUDENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
            }

            val primaryFather = primary.fatherName.trim().lowercase()
            val primaryMother = primary.motherName.trim().lowercase()
            val primaryPhone  = listOf(primary.phone, primary.phoneNumber)
                .map { it.trim() }.firstOrNull { it.isNotBlank() } ?: ""
            val primaryParentKey = primary.parentDbKey.trim()
            val primaryId = primary.userId.ifBlank { primary.studentId }.ifBlank { primary.id }

            val siblings = fetched.filter { s ->
                val sid = s.userId.ifBlank { s.studentId }.ifBlank { s.id }
                if (sid == primaryId || sid.isBlank()) return@filter false
                val sFather = s.fatherName.trim().lowercase()
                val sMother = s.motherName.trim().lowercase()
                val sPhone  = listOf(s.phone, s.phoneNumber)
                    .map { it.trim() }.firstOrNull { it.isNotBlank() } ?: ""

                val keyMatch = primaryParentKey.isNotBlank() &&
                        s.parentDbKey.trim() == primaryParentKey
                val namesMatch = primaryFather.isNotBlank() && primaryMother.isNotBlank() &&
                        sFather == primaryFather && sMother == primaryMother
                val phoneMatch = primaryPhone.isNotBlank() && sPhone == primaryPhone

                keyMatch || namesMatch || phoneMatch
            }.sortedBy { it.name }
            Result.success(siblings)
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
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }
}
