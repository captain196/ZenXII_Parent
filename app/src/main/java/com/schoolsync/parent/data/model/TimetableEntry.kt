package com.schoolsync.parent.data.model

/**
 * Timetable models matching the Firebase structure:
 *   Time_table/{Day}/{PeriodKey} -> { time, subject, teacher, room, type? }
 *
 * Period keys are "1", "2", "Break_1", "Lunch", etc.
 * Breaks have `type: "break"` and no subject/teacher/room.
 */
data class TimetableSlot(
    val periodKey: String = "",     // "1", "2", "Break_1", "Lunch", etc.
    val time: String = "",          // "8:00 - 8:45"
    val subject: String = "",       // "Maths" or empty for breaks
    val teacher: String = "",
    val room: String = "",
    val isBreak: Boolean = false
) {
    /** Start time extracted from "8:00 - 8:45" format */
    val startTime: String
        get() = time.split("-").firstOrNull()?.trim() ?: ""

    /** End time extracted from "8:00 - 8:45" format */
    val endTime: String
        get() = time.split("-").getOrNull(1)?.trim() ?: ""

    /** Display label for breaks: the period key cleaned up */
    val breakLabel: String
        get() = when {
            periodKey.equals("Lunch", ignoreCase = true) -> "Lunch"
            periodKey.startsWith("Break", ignoreCase = true) -> "Break"
            subject.equals("Lunch", ignoreCase = true) -> "Lunch"
            else -> "Break"
        }

    /** Sort order based on start time (minutes since midnight). Falls back to period number. */
    val sortOrder: Int
        get() {
            // First try to sort by parsed start time
            try {
                val parts = startTime.split(":")
                if (parts.size >= 2) {
                    return parts[0].trim().toInt() * 60 + parts[1].trim().toInt()
                }
            } catch (_: Exception) { /* fall through */ }
            // Fallback: use period key as number
            val num = periodKey.toIntOrNull()
            if (num != null) return num * 10
            return 999
        }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(key: String, data: Map<String, Any?>): TimetableSlot {
            val type = (data["type"] ?: "").toString()
            val isBreak = type.equals("break", ignoreCase = true)
                    || key.startsWith("Break", ignoreCase = true)
                    || key.equals("Lunch", ignoreCase = true)

            return TimetableSlot(
                periodKey = key,
                time = (data["time"] ?: data["Time"] ?: "").toString(),
                subject = if (isBreak) "" else (data["subject"] ?: data["Subject"] ?: "").toString(),
                teacher = if (isBreak) "" else (data["teacher"] ?: data["Teacher"] ?: "").toString(),
                room = if (isBreak) "" else (data["room"] ?: data["Room"] ?: "").toString(),
                isBreak = isBreak
            )
        }
    }
}

data class DayTimetable(
    val dayName: String = "",       // "Monday", "Tuesday", etc.
    val slots: List<TimetableSlot> = emptyList()
) {
    /** Only class slots (not breaks) */
    val classSlots: List<TimetableSlot> get() = slots.filter { !it.isBreak }

    companion object {
        val DAYS_OF_WEEK = listOf(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
        )

        val DAY_ABBREVIATIONS = mapOf(
            "Monday" to "Mon",
            "Tuesday" to "Tue",
            "Wednesday" to "Wed",
            "Thursday" to "Thu",
            "Friday" to "Fri",
            "Saturday" to "Sat"
        )
    }
}

/**
 * Full weekly timetable: day name -> DayTimetable
 */
data class Timetable(
    val days: Map<String, DayTimetable> = emptyMap()
) {
    fun forDay(day: String): DayTimetable =
        days[day] ?: DayTimetable(dayName = day)

    val dayNames: List<String>
        get() = DayTimetable.DAYS_OF_WEEK.filter { days.containsKey(it) }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(data: Map<String, Any?>): Timetable {
            val days = mutableMapOf<String, DayTimetable>()

            data.forEach { (dayName, periods) ->
                if (periods is Map<*, *>) {
                    val periodMap = periods as Map<String, Any?>
                    val slots = mutableListOf<TimetableSlot>()

                    periodMap.forEach { (key, details) ->
                        when (details) {
                            is Map<*, *> -> {
                                val detailMap = details as Map<String, Any?>
                                slots.add(TimetableSlot.fromMap(key, detailMap))
                            }
                            is String -> {
                                // Simple string value: just a subject name
                                slots.add(
                                    TimetableSlot(
                                        periodKey = key,
                                        subject = details,
                                        time = "",
                                        teacher = "",
                                        room = "",
                                        isBreak = false
                                    )
                                )
                            }
                        }
                    }

                    // Sort by time (using sortOrder which parses time for breaks)
                    days[dayName] = DayTimetable(
                        dayName = dayName,
                        slots = slots.sortedBy { it.sortOrder }
                    )
                }
            }

            return Timetable(days = days)
        }
    }
}

// Keep backward compatibility alias
typealias TimetableEntry = TimetableSlot
