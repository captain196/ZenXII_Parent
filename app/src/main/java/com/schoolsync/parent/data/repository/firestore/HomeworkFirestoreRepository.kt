package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.HomeworkDoc
import com.schoolsync.parent.data.model.firestore.SubmissionDoc
import com.schoolsync.parent.util.Constants
import com.schoolsync.parent.util.debugLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
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
     * Bulk-fetch every submission this student has made, keyed by homeworkId.
     * One indexed query replaces the per-homework getSubmissionStatus N+1
     * pattern when building the homework list. Best-effort — empty map on
     * failure so the homework list keeps rendering.
     */
    suspend fun getSubmissionsForStudent(studentId: String): Map<String, SubmissionDoc> {
        if (studentId.isBlank()) return emptyMap()
        val schoolCode = getSchoolCode() ?: return emptyMap()
        return try {
            val rows = firestoreService.queryDocumentsAs<SubmissionDoc>(
                Constants.Firestore.SUBMISSIONS
            ) { ref ->
                ref.whereEqualTo("schoolId",  schoolCode)
                    .whereEqualTo("studentId", studentId)
            }
            val out = mutableMapOf<String, SubmissionDoc>()
            for (s in rows) {
                if (s.homeworkId.isNotBlank()) out[s.homeworkId] = s
            }
            out
        } catch (e: Exception) {
            // BUG-022 — structured debugLog (OEM-strip-immune) replaces the
            // prior fully-silent catch. Result.success(emptyMap()) contract
            // preserved so the homework list keeps rendering.
            debugLog("ACC_HW_PARENT_REPO_GET_SUBMISSIONS_FOR_STUDENT_FAILED err=${e.javaClass.simpleName}:${e.message}")
            emptyMap()
        }
    }

    /**
     * Fetch the teacherMark recorded for (homeworkId, studentId), if any.
     * Used to display "Evaluated (no submission)" + score + remark when
     * the student has no submission of their own. Returns null if absent
     * or on read failure (best-effort — never blocks the homework list).
     */
    suspend fun getTeacherMark(homeworkId: String, studentId: String): Pair<Int, String>? {
        if (homeworkId.isBlank() || studentId.isBlank()) return null
        val markId = "${homeworkId}_${studentId}"
        return try {
            val snap = firestoreService.getDocument(Constants.Firestore.TEACHER_MARKS, markId)
            if (snap == null) null
            else {
                val sc = (snap.getLong("score") ?: -1L).toInt()
                val rk = snap.getString("remark") ?: ""
                sc to rk
            }
        } catch (e: Exception) {
            // BUG-022 — structured debugLog replaces silent catch.
            // null contract preserved so caller renders "no mark".
            debugLog("ACC_HW_PARENT_REPO_GET_TEACHER_MARK_FAILED hwId=$homeworkId err=${e.javaClass.simpleName}:${e.message}")
            null
        }
    }

    /**
     * Bulk-fetch every teacherMark for a single student in one query —
     * keyed by homeworkId. Replaces the N+1 pattern of calling
     * getTeacherMark() per-homework while building the homework list.
     * Best-effort: returns an empty map on failure so the caller never
     * sees a homework list missing entries because of a marks-read hiccup.
     */
    suspend fun getTeacherMarksForStudent(studentId: String): Map<String, Pair<Int, String>> {
        if (studentId.isBlank()) return emptyMap()
        val schoolCode = getSchoolCode() ?: return emptyMap()
        return try {
            val snap = firestoreService.queryDocuments(Constants.Firestore.TEACHER_MARKS) { ref ->
                ref.whereEqualTo("schoolId",  schoolCode)
                    .whereEqualTo("studentId", studentId)
            }
            val out = mutableMapOf<String, Pair<Int, String>>()
            for (d in snap.documents) {
                val hwId = d.getString("homeworkId") ?: continue
                val sc   = (d.getLong("score") ?: -1L).toInt()
                val rk   = d.getString("remark") ?: ""
                out[hwId] = sc to rk
            }
            out
        } catch (e: Exception) {
            // BUG-022 — structured debugLog replaces silent catch.
            // emptyMap() contract preserved.
            debugLog("ACC_HW_PARENT_REPO_GET_TEACHER_MARKS_FOR_STUDENT_FAILED err=${e.javaClass.simpleName}:${e.message}")
            emptyMap()
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
        // BUG-023 — input-boundary length validation (mirror of admin BUG-013
        // and Teacher BUG-020). Byte-count via toByteArray().size for accurate
        // Firestore 1MB doc-cap semantics; user-facing messages say
        // "characters"/"files" for clarity.
        if (text.toByteArray().size > 10000) {
            return Result.failure(IllegalArgumentException("Submission text exceeds 10000 characters."))
        }
        if (files.size > 10) {
            return Result.failure(IllegalArgumentException("Submission cannot have more than 10 attachments."))
        }
        if (studentName.toByteArray().size > 200) {
            return Result.failure(IllegalArgumentException("Student name exceeds 200 characters."))
        }

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
            // Atomic: submission write + counter increment in one transaction.
            // The increment fires ONLY when this is the first time the doc is
            // created (re-submission / edit updates the same docId but does
            // NOT double-count). If either operation fails, the whole
            // transaction rolls back — no half-state.
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val submissionRef = firestore
                .collection(Constants.Firestore.SUBMISSIONS).document(docId)
            val homeworkRef = firestore
                .collection(Constants.Firestore.HOMEWORK).document(homeworkId)

            firestore.runTransaction { txn ->
                val existingSnap = txn.get(submissionRef)
                val isFirstSubmission = !existingSnap.exists()

                txn.set(
                    submissionRef,
                    data,
                    com.google.firebase.firestore.SetOptions.merge()
                )

                if (isFirstSubmission) {
                    txn.update(
                        homeworkRef,
                        "submissionCount",
                        com.google.firebase.firestore.FieldValue.increment(1)
                    )
                }
                null
            }.await()

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

    /**
     * Observe submissions for a student in real time, keyed by homeworkId.
     * Emits a fresh map each time any submission for this student changes
     * (e.g., teacher reviews the submission and writes score/remark/status).
     * Emits empty map when identifiers are unavailable.
     *
     * Used by Dashboard to keep "pending homework" count accurate the moment
     * the teacher reviews a submission — without this, the dashboard would
     * keep counting reviewed homework as pending until the user manually
     * refreshes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSubmissionsForStudent(studentId: String): Flow<Map<String, SubmissionDoc>> {
        return tokenManager.user
            .map { user ->
                val schoolId = user.schoolId.takeIf { it.isNotBlank() }
                if (schoolId == null || studentId.isBlank()) null
                else schoolId
            }
            .flatMapLatest { schoolId ->
                if (schoolId == null) {
                    flowOf(emptyMap())
                } else {
                    firestoreService.observeQuery(
                        Constants.Firestore.SUBMISSIONS
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolId)
                            .whereEqualTo("studentId", studentId)
                    }.map { snapshot ->
                        val out = mutableMapOf<String, SubmissionDoc>()
                        for (doc in snapshot.documents) {
                            val s = doc.toObject(SubmissionDoc::class.java) ?: continue
                            if (s.homeworkId.isNotBlank()) out[s.homeworkId] = s
                        }
                        out
                    }
                }
            }
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }
}
