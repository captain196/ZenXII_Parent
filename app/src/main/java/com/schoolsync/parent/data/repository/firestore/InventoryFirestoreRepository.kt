package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.SurveyDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for inventory-adjacent parent features: surveys and school events.
 *
 * Collections used:
 * - surveys: school surveys targeting parents
 * - surveyResponses: individual survey responses
 * - events: school events (from Firestore, if migrated)
 */
@Singleton
class InventoryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch active surveys targeting parents for the current school.
     * Query: schoolId + status = "active" + targetRoles contains "parent"
     */
    suspend fun getSurveys(): Result<List<SurveyDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val surveys = firestoreService.queryDocumentsAs<SurveyDoc>(
                Constants.Firestore.SURVEYS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("status", "active")
                    .whereArrayContains("targetRoles", "parent")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            }
            Result.success(surveys)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit a response to a survey.
     * Document ID pattern: {surveyId}_{userId} for idempotent writes.
     */
    suspend fun submitSurveyResponse(
        surveyId: String,
        answers: Map<String, String>
    ): Result<Unit> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val userId = getUserId()
            ?: return Result.failure(Exception("User ID not available"))
        val userName = getUserName()

        val docId = "${surveyId}_${userId}"
        val data = mapOf(
            "schoolId" to schoolCode,
            "surveyId" to surveyId,
            "respondentId" to userId,
            "respondentName" to userName,
            "respondentRole" to "parent",
            "answers" to answers,
            "submittedAt" to FieldValue.serverTimestamp()
        )

        return try {
            firestoreService.setDocument(
                Constants.Firestore.SURVEY_RESPONSES,
                docId,
                data
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch school events from Firestore events collection.
     * Returns raw maps as events may have flexible schemas.
     * Returns an empty list if the collection is not yet migrated.
     */
    suspend fun getEvents(): Result<List<Map<String, Any?>>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val snapshot = firestoreService.queryDocuments(
                Constants.Firestore.EVENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .orderBy("eventDate", Query.Direction.ASCENDING)
                    .limit(50)
            }
            val events = snapshot.documents.mapNotNull { doc ->
                doc.data?.plus("id" to doc.id)
            }
            Result.success(events)
        } catch (e: Exception) {
            // Collection may not exist yet; return empty gracefully
            Result.success(emptyList())
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
    }

    private suspend fun getUserId(): String? {
        return tokenManager.user.firstOrNull()?.userId?.takeIf { it.isNotBlank() }
    }

    private suspend fun getUserName(): String {
        return tokenManager.user.firstOrNull()?.name ?: ""
    }
}
