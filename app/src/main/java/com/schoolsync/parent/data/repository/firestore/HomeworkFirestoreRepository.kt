package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.HomeworkDoc
import com.schoolsync.parent.data.model.firestore.SubmissionDoc
import com.schoolsync.parent.util.Constants
import com.schoolsync.parent.util.debugLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX 1: distinguishable signal that the active-homework read was SKIPPED
 * because the user has no admin session yet. We fail CLOSED (return no
 * homework) rather than widen the query across sessions — an un-sessioned
 * parent must never see another session's active homework. Callers can pattern
 * -match on this type to show a "session not set" message instead of an empty
 * list.
 */
class SessionNotSetException : Exception("Session not set")

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

    private companion object {
        // How long to hold a cache-only homework snapshot before showing it,
        // giving the server snapshot time to supersede it (avoids a flash of
        // stale/just-closed homework). Offline: cache still shows after this.
        const val STALE_CACHE_GRACE_MS = 700L

        // FIX 5(a): hard cap on the active-homework set so a class with a huge
        // backlog can't spin up an unbounded listener / fetch. Newest-first
        // (createdAt DESC) so the cap keeps the most recent items.
        const val ACTIVE_HW_LIMIT = 200L
    }

    /**
     * Fetch all active homework for a class and section.
     * Query: schoolId + sectionKey + status=="active", ordered by createdAt descending.
     */
    suspend fun getActiveHomework(
        className: String,
        section: String
    ): Result<List<HomeworkDoc>> {
        val user = tokenManager.user.firstOrNull()
        val schoolCode = user?.schoolId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))
        // S1: filter by the admin currentSession so old-session homework
        // doesn't leak after a session rollover.
        // FIX 1: fail CLOSED when the user has no session yet — do NOT run the
        // session-less (widened) query, which would surface every session's
        // active homework. Return a distinguishable failure so the UI can say
        // "session not set" instead of leaking other-session data.
        val session = user.session
        if (session.isBlank()) return Result.failure(SessionNotSetException())

        val sectionKey = "${Constants.Firebase.classKey(className)}/${Constants.Firebase.sectionKey(section)}"

        return try {
            val homework = firestoreService.queryDocumentsAs<HomeworkDoc>(
                Constants.Firestore.HOMEWORK
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("sectionKey", sectionKey)
                    .whereEqualTo("status", "active")
                    .whereEqualTo("session", session)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(ACTIVE_HW_LIMIT)
            }
            Result.success(homework)
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            // HW-6: Handle missing composite index — fall back to
            // client-side sort (same pattern as teacher app). The S1 session
            // filter widens the composite index, so during the pre-deploy
            // window we also drop the session filter and re-apply it
            // client-side (blank-guard preserved).
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
                            .limit(ACTIVE_HW_LIMIT)
                    }
                    // session is guaranteed non-blank here (fail-closed guard
                    // above), so the client-side session filter is unconditional.
                    rows.asSequence()
                        .filter { it.session == session }
                        .sortedByDescending { row ->
                            when (val ts = row.createdAt) {
                                is com.google.firebase.Timestamp -> ts.seconds
                                is Long -> ts / 1000
                                is Number -> ts.toLong() / 1000
                                else -> 0L
                            }
                        }
                        .toList()
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

        return try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val submissionRef = firestore
                .collection(Constants.Firestore.SUBMISSIONS).document(docId)
            val homeworkRef = firestore
                .collection(Constants.Firestore.HOMEWORK).document(homeworkId)

            // Determine first-vs-resubmission OUTSIDE a transaction. The
            // /submissions read rule is strict (isSameSchoolStrict) and DENIES
            // reading a non-existent doc. A first submission's doc doesn't exist
            // yet, so a txn.get() here throws PERMISSION_DENIED and aborts the
            // whole transaction — this was the "submit does nothing" bug. Per the
            // documented submissions-read contract we treat PERMISSION_DENIED on
            // this read as "not found" (same as getSubmissionStatus/getTeacherMark),
            // which a txn.get() cannot do.
            val isFirstSubmission = try {
                !submissionRef.get().await().exists()
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) true
                else throw e
            }

            // Build the payload to match whichever /submissions rule applies.
            //
            // FIRST submission -> Firestore evaluates the CREATE rule, which has
            // NO field whitelist, so we write the full doc (incl. the grading
            // fields as their empty defaults).
            //
            // RE-submission (doc exists, e.g. teacher bounced it back to
            // 'pending'/'incomplete') -> the parent UPDATE rule whitelists ONLY
            // ['text','files','submittedAt','status']. The teacher's prior review
            // left reviewedBy/remark/score populated; if we re-send those (even
            // as ""/-1 defaults) they DIFF the teacher's values, land in
            // affectedKeys(), and the rule's hasOnly() check rejects the write
            // with PERMISSION_DENIED (the "submission blocked by permissions"
            // bug). Grading fields are staff-only by contract — leave them for
            // the teacher's next review to overwrite; write only the WIP fields.
            val data: HashMap<String, Any> = if (isFirstSubmission) {
                hashMapOf(
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
            } else {
                hashMapOf(
                    "status" to "submitted",
                    "text" to text,
                    "files" to files,
                    "submittedAt" to firestoreService.serverTimestamp()
                )
            }

            // Create/refresh the submission. On create the rule requires
            // submittedAt == request.time, satisfied by serverTimestamp().
            submissionRef.set(
                data,
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            // Best-effort denormalized counter bump (display-only; teacher/admin
            // compute accurate counts from /submissions directly). A counter
            // failure must never fail the submission itself.
            if (isFirstSubmission) {
                runCatching {
                    homeworkRef.update(
                        "submissionCount",
                        com.google.firebase.firestore.FieldValue.increment(1)
                    ).await()
                }
            }

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
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun observeHomework(className: String, section: String): Flow<List<HomeworkDoc>> {
        val sectionKey = "${Constants.Firebase.classKey(className)}/${Constants.Firebase.sectionKey(section)}"

        return tokenManager.user
            .map { user ->
                // Use schoolId (e.g. "SCH_D94FE8F7AD") — matches what teacher/admin
                // write to the homework doc's schoolId field.
                // NOT schoolCode (which is the login code like "10004").
                // S1: also carry the admin currentSession so old-session
                // homework doesn't leak after a rollover (blank-guard below).
                val schoolId = user.schoolId.takeIf { it.isNotBlank() }
                schoolId?.let { it to user.session }
            }
            .flatMapLatest { keys ->
                if (keys == null) {
                    flowOf(emptyList())
                } else if (keys.second.isBlank()) {
                    // FIX 1: fail CLOSED — with no admin session we must NOT run
                    // the session-less (widened) listener, which would stream
                    // every session's active homework. Emit nothing until the
                    // session claim arrives; flatMapLatest re-subscribes
                    // automatically the moment user.session becomes non-blank.
                    flowOf(emptyList())
                } else {
                    val (schoolId, session) = keys
                    firestoreService.observeQuery(
                        Constants.Firestore.HOMEWORK
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolId)
                            .whereEqualTo("sectionKey", sectionKey)
                            .whereEqualTo("status", "active")
                            .whereEqualTo("session", session)
                            .orderBy("createdAt", Query.Direction.DESCENDING)
                            .limit(ACTIVE_HW_LIMIT)
                    }
                    // Suppress the stale-cache flash: hold a cache-only snapshot
                    // briefly so the server snapshot (which follows within ms when
                    // online) supersedes it — avoids showing e.g. a just-closed
                    // homework for one frame. Offline: the cache still shows after
                    // the grace window (server snapshot never comes).
                    .debounce { if (it.metadata.isFromCache) STALE_CACHE_GRACE_MS else 0L }
                    .map { snapshot ->
                        snapshot.documents.mapNotNull { doc ->
                            doc.toObject(HomeworkDoc::class.java)
                        }
                    }.catch { e ->
                        // observeQuery has no built-in index fallback: a missing
                        // composite index (FAILED_PRECONDITION) closes the flow
                        // with an error. Until the [schoolId, sectionKey, status,
                        // session, createdAt DESC] index is deployed, fall back to
                        // a listener WITHOUT the session filter / orderBy, then
                        // apply session-filter + createdAt sort client-side.
                        val fpe = e as? com.google.firebase.firestore.FirebaseFirestoreException
                        if (fpe?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                            android.util.Log.w("HomeworkRepo",
                                "observeHomework composite index missing — falling back to client-side filter/sort")
                            emitAll(
                                firestoreService.observeQuery(
                                    Constants.Firestore.HOMEWORK
                                ) { ref ->
                                    ref.whereEqualTo("schoolId", schoolId)
                                        .whereEqualTo("sectionKey", sectionKey)
                                        .whereEqualTo("status", "active")
                                        .limit(ACTIVE_HW_LIMIT)
                                }
                                .debounce { if (it.metadata.isFromCache) STALE_CACHE_GRACE_MS else 0L }
                                .map { snapshot ->
                                    // session is guaranteed non-blank here
                                    // (fail-closed guard above), so the
                                    // client-side session filter is unconditional.
                                    snapshot.documents
                                        .mapNotNull { doc -> doc.toObject(HomeworkDoc::class.java) }
                                        .filter { it.session == session }
                                        .sortedByDescending { row ->
                                            when (val ts = row.createdAt) {
                                                is com.google.firebase.Timestamp -> ts.seconds
                                                is Long -> ts / 1000
                                                is Number -> ts.toLong() / 1000
                                                else -> 0L
                                            }
                                        }
                                }
                            )
                        } else {
                            throw e
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
