package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.rtdb.NotifBadgeRtdb
import com.schoolsync.parent.data.model.rtdb.PresenceRtdb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for RTDB-backed communication features: notification badges and presence.
 *
 * RTDB paths used:
 * - NotifBadge/{userId}: per-user badge counts by category
 * - Presence/{userId}: online/offline status and last-seen timestamp
 */
@Singleton
class ChatRtdbRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    // ── Notification Badge ──────────────────────────────────────────────────

    /**
     * One-shot read of notification badge counts for the current user.
     */
    suspend fun getNotifBadge(): Result<NotifBadgeRtdb> {
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))

        return try {
            val snapshot = firebaseService.readValue("NotifBadge/$userId")
            if (snapshot != null && snapshot.exists()) {
                val badge = snapshot.getValue(NotifBadgeRtdb::class.java)
                    ?: NotifBadgeRtdb()
                Result.success(badge)
            } else {
                Result.success(NotifBadgeRtdb())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe notification badge counts in real time.
     * Reacts to user profile changes (userId) via [flatMapLatest].
     * Emits null when no badge data exists or user is not logged in.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNotifBadge(): Flow<NotifBadgeRtdb?> {
        return tokenManager.user
            .map { user -> user.userId.takeIf { it.isNotBlank() } }
            .flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(null)
                } else {
                    firebaseService.observeValue("NotifBadge/$userId")
                        .map { snapshot ->
                            snapshot?.getValue(NotifBadgeRtdb::class.java)
                        }
                }
            }
    }

    // ── Presence ────────────────────────────────────────────────────────────

    /**
     * Update the current user's online/offline presence.
     */
    suspend fun updatePresence(online: Boolean): Result<Unit> {
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))

        return try {
            val data = mapOf(
                "online" to online,
                "lastSeen" to System.currentTimeMillis()
            )
            firebaseService.writeValue("Presence/$userId", data)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * One-shot read of a user's presence status.
     */
    suspend fun getPresence(userId: String): Result<PresenceRtdb?> {
        return try {
            val snapshot = firebaseService.readValue("Presence/$userId")
            if (snapshot != null && snapshot.exists()) {
                Result.success(snapshot.getValue(PresenceRtdb::class.java))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe a user's presence in real time.
     */
    fun observePresence(userId: String): Flow<PresenceRtdb?> {
        return firebaseService.observeValue("Presence/$userId")
            .map { snapshot ->
                snapshot?.getValue(PresenceRtdb::class.java)
            }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getUserId(): String? {
        return tokenManager.user.firstOrNull()?.userId?.takeIf { it.isNotBlank() }
    }
}
