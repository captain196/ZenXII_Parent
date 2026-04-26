package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.StoryDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stories — single source of truth for the parent app.
 *
 * Reads the Firestore collection `stories` (the SAME collection
 * teacher app writes to and admin panel moderates). NO RTDB.
 *
 * Real-time snapshot listener so a teacher uploading from another
 * device, or an admin flagging a story, propagates to every parent's
 * Dashboard within ~100ms.
 *
 * Per-user view-tracking lives in subcollection `stories/{id}/viewers/{userId}`
 * — see [markAsViewed] for the contract. The parent VM uses the
 * presence of the parent's id in that subcollection to drive the
 * "unviewed ring" affordance on StoriesRow.
 */
@Singleton
class StoryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    companion object {
        const val COLLECTION = "stories"
        const val VIEWERS_SUBCOLLECTION = "viewers"
    }

    /**
     * Real-time stream of all ACTIVE + NON-EXPIRED stories for this
     * parent's school, newest expiry first.
     *
     * Filtering rules (mirrored on every client):
     *   - schoolId == this parent's school
     *   - expiresAt > now (server-side range query)
     *   - status == 'active' (client-side filter — covers admin
     *     moderation actions that propagate via the same listener)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveStories(): Flow<List<StoryDoc>> {
        return tokenManager.user
            .map { user -> user.schoolCode.takeIf { it.isNotBlank() } }
            .flatMapLatest { schoolCode ->
                if (schoolCode == null) flowOf(emptyList())
                else {
                    // Canonical expiry filter: Timestamp field, Timestamp.now().
                    // Firestore TTL also reads this field so the physical
                    // cleanup matches what the client already hides.
                    val nowTs = com.google.firebase.Timestamp.now()
                    firestoreService.observeQuery(COLLECTION) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereGreaterThan("expiresAtTs", nowTs)
                            .orderBy("expiresAtTs", Query.Direction.DESCENDING)
                    }.map { snap ->
                        val nowMs = System.currentTimeMillis()
                        snap.documents
                            .mapNotNull { it.toObject(StoryDoc::class.java) }
                            // Defense in depth: status active + expiry
                            // not past (covers legacy docs that only have
                            // the Long expiresAt field).
                            .filter { it.status == "active" && it.expiresAtMillis > nowMs }
                    }.onStart { emit(emptyList()) }
                     .catch { emit(emptyList()) }
                }
            }
    }

    /**
     * One-shot fetch — kept for backwards-compat with any caller that
     * doesn't want a live stream. Prefer [observeActiveStories].
     */
    suspend fun getActiveStories(): Result<List<StoryDoc>> {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))
        return try {
            val nowTs = com.google.firebase.Timestamp.now()
            val nowMs = System.currentTimeMillis()
            val stories = firestoreService.queryDocumentsAs<StoryDoc>(COLLECTION) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereGreaterThan("expiresAtTs", nowTs)
                    .orderBy("expiresAtTs", Query.Direction.DESCENDING)
            }
            Result.success(stories.filter { it.status == "active" && it.expiresAtMillis > nowMs })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark a story as viewed by the current parent — guaranteed
     * one-user-one-view via Firestore transaction:
     *
     *   1. tx.get(viewers/{userId})
     *   2. if EXISTS  → no-op (viewer already counted, do not bump
     *                  viewCount again no matter how many times the
     *                  parent re-opens the story)
     *   3. if MISSING → tx.set(viewers/{userId}) AND
     *                   tx.update(stories/{id}, viewCount++)
     *
     * Both writes happen in the SAME transaction so a partial state
     * (viewer doc without counter, or counter without viewer doc) is
     * impossible. Firestore retries the transaction on contention so
     * concurrent first-views from the same user across two devices
     * still result in exactly one viewer doc + exactly one increment.
     *
     * Phase D — fixes the previous overcount where a re-view bumped
     * viewCount despite the viewer doc already existing.
     */
    suspend fun markAsViewed(storyId: String): Result<Unit> {
        val userId = tokenManager.user.firstOrNull()?.userId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))
        return try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val storyRef  = firestore.collection(COLLECTION).document(storyId)
            val viewerRef = storyRef.collection(VIEWERS_SUBCOLLECTION).document(userId)

            firestore.runTransaction { tx ->
                val existing = tx.get(viewerRef)
                if (existing.exists()) {
                    // Already counted — explicitly no-op so re-views
                    // never inflate viewCount.
                    return@runTransaction null
                }
                // First view — write viewer marker + bump counter atomically.
                tx.set(viewerRef, hashMapOf<String, Any?>(
                    "viewedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "userId"   to userId
                ))
                tx.update(storyRef, "viewCount", FieldValue.increment(1))
                null
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
