package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.SchoolDoc
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
 * Repository for reading school data from Firestore.
 * Collection: schools/{schoolCode}
 */
@Singleton
class SchoolFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the current school document from Firestore.
     * Uses the school code stored in TokenManager to identify the document.
     */
    suspend fun getSchool(): Result<SchoolDoc> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val doc = firestoreService.getDocumentAs<SchoolDoc>(
                Constants.Firestore.SCHOOLS,
                schoolCode
            )
            if (doc != null) {
                Result.success(doc)
            } else {
                Result.failure(Exception("School document not found for code: $schoolCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the school document as a raw map for flexible field access.
     * Useful when consuming fields not yet modelled in [SchoolDoc].
     */
    suspend fun getSchoolConfig(): Map<String, Any?>? {
        val schoolCode = getSchoolCode() ?: return null

        return try {
            firestoreService.getDocumentMap(Constants.Firestore.SCHOOLS, schoolCode)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Observe the school document for real-time updates.
     * Reacts to school code changes in the user profile via [flatMapLatest],
     * and emits `null` when the document does not exist or the school code is unavailable.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSchool(): Flow<SchoolDoc?> {
        return tokenManager.user
            .map { user -> user.schoolCode.takeIf { it.isNotBlank() } }
            .flatMapLatest { schoolCode ->
                if (schoolCode == null) {
                    flowOf(null)
                } else {
                    firestoreService.observeDocumentAs<SchoolDoc>(
                        Constants.Firestore.SCHOOLS,
                        schoolCode
                    )
                }
            }
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }
}
