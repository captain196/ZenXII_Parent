package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.RecruitmentDoc
import com.schoolsync.parent.data.model.firestore.TrainingDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for HR-related data visible to parents (minimal, read-only).
 * Exposes publicly-available training sessions and recruitment openings.
 *
 * Collections used:
 * - trainingSessions: school training/workshop events
 * - recruitmentOpenings: open job positions at the school
 */
@Singleton
class HRFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch publicly visible training sessions for the school.
     * Query: schoolId + visibility includes "parent" or "public"
     */
    suspend fun getTrainingSessions(): Result<List<TrainingDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val sessions = firestoreService.queryDocumentsAs<TrainingDoc>(
                Constants.Firestore.TRAINING
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "published")
                    .orderBy("startDate", Query.Direction.DESCENDING)
            }
            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch open recruitment positions at the school.
     * Query: schoolId + status = "open"
     */
    suspend fun getRecruitmentOpenings(): Result<List<RecruitmentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val openings = firestoreService.queryDocumentsAs<RecruitmentDoc>(
                Constants.Firestore.RECRUITMENT
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "open")
                    .orderBy("postedAt", Query.Direction.DESCENDING)
            }
            Result.success(openings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }
}
