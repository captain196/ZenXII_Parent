package com.schoolsync.parent.homework

import com.schoolsync.parent.data.model.Homework
import com.schoolsync.parent.data.model.isActionNeeded
import com.schoolsync.parent.ui.homework.HomeworkViewModel
import com.schoolsync.parent.ui.homework.getSubjectInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure status/filter predicates that back the nav
 * badge, the tab pager and dashboard counts. No Firebase / Android runtime.
 */
class HomeworkPredicatesTest {

    private fun hw(
        id: String = "",
        status: String = "pending",
        subject: String = "",
        priority: String = "",
        timestamp: Long = 0L
    ) = Homework(
        homeworkId = id,
        studentStatus = status,
        subject = subject,
        priority = priority,
        timestamp = timestamp
    )

    // ── isActionNeeded (pending OR incomplete) ───────────────────────────────

    @Test
    fun isActionNeeded_trueOnlyForPendingAndIncomplete() {
        assertTrue(isActionNeeded("pending"))
        assertTrue(isActionNeeded("incomplete"))
        // case + whitespace tolerant
        assertTrue(isActionNeeded("  Pending "))
        assertTrue(isActionNeeded("INCOMPLETE"))
    }

    @Test
    fun isActionNeeded_falseForEveryOtherStatus() {
        assertFalse(isActionNeeded(""))
        assertFalse(isActionNeeded("submitted"))
        assertFalse(isActionNeeded("reviewed"))
        assertFalse(isActionNeeded("complete"))
        assertFalse(isActionNeeded("graded"))
        assertFalse(isActionNeeded("done"))
    }

    // ── isCompleted ──────────────────────────────────────────────────────────

    @Test
    fun isCompleted_trueForSubmittedCompleteDoneReviewed() {
        assertTrue(HomeworkViewModel.isCompleted(hw(status = "submitted")))
        assertTrue(HomeworkViewModel.isCompleted(hw(status = "complete")))
        assertTrue(HomeworkViewModel.isCompleted(hw(status = "done")))
        assertTrue(HomeworkViewModel.isCompleted(hw(status = "reviewed")))
        // case + whitespace tolerant
        assertTrue(HomeworkViewModel.isCompleted(hw(status = "  Submitted ")))
    }

    @Test
    fun isCompleted_falseForPendingIncompleteGradedBlank() {
        assertFalse(HomeworkViewModel.isCompleted(hw(status = "pending")))
        assertFalse(HomeworkViewModel.isCompleted(hw(status = "incomplete")))
        // NOTE: "graded" is NOT treated as completed by isCompleted (only
        // "reviewed" is). Documenting current production behavior.
        assertFalse(HomeworkViewModel.isCompleted(hw(status = "graded")))
        assertFalse(HomeworkViewModel.isCompleted(hw(status = "")))
    }

    // ── filterForTab ─────────────────────────────────────────────────────────

    private val sample = listOf(
        hw(id = "p1", status = "pending", subject = "Math", priority = "high", timestamp = 10),
        hw(id = "p2", status = "pending", subject = "Science", priority = "low", timestamp = 20),
        hw(id = "i1", status = "incomplete", subject = "Math", priority = "medium", timestamp = 30),
        hw(id = "s1", status = "submitted", subject = "Math", priority = "low", timestamp = 40),
        hw(id = "r1", status = "reviewed", subject = "English", priority = "low", timestamp = 50),
        hw(id = "c1", status = "complete", subject = "Math", priority = "low", timestamp = 60)
    )

    @Test
    fun filterForTab_pendingReturnsOnlyPending() {
        val ids = HomeworkViewModel.filterForTab(sample, "pending", null).map { it.homeworkId }.toSet()
        assertEquals(setOf("p1", "p2"), ids)
    }

    @Test
    fun filterForTab_incompleteReturnsOnlyIncomplete() {
        val ids = HomeworkViewModel.filterForTab(sample, "incomplete", null).map { it.homeworkId }
        assertEquals(listOf("i1"), ids)
    }

    @Test
    fun filterForTab_submittedReturnsOnlySubmitted() {
        val ids = HomeworkViewModel.filterForTab(sample, "submitted", null).map { it.homeworkId }
        assertEquals(listOf("s1"), ids)
    }

    @Test
    fun filterForTab_gradedReturnsReviewedAndComplete() {
        val ids = HomeworkViewModel.filterForTab(sample, "graded", null).map { it.homeworkId }.toSet()
        assertEquals(setOf("r1", "c1"), ids)
    }

    @Test
    fun filterForTab_allReturnsEverything() {
        val ids = HomeworkViewModel.filterForTab(sample, "all", null).map { it.homeworkId }.toSet()
        assertEquals(sample.map { it.homeworkId }.toSet(), ids)
    }

    @Test
    fun filterForTab_subjectFilterIsCaseInsensitive() {
        val ids = HomeworkViewModel.filterForTab(sample, "pending", "math").map { it.homeworkId }
        assertEquals(listOf("p1"), ids)
    }

    @Test
    fun filterForTab_pendingSortsByExplicitPriorityHighFirst() {
        // Explicit priorities keep the sort deterministic (no Date() dependence).
        val ordered = HomeworkViewModel.filterForTab(sample, "pending", null).map { it.homeworkId }
        // p1 = high, p2 = low  ->  high must come first
        assertEquals(listOf("p1", "p2"), ordered)
    }

    // ── getSubjectInfo (pure lookup) ─────────────────────────────────────────

    @Test
    fun getSubjectInfo_knownSubjectMapsAndUnknownFallsBack() {
        assertEquals("accent", getSubjectInfo("Mathematics").colorKey)
        assertEquals("success", getSubjectInfo("  SCIENCE ").colorKey)
        // unknown -> default books emoji + accent
        assertEquals("accent", getSubjectInfo("Astrology").colorKey)
    }

    // ── subjectTip (pure) ────────────────────────────────────────────────────

    @Test
    fun subjectTip_returnsSubjectSpecificAndDefault() {
        assertTrue(HomeworkViewModel.subjectTip("maths").contains("working steps"))
        assertTrue(HomeworkViewModel.subjectTip("physics").contains("diagrams"))
        // unknown subject -> generic tip
        assertTrue(HomeworkViewModel.subjectTip("astrology").contains("instructions"))
    }
}
