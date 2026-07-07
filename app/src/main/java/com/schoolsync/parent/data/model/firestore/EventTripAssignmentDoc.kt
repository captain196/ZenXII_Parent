package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * EventTripAssignmentDoc — per-student-per-event assignment with
 * consent workflow (F8 LC-23).
 *
 * F10 (2026-07-07) — Parent App READS this collection to show whether
 * the child is on an event roster + what the consent status is.
 * Consent editing is admin-only at F10 (Refinement 1 from F8);
 * parent self-service consent update is deferred to a future feature.
 */
data class EventTripAssignmentDoc(
    @DocumentId
    val id: String = "",
    val assignmentId: String = "",
    val schoolId: String = "",

    @get:PropertyName("event_id") @set:PropertyName("event_id")
    var eventId: String = "",

    val studentId: String = "",

    @get:PropertyName("assigned_vehicle_id") @set:PropertyName("assigned_vehicle_id")
    var assignedVehicleId: String = "",

    @get:PropertyName("pickup_stop") @set:PropertyName("pickup_stop")
    var pickupStop: String = "",

    @get:PropertyName("drop_stop") @set:PropertyName("drop_stop")
    var dropStop: String = "",

    @get:PropertyName("consent_status") @set:PropertyName("consent_status")
    var consentStatus: String = "Pending",

    @get:PropertyName("consent_notes") @set:PropertyName("consent_notes")
    var consentNotes: String = ""
)
