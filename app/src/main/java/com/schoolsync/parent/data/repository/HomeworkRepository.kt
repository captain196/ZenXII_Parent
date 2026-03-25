package com.schoolsync.parent.data.repository

import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.Homework
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTDB-based homework repository (legacy fallback).
 * Primary homework data now lives in Firestore via [HomeworkFirestoreRepository].
 * This stub returns empty data so the build passes while RTDB paths are phased out.
 */
@Singleton
class HomeworkRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Observe all homework for the current student as a Flow.
     */
    fun observeAllHomework(): Flow<List<Homework>> {
        // TODO: wire to RTDB if legacy path is still needed; Firestore is primary
        return flowOf(emptyList())
    }
}
