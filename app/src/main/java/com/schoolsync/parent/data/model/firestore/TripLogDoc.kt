package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * TripLogDoc — Firestore trip record.
 *
 * F10 (2026-07-07) — additive fields land here matching what F7/F8/F9
 * write on the PHP+Teacher sides. The Kotlin base fields (id, schoolId,
 * vehicleId, date, tripType, startTime, endTime, startOdometer,
 * endOdometer, distance, fuelUsed, stopsCompleted, alerts) stay
 * unchanged so any pre-existing consumer keeps working.
 *
 * Parent app is a READ-ONLY consumer — never writes to tripLogs.
 * F1-F9 writers use snake_case field names on Firestore; the
 * @PropertyName annotations map camelCase Kotlin properties to those
 * snake_case fields so both directions deserialise cleanly.
 */
data class TripLogDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val vehicleId: String = "",
    val date: String = "",
    val tripType: String = "morning",      // morning, afternoon
    val startTime: String = "",
    val endTime: String = "",
    val startOdometer: Double = 0.0,
    val endOdometer: Double = 0.0,
    val distance: Double = 0.0,
    val fuelUsed: Double = 0.0,
    val stopsCompleted: Int = 0,
    val alerts: List<String> = emptyList(),

    // ── F10 additive fields (mapped to server-side snake_case) ──────────
    @get:com.google.firebase.firestore.PropertyName("driver_staff_id")
    @set:com.google.firebase.firestore.PropertyName("driver_staff_id")
    var driverStaffId: String = "",

    @get:com.google.firebase.firestore.PropertyName("trip_direction")
    @set:com.google.firebase.firestore.PropertyName("trip_direction")
    var tripDirection: String = "",

    @get:com.google.firebase.firestore.PropertyName("route_id")
    @set:com.google.firebase.firestore.PropertyName("route_id")
    var routeId: String = "",

    @get:com.google.firebase.firestore.PropertyName("actual_start_at")
    @set:com.google.firebase.firestore.PropertyName("actual_start_at")
    var actualStartAt: Any? = null,

    @get:com.google.firebase.firestore.PropertyName("actual_end_at")
    @set:com.google.firebase.firestore.PropertyName("actual_end_at")
    var actualEndAt: Any? = null,

    @get:com.google.firebase.firestore.PropertyName("roster_student_ids")
    @set:com.google.firebase.firestore.PropertyName("roster_student_ids")
    var rosterStudentIds: List<String> = emptyList(),

    var checkpoints: List<Map<String, Any?>> = emptyList(),

    @get:com.google.firebase.firestore.PropertyName("trip_kind")
    @set:com.google.firebase.firestore.PropertyName("trip_kind")
    var tripKind: String = "Regular",

    @get:com.google.firebase.firestore.PropertyName("event_id")
    @set:com.google.firebase.firestore.PropertyName("event_id")
    var eventId: String = "",

    // F10 — GPS-native placeholder. When the future GPS phase populates
    // this field, the Parent App transparently shows the "Live tracking"
    // affordance on MyBusScreen. NULL today; do not read for tracking.
    @get:com.google.firebase.firestore.PropertyName("gps_session_id")
    @set:com.google.firebase.firestore.PropertyName("gps_session_id")
    var gpsSessionId: String = "",

    var status: String = "",

    @get:com.google.firebase.firestore.PropertyName("route_progress_pct")
    @set:com.google.firebase.firestore.PropertyName("route_progress_pct")
    var routeProgressPct: Int = 0,

    @get:com.google.firebase.firestore.PropertyName("next_checkpoint_id")
    @set:com.google.firebase.firestore.PropertyName("next_checkpoint_id")
    var nextCheckpointId: String = ""
)
