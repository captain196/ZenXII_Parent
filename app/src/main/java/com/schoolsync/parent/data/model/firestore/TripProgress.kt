package com.schoolsync.parent.data.model.firestore

/**
 * Stop progress for a running trip — F13.2 parent slice.
 *
 * Derived from `tripLogs.checkpoints[]`, the same itinerary the admin fleet map
 * and `Transport_fleet_service` read. Deliberately mirrors the server's rules so
 * a parent and an administrator can never disagree about which stop a bus is on:
 *
 *  • a stop counts as served only when the DRIVER confirmed it
 *    (`checkpoint_state` = Reached or Skipped) — never inferred from GPS
 *    proximity, because passing near a stop is not serving it;
 *  • "N stops away" counts only PENDING stops before the child's own stop;
 *  • schedule adherence compares now against `scheduled_pickup_time`, frozen
 *    onto the trip when it was created.
 *
 * There is deliberately **no ETA**. An arrival time needs travel-time modelling
 * that does not exist, and `distance ÷ speed` is worst exactly where it matters
 * — a bus waiting at a light reports 0 km/h. A parent deciding when to walk a
 * child to the stop is precisely who a wrong number would hurt, so F13.2 gives
 * them stop counts and schedule facts instead. Deferred to F13.3.
 */
data class TripProgress(
    val totalStops: Int = 0,
    val stopsDone: Int = 0,
    val stopsRemaining: Int = 0,
    val nextStopName: String = "",
    /** Pending stops before the child's own stop; null when it is not on this trip. */
    val stopsAwayFromMine: Int? = null,
    val myStopName: String = "",
    /** True once the child's stop has already been served. */
    val myStopPassed: Boolean = false,
    val progressPct: Int = 0,
) {
    /**
     * Parent-facing sentence. Returns null when there is nothing honest to say,
     * so the caller renders nothing rather than filler.
     */
    fun summaryOrNull(): String? = when {
        totalStops == 0 -> null
        myStopPassed -> "Your stop has been served"
        stopsAwayFromMine == 0 -> "Arriving at your stop next"
        stopsAwayFromMine == 1 -> "1 stop away from your stop"
        stopsAwayFromMine != null -> "$stopsAwayFromMine stops away from your stop"
        nextStopName.isNotBlank() -> "Next stop: $nextStopName"
        else -> null
    }

    companion object {
        /**
         * @param checkpoints raw `tripLogs.checkpoints[]`
         * @param myStopId    the child's stop from their route assignment; blank
         *                    when unknown, in which case per-child fields stay null
         */
        fun from(checkpoints: List<Map<String, Any?>>, myStopId: String): TripProgress {
            if (checkpoints.isEmpty()) return TripProgress()

            // Order defensively: the array should already be ordered, but a
            // partially-written document must not scramble the itinerary.
            val ordered = checkpoints.sortedBy { (it["order"] as? Number)?.toInt() ?: 0 }

            var done = 0
            var next = ""
            var away: Int? = null
            var myName = ""
            var passed = false
            var pendingBeforeMine = 0
            var reachedMine = false

            for (c in ordered) {
                val state = (c["checkpoint_state"] as? String).orEmpty().ifBlank { "Pending" }
                val id = (c["stop_id"] as? String).orEmpty()
                val name = (c["stop_name"] as? String).orEmpty()
                val served = state == "Reached" || state == "Skipped"

                if (served) done++
                if (next.isBlank() && !served) next = name

                if (myStopId.isNotBlank() && id == myStopId) {
                    myName = name
                    if (served) { passed = true; reachedMine = true } else { away = pendingBeforeMine }
                } else if (!reachedMine && !served) {
                    pendingBeforeMine++
                }
            }

            return TripProgress(
                totalStops = ordered.size,
                stopsDone = done,
                stopsRemaining = (ordered.size - done).coerceAtLeast(0),
                nextStopName = next,
                stopsAwayFromMine = away,
                myStopName = myName,
                myStopPassed = passed,
                progressPct = if (ordered.isEmpty()) 0 else (done * 100) / ordered.size,
            )
        }
    }
}
