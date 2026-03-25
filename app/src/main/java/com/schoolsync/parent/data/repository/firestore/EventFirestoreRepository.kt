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
     */
    suspend fun getEvents(): Result<List<EventDoc>> {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val events = firestoreService.queryDocumentsAs<EventDoc>(
                "events"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .orderBy("startDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch a single event by ID.
     */
    suspend fun getEvent(eventId: String): Result<EventDoc?> {
        return try {
            val doc = firestoreService.getDocumentAs<EventDoc>("events", eventId)
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
