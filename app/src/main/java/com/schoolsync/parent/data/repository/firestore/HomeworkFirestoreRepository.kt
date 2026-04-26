package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.HomeworkDoc
import com.schoolsync.parent.data.model.firestore.SubmissionDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for homework operations from the parent side.
 * Supports reading homework and submitting homework on behalf of a student.
 *
 * Collections used:
 * - homework: class-level homework documents
 * - submissions: per-student submission documents
 */
@Singleton
class HomeworkFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch all active homework for a class and section.
     * Query: schoolId + sectionKey + status=="active", ordered by createdAt descending.
     */
    suspend fun getActiveHomework(
        className: String,
        section: String
    ): Result<List<HomeworkDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        val sectionKey = "${Constants.Firebase.classKey(className)}/${Constants.Firebase.sectionKey(section)}"

        return try {
            val homework = firestoreService.queryDocumentsAs<HomeworkDoc>(
                Constants.Firestore.HOMEWORK
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("sectionKey", sectionKey)
                    .whereEqualTo("status", "active")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            }
            Result.success(homework)
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            // HW-6: Handle missing composite index — fall back to
            // client-side sort (same pattern as teacher app).
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                android.util.Log.w("HomeworkRepo",
                    "Composite index missing — falling back to client-side sort")
                runCatching {
                    val rows = firestoreService.queryDocumentsAs<HomeworkDoc>(
                        Constants.Firestore.HOMEWORK
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("sectionKey", sectionKey)
                            .whereEqualTo("status", "active")
                    }
                    rows.sortedByDescending { row ->
                        when (val ts = row.createdAt) {
                            is com.google.firebase.Timestamp -> ts.seconds
                            is Long -> ts / 1000
                            is Number -> ts.toLong() / 1000
                            else -> 0L
                        }
                    }
                }.fold(
                    onSuccess = { Result.success(it) },
                    onFailure = { Result.failure(it) }
                )
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the submission status for a student on a specific homework.
     * Query: homeworkId + studentId.
     * Returns null if the student has not submitted yet.
     */
    suspend fun getSubmissionStatus(
        homeworkId: String,
        studentId: String
    ): Result<SubmissionDoc?> {
        // Direct doc read by the canonical doc ID: {hwId}_{studentId}.
        // This is the ONLY read path — we DON'T use a query because
        // Firestore queries without a schoolId filter get PERMISSION_DENIED.
        // If the doc doesn't exist, the SDK returns null (not an error).
        // If rules block the read, we catch and treat it as "not submitted".
        val docId = "${homeworkId}_${studentId}"
        return try {
            val doc = firestoreService.getDocumentAs<SubmissionDoc>(
                Constants.Firestore.SUBMISSIONS, docId
            )
            Result.success(doc)  // null = not submitted yet
        } catch (e: Exception) {
            // PERMISSION_DENIED or any error → treat as "not submitted"
            // (safe: if submission exists but can't be read, the UI shows
            // pending — the teacher can still see it from their side)
            Result.success(null)
        }
    }

    /**
     * Submit homework for a student.
     * Creates a new submission document with status "submitted".
     */
    suspend fun submitHomework(
        homeworkId: String,
        studentId: String,
        studentName: String,
        sectionKey: String,
        text: String,
        files: List<String>
    ): Result<Unit> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        val docId = "${homeworkId}_${studentId}"
        val data = hashMapOf(
            "schoolId" to schoolCode,
            "homeworkId" to homeworkId,
            "studentId" to studentId,
            "studentName" to studentName,
            "sectionKey" to sectionKey,
            "status" to "submitted",
            "text" to text,
            "files" to files,
            "submittedAt" to firestoreService.serverTimestamp(),
            "remark" to "",
            "reviewedBy" to "",
            "score" to -1,
            "maxMarks" to 0
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.SUBMISSIONS,
                docId,
                data,
                merge = true
            )

            // submissionCount: can't increment from parent app (Firestore
            // rules only allow staff to update homework docs). The admin
            // panel counts actual submissions when it needs the number.

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe homework for a class and section in real time.
     * Reacts to user profile changes (school code) via [flatMapLatest].
     * Emits an empty list when identifiers are unavailable.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHomework(className: String, section: String): Flow<List<HomeworkDoc>> {
        val sectionKey = "${Constants.Firebase.classKey(className)}/${Constants.Firebase.sectionKey(section)}"

        return tokenManager.user
            .map { user ->
                // Use schoolId (e.g. "SCH_D94FE8F7AD") — matches what teacher/admin
                // write to the homework doc's schoolId field.
                // NOT schoolCode (which is the login code like "10004").
                user.schoolId.takeIf { it.isNotBlank() }
            }
            .flatMapLatest { schoolId ->
                if (schoolId == null) {
                    flowOf(emptyList())
                } else {
                    firestoreService.observeQuery(
                        Constants.Firestore.HOMEWORK
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolId)
                            .whereEqualTo("sectionKey", sectionKey)
                            .whereEqualTo("status", "active")
                            .orderBy("createdAt", Query.Direction.DESCENDING)
                    }.map { snapshot ->
                        snapshot.documents.mapNotNull { doc ->
                            doc.toObject(HomeworkDoc::class.java)
                        }
                    }
                }
            }
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }
}
