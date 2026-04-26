package com.schoolsync.parent.data.model

/**
 * Attendance status codes as stored in Firebase RTDB.
 * The attendance for a month is stored as a single string where
 * each character represents one day's status (index 0 = day 1).
 *
 * Example: "PPAPL" means Day1=Present, Day2=Present, Day3=Absent, Day4=Present, Day5=Leave
 */
enum class AttendanceStatus(val code: Char, val label: String) {
    PRESENT('P', "Present"),
    ABSENT('A', "Absent"),
    LEAVE('L', "Leave"),
    HOLIDAY('H', "Holiday"),
    TRIP('T', "Tardy"),
    VACATION('V', "Vacation");

    companion object {
        fun fromCode(code: Char): AttendanceStatus {
            return entries.find { it.code == code } ?: ABSENT
        }
    }
}

/**
 * Decoded attendance for a single month.
 */
data class AttendanceData(
    val month: String,              // e.g., "January", "February"
    val year: Int,
    val rawString: String,          // raw Firebase string like "PPAPHLPP..."
    val dailyStatus: List<AttendanceStatus>  // decoded per-day list (index 0 = day 1)
) {
    /** Total number of days recorded this month */
    val totalDays: Int get() = dailyStatus.size

    /** Count of a specific status */
    fun countOf(status: AttendanceStatus): Int = dailyStatus.count { it == status }

    val presentCount: Int get() = countOf(AttendanceStatus.PRESENT)
    val absentCount: Int get() = countOf(AttendanceStatus.ABSENT)
    val leaveCount: Int get() = countOf(AttendanceStatus.LEAVE)
    val holidayCount: Int get() = countOf(AttendanceStatus.HOLIDAY)

    /**
     * Working days = total − holidays − vacations.
     *
     * Tardy days (TRIP) DO count as working days — the student was at
     * school, just arrived late. Excluding them would shrink the
     * denominator and double-count their non-attendance.
     */
    val workingDays: Int get() = totalDays - countOf(AttendanceStatus.HOLIDAY) -
            countOf(AttendanceStatus.VACATION)

    /**
     * Attendance percentage = (present + tardy) / working × 100.
     *
     * Matches the Firestore-side formula written by
     * `save_student_attendance` in the admin panel so the local
     * getter and the server-stored `percentage` field stay in sync.
     */
    val attendancePercentage: Float get() {
        val tardyCount = countOf(AttendanceStatus.TRIP)
        return if (workingDays > 0) {
            ((presentCount + tardyCount).toFloat() / workingDays.toFloat()) * 100f
        } else {
            0f
        }
    }

    /** Get status for a specific day (1-indexed, like calendar day) */
    fun statusForDay(dayOfMonth: Int): AttendanceStatus? {
        val index = dayOfMonth - 1
        return if (index in dailyStatus.indices) dailyStatus[index] else null
    }

    companion object {
        /**
         * Decode a raw attendance string from Firebase into an [AttendanceData].
         * @param month Month name (e.g., "January")
         * @param year Calendar year
         * @param rawString The attendance string from Firebase, e.g., "PPAHPLV..."
         */
        fun decode(month: String, year: Int, rawString: String): AttendanceData {
            val dailyStatus = rawString.map { char ->
                AttendanceStatus.fromCode(char)
            }
            return AttendanceData(
                month = month,
                year = year,
                rawString = rawString,
                dailyStatus = dailyStatus
            )
        }

        /**
         * Decode with fallback for empty/null strings.
         */
        fun decodeOrEmpty(month: String, year: Int, rawString: String?): AttendanceData {
            if (rawString.isNullOrBlank()) {
                return AttendanceData(
                    month = month,
                    year = year,
                    rawString = "",
                    dailyStatus = emptyList()
                )
            }
            return decode(month, year, rawString)
        }

        /** All month names in academic order (April to March) */
        val ACADEMIC_MONTHS = listOf(
            "April", "May", "June", "July", "August", "September",
            "October", "November", "December", "January", "February", "March"
        )
    }
}

/**
 * Summary of attendance across all months for a student.
 */
data class AttendanceSummary(
    val months: List<AttendanceData>,
    val totalPresent: Int,
    val totalAbsent: Int,
    val totalLeave: Int,
    val totalTardy: Int = 0,
    val totalWorkingDays: Int
) {
    /** (present + tardy) / working × 100 — matches all other screens. */
    val overallPercentage: Float get() {
        return if (totalWorkingDays > 0) {
            ((totalPresent + totalTardy).toFloat() / totalWorkingDays.toFloat()) * 100f
        } else {
            0f
        }
    }

    companion object {
        fun fromMonths(months: List<AttendanceData>): AttendanceSummary {
            return AttendanceSummary(
                months = months,
                totalPresent = months.sumOf { it.presentCount },
                totalAbsent = months.sumOf { it.absentCount },
                totalLeave = months.sumOf { it.leaveCount },
                totalTardy = months.sumOf { it.countOf(AttendanceStatus.TRIP) },
                totalWorkingDays = months.sumOf { it.workingDays }
            )
        }
    }
}
