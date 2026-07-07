package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class VehicleDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val registrationNo: String = "",
    val type: String = "bus",
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val capacity: Int = 0,
    val color: String = "",
    val currentRouteId: String = "",
    // Legacy — retained for compat with pre-F9 UI code (F1-F9 writers no
    // longer populate it). F9+ canonical driver reference is staffId
    // below (mapped to server-side staff_id).
    val driverId: String = "",
    // F10 (2026-07-07) — canonical driver reference matching F1-F9 admin
    // and Teacher-app writes. @PropertyName aligns Kotlin camelCase with
    // Firestore snake_case.
    @get:com.google.firebase.firestore.PropertyName("staff_id")
    @set:com.google.firebase.firestore.PropertyName("staff_id")
    var staffId: String = "",
    val driverName: String = "",
    val driverPhone: String = "",
    val insuranceExpiry: String = "",
    val pucExpiry: String = "",
    val fitnessExpiry: String = "",
    val permitExpiry: String = "",
    val gpsDeviceId: String = "",
    val status: String = "active",
    val updatedAt: Any? = null
)
