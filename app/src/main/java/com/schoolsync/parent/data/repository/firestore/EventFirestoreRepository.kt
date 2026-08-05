package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.Event
import com.schoolsync.parent.data.model.firestore.EventDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading school events from Firestore.
 *
 * Collection: `events`
 * Query: schoolId, ordered by startDate descending.
 */
@Singleton
class EventFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch all events for the school.
     *
     * Naming note: the Firestore field is `schoolId` (canonical) and the
     * token exposes it under `schoolCode` (legacy name). They hold the same
     * value for current schools. We alias it locally as `schoolId` so every
     * Firestore query in this file uses a single, consistent variable and
     * future readers don't have to track the naming drift.
     */
    suspend fun getEvents(): Result<List<EventDoc>> {
        val schoolId = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School id not available"))

        return try {
            val events = firestoreService.queryDocumentsAs<EventDoc>(
                "events"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .orderBy("startDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(200)
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real-time variant of [getEvents]: emits a fresh [Result] every time the
     * `events` collection changes, so newly published events appear on the
     * Events screen without a manual refresh.
     *
     * Preserves the exact query of [getEvents] (schoolId filter, startDate DESC,
     * limit 200) and the same Result contract — a listener error (undeployed
     * index / PERMISSION_DENIED) surfaces as `Result.failure`, distinct from an
     * empty-but-successful snapshot. The registration is removed on cancellation
     * via [awaitClose].
     */
    fun observeEvents(): Flow<Result<List<EventDoc>>> = callbackFlow {
        val schoolId = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
        if (schoolId == null) {
            trySend(Result.failure(Exception("School id not available")))
            close()
            return@callbackFlow
        }

        val registration = firestoreService.firestore.collection("events")
            .whereEqualTo("schoolId", schoolId)
            .orderBy("startDate", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val docs = try {
                        snapshot.toObjects(EventDoc::class.java)
                    } catch (e: Exception) {
                        trySend(Result.failure(e))
                        return@addSnapshotListener
                    }
                    trySend(Result.success(docs))
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Fetch a single event by ID. Admin writes events with Firestore docId
     * `{schoolId}_{eventId}` so we try that first; fall back to the plain
     * eventId for any legacy docs written without the prefix.
     */
    suspend fun getEvent(eventId: String): Result<EventDoc?> {
        return try {
            val schoolId = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            var doc: EventDoc? = null
            if (schoolId != null) {
                // Prefixed docId `{schoolId}_{eventId}` is already school-scoped.
                doc = firestoreService.getDocumentAs<EventDoc>("events", "${schoolId}_$eventId")
            }
            if (doc == null) {
                // Bare-eventId fallback for legacy docs. This path is reachable
                // via a crafted deep-link, so re-verify the doc's schoolId
                // matches the signed-in user's school; drop it on mismatch to
                // prevent cross-school event leakage.
                val fallback = firestoreService.getDocumentAs<EventDoc>("events", eventId)
                doc = fallback?.takeIf { schoolId != null && it.schoolId == schoolId }
            }
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
