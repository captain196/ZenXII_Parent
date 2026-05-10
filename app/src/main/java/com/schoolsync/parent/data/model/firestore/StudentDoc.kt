package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Mirror of the canonical student document the admin panel writes to the
 * Firestore `students` collection.
 *
 * Field names match what `Entity_firestore_sync::syncStudent` emits
 * (camelCase). For boolean fields we pin Firestore's property name with
 * `@PropertyName` because Kotlin's getter-name rules for booleans (`is*`
 * vs `get*`) can mismatch Firestore's reflection lookup.
 *
 * `address`, `documents`, `monthFee`, `sessions`, `createdAt` are all
 * declared `Any?` because production data has mixed shapes:
 *   - `address` is sometimes a flat String, sometimes a nested map
 *     ({Street, City, State, PostalCode}) depending on which admin
 *     controller wrote it. A `String` declaration crashes the whole
 *     document with "Could not deserialize object. Failed to convert
 *     value of type java.util.HashMap to String".
 *   - `documents` carries the legacy `Doc` nested map (Photo, BirthCert,
 *     ProfilePic, …) — never a primitive.
 *   - `monthFee` may be a numeric (Long/Double) or a string.
 *   - `sessions` is a per-session enrollment map {sessionKey: {...}}.
 *   - `createdAt` may arrive as Firestore `Timestamp` OR an ISO string
 *     depending on which writer last touched the doc.
 *
 * Keeping these `Any?` makes deserialisation tolerant of cross-system
 * drift; consumers that need the values cast/parse them at the call site.
 *
 * Adding a new camelCase field here is always safe — missing fields
 * deserialise to their type's default.
 */
data class StudentDoc(
    @DocumentId
    val id: String = "",

    // ── Identity ─────────────────────────────────────────────────────
    val userId: String = "",
    val studentId: String = "",
    val schoolId: String = "",
    val schoolCode: String = "",
    val parentDbKey: String = "",
    val session: String = "",

    // ── Personal ─────────────────────────────────────────────────────
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val phoneNumber: String = "",
    val dob: String = "",
    val gender: String = "",
    val bloodGroup: String = "",
    val religion: String = "",
    val nationality: String = "",
    val category: String = "",

    // ── Class & Section (canonical Phase 1 shape) ────────────────────
    val className: String = "",          // "Class 10th"
    val section: String = "",            // "Section A"
    val classOrder: Int? = null,         // 10 | -2 (LKG) | null (unknown)
    val sectionCode: String = "",        // "A" (raw token, no prefix)
    val sectionKey: String = "",         // "Class 10th/Section A" (compound)
    val rollNo: String = "",
    val admissionDate: String = "",

    // ── Family ──────────────────────────────────────────────────────
    val fatherName: String = "",
    val fatherOccupation: String = "",
    val motherName: String = "",
    val motherOccupation: String = "",
    val guardContact: String = "",
    val guardRelation: String = "",

    // ── Address — see class-level kdoc on why this is Any? ──────────
    val address: Any? = null,

    // ── Previous education ──────────────────────────────────────────
    val preClass: String = "",
    val preSchool: String = "",
    val preMarks: String = "",

    // ── Documents / fees / sessions / status / misc ─────────────────
    val profilePic: String = "",
    val status: String = "",
    val documents: Any? = null,          // legacy `Doc` nested map
    val monthFee: Any? = null,           // number or string
    val sessions: Any? = null,           // per-session enrollment map

    // ── Timestamps ──────────────────────────────────────────────────
    val createdAt: Any? = null,          // Timestamp OR ISO string
    val updatedAt: String = "",

    /** Phase A — true on freshly-enrolled students; cleared after the
     *  parent successfully sets a new password from the parent app.
     *  When true the parent app forces a change-password screen before
     *  showing any other content. PropertyName annotation defensively
     *  pins the Firestore field name to avoid any reflection mismatch
     *  with Kotlin's getter-name generation rules for Boolean values. */
    @get:PropertyName("mustChangePassword")
    @set:PropertyName("mustChangePassword")
    var mustChangePassword: Boolean = false,
)
