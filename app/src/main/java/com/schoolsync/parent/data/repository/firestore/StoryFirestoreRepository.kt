package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.StoryDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
        const val REACTIONS_SUBCOLLECTION = "reactions"
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
            // React to school OR active-child class/section changes
            // (sibling-switch updates className/section → re-filters).
            // Prefer schoolId (the SCH_ claim, always set and the value the
            // story doc's `schoolId` field holds); fall back to schoolCode.
            // Using schoolCode alone was fragile — it can be blank on some
            // accounts, which returned ZERO stories. Mirrors the teacher fix.
            .map { user -> Triple(user.schoolId.ifBlank { user.schoolCode }, user.className, user.section) }
            // Only restart the Firestore story listener when the school or
            // the active child's class/section ACTUALLY changes. Without
            // this, an unrelated profile write (dashboard self-heal on
            // refresh rewrites dob/name/etc.) re-emits the user and
            // flatMapLatest tears down + recreates the listener, which
            // emits emptyList() first — so the stories row blanked out on
            // every pull-to-refresh. distinctUntilChanged keeps the live
            // listener alive across those no-op profile updates.
            .distinctUntilChanged()
            .flatMapLatest { (storedSchool, className, section) ->
                flow {
                    // CRITICAL: the Firestore rule authorises a story read
                    // ONLY when story.schoolId == the caller's `school_id`
                    // custom claim. A listener whose query filters on any
                    // OTHER value is rejected wholesale (PERMISSION_DENIED →
                    // caught → zero stories). So resolve the filter value
                    // from the LIVE ID token claim — the exact value the rule
                    // compares against — and fall back to the stored school
                    // only when the token can't be read (e.g. offline).
                    val claimSchool = runCatching {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            ?.getIdToken(false)?.await()?.claims?.get("school_id")?.toString()
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    val schoolForQuery = claimSchool ?: storedSchool
                    if (schoolForQuery.isBlank()) {
                        emit(emptyList())
                    } else {
                        // Canonical token for THIS child's class-section.
                        // A story is visible if it's school-wide (empty
                        // audience) or explicitly targets this section.
                        val myKey = com.schoolsync.parent.data.model.firestore.StorySharedConfig
                            .audienceKey(className, section)
                        emitAll(
                            firestoreService.observeQuery(COLLECTION) { ref ->
                                ref.whereEqualTo("schoolId", schoolForQuery)
                            }.map { snap ->
                                val nowMs = System.currentTimeMillis()
                                snap.documents
                                    .mapNotNull { it.toObject(StoryDoc::class.java) }
                                    // status active + expiry not past.
                                    .filter { it.status == "active" && it.expiresAtMillis > nowMs }
                                    // Audience: empty = school-wide, else must
                                    // target this child's section.
                                    .filter { it.audienceClassKeys.isEmpty() || myKey in it.audienceClassKeys }
                                    .sortedByDescending { it.expiresAtMillis }
                            }.onStart { emit(emptyList()) }
                             .catch { emit(emptyList()) }
                        )
                    }
                }
            }
    }

    /**
     * LIVE set of story ids this user has already viewed — a real-time
     * snapshot listener over the `viewers` collection-group filtered by
     * userId. Unlike a one-shot hydrate, this keeps the ring's seen/unseen
     * state correct across SEPARATE ViewModel instances: when the full-screen
     * viewer writes a viewer doc, the Dashboard ring's VM (also observing
     * this) sees it within ~100ms and greys the ring immediately.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSeenStoryIds(): Flow<Set<String>> =
        tokenManager.user
            .map { it.userId }
            .distinctUntilChanged()
            .flatMapLatest { userId ->
                if (userId.isBlank()) flowOf(emptySet())
                else callbackFlow {
                    val reg = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collectionGroup(VIEWERS_SUBCOLLECTION)
                        .whereEqualTo("userId", userId)
                        .addSnapshotListener { snap, err ->
                            if (err != null || snap == null) { trySend(emptySet()); return@addSnapshotListener }
                            trySend(snap.documents.mapNotNull { it.reference.parent.parent?.id }.toSet())
                        }
                    awaitClose { reg.remove() }
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
            val nowMs = System.currentTimeMillis()
            // Single-field query (schoolId) — no composite index needed;
            // expiry/status filter + sort client-side.
            val stories = firestoreService.queryDocumentsAs<StoryDoc>(COLLECTION) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
            }
            Result.success(
                stories.filter { it.status == "active" && it.expiresAtMillis > nowMs }
                    .sortedByDescending { it.expiresAtMillis }
            )
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
        val user = tokenManager.user.firstOrNull()
        val userId = user?.userId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))
        // Denormalise the viewer's name so the teacher/admin "who saw this"
        // list can show a real name without a per-viewer parent-doc lookup.
        val userName = user.name.orEmpty()
        return try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val storyRef  = firestore.collection(COLLECTION).document(storyId)
            val viewerRef = storyRef.collection(VIEWERS_SUBCOLLECTION).document(userId)

            firestore.runTransaction { tx ->
                val existing = tx.get(viewerRef)
                if (existing.exists()) {
                    // Already counted — don't inflate viewCount. But
                    // backfill the viewer's name if an earlier (pre-
                    // denormalisation) view left it blank, so the
                    // teacher's "who saw this" list can show a real name.
                    if (existing.getString("userName").isNullOrBlank() && userName.isNotBlank()) {
                        tx.update(viewerRef, "userName", userName)
                    }
                    return@runTransaction null
                }
                // First view — write viewer marker + bump counter atomically.
                tx.set(viewerRef, hashMapOf<String, Any?>(
                    "viewedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "userId"   to userId,
                    "userName" to userName
                ))
                tx.update(storyRef, "viewCount", FieldValue.increment(1))
                null
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set / change / clear the current parent's emoji reaction on a
     * story. One reaction per parent (toggle): tapping the SAME emoji
     * again clears it. Kept consistent with [markAsViewed] via a
     * transaction so the per-user `reactions/{userId}` doc and the
     * denormalised `reactionCounts` map never drift:
     *
     *   - no existing reaction → set doc, reactionCounts[emoji] += 1
     *   - existing == emoji     → delete doc, reactionCounts[emoji] -= 1
     *   - existing != emoji     → set doc, old -= 1, new += 1
     *
     * The story doc's reactionCounts is what teacher/admin read for
     * aggregate analytics.
     */
    suspend fun reactToStory(storyId: String, emoji: String): Result<Unit> {
        val user = tokenManager.user.firstOrNull()
        val userId = user?.userId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))
        val userName = user.name.orEmpty()
        return try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val storyRef = firestore.collection(COLLECTION).document(storyId)
            val reactionRef = storyRef.collection(REACTIONS_SUBCOLLECTION).document(userId)

            firestore.runTransaction { tx ->
                val existing = tx.get(reactionRef)
                val prevEmoji = if (existing.exists()) existing.getString("emoji").orEmpty() else ""

                when {
                    prevEmoji.isBlank() -> {
                        tx.set(reactionRef, hashMapOf<String, Any?>(
                            "emoji"     to emoji,
                            "userId"    to userId,
                            "userName"  to userName,
                            "reactedAt" to FieldValue.serverTimestamp()
                        ))
                        tx.update(storyRef, com.google.firebase.firestore.FieldPath.of("reactionCounts", emoji), FieldValue.increment(1))
                    }
                    prevEmoji == emoji -> {
                        // Toggle off.
                        tx.delete(reactionRef)
                        tx.update(storyRef, com.google.firebase.firestore.FieldPath.of("reactionCounts", emoji), FieldValue.increment(-1))
                    }
                    else -> {
                        // Change reaction.
                        tx.set(reactionRef, hashMapOf<String, Any?>(
                            "emoji"     to emoji,
                            "userId"    to userId,
                            "userName"  to userName,
                            "reactedAt" to FieldValue.serverTimestamp()
                        ))
                        tx.update(storyRef, com.google.firebase.firestore.FieldPath.of("reactionCounts", prevEmoji), FieldValue.increment(-1))
                        tx.update(storyRef, com.google.firebase.firestore.FieldPath.of("reactionCounts", emoji), FieldValue.increment(1))
                    }
                }
                null
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
