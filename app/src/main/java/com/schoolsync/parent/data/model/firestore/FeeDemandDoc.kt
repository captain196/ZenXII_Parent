package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Represents a monthly fee demand raised against a student.
 *
 * Collection: `feeDemands`
 * Doc ID: auto-generated or `{schoolId}_{session}_{studentId}_{month}`
 */
data class FeeDemandDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val sectionKey: String = "",
    val month: String = "",
    val demandId: String = "",
    val feeHead: String = "",
    val category: String = "",
    val frequency: String = "",
    val period: String = "",
    val feeItems: Map<String, Double> = emptyMap(),
    val grossAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val fineAmount: Double = 0.0,
    val netAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val balance: Double = 0.0,
    val status: String = "unpaid",
    // PARENT-DUE-1 — the server's own due date for this demand.
    // Written by Fee_firestore_txn::_buildDemandDoc/updateDemand and treated as
    // authoritative by the late-fee engine, but it was never declared here, so
    // the app could not read it and fell back to inferring a due date from the
    // month label. Empty on demands generated before dueDate was populated.
    val dueDate: String = "",

    /**
     * PA-FEE-GRACE — the last day this demand is still on time, computed by
     * the SERVER as `dueDate + overdue_grace_days` and stamped at generation.
     *
     * The app used to decide this itself, hard-coding a 15-day grace that
     * matched no server rule: the server's only grace was
     * `late_fee_grace_days`, which governs FINES, defaults to 0, and applies
     * only when late fees are enabled. A demand due 2026-04-10 was therefore
     * 139 days past due on 2026-08-27 while this app displayed "Overdue ·
     * 124d", and showed nothing at all for the first fifteen days.
     *
     * The POLICY is deliberately not sent to the app. `feeSettings` also holds
     * the gateway `api_secret`, so letting the app read it would be wrong, and
     * a new parent-readable config collection would need its own Firestore
     * rule. Stamping the derived DATE onto the demand — which the app already
     * reads — means the app holds no policy, cannot drift from the school's
     * setting, and needs no new permission.
     *
     * EMPTY on every demand generated before this field existed, and on
     * demands with no due date at all (Transport and Hostel raise some).
     * `FeeDueDates` falls back to `dueDate` there, which is exactly the
     * zero-grace answer — an old demand degrades to the plain truth rather
     * than to an invented grace period.
     */
    val overdueFrom: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)
