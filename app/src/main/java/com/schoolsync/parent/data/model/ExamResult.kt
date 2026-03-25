package com.schoolsync.parent.data.model

/**
 * Computed result for a student in a specific exam.
 * Path: Schools/{schoolCode}/{session}/Results/Computed/{examId}/{class}/{section}/{studentId}
 *
 * The structure under the student node typically contains subject-wise marks
 * and aggregate fields.
 */
data class ExamResult(
    val examId: String,
    val examName: String = "",
    val studentId: String = "",
    val className: String = "",
    val section: String = "",
    val session: String = "",
    val subjects: List<SubjectResult> = emptyList(),
    val totalMarks: Double = 0.0,
    val maxMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val grade: String = "",
    val rank: Int = 0,
    val remarks: String = "",
    val rawData: Map<String, Any?> = emptyMap()
) {
    val isPassed: Boolean get() = percentage >= 33.0

    companion object {
        /**
         * Parse a computed result from Firebase snapshot data.
         * The exact fields depend on how the admin panel computes results.
         */
        fun fromMap(
            examId: String,
            studentId: String,
            className: String,
            section: String,
            session: String,
            data: Map<String, Any?>
        ): ExamResult {
            val subjects = mutableListOf<SubjectResult>()
            var totalObtained = 0.0
            var totalMax = 0.0

            // Parse subject-wise results
            data.forEach { (key, value) ->
                // Skip aggregate keys
                if (key in listOf("Total", "Percentage", "Grade", "Rank", "Remarks", "exam_name")) {
                    return@forEach
                }
                if (value is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val subjectData = value as Map<String, Any?>
                    val obtained = (subjectData["obtained"] ?: subjectData["marks_obtained"] ?: subjectData["Total"])
                        .toDoubleOrNull()
                    val max = (subjectData["max"] ?: subjectData["max_marks"] ?: subjectData["total_marks"])
                        .toDoubleOrNull()
                    val subGrade = (subjectData["grade"] ?: "").toString()

                    if (obtained != null) {
                        subjects.add(
                            SubjectResult(
                                subjectName = key,
                                marksObtained = obtained,
                                maxMarks = max ?: 100.0,
                                grade = subGrade
                            )
                        )
                        totalObtained += obtained
                        totalMax += (max ?: 100.0)
                    }
                }
            }

            val aggregateTotal = data["Total"].toDoubleOrNull() ?: totalObtained
            val aggregatePercentage = data["Percentage"].toDoubleOrNull()
                ?: if (totalMax > 0) (totalObtained / totalMax * 100.0) else 0.0
            val aggregateGrade = (data["Grade"] ?: "").toString()
            val aggregateRank = data["Rank"].toIntOrNull() ?: 0
            val aggregateRemarks = (data["Remarks"] ?: "").toString()
            val examName = (data["exam_name"] ?: examId).toString()

            return ExamResult(
                examId = examId,
                examName = examName,
                studentId = studentId,
                className = className,
                section = section,
                session = session,
                subjects = subjects,
                totalMarks = aggregateTotal,
                maxMarks = totalMax,
                percentage = aggregatePercentage,
                grade = aggregateGrade,
                rank = aggregateRank,
                remarks = aggregateRemarks,
                rawData = data
            )
        }

        private fun Any?.toDoubleOrNull(): Double? {
            return when (this) {
                is Number -> this.toDouble()
                is String -> this.toDoubleOrNull()
                else -> null
            }
        }

        private fun Any?.toIntOrNull(): Int? {
            return when (this) {
                is Number -> this.toInt()
                is String -> this.toIntOrNull()
                else -> null
            }
        }
    }
}

data class SubjectResult(
    val subjectName: String,
    val marksObtained: Double,
    val maxMarks: Double = 100.0,
    val grade: String = ""
) {
    val percentage: Double get() = if (maxMarks > 0) (marksObtained / maxMarks * 100.0) else 0.0
    val isPassed: Boolean get() = percentage >= 33.0
}

/**
 * Exam schedule entry.
 * Path: Schools/{schoolCode}/{session}/{class}/{section}/Exams/{examName}
 */
data class ExamSchedule(
    val examName: String,
    val subjects: List<ExamSubjectSchedule> = emptyList(),
    val rawData: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun fromMap(examName: String, data: Map<String, Any?>): ExamSchedule {
            val subjects = mutableListOf<ExamSubjectSchedule>()
            data.forEach { (key, value) ->
                if (value is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val subjectData = value as Map<String, Any?>
                    subjects.add(
                        ExamSubjectSchedule(
                            subjectName = key,
                            date = (subjectData["date"] ?: "").toString(),
                            time = (subjectData["time"] ?: "").toString(),
                            maxMarks = (subjectData["max_marks"] ?: subjectData["total_marks"] ?: "").toString(),
                            syllabus = (subjectData["syllabus"] ?: "").toString()
                        )
                    )
                }
            }
            return ExamSchedule(
                examName = examName,
                subjects = subjects,
                rawData = data
            )
        }
    }
}

data class ExamSubjectSchedule(
    val subjectName: String,
    val date: String = "",
    val time: String = "",
    val maxMarks: String = "",
    val syllabus: String = ""
)
