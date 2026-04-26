package com.schoolsync.parent.data.repository.firestore

import android.util.Log
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.StaffDoc
import com.schoolsync.parent.data.model.firestore.SubjectAssignmentDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the canonical `subjectAssignments` collection for the parent's child's
 * class/section, then resolves each `teacherId` to a full `StaffDoc` so the UI
 * can show name + photo + phone + role.
 *
 * This is the wiring contract between the admin panel and the Parent app:
 *   admin writes Subject_assignment_service → Firestore subjectAssignments
 *   Parent app reads here, joined with the staff doc by teacherId.
 *
 * No RTDB fallback — `subjectAssignments` is Firestore-only by design.
 */
@Singleton
class MyTeachersFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {
    companion object { private const val TAG = "MyTeachersRepo" }

    /**
     * One row in the "My Teachers" UI: a single subject assignment joined
     * with the resolved staff profile.
     */
    data class TeacherEntry(
        val assignment: SubjectAssignmentDoc,
        val staff: StaffDoc?
    )

    /**
     * Returns every subject assignment for the parent's child's current
     * class/section, joined with the resolved staff doc.
     *
     * Class-wide rows (section == "_ALL_") are included alongside section-
     * specific rows because they apply to every section in the class.
     */
    suspend fun getMyTeachers(): Result<List<TeacherEntry>> {
        return try {
            val user = tokenManager.user.firstOrNull()
                ?: return Result.failure(Exception("Not logged in"))

            val schoolId = user.schoolId.takeIf { it.isNotBlank() }
                ?: return Result.failure(Exception("schoolId missing on user profile"))
            val session  = user.session.takeIf { it.isNotBlank() }
                ?: return Result.failure(Exception("session missing on user profile"))
            val className = user.className.takeIf { it.isNotBlank() }
                ?: return Result.failure(Exception("className missing on user profile"))
            val section   = user.section.takeIf { it.isNotBlank() }
                ?: return Result.failure(Exception("section missing on user profile"))

            val ck = Constants.Firebase.classKey(className)
            val sk = Constants.Firebase.sectionKey(section)

            Log.d(TAG, "getMyTeachers: schoolId=$schoolId session=$session class=$ck section=$sk")

            // Two queries: section-specific + class-wide ("_ALL_").
            // Cannot use `whereIn` here because Firestore SDK requires the
            // same field for compound queries — easier to fire two reads.
            val sectionSpecific = firestoreService.queryDocumentsAs<SubjectAssignmentDoc>(
                Constants.Firestore.SUBJECT_ASSIGNMENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("session", session)
                    .whereEqualTo("className", ck)
                    .whereEqualTo("section", sk)
            }

            val classWide = firestoreService.queryDocumentsAs<SubjectAssignmentDoc>(
                Constants.Firestore.SUBJECT_ASSIGNMENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("session", session)
                    .whereEqualTo("className", ck)
                    .whereEqualTo("section", "_ALL_")
            }

            val assignments = (sectionSpecific + classWide)
                .distinctBy { it.id }
            Log.d(TAG, "getMyTeachers: ${assignments.size} assignments")

            // Resolve each unique teacherId to a StaffDoc — cache by id so
            // we don't re-fetch the same teacher when they teach multiple
            // subjects to the same class.
            val staffCache = mutableMapOf<String, StaffDoc?>()
            val entries = assignments.map { a ->
                val staff = staffCache.getOrPut(a.teacherId) {
                    if (a.teacherId.isBlank()) null
                    else loadStaff(schoolId, a.teacherId)
                }
                TeacherEntry(assignment = a, staff = staff)
            }

            // Class teacher first, then by subject name
            val sorted = entries.sortedWith(
                compareByDescending<TeacherEntry> { it.assignment.isClassTeacher }
                    .thenBy { it.assignment.subjectName.lowercase() }
            )
            Result.success(sorted)
        } catch (e: Exception) {
            Log.w(TAG, "getMyTeachers failed", e)
            Result.failure(e)
        }
    }

    private suspend fun loadStaff(schoolId: String, teacherId: String): StaffDoc? {
        return try {
            firestoreService.getDocumentAs<StaffDoc>(
                Constants.Firestore.STAFF,
                "${schoolId}_$teacherId"
            )
        } catch (e: Exception) {
            Log.w(TAG, "loadStaff failed for $teacherId", e)
            null
        }
    }
}
