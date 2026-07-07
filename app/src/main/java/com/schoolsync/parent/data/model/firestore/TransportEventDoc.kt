package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * TransportEventDoc — F8 transport event (educational tour / sports /
 * examination / etc.).
 *
 * F10 (2026-07-07) — Parent App READS this collection for the "Events"
 * card on the Transport Home screen (LC-20 P-E adjacent). Parent NEVER
 * writes. F8 admin-panel is the only writer.
 */
data class TransportEventDoc(
    @DocumentId
    val id: String = "",
    val eventId: String = "",
    val schoolId: String = "",
    val session: String = "",

    @get:PropertyName("event_type") @set:PropertyName("event_type")
    var eventType: String = "",

    val status: String = "",
    val title: String = "",
    val description: String = "",

    @get:PropertyName("event_date") @set:PropertyName("event_date")
    var eventDate: String = "",

    @get:PropertyName("event_end_date") @set:PropertyName("event_end_date")
    var eventEndDate: String = "",

    @get:PropertyName("event_start_at") @set:PropertyName("event_start_at")
    var eventStartAt: String = "",

    @get:PropertyName("event_end_at") @set:PropertyName("event_end_at")
    var eventEndAt: String = "",

    val destination: String = "",

    @get:PropertyName("pickup_location") @set:PropertyName("pickup_location")
    var pickupLocation: String = "",

    @get:PropertyName("drop_location") @set:PropertyName("drop_location")
    var dropLocation: String = "",

    @get:PropertyName("assigned_vehicle_ids") @set:PropertyName("assigned_vehicle_ids")
    var assignedVehicleIds: List<String> = emptyList(),

    @get:PropertyName("assigned_driver_ids") @set:PropertyName("assigned_driver_ids")
    var assignedDriverIds: List<String> = emptyList(),

    @get:PropertyName("roster_size") @set:PropertyName("roster_size")
    var rosterSize: Int = 0,

    @get:PropertyName("transport_charter_type") @set:PropertyName("transport_charter_type")
    var transportCharterType: String = "",

    val notes: String = "",

    @get:PropertyName("dispatched_trip_ids") @set:PropertyName("dispatched_trip_ids")
    var dispatchedTripIds: List<String> = emptyList(),

    @get:PropertyName("gps_session_ids") @set:PropertyName("gps_session_ids")
    var gpsSessionIds: List<String> = emptyList()
)
