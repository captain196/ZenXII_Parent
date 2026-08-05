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
 *
 * FIX 4: QUARANTINED dead code. [observeAllHomework] always returns an empty
 * flow and has no live caller. It is NOT deleted only because AppModule still
 * @Provides this type; removing it would touch DI wiring. Do not build new
 * features on this — use [HomeworkFirestoreRepository] (the live Firestore
 * path) instead. Safe to delete this class together with its AppModule
 * provider once the DI graph is confirmed to have no consumers.
 */
@Deprecated(
    message = "Dead RTDB stub — always empty. Use HomeworkFirestoreRepository.",
    level = DeprecationLevel.WARNING
)
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
