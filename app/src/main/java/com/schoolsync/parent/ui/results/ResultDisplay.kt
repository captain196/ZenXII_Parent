package com.schoolsync.parent.ui.results

/**
 * Pure, Android-free display logic for the Results screen and the dashboard's
 * "Latest Result" card. Extracted out of the composables so the pass/fail/absent
 * decisions and the "—"/"AB" rendering are hermetically unit-testable.
 *
 * Behaviour here mirrors (and is now the single source of) what the composables
 * previously computed inline — nothing about the rendered output changes.
 */

/** Tri-state outcome for an overall exam result. */
enum class ResultStatus { PASS, FAIL, ABSENT }

/**
 * Resolve the overall pass/fail/absent state.
 *
 * Precedence (matches the hardened contract):
 *  1. Absent wins — an [absent] flag or a null [percentage] means the student
 *     wasn't assessed, so we render neither Pass nor Fail.
 *  2. The authoritative [passFail] from the admin writer wins next.
 *  3. Only as a fallback do we compare [percentage] against the exam's
 *     [passingPercent] — NOT a hard-coded 33.
 */
fun resolveResultStatus(
    passFail: String,
    percentage: Double?,
    absent: Boolean,
    passingPercent: Int
): ResultStatus = when {
    absent || percentage == null -> ResultStatus.ABSENT
    passFail.isNotBlank() ->
        if (passFail.equals("Pass", ignoreCase = true)) ResultStatus.PASS else ResultStatus.FAIL
    percentage >= passingPercent -> ResultStatus.PASS
    else -> ResultStatus.FAIL
}

/**
 * Overall percentage as rendered on the Results screen: one-decimal percent, or
 * "—" for an absentee (never a fabricated "0.0%").
 */
fun formatOverallPercentage(percentage: Double?): String =
    if (percentage != null) "%.1f%%".format(percentage) else "—"

/**
 * Compact percentage chip used on the dashboard "Latest Result" card: whole
 * percent, or "AB" for an absentee (never "0%").
 */
fun formatPercentageChip(percentage: Double?): String =
    if (percentage != null) "${percentage.toInt()}%" else "AB"

/**
 * Per-subject marks text: "obtained/max", or "—" when the subject is absent /
 * has no marks (never "0/…").
 */
fun formatSubjectMarks(marksObtained: Double?, maxMarks: Double, absent: Boolean = false): String =
    if (absent || marksObtained == null) "—" else "${marksObtained.toInt()}/${maxMarks.toInt()}"

/**
 * Per-subject grade chip label. "AB" when absent, the writer's grade when
 * present, else "—" — we NEVER fabricate an A–F band client-side.
 */
fun subjectGradeLabel(grade: String, marksObtained: Double?, absent: Boolean): String = when {
    absent || marksObtained == null -> "AB"
    grade.isNotBlank() -> grade
    else -> "—"
}
