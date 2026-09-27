package com.schoolsync.parent.fees

import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  A PARENT MUST NEVER BE SHOWN MONEY THE SCHOOL DECIDED NOT TO COLLECT
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── THE DEFECT ───────────────────────────────────────────────────────────
 * `getPendingDemands()` filtered `status != "paid"`, with the server also
 * excluding "archived". That is a DENYLIST over a six-value vocabulary, and
 * it admitted the two values that mean "do not collect":
 *
 *     unpaid   payable
 *     partial  payable
 *     paid     settled
 *     archived superseded by a later class/section
 *     frozen   student exited — do not collect      <-- shown as dues
 *     reversed admission reversed                   <-- shown as dues
 *
 * So a withdrawn student's parent, and a family whose admission had been
 * reversed, were shown outstanding dues they did not owe — and could act on
 * them. The admin side already agreed on the meaning: `Fee_late_fee_service`
 * exempts exactly `frozen` from fines, and `Fee_lifecycle`'s docblock defines
 * the vocabulary. Only the app disagreed.
 *
 * The method's own docblock had always claimed the "unpaid + partial only"
 * semantic. The implementation was what drifted.
 *
 * ── WHY THE DIRECTION OF THE LIST MATTERS ────────────────────────────────
 * An allowlist fails safe. A status added on the admin side later shows up as
 * NOT payable until someone adds it here on purpose. Under the old denylist
 * the default was the opposite: any new state became money owed.
 */
class PayableDemandStatusTest {

    @Test
    fun `the two collectable statuses are payable`() {
        assertTrue("unpaid" in FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES)
        assertTrue("partial" in FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES)
    }

    @Test
    fun `a frozen demand is never payable`() {
        // The LC-1 / LC-6 state: the student left, every exit door freezes the
        // fee book, and a frozen demand means "do not collect".
        assertFalse("a frozen demand is offered to the parent as an outstanding due",
            "frozen" in FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES)
    }

    @Test
    fun `a reversed admission is never payable`() {
        // The admission was undone. Asking that family for money is worse than
        // a display bug.
        assertFalse("a reversed admission's demands are offered as dues",
            "reversed" in FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES)
    }

    @Test
    fun `settled and superseded demands are not payable`() {
        assertFalse("paid" in FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES)
        assertFalse("archived" in FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES)
    }

    @Test
    fun `an unknown status fails closed`() {
        // The whole point of the allowlist. A status introduced on the admin
        // side must not become money owed by default.
        assertFalse("a status this app has never heard of is treated as payable",
            "some_future_state" in FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES)
        assertFalse("" in FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES)
    }

    @Test
    fun `the payable set is exactly the two collectable statuses`() {
        // Pinned as a whole, so a value cannot be added without this test
        // being updated deliberately.
        assertTrue(FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES == setOf("unpaid", "partial"))
    }
}
