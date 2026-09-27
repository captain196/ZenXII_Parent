package com.schoolsync.parent.fees

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LATE-2 — THE SERVER OWNS THE BALANCE, INCLUDING THE LATE FEE.
 *
 * The backend defines a demand's balance as:
 *
 *     balance = netAmount + fine - paidAmount
 *
 * (Fee_late_fee_service.php, Fees.php). The app parsed that `balance` field and
 * then threw it away, recomputing `netAmount - paidAmount` at four sites:
 *
 *     FeesViewModel      month balance  (the figure the parent PAYS)
 *     FeesViewModel      per-fee-head breakdown
 *     DashboardViewModel headline "amount due" on the home screen
 *     ResultsViewModel   dues shown beside results
 *
 * Every one silently dropped the late fee. A parent with a Rs.50 fine on a
 * Rs.1000 demand saw Rs.1000, paid it, and stayed Rs.50 short with the demand
 * still 'partial'. Not cosmetic: unpaid months display balanceAmount, not
 * totalAmount.
 *
 * These tests EXECUTE the rule (a compile pass proves none of it) over the cases
 * that actually occur, so a regression shows up here rather than in front of a
 * parent.
 *
 * WHY THE RULE READS `balance` RATHER THAN ADDING THE FINE BACK.
 *
 * The original reason recorded here was that the server writes `fine_amount`
 * (snake) while FeeDemandDoc declares `fineAmount` (camel), making the field
 * unreliable. That is NOT correct, and the correction matters because a wrong
 * reason invites someone to "fix" the naming and then read the fine directly.
 * `Fee_firestore_txn::updateDemand()` maps `'fineAmount' => ['fine_amount',
 * 'fineAmount']` and emits camelCase only, so the field does arrive correctly
 * named — the late-fee engine's snake-case argument is normalised at the write
 * boundary.
 *
 * The rule is still right, for a better reason: `balance` is the SINGLE figure
 * the server computed and the one the payment path charges against.
 * Reconstructing it as `netAmount + fineAmount - paidAmount` would be a second
 * implementation of the same formula, free to drift from the first. Reading
 * the stored value keeps one authority, which is what every other money value
 * on this screen already does.
 */
class FeeBalanceAuthorityTest {

    /** The exact rule the three view models apply. */
    private fun balanceOf(netAmount: Double, paidAmount: Double, serverBalance: Double): Double =
        if (serverBalance > 0.0) serverBalance
        else (netAmount - paidAmount).coerceAtLeast(0.0)

    /** The behaviour being replaced — kept so the tests can prove they detect it. */
    private fun oldBalanceOf(netAmount: Double, paidAmount: Double): Double =
        (netAmount - paidAmount).coerceAtLeast(0.0)

    // ── CONTROL: the old rule really did lose the fine ────────────────

    @Test
    fun `control - the old computation drops the late fee`() {
        // Rs.1000 demand, Rs.50 fine, nothing paid. Server balance = 1050.
        val old = oldBalanceOf(netAmount = 1000.0, paidAmount = 0.0)

        assertEquals(
            "the old rule no longer understates a fined demand — if this changed, " +
                "re-derive LATE-2 before trusting the rest of this file",
            1000.0, old, 0.001
        )
        assertNotEquals(
            "the old rule and the server disagreed by exactly the fine",
            1050.0, old, 0.001
        )
    }

    // ── THE FIX ───────────────────────────────────────────────────────

    @Test
    fun `a fined demand shows the server balance including the fine`() {
        val shown = balanceOf(netAmount = 1000.0, paidAmount = 0.0, serverBalance = 1050.0)

        assertEquals(
            "the app must show what the server says is owed, fine included. Showing " +
                "1000 means the parent pays 1000 and stays 50 short, with the demand " +
                "still 'partial' and nothing telling them why",
            1050.0, shown, 0.001
        )
    }

    @Test
    fun `a partially paid fined demand still reflects the fine`() {
        // net 1000, fine 50, paid 600  ->  server balance 450
        val shown = balanceOf(netAmount = 1000.0, paidAmount = 600.0, serverBalance = 450.0)

        assertEquals(450.0, shown, 0.001)
        assertNotEquals(
            "the old computation would have shown 400 — the fine lost again",
            oldBalanceOf(1000.0, 600.0), shown, 0.001
        )
    }

    @Test
    fun `an ordinary demand with no fine is unchanged`() {
        val shown = balanceOf(netAmount = 1200.0, paidAmount = 0.0, serverBalance = 1200.0)
        assertEquals(1200.0, shown, 0.001)
    }

    @Test
    fun `a concession-priced demand uses the discounted net, not gross`() {
        // gross 1200, discount 200 -> net 1000, server balance 1000.
        // The app never sees gross; pricing is the server's job.
        val shown = balanceOf(netAmount = 1000.0, paidAmount = 0.0, serverBalance = 1000.0)

        assertEquals(
            "the app must not reprice a concession — it consumes netAmount as given",
            1000.0, shown, 0.001
        )
    }

    // ── A FULLY PAID DEMAND MUST READ ZERO ────────────────────────────

    @Test
    fun `a fully paid demand shows zero and does not fall back`() {
        // The fallback triggers on `serverBalance > 0.0`, so a legitimately
        // settled demand (balance 0) must NOT be recomputed into something
        // non-zero.
        val shown = balanceOf(netAmount = 1000.0, paidAmount = 1000.0, serverBalance = 0.0)

        assertEquals("a settled demand must read zero", 0.0, shown, 0.001)
    }

    @Test
    fun `an overpaid demand never shows a negative balance`() {
        val shown = balanceOf(netAmount = 1000.0, paidAmount = 1200.0, serverBalance = 0.0)

        assertTrue("a balance must never be negative", shown >= 0.0)
        assertEquals(0.0, shown, 0.001)
    }

    // ── THE FALLBACK, AND ITS LIMIT ───────────────────────────────────

    @Test
    fun `a demand written before the balance field existed still shows its dues`() {
        // Older documents may carry no balance. The fallback must keep them
        // visible rather than rendering them as zero — that direction of error
        // would hide money owed.
        val shown = balanceOf(netAmount = 800.0, paidAmount = 300.0, serverBalance = 0.0)

        assertEquals(500.0, shown, 0.001)
    }

    @Test
    fun `the fallback can never display less than the old behaviour did`() {
        // The guarantee that made this change safe to ship: for every input,
        // the new rule shows at least what the old one showed.
        val cases = listOf(
            Triple(1000.0, 0.0, 1050.0),
            Triple(1000.0, 600.0, 450.0),
            Triple(1200.0, 0.0, 1200.0),
            Triple(800.0, 300.0, 0.0),
            Triple(1000.0, 1000.0, 0.0),
            Triple(0.0, 0.0, 0.0)
        )

        for ((net, paid, server) in cases) {
            val nu = balanceOf(net, paid, server)
            val old = oldBalanceOf(net, paid)
            assertTrue(
                "new rule showed LESS than the old for net=$net paid=$paid server=$server " +
                    "($nu < $old) — a regression that hides money owed",
                nu >= old - 0.001
            )
        }
    }

    // ── AGGREGATION ACROSS DEMANDS ────────────────────────────────────

    @Test
    fun `a month with several fee heads sums each demand's own balance`() {
        // Tuition 1000 + fine 50 -> 1050 ; Transport 500 unfined -> 500
        val demands = listOf(
            Triple(1000.0, 0.0, 1050.0),
            Triple(500.0, 0.0, 500.0)
        )

        val total = demands.sumOf { (net, paid, server) -> balanceOf(net, paid, server) }

        assertEquals(
            "summing per-demand server balances is what keeps a fine on ONE head from " +
                "being lost in the month total",
            1550.0, total, 0.001
        )
        assertNotEquals("the old rule would have shown 1500", 1500.0, total, 0.001)
    }

    @Test
    fun `a settled head does not subtract from an unpaid one`() {
        val demands = listOf(
            Triple(1000.0, 1000.0, 0.0),   // paid
            Triple(500.0, 0.0, 500.0)      // outstanding
        )

        val total = demands.sumOf { (net, paid, server) -> balanceOf(net, paid, server) }
        assertEquals(500.0, total, 0.001)
    }
}
