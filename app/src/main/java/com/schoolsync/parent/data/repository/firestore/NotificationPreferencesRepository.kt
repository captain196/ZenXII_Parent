package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.NotificationPreferencesDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F10 (2026-07-07) — parent-facing notification preferences.
 *
 * Doc-id shape: {schoolId}_{userId}. Firestore security rule enforces
 * owner-only read/write; server-side enforcement in PHP
 * Transport_notifier::_filterByPreferences honors mutedCategories at
 * fanout time UNLESS the category is in the safety-exempt list
 * (safety notices are non-suppressible per operator directive).
 *
 * The UI shouldn't allow the parent to mute a safety-exempt category;
 * this repo's `setMutedCategories` defensively filters them out too,
 * so even a malformed UI can't push a safety-category mute through.
 */
@Singleton
class NotificationPreferencesRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    suspend fun getPreferences(): Result<NotificationPreferencesDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val userId = getUserId()
            ?: return Result.failure(Exception("User id not available"))
        val docId = "${schoolCode}_${userId}"
        return try {
            val doc = firestoreService.getDocumentAs<NotificationPreferencesDoc>(
                Constants.Firestore.NOTIFICATION_PREFERENCES, docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Persist the mute list. Safety-exempt categories are DEFENSIVELY
     * stripped client-side (server also enforces via
     * Transport_notifier::_filterByPreferences safety-exempt bypass).
     */
    suspend fun setMutedCategories(categories: List<String>): Result<Unit> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val userId = getUserId()
            ?: return Result.failure(Exception("User id not available"))
        val docId = "${schoolCode}_${userId}"
        // Filter out safety-exempt entries (client-side defence; server
        // authoritative)
        val cleaned = categories.distinct().filter { it !in NotificationPreferencesDoc.SAFETY_EXEMPT }
        val data = mapOf(
            "schoolId" to schoolCode,
            "userId" to userId,
            "mutedCategories" to cleaned,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            firestoreService.setDocument(
                Constants.Firestore.NOTIFICATION_PREFERENCES, docId, data
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }

    private suspend fun getUserId(): String? {
        return tokenManager.user.firstOrNull()?.userId?.takeIf { it.isNotBlank() }
    }
}
