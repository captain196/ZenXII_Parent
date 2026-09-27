package com.schoolsync.parent.fees

import com.schoolsync.parent.data.model.firestore.FeeDemandDoc
import com.schoolsync.parent.ui.fees.FeeDueDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * PARENT-DUE-1 — A CHARGE RAISED TODAY SHOWED AS MONTHS OVERDUE.
 *
 * `FeesViewModel` inferred a due date from the MONTH LABEL — the 16th of that
 * month, 15-day grace. Fine for recurring tuition; wrong for every demand whose
 * real due date is not tied to its period.
 *
 * Every per-student one-off rides `Fee_lifecycle::createModuleFee()`, which
 * files the demand under the SESSION START whatever the date:
 *
 *     $periodKey = sprintf('%04d-%02d', $year, self::ACADEMIC_START_MONTH);
 *     $periodLbl = "April {$year}";
 *
 * So a lost-book charge raised in November is labelled "April". The server
 * stamps `dueDate = today + 30 days` and its late-fee engine honours that,
 * while this screen showed the charge as roughly seven months overdue on the
 * day it was raised. Same demand, two systems, opposite answers — and the
 * parent saw the one that does not govern whether they are actually fined.
 *
 * These tests EXECUTE the real `FeeDueDates` (a compile pass proves none of
 * it), with `now` injected so they assert exact day counts rather than
 * "roughly", and never depend on the wall clock.
 */
class FeeDueDateAuthorityTest {

    private fun at(y: Int, m: Int, d: Int): Long {
        val c = Calendar.getInstance()
        c.set(y, m - 1, d, 0, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun demand(
        status: String = "unpaid",
        dueDate: String = "",
        head: String = "Lost Book",
        // PA-FEE-GRACE — the server's stamped boundary. Blank by default so
        // the existing cases still exercise the dueDate fallback, which is
        // what the 76 pre-existing demands carry.
        overdueFrom: String = ""
    ) = FeeDemandDoc(
        studentId = "STU0012",
        feeHead = head,
        month = "April",
        status = status,
        dueDate = dueDate,
        overdueFrom = overdueFrom,
        netAmount = 450.0,
        balance = 450.0
    )

    // ══ THE DEFECT ══════════════════════════════════════════════════════════

    /**
     * The exact production scenario. A charge raised 23 Nov 2026, due 23 Dec,
     * viewed the day it was raised — filed under "April" because that is what
     * `createModuleFee()` writes.
     *
     * WOULD HAVE FAILED BEFORE: the month label put it past 16 April 2026, so
     * the parent saw ~221 days overdue on a charge issued that morning.
     */
    @Test
    fun `a charge raised today is not overdue even though it is filed under April`() {
        val today = at(2026, 11, 23)

        val days = FeeDueDates.overdueDaysFor(
            listOf(demand(dueDate = "2026-12-23")), "April", 2026, today
        )

        assertEquals(
            "a charge raised today reported as overdue. It is filed under the session start " +
                "by createModuleFee(), so the month label says April while the server's own " +
                "dueDate is a month away — and the server's date is the one that decides " +
                "whether a late fee is actually charged.",
            0, days
        )
    }

    /** The month-label path, unchanged, is what the old behaviour would have given. */
    @Test
    fun `control the old month label rule really did call it overdue`() {
        val today = at(2026, 11, 23)

        val viaLabel = FeeDueDates.overdueDaysFromMonthLabel("April", 2026, today)

        assertTrue(
            "the month-label rule no longer reports a large overdue for April, so the test " +
                "above is no longer demonstrating the defect it claims to",
            viaLabel > 200
        )
    }

    // ══ THE SERVER'S DATE IS HONOURED, IN BOTH DIRECTIONS ═══════════════════

    /**
     * ── PA-FEE-GRACE · THE POLICY MOVED TO THE SERVER ───────────────────
     *
     * These two cases previously asserted a 15-day grace HARD-CODED in this
     * app. That number matched no server rule: the server's only grace was
     * `late_fee_grace_days`, which governs FINES, defaults to 0, and applies
     * only when late fees are enabled. The result was a demand due 2026-04-10
     * reading "Overdue · 124d" on 2026-08-27 when it was genuinely 139 days
     * past due, and reading as not overdue at all for the first fifteen.
     *
     * The policy is now `overdue_grace_days`, owned by `Fee_overdue_policy` on
     * the server and applied THERE: each demand is stamped with
     * `overdueFrom = dueDate + grace`. This app holds no policy — it compares
     * that date to now. Proven at runtime: default → `overdueFrom == dueDate`
     * on all 25 demands; grace 15 → a consistent 15-day gap on all 25.
     *
     * So the assertions below now pin the CONTRACT rather than a constant.
     */
    @Test
    fun `a stamped boundary is honoured exactly`() {
        // The school configured 15 days, so the server stamped 2026-10-07.
        // Viewed 2026-10-17 → 10 days overdue.
        val days = FeeDueDates.overdueDaysFor(
            listOf(demand(dueDate = "2026-09-22", overdueFrom = "2026-10-07")),
            "April", 2026, at(2026, 10, 17)
        )
        assertEquals(10, days)
    }

    @Test
    fun `inside the school's configured grace nothing is overdue`() {
        val days = FeeDueDates.overdueDaysFor(
            listOf(demand(dueDate = "2026-09-22", overdueFrom = "2026-10-07")),
            "April", 2026, at(2026, 10, 5)
        )
        assertEquals(
            "a demand inside the school's own grace window was reported overdue",
            0, days
        )
    }

    @Test
    fun `the boundary day itself is still on time`() {
        // overdueFrom is the LAST on-time day: "pay by the 7th" means the 7th
        // is not late. Getting this off by one tells a parent they are late on
        // the very day they were asked to pay.
        assertEquals(0, FeeDueDates.overdueDaysFor(
            listOf(demand(dueDate = "2026-09-22", overdueFrom = "2026-10-07")),
            "April", 2026, at(2026, 10, 7)
        ))
        assertEquals(1, FeeDueDates.overdueDaysFor(
            listOf(demand(dueDate = "2026-09-22", overdueFrom = "2026-10-07")),
            "April", 2026, at(2026, 10, 8)
        ))
    }

    @Test
    fun `a demand with no stamped boundary falls back to its due date`() {
        // The 76 demands that predate the field. Zero grace is the plain
        // truth, not an invented grace period.
        val days = FeeDueDates.overdueDaysFor(
            listOf(demand(dueDate = "2026-09-22")), "April", 2026, at(2026, 10, 2)
        )
        assertEquals(
            "an unstamped demand did not fall back to its due date — the app is still " +
                "applying a grace period of its own",
            10, days
        )
    }

    @Test
    fun `the app applies no grace of its own`() {
        // The regression guard for the whole change. With no stamped boundary
        // and a due date exactly 15 days ago, the old hard-coded rule reported
        // 0. Anything other than 15 means a grace has crept back in.
        val days = FeeDueDates.overdueDaysFor(
            listOf(demand(dueDate = "2026-09-22")), "April", 2026, at(2026, 10, 7)
        )
        assertEquals(
            "the app re-applied a grace period the school never configured",
            15, days
        )
    }

    // ══ THE FALLBACK ════════════════════════════════════════════════════════

    /**
     * Demands generated before `dueDate` was populated carry no date. Those
     * must behave exactly as they do today — this change must not alter a
     * single existing tuition row.
     */
    @Test
    fun `a demand without a server due date falls back to the month label`() {
        val today = at(2026, 6, 20)

        val viaHelper = FeeDueDates.overdueDaysFor(listOf(demand(dueDate = "")), "April", 2026, today)
        val viaLabel  = FeeDueDates.overdueDaysFromMonthLabel("April", 2026, today)

        assertEquals(
            "an existing demand with no dueDate changed its overdue figure. Every tuition row " +
                "generated before dueDate was populated must be unaffected by this change.",
            viaLabel, viaHelper
        )
        assertTrue(viaLabel > 0)
    }

    /**
     * An unparseable date must fall back, never be treated as "due now" — a
     * formatting problem must not put an overdue badge in front of a parent.
     */
    @Test
    fun `an unparseable due date falls back instead of reading as due now`() {
        val today = at(2026, 6, 20)

        for (junk in listOf("not-a-date", "2026/09/22", "22-09-2026", "2026-13-01", "2026-02-30", "")) {
            val days = FeeDueDates.overdueDaysFor(listOf(demand(dueDate = junk)), "April", 2026, today)

            assertEquals(
                "'$junk' did not fall back to the month label — an unparseable date must never " +
                    "be treated as a real one",
                FeeDueDates.overdueDaysFromMonthLabel("April", 2026, today), days
            )
        }
    }

    @Test
    fun `an iso timestamp is accepted, not just a bare date`() {
        // The server writes date('c') in places, so the value can carry a time.
        assertEquals(
            FeeDueDates.parseIsoDateMillis("2026-09-22"),
            FeeDueDates.parseIsoDateMillis("2026-09-22T14:31:07+05:30")
        )
    }

    // ══ PICKING THE RIGHT DEMAND OUT OF THE BUCKET ══════════════════════════

    /**
     * A month bucket holds several demands. The month is as overdue as its
     * WORST open demand, so the earliest open due date wins.
     */
    @Test
    fun `the earliest open due date decides the month`() {
        val demands = listOf(
            demand(dueDate = "2026-12-01", head = "Breakage"),
            demand(dueDate = "2026-09-22", head = "Lost Book"),
            demand(dueDate = "2026-10-15", head = "Trip")
        )

        assertEquals("2026-09-22", FeeDueDates.earliestOpenDueDate(demands))
    }

    /**
     * A settled demand must not drag the month's due date backwards. An April
     * tuition row that was paid in April cannot make a November charge look
     * overdue.
     */
    @Test
    fun `a paid demand does not set the month due date`() {
        val demands = listOf(
            demand(status = "paid", dueDate = "2026-04-10", head = "Tuition"),
            demand(status = "unpaid", dueDate = "2026-12-23", head = "Lost Book")
        )

        assertEquals(
            "a settled demand set the month's due date. A paid April tuition row would then " +
                "make an unpaid November charge display as eight months overdue.",
            "2026-12-23", FeeDueDates.earliestOpenDueDate(demands)
        )
    }

    /** A partially-paid demand is still open and still counts. */
    @Test
    fun `a partial demand still counts as open`() {
        val demands = listOf(demand(status = "partial", dueDate = "2026-09-22"))

        assertEquals("2026-09-22", FeeDueDates.earliestOpenDueDate(demands))
    }

    @Test
    fun `an empty bucket yields no due date rather than throwing`() {
        assertEquals("", FeeDueDates.earliestOpenDueDate(emptyList()))
    }

    // ══ THE UNCHANGED CASES ═════════════════════════════════════════════════

    /**
     * The month-label table itself must not have drifted during the move out
     * of the view model. Jan–Mar roll into sessionYear+1, and that is easy to
     * lose in a refactor.
     */
    @Test
    fun `january to march still roll into the next calendar year`() {
        // Session 2026-27. February belongs to Feb 2027, so on 2027-01-10 it is
        // not yet overdue — but it would be if the year rolled wrongly.
        val days = FeeDueDates.overdueDaysFromMonthLabel("February", 2026, at(2027, 1, 10))

        assertEquals(
            "February of session 2026-27 registered as overdue in January 2027, which means " +
                "the sessionYear+1 roll for Jan-Mar was lost",
            0, days
        )

        assertTrue(FeeDueDates.overdueDaysFromMonthLabel("February", 2026, at(2027, 3, 1)) > 0)
    }

    @Test
    fun `an unknown month label is never overdue`() {
        assertEquals(0, FeeDueDates.overdueDaysFromMonthLabel("Unknown", 2026, at(2027, 3, 1)))
    }

    @Test
    fun `yearly fees still overdue alongside april`() {
        val now = at(2026, 5, 1)

        assertEquals(
            FeeDueDates.overdueDaysFromMonthLabel("April", 2026, now),
            FeeDueDates.overdueDaysFromMonthLabel("Yearly Fees", 2026, now)
        )
    }
    // ══ THE MIXED BUCKET — THE LIVE STATE AFTER THE DUE-2 FIX ═══════════════

    /**
     * A real April bucket on a live device held TWO charges: one raised before
     * the DUE-2 fix (no dueDate on its demand) and one raised after it
     * (dueDate 2026-09-22). Both are unpaid, both sit in April.
     *
     * The dateless one must NOT drag the month back to the month-label
     * inference. `earliestOpenDueDate()` filters on `isNotBlank()`, so a demand
     * carrying a real date is preferred over an inference — which is the right
     * precedence: a stored date is evidence, a month label is a guess.
     *
     * This was the observed outcome — the "Overdue · 129d" badge disappeared
     * once one dated demand joined the bucket.
     */
    @Test
    fun `a dated demand wins over a dateless one in the same month`() {
        val preFix  = demand(dueDate = "", head = "Tuition Fee")            // raised before DUE-2
        val postFix = demand(dueDate = "2026-09-22", head = "ID Card Replacement")

        val days = FeeDueDates.overdueDaysFor(
            listOf(preFix, postFix), "April", 2026, at(2026, 8, 23)
        )

        assertEquals(
            "a dateless demand dragged the month back to the month-label inference, so a " +
                "charge raised today still reads as months overdue whenever an older " +
                "pre-fix demand shares its bucket",
            0, days
        )
    }

    /**
     * And with NO dated demand at all, the fallback must still engage — a
     * legitimate April tuition demand genuinely is overdue in August.
     */
    @Test
    fun `an all-dateless bucket still falls back to the month label`() {
        val a = demand(dueDate = "", head = "Tuition Fee")
        val b = demand(dueDate = "", head = "Library Fee")

        val days = FeeDueDates.overdueDaysFor(listOf(a, b), "April", 2026, at(2026, 8, 23))

        assertEquals(
            "the month-label fallback stopped working, so demands with no stored due date " +
                "would never register as overdue at all",
            FeeDueDates.overdueDaysFromMonthLabel("April", 2026, at(2026, 8, 23)), days
        )
        assertTrue(days > 100)
    }

    /**
     * Once the dated charge genuinely passes its own due date, the month
     * becomes overdue on THAT date — not on the April inference.
     */
    @Test
    fun `the mixed bucket becomes overdue on the real date, not the label`() {
        val preFix  = demand(dueDate = "")
        val postFix = demand(dueDate = "2026-09-22")

        // PA-FEE-GRACE changed the EXPECTED VALUE here, not the intent.
        // Previously: 2026-09-22 + 15 hard-coded grace = 2026-10-07, so
        // 2026-10-17 gave 10 days. The app no longer applies a grace of its
        // own; this demand carries no server-stamped `overdueFrom`, so it
        // falls back to its due date — 2026-09-22 to 2026-10-17 = 25 days.
        //
        // What this test is FOR is unchanged and still asserted below: the
        // demand's real date must beat the April month-label inference.
        val days = FeeDueDates.overdueDaysFor(
            listOf(preFix, postFix), "April", 2026, at(2026, 10, 17)
        )

        assertEquals(25, days)
        assertTrue(
            "the month-label inference would give a far larger figure — the real date must win",
            days < FeeDueDates.overdueDaysFromMonthLabel("April", 2026, at(2026, 10, 17))
        )
    }
}
