package com.schoolsync.parent.data.repository

import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.Notice
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTDB-based notice repository (legacy fallback).
 * Primary notice/circular data now lives in Firestore via [CommunicationFirestoreRepository].
 * This stub returns empty data so the build passes while RTDB paths are phased out.
 */
@Singleton
class NoticeRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch recent notices, optionally limited.
     */
    suspend fun getNotices(limit: Int = 10): List<Notice> {
        // TODO: wire to RTDB if legacy path is still needed; Firestore is primary
        return emptyList()
    }
}
