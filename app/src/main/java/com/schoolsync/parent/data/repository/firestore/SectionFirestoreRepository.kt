package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.SectionDoc
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
 * Repository for reading section data from Firestore.
 * Collection: sections/{schoolCode}_{session}_{class}_{section}
 */
@Singleton
class SectionFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch a specific section document.
     * Document ID format: {schoolCode}_{session}_{className}_{section}
     */
    suspend fun getSection(className: String, section: String): Result<SectionDoc> {
        val docId = buildSectionDocId(className, section)
            ?: return Result.failure(Exception("School code or session not available"))

        return try {
            val doc = firestoreService.getDocumentAs<SectionDoc>(
                Constants.Firestore.SECTIONS,
                docId
            )
            if (doc != null) {
                Result.success(doc)
            } else {
                Result.failure(Exception("Section not found: $docId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all sections in the current school for the active session.
     * Uses compound query: schoolId == schoolCode AND session == session.
     */
    suspend fun getSectionsBySchool(): Result<List<SectionDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val sections = firestoreService.queryDocumentsAs<SectionDoc>(
                Constants.Firestore.SECTIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
            }
            Result.success(sections)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all sections for a specific class within the current school and session.
     * Uses compound query: schoolId == schoolCode AND session == session AND className == [className].
     */
    suspend fun getSectionsByClass(className: String): Result<List<SectionDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val sections = firestoreService.queryDocumentsAs<SectionDoc>(
                Constants.Firestore.SECTIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("className", className)
            }
            Result.success(sections)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe a section document for real-time changes.
     * Reacts to user profile changes (school code / session) via [flatMapLatest],
     * and emits `null` when the document does not exist or identifiers are unavailable.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSection(className: String, section: String): Flow<SectionDoc?> {
        return tokenManager.user
            .map { user ->
                val code = user.schoolCode.takeIf { it.isNotBlank() }
                val sess = user.session.takeIf { it.isNotBlank() }
                if (code != null && sess != null) "${code}_${sess}_${className}_${section}" else null
            }
            .flatMapLatest { docId ->
                if (docId == null) {
                    flowOf(null)
                } else {
                    firestoreService.observeDocumentAs<SectionDoc>(
                        Constants.Firestore.SECTIONS,
                        docId
                    )
                }
            }
    }

    /**
     * Build the composite document ID: {schoolCode}_{session}_{className}_{section}.
     * Returns null if school code or session is unavailable.
     */
    private suspend fun buildSectionDocId(className: String, section: String): String? {
        val schoolCode = getSchoolCode() ?: return null
        val session = getSession() ?: return null
        return "${schoolCode}_${session}_${className}_${section}"
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.user.firstOrNull()?.session?.takeIf { it.isNotBlank() }
    }
}
