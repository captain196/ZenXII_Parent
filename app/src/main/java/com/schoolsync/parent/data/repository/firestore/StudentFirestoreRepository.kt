package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.StudentDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
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

            // Option B sibling matching:
            //   phone matches (normalized)
            //   AND (
            //     fatherName matches OR
            //     motherName matches OR
            //     either side has missing parent names
            //   )
            //
            // Why not phone-only: shared/recycled phone numbers (e.g.
            // grandparents, in-laws, extended family) caused unrelated
            // students to appear as siblings — the Mahendra (Brijesh
            // Verma) / Hanuman Ji (Maharaj Kesari) collision in prod
            // was the trigger.
            // Why the "missing names → still match" branch: legacy
            // student records often have blank parent fields. Refusing
            // to match when names are unavailable would hide real
            // siblings purely because of incomplete data.
            //
            // Phone normalization: strip whitespace, dashes, parens,
            // and the optional + prefix so "9988-776-655", "9988776655",
            // and "+91 9988776655" collapse to one canonical key.
            //
            // Name normalization: trim, lowercase, collapse internal
            // multi-space runs to a single space so "  Brijesh   Verma "
            // and "brijesh verma" compare equal.
            fun normPhone(s: String): String =
                s.replace(Regex("[\\s\\-()]"), "").removePrefix("+")

            fun normName(s: String): String =
                s.trim().lowercase().replace(Regex("\\s+"), " ")

            val primaryPhone = listOfNotNull(
                primary.phone.takeIf { it.isNotBlank() },
                primary.phoneNumber.takeIf { it.isNotBlank() },
            ).firstOrNull()?.let(::normPhone).orEmpty()
            val primaryId = primary.userId.ifBlank { primary.studentId }.ifBlank { primary.id }

            val primaryFather = normName(primary.fatherName)
            val primaryMother = normName(primary.motherName)
            val primaryNamesBlank = primaryFather.isBlank() && primaryMother.isBlank()

            // Hard prerequisite: without a phone we can't identify
            // siblings reliably. Empty list rather than match against blank.
            val siblings = if (primaryPhone.isBlank()) {
                emptyList()
            } else {
                fetched.filter { s ->
                    val sid = s.userId.ifBlank { s.studentId }.ifBlank { s.id }
                    if (sid == primaryId || sid.isBlank()) return@filter false

                    val sPhone = listOfNotNull(
                        s.phone.takeIf { it.isNotBlank() },
                        s.phoneNumber.takeIf { it.isNotBlank() },
                    ).firstOrNull()?.let(::normPhone).orEmpty()
                    if (sPhone.isBlank()) return@filter false
                    if (sPhone != primaryPhone) return@filter false

                    val sFather = normName(s.fatherName)
                    val sMother = normName(s.motherName)
                    val sNamesBlank = sFather.isBlank() && sMother.isBlank()

                    val fatherMatch = primaryFather.isNotBlank() &&
                        sFather.isNotBlank() && primaryFather == sFather
                    val motherMatch = primaryMother.isNotBlank() &&
                        sMother.isNotBlank() && primaryMother == sMother

                    fatherMatch || motherMatch || primaryNamesBlank || sNamesBlank
                }.sortedBy { it.name }
            }
            Result.success(siblings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe a student document for real-time changes.
     * Emits `null` when the document does not exist OR when schoolCode
     * isn't yet known (pre-login state).
     *
     * B4 — Pre-fix this passed bare `studentId` as the docId, but the
     * canonical Firestore docId is `{schoolId}_{studentId}` (matches
     * `getStudent()` above and the admin writer). The bug never
     * surfaced because `observeStudent` had no live callers, but it
     * was a trap waiting for the next consumer to wire it up.
     */
    fun observeStudent(studentId: String): Flow<StudentDoc?> = flow {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
        if (schoolCode == null) {
            emit(null)
            return@flow
        }
        val docId = "${schoolCode}_$studentId"
        firestoreService.observeDocumentAs<StudentDoc>(
            Constants.Firestore.STUDENTS,
            docId
        ).collect { emit(it) }
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }
}
