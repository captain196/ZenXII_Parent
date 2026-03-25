package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.DayTimetable
import com.schoolsync.parent.data.model.Timetable
import com.schoolsync.parent.data.model.TimetableSlot
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
            val docs = firestoreService.queryDocumentsAs<TimetableDoc>(
                Constants.Firestore.TIMETABLES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("session", session)
                    .whereEqualTo("sectionKey", sectionKey)
            }

            val days = mutableMapOf<String, DayTimetable>()
            for (doc in docs) {
                val slots = doc.periods.map { period ->
                    TimetableSlot(
                        periodKey = period.periodNumber.toString(),
                        time = if (period.startTime.isNotBlank() && period.endTime.isNotBlank())
                            "${period.startTime} - ${period.endTime}" else "",
                        subject = if (period.type == "break" || period.type == "lunch") "" else period.subject,
                        teacher = period.teacher,
                        room = period.room,
                        isBreak = period.type == "break" || period.type == "lunch"
                    )
                }.sortedBy { it.sortOrder }

                days[doc.day] = DayTimetable(dayName = doc.day, slots = slots)
            }

            Result.success(Timetable(days = days))
        } catch (e: Exception) {
            Result.failure(e)
        }
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
