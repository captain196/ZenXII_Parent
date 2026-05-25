package com.schoolsync.parent.data.repository.firestore

import android.util.Log
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.DayTimetable
import com.schoolsync.parent.data.model.Timetable
import com.schoolsync.parent.data.model.TimetableSlot
import com.schoolsync.parent.data.model.firestore.StaffDoc
import com.schoolsync.parent.data.model.firestore.TimetableDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading timetable data from Firestore (parent-side).
 *
 * Collection: `timetables`
 * One document per day per class/section.
 * Doc ID: `{schoolId}_{session}_{sectionKey}_{day}`
 */
@Singleton
class TimetableFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {
    companion object { private const val TAG = "TimetableRepo" }

    /**
     * Fetch the full weekly timetable for the student's class/section.
     * Queries all day documents matching the sectionKey.
     */
    suspend fun getTimetable(className: String, section: String): Result<Timetable> {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School code not available"))
        val session = tokenManager.user.firstOrNull()?.session?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("Session not available"))

        val cls = Constants.Firebase.classKey(className)
        val sec = Constants.Firebase.sectionKey(section)
        val sectionKey = "$cls/$sec"

        return try {
            // Query by schoolId + sectionKey (schoolId required by security rules)
            // Filter session client-side to avoid 3-field composite index
            val allDocs = firestoreService.queryDocumentsAs<TimetableDoc>(
                Constants.Firestore.TIMETABLES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("sectionKey", sectionKey)
            }
            val docs = allDocs.filter { it.session == session }

            // Defensive teacher-name resolution: most timetable documents
            // already store the teacher's full name in `period.teacher`, but
            // legacy or partially-migrated rows have only `teacherId`. Collect
            // the unique ids that need a name, fetch each staff doc once, and
            // use the resulting map as a fallback during slot mapping.
            val unresolvedTeacherIds = docs
                .flatMap { it.periods }
                .filter { it.teacher.isBlank() && it.teacherId.isNotBlank() }
                .map { it.teacherId }
                .distinct()
            val teacherNameById = if (unresolvedTeacherIds.isEmpty()) {
                emptyMap()
            } else {
                resolveTeacherNames(schoolCode, unresolvedTeacherIds)
            }

            val days = mutableMapOf<String, DayTimetable>()
            for (doc in docs) {
                val slots = doc.periods.map { period ->
                    val resolvedTeacher = period.teacher.ifBlank {
                        teacherNameById[period.teacherId].orEmpty()
                    }
                    val isBrk = period.type == "break" || period.type == "lunch"
                    val subj = if (isBrk) "" else period.subject
                    // Skip empty class periods entirely — parents only need to
                    // see actual classes and real breaks, not "Free Period" rows
                    val isFree = !isBrk && subj.isBlank()
                    if (isFree) null  // filtered out below
                    else TimetableSlot(
                        periodKey = period.periodNumber.toString(),
                        time = if (period.startTime.isNotBlank() && period.endTime.isNotBlank())
                            "${period.startTime} - ${period.endTime}" else "",
                        subject = subj,
                        teacher = resolvedTeacher,
                        room = period.room,
                        isBreak = isBrk
                    )
                }.filterNotNull().sortedBy { it.sortOrder }

                days[doc.day] = DayTimetable(dayName = doc.day, slots = slots)
            }

            // Overlay substitute teachers for today
            try {
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val todayDay = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
                    .format(java.util.Date())

                // Stage 0 FZ-3 (2026-05-24) — added schoolId predicate to prevent
                // cross-tenant substitute leakage. Composite index (schoolId, date)
                // already deployed per firestore.indexes.json (stale "avoid composite
                // index" comment removed). Server-side rule tightening is
                // FZ-2-CRITICAL follow-up; for now, client-side scoping closes the
                // data-leakage path observed in app traffic.
                val subsSnapshot = firestoreService.queryDocuments("substitutes") { ref ->
                    ref.whereEqualTo("schoolId", schoolCode)
                        .whereEqualTo("date", todayStr)
                }

                val todayTimetable = days[todayDay]
                if (todayTimetable != null && !subsSnapshot.isEmpty) {
                    val updatedSlots = todayTimetable.slots.map { slot ->
                        val periodNum = slot.periodKey.toIntOrNull() ?: -1
                        var newTeacher = slot.teacher
                        for (doc in subsSnapshot.documents) {
                            val sub = doc.data ?: continue
                            if ((sub["schoolId"]?.toString() ?: "") != schoolCode) continue
                            if ((sub["status"]?.toString() ?: "") == "cancelled") continue

                            val absentName = sub["absent_teacher_name"]?.toString() ?: ""

                            // New format: assignments[] array
                            @Suppress("UNCHECKED_CAST")
                            val assignments = sub["assignments"] as? List<Map<String, Any>>

                            if (assignments != null && assignments.isNotEmpty()) {
                                for (a in assignments) {
                                    val pn = (a["periodNumber"] as? Number)?.toInt() ?: continue
                                    val aSubName = a["substitute_teacher_name"]?.toString() ?: ""
                                    if (pn == periodNum && slot.teacher.isNotBlank()
                                        && slot.teacher.equals(absentName, ignoreCase = true)) {
                                        newTeacher = "$aSubName (Substitute)"
                                    }
                                }
                            } else {
                                // Legacy flat format
                                @Suppress("UNCHECKED_CAST")
                                val periods = (sub["periods"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
                                val subName = sub["substitute_teacher_name"]?.toString() ?: ""
                                if (periods.contains(periodNum) && slot.teacher.isNotBlank()
                                    && slot.teacher.equals(absentName, ignoreCase = true)) {
                                    newTeacher = "$subName (Substitute)"
                                }
                            }
                        }
                        if (newTeacher != slot.teacher) {
                            TimetableSlot(
                                periodKey = slot.periodKey,
                                time = slot.time,
                                subject = slot.subject,
                                teacher = newTeacher,
                                room = slot.room,
                                isBreak = slot.isBreak
                            )
                        } else slot
                    }
                    days[todayDay] = DayTimetable(dayName = todayDay, slots = updatedSlots)
                }
            } catch (_: Exception) { /* substitute overlay is best-effort */ }

            Result.success(Timetable(days = days))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch each teacher's display name from `staff/{schoolId}_{teacherId}`.
     * Returns a teacherId → name map. Failed lookups are silently dropped
     * so a single broken doc never blocks the whole timetable from rendering.
     */
    private suspend fun resolveTeacherNames(
        schoolId: String,
        teacherIds: List<String>
    ): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (id in teacherIds) {
            try {
                val staff = firestoreService.getDocumentAs<StaffDoc>(
                    Constants.Firestore.STAFF,
                    "${schoolId}_$id"
                )
                val name = staff?.name?.takeIf { it.isNotBlank() }
                if (name != null) out[id] = name
            } catch (e: Exception) {
                Log.w(TAG, "resolveTeacherNames failed for $id", e)
            }
        }
        return out
    }

    /**
     * Fetch today's timetable.
     */
    suspend fun getTodaySchedule(className: String, section: String): Result<DayTimetable> {
        val dayName = java.util.Calendar.getInstance().let { cal ->
            when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> "Monday"
                java.util.Calendar.TUESDAY -> "Tuesday"
                java.util.Calendar.WEDNESDAY -> "Wednesday"
                java.util.Calendar.THURSDAY -> "Thursday"
                java.util.Calendar.FRIDAY -> "Friday"
                java.util.Calendar.SATURDAY -> "Saturday"
                else -> "Sunday"
            }
        }

        return getTimetable(className, section).map { timetable ->
            timetable.forDay(dayName)
        }
    }
}
