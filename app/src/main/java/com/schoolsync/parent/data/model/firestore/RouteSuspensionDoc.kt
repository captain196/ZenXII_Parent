package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * RouteSuspensionDoc — F2 route suspension record.
 *
 * F10 (2026-07-07) — Parent App READS route change history (LC-20 P-E)
 * to explain "why is my child's bus not running today". Parent NEVER
 * writes.
 */
data class RouteSuspensionDoc(
    @DocumentId
    val id: String = "",
    val suspensionId: String = "",
    val schoolId: String = "",

    @get:PropertyName("route_id") @set:PropertyName("route_id")
    var routeId: String = "",

    @get:PropertyName("reason_category") @set:PropertyName("reason_category")
    var reasonCategory: String = "",

    val reason: String = "",

    @get:PropertyName("suspended_from") @set:PropertyName("suspended_from")
    var suspendedFrom: String = "",

    @get:PropertyName("suspended_to") @set:PropertyName("suspended_to")
    var suspendedTo: String = "",

    @get:PropertyName("resumed_at") @set:PropertyName("resumed_at")
    var resumedAt: Any? = null,

    val status: String = "",   // Active, Ended
    val createdAt: Any? = null
)
