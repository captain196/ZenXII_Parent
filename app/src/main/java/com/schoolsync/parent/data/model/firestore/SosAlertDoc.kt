package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class SosAlertDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val triggeredBy: String = "",
    val triggeredByRole: String = "",
    val vehicleId: String = "",
    val routeId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val alertType: String = "emergency",
    // F10 (2026-07-07) — severity from F9 (Low/Medium/High/Critical).
    // Default 'High' matches server-side SOS_DEFAULT_SEVERITY; pre-F9
    // docs deserialise cleanly (Kotlin data classes tolerate unknown
    // fields and use the default when a field is absent).
    val severity: String = "High",
    val message: String = "",
    val status: String = "active",         // active, responded, resolved
    val respondedBy: String = "",
    val resolvedAt: Any? = null,
    val notifiedParents: Int = 0,
    val createdAt: Any? = null
)
