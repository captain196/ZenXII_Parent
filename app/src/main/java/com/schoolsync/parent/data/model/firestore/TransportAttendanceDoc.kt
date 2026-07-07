package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * TransportAttendanceDoc — per-student boarding event.
 *
 * F10 (2026-07-07) — Parent App READS this collection for the child's
 * transport attendance history (LC-20 P-B). Parent NEVER writes. The
 * doc-id is deterministic ({schoolId}_{tripId}_{studentId}_{event_type})
 * so pre-F9 admin-fired attendance and F9 driver-fired attendance
 * coexist in the same collection with the same shape.
 *
 * event_type ∈ {boarded_pickup, dropped_home, no_show, alternate_pickup}
 * marked_by_role ∈ {'admin', 'driver'} — lets the Parent UI show
 *   "Marked by driver" vs "Recorded by school office"
 */
data class TransportAttendanceDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val tripId: String = "",
    val studentId: String = "",

    @get:PropertyName("route_id") @set:PropertyName("route_id")
    var routeId: String = "",

    val session: String = "",

    @get:PropertyName("trip_date") @set:PropertyName("trip_date")
    var tripDate: String = "",

    @get:PropertyName("trip_direction") @set:PropertyName("trip_direction")
    var tripDirection: String = "",

    @get:PropertyName("planned_stop_id") @set:PropertyName("planned_stop_id")
    var plannedStopId: String = "",

    @get:PropertyName("actual_stop_id") @set:PropertyName("actual_stop_id")
    var actualStopId: String = "",

    @get:PropertyName("event_type") @set:PropertyName("event_type")
    var eventType: String = "",

    @get:PropertyName("event_at") @set:PropertyName("event_at")
    var eventAt: Any? = null,

    @get:PropertyName("marked_by_staff_id") @set:PropertyName("marked_by_staff_id")
    var markedByStaffId: String = "",

    @get:PropertyName("marked_by_role") @set:PropertyName("marked_by_role")
    var markedByRole: String = "",

    val notes: String = "",

    // GPS-native reserved (NULL until future GPS phase populates)
    @get:PropertyName("gps_session_id") @set:PropertyName("gps_session_id")
    var gpsSessionId: String = ""
)
