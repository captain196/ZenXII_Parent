package com.schoolsync.parent.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.StudentFlag
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for student red flags / alerts (read-only for parent).
 *
 * Firestore canonical (Phase B migration 2026-04-25):
 *   collection: studentFlags
 *   document ID: {schoolId}_{flagId}
 *
 * Replaces the RTDB path `StudentFlags/{schoolCode}/{studentId}/{flagId}`.
 */
@Singleton
class RedFlagRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * One-shot dump of the parent's auth claims. The Firestore rule for
     * studentFlags.read checks `request.auth.token.student_id` /
     * `student_ids` — if those claims aren't set (or don't match the
     * flag's studentId), every read is silently denied and the listener
     * receives an empty snapshot. Call this from the VM init so the user
     * can see exactly what the server sees in logcat.
     */
    suspend fun dumpAuthClaimsForDebug() {
        try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Log.w(TAG, "dumpAuthClaims: no Firebase user signed in")
                return
            }
            val tokenResult = user.getIdToken(false).await()
            val claims = tokenResult.claims
            Log.i(TAG, "auth uid=${user.uid}")
            Log.i(TAG, "auth claims = $claims")
            Log.i(TAG, "auth.role='${claims["role"]}', auth.school_id='${claims["school_id"]}', " +
                "auth.student_id='${claims["student_id"]}', auth.student_ids=${claims["student_ids"]}, " +
                "auth.parent_db_key='${claims["parent_db_key"]}'")
        } catch (e: Exception) {
            Log.w(TAG, "dumpAuthClaims failed", e)
        }
    }

    private fun User.preferredSchoolId(): String =
        if (schoolId.isNotBlank()) schoolId else schoolCode

    private fun snapshotToFlag(doc: DocumentSnapshot): StudentFlag {
        val data = doc.data ?: emptyMap<String, Any?>()
        val flagId = (data["flagId"] as? String) ?: doc.id
        return StudentFlag.fromMap(flagId, data)
    }

    /**
     * Soft-deleted flags should never reach the parent UI — they're an
     * internal admin/teacher concept (audit trail). We filter client-side
     * to avoid adding a `whereNotEqualTo("status", "deleted")` clause that
     * would require a new composite index just for parents.
     */
    private fun List<StudentFlag>.dropDeleted(): List<StudentFlag> =
        filter { it.status != "deleted" }

    /**
     * Fetch ALL non-deleted flags (active + resolved) for the current
     * student in one shot. Use [observeFlags] for live updates; this is
     * kept for callers that just need a snapshot (e.g. badge count refreshes).
     */
    suspend fun getAllFlags(): List<StudentFlag> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.userId.isBlank()) return emptyList()
        val schoolId = user.preferredSchoolId().ifBlank { return emptyList() }

        return try {
            val snap = firestoreService.queryDocuments(Constants.Firestore.STUDENT_FLAGS) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("studentId", user.userId)
                    .orderBy("createdAtMs", Query.Direction.DESCENDING)
            }
            snap.documents.map { snapshotToFlag(it) }.dropDeleted()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Observe ALL non-deleted flags (active + resolved) for the current
     * student in real-time via a Firestore snapshot listener. Re-subscribes
     * when the user/login state changes. Status filtering happens in the VM.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFlags(): Flow<List<StudentFlag>> {
        return tokenManager.user.flatMapLatest { user ->
            if (!user.isLoggedIn || user.userId.isBlank()
                || user.preferredSchoolId().isBlank()) {
                Log.w(TAG, "observeFlags: skipping query (not logged in or missing school). " +
                    "user.isLoggedIn=${user.isLoggedIn} user.userId='${user.userId}' " +
                    "schoolId='${user.schoolId}' schoolCode='${user.schoolCode}'")
                flowOf(emptyList())
            } else {
                val schoolId  = user.preferredSchoolId()
                val studentId = user.userId
                // Loud log so a teacher/parent studentId mismatch is
                // immediately visible in logcat. Compare against the
                // RedFlagRepo log on the Teacher app:
                //   adb logcat -s RedFlagRepoP:* RedFlagRepo:*
                Log.i(TAG, "observeFlags query -> schoolId=$schoolId, studentId=$studentId")
                firestoreService.observeQuery(Constants.Firestore.STUDENT_FLAGS) { ref ->
                    ref.whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("studentId", studentId)
                        .orderBy("createdAtMs", Query.Direction.DESCENDING)
                }.map { snap ->
                    Log.i(TAG, "observeFlags emit -> raw=${snap.documents.size} docs")
                    snap.documents.map { snapshotToFlag(it) }.dropDeleted()
                }
            }
        }
    }

    companion object { private const val TAG = "RedFlagRepoP" }

    /**
     * Get count of active flags for the current student (one-shot).
     */
    suspend fun getActiveFlagCount(): Int =
        getAllFlags().count { it.status == "active" }
}
