package com.schoolsync.parent.ui.fees

import com.schoolsync.parent.data.model.firestore.FeeDemandDoc
import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  WHICH DEMANDS MAY ENTER THE FEE AGGREGATION
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ## Why this is its own file
 *
 * This rule lived inline in `FeesViewModel.mapDemandsToPendingFees()`, where
 * the only way to test it was to restate it in the test — and
 * `FrozenDemandNotPayableTest` did exactly that:
 *
 *     private val aggregatable = FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES + "paid"
 *
 * That line asserts properties of the TEST'S OWN copy. Mutation-testing proved
 * the consequence: reverting the view model to its original
 * `it.status != "archived"` denylist — the PA-FEE-1 defect itself — left every
 * one of those assertions green. The test could not fail for the bug it was
 * written to catch.
 *
 * `FeeDueDates` in this same package was extracted for precisely this reason,
 * and says so: "a copy passes whether or not the real code is right". This is
 * the same move, for the same reason.
 *
 * ## The rule
 *
 * An ALLOWLIST, never a denylist. `PAYABLE_DEMAND_STATUSES` documents why:
 * "a status added on the admin side later defaults to NOT-payable, which is
 * the safe direction". A denylist inverts that — a new admin-side status
 * arrives in the app as money owed, which is how `frozen` and `reversed` came
 * to be offered to parents as payable.
 *
 * `paid` is aggregatable but NOT payable: it feeds the Paid card and the
 * settled-month rows. The two sets are deliberately separate.
 */
internal object FeeAggregation {

    /**
     * Statuses that may take part in the Total / Paid / Due aggregation.
     * Derived from the payable set so the two can never drift apart.
     */
    val AGGREGATABLE_STATUSES: Set<String> =
        FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES + "paid"

    /** True iff this demand may enter the aggregation at all. */
    fun isAggregatable(status: String): Boolean = status in AGGREGATABLE_STATUSES

    /**
     * True iff a parent may be offered this demand for payment.
     *
     * Deliberately NOT `AGGREGATABLE_STATUSES`: that set adds `paid`, which
     * belongs in the Total/Paid cards but must never be offered for payment
     * again. The two sets differ by exactly that one status, which is why they
     * are separate functions rather than one.
     */
    fun isPayable(status: String): Boolean =
        status in FeeFirestoreRepository.PAYABLE_DEMAND_STATUSES

    /**
     * The demands that may enter the aggregation, in input order.
     *
     * This is the function the view model actually calls, so a test that
     * exercises it is testing the shipped behaviour rather than a restatement
     * of it.
     */
    fun aggregatable(demands: List<FeeDemandDoc>): List<FeeDemandDoc> =
        demands.filter { isAggregatable(it.status) }
}
