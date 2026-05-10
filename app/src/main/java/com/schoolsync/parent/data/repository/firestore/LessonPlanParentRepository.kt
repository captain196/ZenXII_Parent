package com.schoolsync.parent.data.repository.firestore

import android.util.Log
import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.LessonPlanDoc
import com.schoolsync.parent.data.model.firestore.SubjectPlanProgressDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parent-side reads for the Phase 6/7/8 Academic Planner.
 *
 * Reads only — admin and teacher apps are the writers. Mirrors the same
 * direct-Firestore pattern the parent app already uses for homework,
 * timetable, attendance, etc. Auth + scoping is enforced by Firebase
 * Security Rules.
 */
@Singleton
class LessonPlanParentRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Today's lesson plans for the child's class — sorted by periodIndex.
     * Returns empty list when no plans exist (or on error — best-effort read).
     */
    suspend fun getDailyLessons(
        className: String,
        section: String,
        date: String
    ): Result<List<LessonPlanDoc>> {
        if (className.isBlank() || section.isBlank() || date.isBlank()) {
            return Result.success(emptyList())
        }
        // IMPORTANT: use `schoolId` (canonical, e.g. "SCH_D94FE8F7AD"), NOT
        // `schoolCode` (which is the parent's numeric login code like
        // "10004"). The lessonPlans documents store schoolId, AND the
        // Firebase Auth custom claim is `school_id` matching it — so
        // queries must filter on the same value or Firestore rules
        // reject the entire query (PERMISSION_DENIED).
        val user = tokenManager.user.firstOrNull()
        val schoolId = user?.schoolId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School ID not available"))
        val session = user.session.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("Session not available"))

        val classSection = canonicalClassSection(className, section)

        return try {
            val lessons = firestoreService.queryDocumentsAs<LessonPlanDoc>(
                Constants.Firestore.LESSON_PLANS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("session", session)
                    .whereEqualTo("classSection", classSection)
                    .whereEqualTo("date", date)
                    .orderBy("periodIndex", Query.Direction.ASCENDING)
            }
            Result.success(lessons)
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            // Composite-index missing — fall back to single-field equality
            // + client-side filter (same pattern as HomeworkFirestoreRepository).
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                Log.w(TAG, "lessonPlans composite index missing — fallback to client-side filter")
                fallbackDailyLessons(schoolId, session, classSection, date)
            } else {
                Log.e(TAG, "getDailyLessons failed", e)
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDailyLessons failed", e)
            Result.failure(e)
        }
    }

    private suspend fun fallbackDailyLessons(
        schoolId: String, session: String, classSection: String, date: String
    ): Result<List<LessonPlanDoc>> = try {
        // Even the fallback must include schoolId in the where clause for
        // rules to authorize the query. classSection alone won't pass.
        val all = firestoreService.queryDocumentsAs<LessonPlanDoc>(
            Constants.Firestore.LESSON_PLANS
        ) { ref ->
            ref.whereEqualTo("schoolId", schoolId)
                .whereEqualTo("classSection", classSection)
        }
        val filtered = all
            .filter { it.session == session && it.date == date }
            .sortedBy { it.periodIndex }
        Result.success(filtered)
    } catch (e: Exception) {
        Log.e(TAG, "fallbackDailyLessons failed", e)
        Result.failure(e)
    }

    /**
     * Per-subject completion counters for the child's class. Used to draw
     * the progress strip on top of the daily lesson view.
     */
    suspend fun getSubjectProgress(
        className: String,
        section: String
    ): Result<List<SubjectPlanProgressDoc>> {
        if (className.isBlank() || section.isBlank()) return Result.success(emptyList())
        val user = tokenManager.user.firstOrNull()
        val schoolId = user?.schoolId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School ID not available"))
        val session = user.session.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("Session not available"))

        val classSection = canonicalClassSection(className, section)

        return try {
            val rows = firestoreService.queryDocumentsAs<SubjectPlanProgressDoc>(
                Constants.Firestore.SUBJECT_PLAN_PROGRESS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("session", session)
                    .whereEqualTo("classSection", classSection)
            }
            Result.success(rows.sortedBy { it.subject })
        } catch (e: Exception) {
            Log.e(TAG, "getSubjectProgress failed", e)
            // Best-effort — empty list is acceptable for the progress strip.
            Result.success(emptyList())
        }
    }

    private fun canonicalClassSection(className: String, section: String): String {
        val cls = Constants.Firebase.classKey(className)
        val sec = Constants.Firebase.sectionKey(section)
        return "$cls/$sec"
    }

    companion object {
        private const val TAG = "LessonPlanParentRepo"
    }
}
