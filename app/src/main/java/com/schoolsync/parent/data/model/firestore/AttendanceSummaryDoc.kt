package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Firestore `attendanceSummary` document model.
 *
 * Phase 9b (2026-04-09): added all fields that the admin writes so
 * the Firestore CustomClassMapper doesn't drop them during
 * deserialization. Previously `type`, `monthLabel`, `className`,
 * `section`, and `updatedBy` were missing, which caused silent
 * query/filter failures on the client.
 */
data class AttendanceSummaryDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val month: String = "",            // "2026-04" (canonical YYYY-MM)
    val monthLabel: String = "",       // "April 2026" (human-readable)
    val type: String = "student",      // "student" or "staff"
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",        // "Class 8th"
    val section: String = "",          // "Section A"
    val sectionKey: String = "",       // "Class 8th/Section A"
    val dayWise: String = "",          // "PPAPLHV..." string for calendar view
    val present: Int = 0,
    val absent: Int = 0,
    /** Tardy/late-arrival count. */
    val tardy: Int = 0,
    val leave: Int = 0,
    val holiday: Int = 0,
    val vacation: Int = 0,
    val totalDays: Int = 0,
    val workingDays: Int = 0,
    val percentage: Double = 0.0,
    /**
     * Per-day arrival times for tardy/late students. Map of
     * `dayOfMonth (1-31, as String)` → `{ "time": "HH:mm" }`.
     * Written by admin `save_student_attendance`. Empty for staff docs.
     */
    val lateTimes: Map<String, Map<String, String>> = emptyMap(),
    val updatedAt: Any? = null,
    val updatedBy: String = ""
) {
    /** Look up the recorded arrival time (e.g. "08:47") for a given day, or null. */
    fun arrivalTimeFor(day: Int): String? {
        val entry = lateTimes[day.toString()] ?: return null
        return entry["time"]?.takeIf { it.isNotBlank() }
    }
}
