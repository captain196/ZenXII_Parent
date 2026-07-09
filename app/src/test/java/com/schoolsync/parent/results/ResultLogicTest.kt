package com.schoolsync.parent.results

import com.schoolsync.parent.data.model.ExamResult
import com.schoolsync.parent.data.model.SubjectResult
import com.schoolsync.parent.data.model.firestore.ExamDoc
import com.schoolsync.parent.ui.results.ResultStatus
import com.schoolsync.parent.ui.results.formatOverallPercentage
import com.schoolsync.parent.ui.results.formatPercentageChip
import com.schoolsync.parent.ui.results.formatSubjectMarks
import com.schoolsync.parent.ui.results.resolveResultStatus
import com.schoolsync.parent.ui.results.subjectGradeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the hardened, pure results logic (no Android / Firebase).
 *
 * Covers pass/fail/absent resolution, absent rendering ("—"/"AB"), the
 * no-fabricated-grade fallback, and the parent-visible exam-status filter.
 */
class ResultLogicTest {

    // ── resolveResultStatus: authoritative passFail wins ─────────────────────

    @Test
    fun status_authoritativePassWins_evenWhenPercentageBelowThreshold() {
        // passFail="Pass" must win over a percentage that would otherwise fail.
        val s = resolveResultStatus(
            passFail = "Pass", percentage = 12.0, absent = false, passingPercent = 40
        )
        assertEquals(ResultStatus.PASS, s)
    }

    @Test
    fun status_authoritativeFailWins_evenWhenPercentageAboveThreshold() {
        // passFail="Fail" must win over a percentage that would otherwise pass.
        val s = resolveResultStatus(
            passFail = "Fail", percentage = 95.0, absent = false, passingPercent = 40
        )
        assertEquals(ResultStatus.FAIL, s)
    }

    @Test
    fun status_passFailIsCaseInsensitive() {
        assertEquals(
            ResultStatus.PASS,
            resolveResultStatus("pass", 5.0, false, 40)
        )
        assertEquals(
            ResultStatus.FAIL,
            resolveResultStatus("FAIL", 99.0, false, 40)
        )
    }

    // ── resolveResultStatus: fallback compares against exam passingPercent ───

    @Test
    fun status_fallbackUsesExamPassingPercent_notHardcoded33() {
        // With a 40% threshold, 39.9 fails and 40 passes.
        assertEquals(
            ResultStatus.FAIL,
            resolveResultStatus("", 39.9, false, 40)
        )
        assertEquals(
            ResultStatus.PASS,
            resolveResultStatus("", 40.0, false, 40)
        )
        // 35 is above the old hardcoded 33 but below the real 40 → must FAIL.
        // (Proves the 33 default is not used.)
        assertEquals(
            ResultStatus.FAIL,
            resolveResultStatus("", 35.0, false, 40)
        )
    }

    @Test
    fun status_fallbackBoundaryIsInclusive() {
        assertEquals(ResultStatus.PASS, resolveResultStatus("", 33.0, false, 33))
        assertEquals(ResultStatus.FAIL, resolveResultStatus("", 32.99, false, 33))
    }

    // ── resolveResultStatus: absent is neither pass nor fail ─────────────────

    @Test
    fun status_absentFlagYieldsAbsent_notFail() {
        val s = resolveResultStatus(
            passFail = "", percentage = 88.0, absent = true, passingPercent = 40
        )
        assertEquals(ResultStatus.ABSENT, s)
    }

    @Test
    fun status_nullPercentageYieldsAbsent_notFail() {
        val s = resolveResultStatus(
            passFail = "", percentage = null, absent = false, passingPercent = 40
        )
        assertEquals(ResultStatus.ABSENT, s)
    }

    // ── ExamResult.isPassed mirrors the same contract ────────────────────────

    @Test
    fun examResult_isPassed_authoritativeAndFallback() {
        // Authoritative Pass wins over a failing percentage.
        assertTrue(examResult(passFail = "Pass", percentage = 10.0, passingPercent = 40).isPassed)
        // Blank flag → fallback compare against the exam threshold (not 33).
        assertFalse(examResult(passFail = "", percentage = 35.0, passingPercent = 40).isPassed)
        assertTrue(examResult(passFail = "", percentage = 40.0, passingPercent = 40).isPassed)
        // Null percentage with blank flag → not passed.
        assertFalse(examResult(passFail = "", percentage = null, passingPercent = 40).isPassed)
    }

    @Test
    fun subjectResult_isPassed_neutralWhenFlagBlank() {
        // Blank per-subject flag → null (neutral), never a fabricated verdict.
        assertNull(subject(passFail = "").isPassed)
        assertEquals(true, subject(passFail = "Pass").isPassed)
        assertEquals(false, subject(passFail = "Fail").isPassed)
    }

    // ── Absent rendering: "—" / "AB", never "0.0%" / "0" ─────────────────────

    @Test
    fun overallPercentage_absentRendersDash_notZero() {
        assertEquals("—", formatOverallPercentage(null))
        assertEquals("87.5%", formatOverallPercentage(87.5))
        // A genuine zero is still a number, distinct from absent.
        assertEquals("0.0%", formatOverallPercentage(0.0))
    }

    @Test
    fun percentageChip_absentRendersAB_notZeroPercent() {
        assertEquals("AB", formatPercentageChip(null))
        assertEquals("72%", formatPercentageChip(72.4))
        assertEquals("0%", formatPercentageChip(0.0))
    }

    @Test
    fun subjectMarks_absentOrNullRendersDash_notZeroSlashMax() {
        assertEquals("—", formatSubjectMarks(null, 100.0))
        assertEquals("—", formatSubjectMarks(50.0, 100.0, absent = true))
        assertEquals("45/100", formatSubjectMarks(45.0, 100.0))
        // A genuine zero mark is shown as "0/100", not "—".
        assertEquals("0/100", formatSubjectMarks(0.0, 100.0))
    }

    // ── Subject grade fallback: blank grade → "—" (no fabricated letter) ─────

    @Test
    fun subjectGradeLabel_blankGradeRendersDash_noFabrication() {
        assertEquals("—", subjectGradeLabel(grade = "", marksObtained = 45.0, absent = false))
    }

    @Test
    fun subjectGradeLabel_presentGradeShown() {
        assertEquals("A+", subjectGradeLabel(grade = "A+", marksObtained = 95.0, absent = false))
    }

    @Test
    fun subjectGradeLabel_absentRendersAB() {
        assertEquals("AB", subjectGradeLabel(grade = "B", marksObtained = null, absent = false))
        assertEquals("AB", subjectGradeLabel(grade = "B", marksObtained = 40.0, absent = true))
    }

    // ── Exam-list status filter: only Published / Completed survive ──────────

    @Test
    fun examFilter_keepsOnlyPublishedAndCompleted() {
        val exams = listOf(
            exam("e1", "Draft"),
            exam("e2", "Published"),
            exam("e3", "Completed"),
            exam("e4", "Draft"),
            exam("e5", ""),
            exam("e6", "Published")
        )
        val kept = ExamDoc.filterParentVisible(exams).map { it.id }
        assertEquals(listOf("e2", "e3", "e6"), kept)
    }

    @Test
    fun examVisibility_predicateMatchesTheWhereInSet() {
        assertTrue(ExamDoc.isParentVisible("Published"))
        assertTrue(ExamDoc.isParentVisible("Completed"))
        assertFalse(ExamDoc.isParentVisible("Draft"))
        assertFalse(ExamDoc.isParentVisible(""))
        // Exact-match, like Firestore whereIn — casing matters.
        assertFalse(ExamDoc.isParentVisible("published"))
        assertEquals(listOf("Published", "Completed"), ExamDoc.PARENT_VISIBLE_STATUSES)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun examResult(
        passFail: String,
        percentage: Double?,
        passingPercent: Int,
        absent: Boolean = false
    ) = ExamResult(
        examId = "x",
        passFail = passFail,
        percentage = percentage,
        passingPercent = passingPercent,
        absent = absent
    )

    private fun subject(passFail: String) = SubjectResult(
        subjectName = "Math",
        marksObtained = 50.0,
        maxMarks = 100.0,
        passFail = passFail
    )

    private fun exam(id: String, status: String) = ExamDoc(id = id, status = status)
}
