package com.schoolsync.parent.fees

import com.schoolsync.parent.data.model.firestore.FeeDemandDoc
import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.parent.ui.fees.FeeAggregation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  PA-FEE-1 — A PARENT MUST NOT BE OFFERED MONEY THE SCHOOL WROTE OFF
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── THE DEFECT, REPRODUCED ON A PHYSICAL DEVICE ──────────────────────────
 * `FeesViewModel.mapDemandsToPendingFees()` filtered with
 * `it.status != "archived"` — a denylist of exactly one status, which means
 * every other status is treated as payable.
 *
 * On device (I2011 / Android 13) with 4 demands frozen and 2 reversed out of
 * 25, Firestore held ₹8,400 collectable and the Fees screen showed
 * "Due ₹11,800 / UNPAID (13 months)" — offering April, whose only two demands
 * were BOTH frozen, as a payable ₹900 row. After the fix the same screen read
 * "Due ₹8,400 / UNPAID (10 months)", April and May gone entirely, and June
 * reduced to ₹300 because its ₹600 tuition was reversed.
 *
 * ── WHY THIS TEST WAS REWRITTEN ──────────────────────────────────────────
 * Its first version restated the rule locally:
 *
 *     private val aggregatable = FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES + "paid"
 *
 * and asserted properties of that copy. Mutation-testing proved it worthless
 * for its own purpose: reverting the view model to the `!= "archived"`
 * denylist — the defect itself — left every assertion green.
 *
 * The rule now lives in `FeeAggregation`, and these tests call it directly, so
 * a regression in the shipped path fails here.
 *
 * ── WHY THE SHAPE IS THE BUG, NOT THE MISSING STATUS ─────────────────────
 * `PAYABLE_DEMAND_STATUSES`'s own docblock gives the reason: "a status added
 * on the admin side later defaults to NOT-payable, which is the safe
 * direction". A denylist inverts that. Adding "frozen" and "reversed" to a
 * denylist would fix today's symptom and leave the next status to slip
 * through, so the fix is the allowlist, not the two names.
 */
class FrozenDemandNotPayableTest {

    private fun demand(status: String, amount: Double = 100.0, month: String = "April") =
        FeeDemandDoc(status = status, netAmount = amount, month = month)

    // ── THE TWO STATUSES THAT CAUSED THE DEFECT ──────────────────────────

    @Test
    fun `a frozen demand never enters the aggregation`() {
        // The student exited — deactivated or TC issued. The lifecycle
        // programme proved the server freezes the fee book on both.
        assertFalse(
            "a frozen demand reached the fee aggregation — the student has exited " +
                "and the school has stopped collecting, yet the parent is shown a bill",
            FeeAggregation.isAggregatable("frozen")
        )
        assertFalse("frozen is offered as payable", FeeAggregation.isPayable("frozen"))
    }

    @Test
    fun `a reversed demand never enters the aggregation`() {
        assertFalse(
            "a reversed demand reached the fee aggregation — the admission was " +
                "reversed, so this money does not exist",
            FeeAggregation.isAggregatable("reversed")
        )
        assertFalse("reversed is offered as payable", FeeAggregation.isPayable("reversed"))
    }

    @Test
    fun `archived still never enters the aggregation`() {
        // The original denylist's single entry. It must remain excluded — the
        // fix widened the exclusion, it did not move it.
        assertFalse(FeeAggregation.isAggregatable("archived"))
    }

    // ── THE POSITIVE CONTROL ─────────────────────────────────────────────

    @Test
    fun `unpaid and partial remain payable`() {
        // Without this, every assertion above would pass on a filter that
        // rejected everything, and the screen would show no dues at all.
        assertTrue("unpaid is not payable", FeeAggregation.isPayable("unpaid"))
        assertTrue("partial is not payable", FeeAggregation.isPayable("partial"))
        assertTrue(FeeAggregation.isAggregatable("unpaid"))
        assertTrue(FeeAggregation.isAggregatable("partial"))
    }

    @Test
    fun `paid is aggregated but not payable`() {
        // The two sets are deliberately different: dropping paid would make
        // the Paid card read zero for a fully settled student.
        assertTrue(
            "paid was dropped from the aggregation — the Paid card would read zero",
            FeeAggregation.isAggregatable("paid")
        )
        assertFalse("paid is offered as payable", FeeAggregation.isPayable("paid"))
    }

    // ── THE SHIPPED FILTER, EXERCISED DIRECTLY ───────────────────────────

    @Test
    fun `the real filter drops exactly the non-collectable demands`() {
        // This is the assertion the previous version could not make: it runs
        // the function the view model calls.
        val input = listOf(
            demand("unpaid",   600.0),
            demand("partial",  300.0),
            demand("paid",     900.0),
            demand("frozen",   600.0),
            demand("reversed", 600.0),
            demand("archived", 900.0),
        )
        val kept = FeeAggregation.aggregatable(input).map { it.status }
        assertEquals(listOf("unpaid", "partial", "paid"), kept)
    }

    @Test
    fun `the device scenario reproduces exactly`() {
        // 25 demands: 19 unpaid (8,400), 4 frozen (2,200), 2 reversed (1,200).
        val demands = buildList {
            repeat(19) { add(demand("unpaid", 8400.0 / 19)) }
            repeat(4)  { add(demand("frozen", 2200.0 / 4)) }
            repeat(2)  { add(demand("reversed", 1200.0 / 2)) }
        }
        val kept = FeeAggregation.aggregatable(demands)
        assertEquals("the frozen and reversed demands survived the filter", 19, kept.size)
        assertEquals(
            "the payable total is not ₹8,400 — this is the figure the device showed",
            8400.0, kept.sumOf { it.netAmount }, 0.01
        )
    }

    @Test
    fun `an unknown future status is excluded by default`() {
        // The whole point of the allowlist. A status invented on the admin
        // side must arrive as NOT money owed until someone decides otherwise.
        assertFalse(
            "an unrecognised status was treated as payable — the denylist failure mode",
            FeeAggregation.isAggregatable("some_new_admin_status")
        )
    }

    @Test
    fun `the aggregation set is derived from the payable set, not restated`() {
        // If someone re-hardcodes one of the two they can drift apart, and the
        // Due card would stop agreeing with what is actually offered.
        assertTrue(
            "the aggregation set no longer contains the payable set",
            FeeAggregation.AGGREGATABLE_STATUSES
                .containsAll(FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES)
        )
    }
}
