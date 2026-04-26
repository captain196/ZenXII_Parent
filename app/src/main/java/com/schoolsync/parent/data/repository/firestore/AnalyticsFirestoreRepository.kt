package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.DashboardDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for parent-side analytics dashboards.
 *
 * Collections used:
 * - dashboards: pre-computed dashboard summaries per user
 *   Doc ID pattern: {schoolId}_parent_{parentId} or {schoolId}_student_{studentId}
 */
@Singleton
class AnalyticsFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the pre-computed dashboard document for the current parent.
     * Document ID pattern: {schoolId}_parent_{parentId}
     */
    suspend fun getDashboard(): Result<DashboardDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))

        return try {
            val docId = "${schoolCode}_parent_${userId}"
            val doc = firestoreService.getDocumentAs<DashboardDoc>(
                Constants.Firestore.DASHBOARDS,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe the dashboard document in real time.
     * Reacts to user profile changes (school code, user ID) via [flatMapLatest].
     * Emits null when identifiers are unavailable or the document does not exist.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDashboard(): Flow<DashboardDoc?> {
        return tokenManager.user
            .map { user ->
                val sc = user.schoolCode.takeIf { it.isNotBlank() }
                val uid = user.userId.takeIf { it.isNotBlank() }
                if (sc != null && uid != null) sc to uid else null
            }
            .flatMapLatest { pair ->
                if (pair == null) {
                    flowOf(null)
                } else {
                    val (schoolCode, userId) = pair
                    val docId = "${schoolCode}_parent_${userId}"
                    firestoreService.observeDocumentAs<DashboardDoc>(
                        Constants.Firestore.DASHBOARDS,
                        docId
                    )
                }
            }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }

    private suspend fun getUserId(): String? {
        return tokenManager.user.firstOrNull()?.userId?.takeIf { it.isNotBlank() }
    }
}
