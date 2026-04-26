package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.Event
import com.schoolsync.parent.data.model.firestore.EventDoc
import com.schoolsync.parent.util.Constants
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
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                doc = firestoreService.getDocumentAs<EventDoc>("events", "${schoolId}_$eventId")
            }
            if (doc == null) {
                doc = firestoreService.getDocumentAs<EventDoc>("events", eventId)
            }
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
