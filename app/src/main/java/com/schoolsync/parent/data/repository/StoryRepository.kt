package com.schoolsync.parent.data.repository

import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.TeacherStoryGroup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTDB-based story repository (legacy fallback).
 * Primary story data now lives in Firestore via [StoryFirestoreRepository].
 * This stub returns empty data so the build passes while RTDB paths are phased out.
 */
@Singleton
class StoryRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch all active (non-expired) teacher stories grouped by teacher.
     */
    suspend fun getAllActiveStories(): List<TeacherStoryGroup> {
        // TODO: wire to RTDB if legacy path is still needed; Firestore is primary
        return emptyList()
    }

    /**
     * Mark a specific story as viewed by the current parent.
     */
    suspend fun markAsViewed(storyId: String) {
        // TODO: wire to RTDB if legacy path is still needed; Firestore is primary
    }
}
