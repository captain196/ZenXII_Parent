package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * `vehicleLivePositions/{schoolId}_{vehicleId}` — the live bus position.
 *
 * ONE document per vehicle, overwritten in place by the driver app at most
 * once every 15 seconds. It is the ONLY document this app subscribes to for
 * live tracking, which is what keeps the parent read cost at exactly one
 * document per position change rather than a query per refresh.
 *
 * ─── PROVIDER-AGNOSTIC BY CONSTRUCTION (amendment A-1) ───────────────────
 * Every field here is canonical telemetry — degrees, km/h, metres, epoch
 * millis. There is no provider SDK type, no encoded polyline, no tile or
 * style URL, no place token. Swapping MapLibre for any other renderer touches
 * the map composable and nothing else; this model, the Firestore schema, the
 * backend and the security rules are all unaffected.
 *
 * ─── READ ACCESS ─────────────────────────────────────────────────────────
 * The Firestore rule admits a parent only when their auth uid appears in the
 * document's server-maintained `authorized_viewer_uids`, rebuilt from the
 * frozen roster at every trip start. The app never reads that field; it is
 * listed here only so the shape is documented in one place.
 *
 * Server contract: `Transport_gps_service` (admin panel).
 */
data class VehicleLivePositionDoc(
    @DocumentId
    val id: String = "",

    val schoolId: String = "",
    val vehicleId: String = "",
    val tripId: String = "",

    val lat: Double? = null,
    val lng: Double? = null,

    /** Degrees clockwise from true north; rotates the bus marker. */
    val heading: Double? = null,
    val accuracy_m: Double? = null,
    val altitude_m: Double? = null,

    @get:PropertyName("speed_kmph")
    @set:PropertyName("speed_kmph")
    var speedKmph: Double? = null,

    /** ISO-8601 capture time — display only. Ordering uses the millis field. */
    @get:PropertyName("recorded_at")
    @set:PropertyName("recorded_at")
    var recordedAt: String = "",

    /**
     * Capture time in epoch millis — the ordering key the server rule requires
     * to increase strictly, and the basis for the staleness check. Integer,
     * not a formatted string, so comparison can never depend on timezone
     * formatting.
     */
    @get:PropertyName("recorded_at_ms")
    @set:PropertyName("recorded_at_ms")
    var recordedAtMs: Long = 0L,

    @get:PropertyName("gps_session_id")
    @set:PropertyName("gps_session_id")
    var gpsSessionId: String = "",

    /**
     * Server-side kill switch. Set true at trip start and false the moment the
     * trip completes or is cancelled, at which point the document is also
     * deleted. If this is ever false the map must not present the position as
     * live, whatever its timestamp says.
     */
    @get:PropertyName("live_tracking_enabled")
    @set:PropertyName("live_tracking_enabled")
    var liveTrackingEnabled: Boolean = false,
) {
    /** True when there is an actual fix to draw. */
    val hasFix: Boolean get() = lat != null && lng != null && recordedAtMs > 0L

    /**
     * Stale when the last fix is older than [STALE_AFTER_MS].
     *
     * Mirrors `Transport_gps_service::STALE_AFTER_S` (120 s). Deliberately
     * generous relative to the 15-second write cadence: a bus in a tunnel or a
     * signal dead-zone is normal, and flapping "offline" every time a lorry
     * passes would be worse than useless.
     */
    fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean =
        !hasFix || (nowMs - recordedAtMs) > STALE_AFTER_MS

    /** Whole minutes since the last fix — for "Last seen N minutes ago". */
    fun minutesSinceFix(nowMs: Long = System.currentTimeMillis()): Long =
        if (recordedAtMs <= 0L) 0L else ((nowMs - recordedAtMs) / 60_000L).coerceAtLeast(0L)

    companion object {
        const val COLLECTION = "vehicleLivePositions"
        /** Mirrors the server's STALE_AFTER_S = 120. */
        const val STALE_AFTER_MS = 120_000L

        /** RC-3 doc id: a single `{schoolId}_` prefix. */
        fun docId(schoolId: String, vehicleId: String): String = "${schoolId}_$vehicleId"
    }
}
