package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.StoryDoc
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading stories from Firestore (parent-side, read-only).
 * Collection: `stories`
 */
@Singleton
class StoryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch all active (non-expired) stories for the school.
     */
    suspend fun getActiveStories(): Result<List<StoryDoc>> {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val now = System.currentTimeMillis()
            val stories = firestoreService.queryDocumentsAs<StoryDoc>(
                "stories"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereGreaterThan("expiresAt", now)
                    .orderBy("expiresAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            }
            Result.success(stories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
