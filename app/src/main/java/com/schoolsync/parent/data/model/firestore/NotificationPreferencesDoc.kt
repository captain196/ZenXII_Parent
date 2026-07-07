package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * NotificationPreferencesDoc — server-authoritative per-user mute list.
 *
 * F10 (2026-07-07) — parent opt-outs for muteable Transport notification
 * categories. Server-side enforcement lives in PHP
 * Transport_notifier::_filterByPreferences; safety-exempt categories
 * (SOS, no_show, cancelled/rescheduled events, critical incidents,
 * trip disruption) are enforced regardless of what mutedCategories
 * contains. Doc-id is {schoolId}_{userId} — Firestore rules enforce
 * owner-only read/write.
 */
data class NotificationPreferencesDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val userId: String = "",
    val mutedCategories: List<String> = emptyList(),
    val updatedAt: Any? = null
) {
    companion object {
        /**
         * Safety-exempt categories that CANNOT be muted regardless of what
         * a client writes. Mirrors PHP Transport_notifier::
         * SAFETY_EXEMPT_CATEGORIES; kept in sync manually — verifier
         * F10-2 cross-checks against PRIORITY_MATRIX Urgent entries so
         * the two never drift.
         */
        val SAFETY_EXEMPT = setOf(
            "sos.raised", "sos.responded", "sos.resolved",
            "trip.student_no_show",
            "event.cancelled", "event.rescheduled",
            "driver.critical_incident",
            "trip.disruption"
        )

        /** All muteable Transport categories the preferences UI can toggle. */
        val MUTEABLE = listOf(
            "trip.delay",
            "event.scheduled",
            "event.consent_reminder",
            "event.completed",
            "route.suspension_notice_parents"
        )
    }
}
