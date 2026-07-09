package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.ExamDoc
import com.schoolsync.parent.data.model.firestore.ExamScheduleDoc
import com.schoolsync.parent.data.model.firestore.ResultDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading exam and result data from Firestore (parent-side, read-only).
 *
 * Collections used:
 * - exams: exam definitions with schedule metadata
 * - examSchedule: per-class-section subject-wise schedules
 * - results: computed per-student result documents
 */
@Singleton
class ExamFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch all published exams for the current school and session,
     * ordered by start date ascending.
     */
    suspend fun getAvailableExams(): Result<List<ExamDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val exams = firestoreService.queryDocumentsAs<ExamDoc>(
                Constants.Firestore.EXAMS
            ) { ref ->
                // HIGH-1: the hardened `exams` rule forbids non-staff from
                // reading Draft exams, and Firestore rules are NOT filters — a
                // single unreadable Draft rejects the WHOLE query. Restrict to
                // parent-visible statuses so the list survives once Drafts exist.
                // "Completed" is included so past exams (which carry results)
                // still surface. Covered by the composite index
                // schoolId + session + status + startDate (admin firebase-rules).
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereIn("status", ExamDoc.PARENT_VISIBLE_STATUSES)
                    .orderBy("startDate")
            }
            Result.success(exams)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the exam schedule for a specific exam, class, and section.
     * Doc ID pattern: `{schoolId}_{examId}_{className}_{section}`
     */
    suspend fun getExamSchedule(
        examId: String,
        className: String,
        section: String
    ): Result<ExamScheduleDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        val docId = "${schoolCode}_${examId}_${Constants.Firebase.classKey(className)}_${Constants.Firebase.sectionKey(section)}"

        return try {
            val doc = firestoreService.getDocumentAs<ExamScheduleDoc>(
                Constants.Firestore.EXAM_SCHEDULE,
                docId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the result for a specific exam and student.
     * Query: schoolId + examId + studentId.
     */
    suspend fun getResult(
        examId: String,
        studentId: String
    ): Result<ResultDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val results = firestoreService.queryDocumentsAs<ResultDoc>(
                Constants.Firestore.RESULTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("examId", examId)
                    .whereEqualTo("studentId", studentId)
                    .limit(1)
            }
            Result.success(results.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all results for a student in the current session (across all exams).
     * Query: schoolId + session + studentId.
     */
    suspend fun getAllResults(studentId: String): Result<List<ResultDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = getSession()
            ?: return Result.failure(Exception("Session not available"))

        return try {
            val results = firestoreService.queryDocumentsAs<ResultDoc>(
                Constants.Firestore.RESULTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("studentId", studentId)
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }

    private suspend fun getSession(): String? {
        return tokenManager.user.firstOrNull()?.session?.takeIf { it.isNotBlank() }
    }
}
