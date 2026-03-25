package com.schoolsync.parent.data.repository

import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.Event
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTDB-based event repository (legacy fallback).
 * Primary event data now lives in Firestore via [EventFirestoreRepository].
 * This stub returns empty data so the build passes while RTDB paths are phased out.
 */
@Singleton
class EventRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch upcoming/active events, optionally including media.
     */
    suspend fun getEvents(withMedia: Boolean = false): List<Event> {
        // TODO: wire to RTDB if legacy path is still needed; Firestore is primary
        return emptyList()
    }
}
