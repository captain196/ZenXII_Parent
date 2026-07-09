package com.schoolsync.parent.data.model

/**
 * Computed result for a student in a specific exam (UI-facing domain model).
 *
 * Mapped from the Firestore `results` doc (see ResultDoc). Percentage is
 * nullable so an absentee ([absent] == true) is never rendered as a genuine
 * "0% / Failed".
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
    /** null = absent / not-yet-computed. */
    val percentage: Double? = null,
    val grade: String = "",
    val rank: Int = 0,
    /** Authoritative "Pass" / "Fail" from the admin writer (may be blank). */
    val passFail: String = "",
    /** Overall absent flag from the writer. */
    val absent: Boolean = false,
    /** Exam pass threshold — used only as a fallback when [passFail] is blank. */
    val passingPercent: Int = 33,
    val remarks: String = ""
) {
    /**
     * Pass/fail is driven by the authoritative [passFail]; when the writer left
     * it blank we fall back to comparing [percentage] against the exam's
     * [passingPercent] (NOT a hard-coded 33). Null percentage → not passed
     * (absentees are handled separately in the UI via [absent]).
     */
    val isPassed: Boolean
        get() = when {
            passFail.isNotBlank() -> passFail.equals("Pass", ignoreCase = true)
            percentage != null -> percentage >= passingPercent
            else -> false
        }
}

data class SubjectResult(
    val subjectName: String,
    /** null = the student was absent for this subject. */
    val marksObtained: Double?,
    val maxMarks: Double = 100.0,
    val grade: String = "",
    /** Authoritative per-subject "Pass" / "Fail" (may be blank). */
    val passFail: String = "",
    val absent: Boolean = false
) {
    val percentage: Double?
        get() = if (marksObtained != null && maxMarks > 0) (marksObtained / maxMarks * 100.0) else null

    /**
     * Per-subject pass/fail from the authoritative [passFail]; returns null when
     * it can't be determined (blank flag or absent), so the UI can stay neutral
     * rather than fabricate a verdict.
     */
    val isPassed: Boolean?
        get() = when {
            passFail.isNotBlank() -> passFail.equals("Pass", ignoreCase = true)
            else -> null
        }
}
