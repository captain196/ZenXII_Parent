package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.schoolsync.parent.data.firebase.FirestoreService
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
 * Notification badges + user-presence repository.
 *
 * Phase 5: migrated off Firebase Realtime Database onto Firestore. The
 * class name is retained for binary-compat with the DI module; the data
 * source is now pure Firestore. Two collections replace the old RTDB
 * subtrees:
 *
 *   RTDB  NotifBadge/{userId}     →  Firestore  notifBadges/{userId}
 *   RTDB  Presence/{userId}        →  Firestore  presence/{userId}
 *
 * Document shapes intentionally mirror the NotifBadgeRtdb / PresenceRtdb
 * POJOs one-for-one so the domain layer needs zero changes. A one-time
 * migration (scripts/migrate_rtdb_to_firestore.js --mapping=notifBadges
 * --mapping=presence) seeds the Firestore collections; after that the
 * RTDB nodes are no longer read or written.
 */
@Singleton
class ChatRtdbRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager,
) {

    companion object {
        private const val COL_NOTIF_BADGES = "notifBadges"
        private const val COL_PRESENCE     = "presence"
    }

    // ── Notification Badge ──────────────────────────────────────────────────

    suspend fun getNotifBadge(): Result<NotifBadgeRtdb> {
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))
        return try {
            val snap: DocumentSnapshot? = firestoreService.getDocument(COL_NOTIF_BADGES, userId)
            val badge = snap?.takeIf { it.exists() }
                ?.toObject(NotifBadgeRtdb::class.java)
                ?: NotifBadgeRtdb()
            Result.success(badge)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNotifBadge(): Flow<NotifBadgeRtdb?> {
        return tokenManager.user
            .map { user -> user.userId.takeIf { it.isNotBlank() } }
            .flatMapLatest { userId ->
                if (userId == null) flowOf(null)
                else firestoreService.observeDocumentAs<NotifBadgeRtdb>(COL_NOTIF_BADGES, userId)
            }
    }

    // ── Presence ────────────────────────────────────────────────────────────

    suspend fun updatePresence(online: Boolean): Result<Unit> {
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))
        return try {
            firestoreService.setDocument(
                COL_PRESENCE,
                userId,
                mapOf(
                    "userId"   to userId,
                    "online"   to online,
                    "lastSeen" to System.currentTimeMillis(),
                ),
                merge = true,
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPresence(userId: String): Result<PresenceRtdb?> {
        return try {
            val snap = firestoreService.getDocument(COL_PRESENCE, userId)
            val obj = snap?.takeIf { it.exists() }?.toObject(PresenceRtdb::class.java)
            Result.success(obj)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePresence(userId: String): Flow<PresenceRtdb?> =
        firestoreService.observeDocumentAs<PresenceRtdb>(COL_PRESENCE, userId)

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getUserId(): String? =
        tokenManager.user.firstOrNull()?.userId?.takeIf { it.isNotBlank() }
}
