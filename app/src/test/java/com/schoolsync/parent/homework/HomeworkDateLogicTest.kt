package com.schoolsync.parent.homework

import com.schoolsync.parent.data.model.Homework
import com.schoolsync.parent.ui.homework.HomeworkViewModel
import com.schoolsync.parent.ui.homework.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * JVM unit tests for the CROWN-JEWEL timezone / due-date logic in
 * [HomeworkViewModel]'s companion object. All instants are built explicitly
 * (fixed millis / IST-boundary Dates), never from the wall clock.
 *
 * IST = Asia/Kolkata = UTC+05:30, no DST.
 *
 * NOTE ON WALL-CLOCK-DEPENDENT PATHS: [HomeworkViewModel.derivePriority],
 * [HomeworkViewModel.dueDateLabel] and [HomeworkViewModel.isOverdue] read
 * `Date()` (now) internally, so their "today / tomorrow / within-3-days"
 * boundaries cannot be pinned to a fixed clock without changing production
 * signatures. Those branches are therefore exercised only with unambiguous
 * far-past / far-future inputs (deterministic regardless of when the suite
 * runs). The underlying day-bucket math they rely on is tested directly and
 * deterministically via [istDay].
 */
class HomeworkDateLogicTest {

    private val IST: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    /** Build an absolute instant from a UTC "yyyy-MM-dd'T'HH:mm:ss.SSS" string. */
    private fun utc(s: String): Date =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
            .apply { timeZone = UTC }
            .parse(s)!!

    /** Build the exact IST instant for the given wall-clock fields. */
    private fun ist(
        year: Int, month1to12: Int, day: Int,
        hour: Int, min: Int, sec: Int, ms: Int
    ): Date = Calendar.getInstance(IST).apply {
        clear()
        set(year, month1to12 - 1, day, hour, min, sec)
        set(Calendar.MILLISECOND, ms)
    }.time

    private fun hw(dueDate: String = "", status: String = "pending", priority: String = "") =
        Homework(dueDate = dueDate, studentStatus = status, priority = priority)

    // ── toEndOfDayIst ────────────────────────────────────────────────────────

    @Test
    fun toEndOfDayIst_pushesIstMidnightTo235959999SameIstDay() {
        // Input: 2026-05-15 00:00:00.000 IST
        val midnight = ist(2026, 5, 15, 0, 0, 0, 0)
        val result = HomeworkViewModel.toEndOfDayIst(midnight)
        // Expected: 2026-05-15 23:59:59.999 IST  ==  2026-05-15 18:29:59.999 UTC
        assertEquals(utc("2026-05-15T18:29:59.999"), result)
    }

    @Test
    fun toEndOfDayIst_isIdempotentAcrossTheDay() {
        // Any instant within the same IST day collapses to the same end-of-day.
        val morning = ist(2026, 12, 31, 7, 15, 3, 250)
        val result = HomeworkViewModel.toEndOfDayIst(morning)
        assertEquals(ist(2026, 12, 31, 23, 59, 59, 999), result)
    }

    // ── istDay (day-bucket normalization + boundary) ─────────────────────────

    @Test
    fun istDay_sameIstDayYieldsSameBucket() {
        val a = ist(2026, 5, 15, 0, 0, 0, 0)
        val b = ist(2026, 5, 15, 23, 59, 59, 999)
        assertEquals(HomeworkViewModel.istDay(a), HomeworkViewModel.istDay(b))
    }

    @Test
    fun istDay_flipsExactlyAtIstMidnightNotUtcMidnight() {
        // 18:29 UTC == 23:59 IST (still May 15 IST)
        val beforeIstMidnight = utc("2026-05-15T18:29:59.999")
        // 18:30 UTC == 00:00 IST (now May 16 IST)
        val atIstMidnight = utc("2026-05-15T18:30:00.000")
        val dayBefore = HomeworkViewModel.istDay(beforeIstMidnight)
        val dayAfter = HomeworkViewModel.istDay(atIstMidnight)
        assertEquals("IST day must advance by exactly 1 across IST midnight", 1L, dayAfter - dayBefore)
    }

    @Test
    fun istDay_utcMidnightDoesNotAdvanceIstDay() {
        // 23:59 UTC and 00:00 UTC straddle UTC midnight but are both the SAME
        // IST day (05:29 and 05:30 IST of the next calendar date) — proving the
        // bucket keys off IST, not UTC/epoch truncation.
        val a = utc("2026-05-15T23:59:00.000") // 2026-05-16 05:29 IST
        val b = utc("2026-05-16T00:00:00.000") // 2026-05-16 05:30 IST
        assertEquals(HomeworkViewModel.istDay(a), HomeworkViewModel.istDay(b))
    }

    // ── parseDueDate ─────────────────────────────────────────────────────────

    @Test
    fun parseDueDate_blankReturnsNull() {
        assertNull(HomeworkViewModel.parseDueDate(""))
        assertNull(HomeworkViewModel.parseDueDate("   "))
    }

    @Test
    fun parseDueDate_undatedIsNull_soComputeStatsExcludesItFromWeekTotal() {
        // FIX 9 rests on this: undated (blank/unparseable) homework parses to
        // null, so computeStats' `dueDate != null` guard never buckets it into
        // weekTotal. Documented here since computeStats itself needs a live
        // ViewModel + repo to instantiate.
        assertNull(HomeworkViewModel.parseDueDate("not-a-date"))
    }

    @Test
    fun parseDueDate_isoDate_yyyyMMdd_isEndOfDayIst() {
        val d = HomeworkViewModel.parseDueDate("2026-05-15")
        assertEquals(ist(2026, 5, 15, 23, 59, 59, 999), d)
    }

    @Test
    fun parseDueDate_slashDate_ddMMyyyy_isEndOfDayIst() {
        val d = HomeworkViewModel.parseDueDate("15/05/2026")
        assertEquals(ist(2026, 5, 15, 23, 59, 59, 999), d)
    }

    @Test
    fun parseDueDate_dashDate_ddMMyyyy_isEndOfDayIst() {
        val d = HomeworkViewModel.parseDueDate("15-05-2026")
        assertEquals(ist(2026, 5, 15, 23, 59, 59, 999), d)
    }

    @Test
    fun parseDueDate_timeBearingWithOffsetUsesInstantVerbatim() {
        // Explicit +05:30 offset — used exactly as written, NOT pushed to EOD.
        val d = HomeworkViewModel.parseDueDate("2026-05-15T10:30:00+05:30")
        assertEquals(utc("2026-05-15T05:00:00.000"), d)
    }

    @Test
    fun parseDueDate_timeBearingWithZuluUsesInstantVerbatim() {
        val d = HomeworkViewModel.parseDueDate("2026-05-15T10:30:00Z")
        assertEquals(utc("2026-05-15T10:30:00.000"), d)
    }

    @Test
    fun parseDueDate_endOfDayIstIso_roundTrips() {
        // The canonical shape teacher/admin write.
        val d = HomeworkViewModel.parseDueDate("2026-05-15T23:59:59+05:30")
        assertEquals(ist(2026, 5, 15, 23, 59, 59, 0), d)
    }

    @Test
    fun parseDueDate_unparseableReturnsNull() {
        assertNull(HomeworkViewModel.parseDueDate("15th May"))
    }

    // ── isOverdue (absolute-instant compare; deterministic extremes) ─────────

    @Test
    fun isOverdue_farPastPendingIsOverdue() {
        assertTrue(HomeworkViewModel.isOverdue(hw(dueDate = "2000-01-01", status = "pending")))
    }

    @Test
    fun isOverdue_farFutureIsNotOverdue() {
        assertFalse(HomeworkViewModel.isOverdue(hw(dueDate = "2099-01-01", status = "pending")))
    }

    @Test
    fun isOverdue_completedIsNeverOverdue_evenIfPastDue() {
        assertFalse(HomeworkViewModel.isOverdue(hw(dueDate = "2000-01-01", status = "submitted")))
    }

    @Test
    fun isOverdue_blankDueDateIsNotOverdue() {
        assertFalse(HomeworkViewModel.isOverdue(hw(dueDate = "", status = "pending")))
    }

    @Test
    fun isOverdue_unparseableDueDateIsNotOverdue() {
        assertFalse(HomeworkViewModel.isOverdue(hw(dueDate = "garbage", status = "pending")))
    }

    // ── derivePriority ───────────────────────────────────────────────────────

    @Test
    fun derivePriority_explicitFieldTakesPrecedence() {
        assertEquals(Priority.HIGH, HomeworkViewModel.derivePriority(hw(priority = "high")))
        assertEquals(Priority.MEDIUM, HomeworkViewModel.derivePriority(hw(priority = "MEDIUM")))
        assertEquals(Priority.LOW, HomeworkViewModel.derivePriority(hw(priority = "low")))
    }

    @Test
    fun derivePriority_explicitGarbageFallsBackToLow() {
        assertEquals(Priority.LOW, HomeworkViewModel.derivePriority(hw(priority = "urgent")))
    }

    @Test
    fun derivePriority_blankDueDateAndNoExplicitIsLow() {
        assertEquals(Priority.LOW, HomeworkViewModel.derivePriority(hw(dueDate = "")))
    }

    @Test
    fun derivePriority_overdueFarPastIsHigh() {
        assertEquals(Priority.HIGH, HomeworkViewModel.derivePriority(hw(dueDate = "2000-01-01")))
    }

    @Test
    fun derivePriority_farFutureIsLow() {
        assertEquals(Priority.LOW, HomeworkViewModel.derivePriority(hw(dueDate = "2099-01-01")))
    }

    // ── dueDateLabel (deterministic: parse-fallback + far-future branch) ─────

    // dueDateLabel's TEXT now lives in string resources and needs a Context, so
    // these assert the pure branch decision instead — which is the part that can
    // actually regress. Rendering is covered on-device.

    @Test
    fun dueDateOffset_blankIsNull() {
        assertNull(HomeworkViewModel.dueDateOffsetDays(hw(dueDate = "")))
    }

    @Test
    fun dueDateOffset_unparseableIsNull() {
        assertNull(HomeworkViewModel.dueDateOffsetDays(hw(dueDate = "15th May")))
    }

    @Test
    fun dueDateOffset_farFutureIsWellBeyondTomorrow() {
        val days = HomeworkViewModel.dueDateOffsetDays(hw(dueDate = "2099-06-15"))
        assertNotNull(days)
        // Beyond the today/tomorrow branches, so the absolute-date branch renders.
        assertTrue(days!! > 1L)
    }

    // ── dueDateFullLabel (pure formatting) ───────────────────────────────────

    @Test
    fun dueDateFullLabel_formatsEndOfDayIstIso() {
        assertEquals(
            "15 May 2026, 11:59 PM IST",
            HomeworkViewModel.dueDateFullLabel(hw(dueDate = "2026-05-15T23:59:00+05:30"))
        )
    }

    @Test
    fun dueDateFullLabel_blankFallsBackToRaw() {
        assertEquals("", HomeworkViewModel.dueDateFullLabel(hw(dueDate = "")))
    }
}
