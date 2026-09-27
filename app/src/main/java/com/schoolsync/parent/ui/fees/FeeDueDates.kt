package com.schoolsync.parent.ui.fees

import com.schoolsync.parent.data.model.firestore.FeeDemandDoc

/**
 * PARENT-DUE-1 — HOW OVERDUE A MONTH IS, PREFERRING THE SERVER'S OWN DATE.
 *
 * ## The defect
 *
 * `FeesViewModel` inferred a due date from the MONTH LABEL: the 16th of that
 * month, with a 15-day grace. That is a reasonable fallback for a recurring
 * tuition demand, and it is wrong for every demand whose real due date is not
 * tied to its period.
 *
 * Every per-student one-off — Transport, Hostel, ID-card replacement, and now
 * Student Charges — rides `Fee_lifecycle::createModuleFee()`, which files the
 * demand under the SESSION START regardless of when it was raised:
 *
 *     $periodKey = sprintf('%04d-%02d', $year, self::ACADEMIC_START_MONTH);
 *     $periodLbl = "April {$year}";
 *
 * So a lost-book charge raised in November is labelled "April". The server
 * stamps it `dueDate = today + 30 days` and the late-fee engine honours that —
 * but this screen read the label and showed the charge as roughly SEVEN MONTHS
 * OVERDUE on the day it was raised.
 *
 * Two systems disagreeing about the same demand, with the parent shown the
 * wrong one. The server's figure is the one that governs whether a late fee is
 * actually charged.
 *
 * ## The rule
 *
 * Same principle as LATE-2: the server's stored value is authoritative and the
 * client's re-derivation is only a fallback. LATE-2 applied it to the amount;
 * this applies it to the date.
 *
 * A demand generated before `dueDate` was populated carries no date, so the
 * month-label inference remains as the fallback and those demands behave
 * exactly as they do today.
 *
 * ## Why this is its own file
 *
 * These were private methods on the view model, which meant the only way to
 * test them was to copy the rule into the test — and a copy passes whether or
 * not the real code is right. `Phase156LateFeeEngineTest` on the server side is
 * a live example: it carries a stale copy of `fineFor()` and stayed green
 * through a real defect. Extracting the logic here lets the tests drive the
 * actual code.
 */
internal object FeeDueDates {

    private const val DAY_MS = 1000L * 60 * 60 * 24

    // NOTE: there is deliberately no grace constant here any more. The overdue
    // policy is the server's (`overdue_grace_days`, owned by
    // Fee_overdue_policy) and arrives already applied as
    // `FeeDemandDoc.overdueFrom`. The only remaining hard-coded date logic is
    // the month-label INFERENCE below, which exists solely for demands that
    // carry no server date at all and is not a policy.

    /**
     * How many days overdue this month's demands are.
     *
     * Prefers the earliest open `dueDate` in the bucket, so a month holding
     * both a settled charge and an overdue one reports the overdue one. Falls
     * back to the month label when no demand carries a usable date.
     *
     * ── PA-FEE-GRACE · THE POLICY NOW COMES FROM THE SERVER ─────────────
     *
     * This used to add a hard-coded 15-day grace to the server's `dueDate`.
     * That number matched no server rule — the server's only grace was
     * `late_fee_grace_days`, which governs FINES, defaults to 0, and applies
     * only when late fees are enabled. Measured on a device 2026-08-27 against
     * a demand due 2026-04-10: genuinely 139 days past due, displayed as
     * "Overdue · 124d", and not shown as overdue at all for fifteen days while
     * a fine could already have been accruing.
     *
     * The school's policy is now `overdue_grace_days`, owned by
     * `Fee_overdue_policy` on the server and applied there: each demand is
     * stamped with `overdueFrom = dueDate + grace`. The app holds NO policy —
     * it compares a date to now.
     *
     *   overdue      <=>  now >  overdueFrom
     *   days overdue  =   now -  overdueFrom   (0 on the day itself)
     *
     * So a demand due on the 10th with no grace is NOT overdue on the 10th —
     * the school said "pay by the 10th" — and is 1 day overdue on the 11th.
     *
     * ── THE THREE-STEP FALLBACK, AND WHY IT DEGRADES SAFELY ─────────────
     *   1. `overdueFrom` — the school's policy, already applied.
     *   2. `dueDate` — for the 76 demands generated before this field existed.
     *      That is exactly the zero-grace answer, i.e. the plain truth, not an
     *      invented grace.
     *   3. the month label — only when there is no server date at all.
     *
     * A school that genuinely wants the old behaviour sets
     * `overdue_grace_days = 15`, and every surface follows, including this one.
     */
    fun overdueDaysFor(
        monthDemands: List<FeeDemandDoc>,
        monthLabel: String,
        sessionStartYear: Int,
        now: Long = System.currentTimeMillis()
    ): Int {
        val boundary = earliestOpenOverdueBoundary(monthDemands)
        if (boundary.isBlank()) return overdueDaysFromMonthLabel(monthLabel, sessionStartYear, now)

        val overdueAt = parseIsoDateMillis(boundary)
            ?: return overdueDaysFromMonthLabel(monthLabel, sessionStartYear, now)

        // Not overdue ON the boundary day itself — it is the last on-time day.
        if (now <= overdueAt) return 0
        return ((now - overdueAt) / DAY_MS).toInt().coerceAtLeast(1)
    }

    /**
     * The earliest open demand's overdue boundary, preferring the server's
     * stamped `overdueFrom` and falling back to its `dueDate`.
     *
     * Ordered by the SAME value that is returned, so the month reports the
     * boundary of the demand that actually becomes overdue first — ordering by
     * `dueDate` while returning `overdueFrom` could report a later demand's
     * boundary if two demands in one month carried different policies.
     */
    fun earliestOpenOverdueBoundary(monthDemands: List<FeeDemandDoc>): String =
        monthDemands
            .asSequence()
            .filter { it.status != "paid" }
            .map { it.overdueFrom.ifBlank { it.dueDate } }
            .filter { it.isNotBlank() }
            .minByOrNull { it.take(10) }
            ?: ""

    /**
     * The earliest `dueDate` among demands that are not yet settled.
     * "" when none carries one.
     *
     * ISO `yyyy-MM-dd` sorts correctly as a string, so no parsing is needed to
     * pick the earliest — only to convert the winner.
     */
    fun earliestOpenDueDate(monthDemands: List<FeeDemandDoc>): String =
        monthDemands
            .filter { it.status != "paid" && it.dueDate.isNotBlank() }
            .minByOrNull { it.dueDate.take(10) }
            ?.dueDate
            ?: ""

    /**
     * The original inference, unchanged: the 16th of the labelled month.
     * Retained as the fallback for demands with no server due date.
     */
    fun overdueDaysFromMonthLabel(
        monthLabel: String,
        sessionStartYear: Int,
        now: Long = System.currentTimeMillis()
    ): Int {
        val monthIndex = when (monthLabel) {
            "April"       -> 4
            "May"         -> 5
            "June"        -> 6
            "July"        -> 7
            "August"      -> 8
            "September"   -> 9
            "October"     -> 10
            "November"    -> 11
            "December"    -> 12
            "January"     -> 1
            "February"    -> 2
            "March"       -> 3
            "Yearly Fees" -> 4     // treat as session-start
            else -> return 0
        }
        val year = if (monthIndex in 1..3) sessionStartYear + 1 else sessionStartYear
        val cal = java.util.Calendar.getInstance()
        // Grace: 15 days from the 1st. Overdue starts on day 16.
        cal.set(year, monthIndex - 1, 16, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val overdueAt = cal.timeInMillis
        if (now < overdueAt) return 0
        return ((now - overdueAt) / DAY_MS).toInt().coerceAtLeast(1)
    }

    /**
     * `yyyy-MM-dd` (or an ISO-8601 timestamp with that prefix) at local
     * midnight.
     *
     * Returns null on anything else so the caller falls back rather than
     * inventing a date. An unparseable value must never be treated as "due
     * now" — that would show a parent an overdue badge because of a formatting
     * problem.
     */
    fun parseIsoDateMillis(value: String): Long? {
        val parts = value.take(10).split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (parts[0].length != 4 || m !in 1..12 || d !in 1..31) return null

        val cal = java.util.Calendar.getInstance()
        cal.isLenient = false          // reject 2026-02-30 rather than rolling it forward
        return try {
            cal.set(y, m - 1, d, 0, 0, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }
}
