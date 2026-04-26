package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Parent-Teacher Meeting (PTM) event scheduled by the school.
 *
 * Collection: `ptmEvents`
 * Doc ID:     `{schoolId}_{ptmEventId}`
 * Filtered for the parent by:
 *   - `schoolId == user.schoolId`
 *   - `sectionKey == "ALL"` OR `sectionKey == "{className}/{section}"`
 *   - `date >= today` AND `status == "scheduled"`
 */
data class PtmEventDoc(
    @DocumentId
    val id: String = "",
    val ptmEventId: String = "",
    val schoolId: String = "",
    val session: String = "",
    val sectionKey: String = "",       // legacy: "ALL" or "Class 8th/Section A"
    val className: String = "",
    val section: String = "",
    val date: String = "",             // "2026-04-30"
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val status: String = "scheduled",  // scheduled / completed / cancelled

    // ── Phase-A (Section-wise PTM model) ───────────────────────────────
    /** Common time window for the whole PTM (no slots). Empty on legacy
     *  docs — fall back to slots.first().startTime / slots.last().endTime
     *  via [windowStartTime]/[windowEndTime]. */
    val startTime: String = "",
    val endTime: String   = "",
    /** One entry per (className, section) the PTM applies to. Each carries
     *  the snapshot of that section's class teacher at save-time. Empty
     *  on legacy slot-based docs — synthesised by [activeSections]. */
    val sections: List<PtmSectionAssignment> = emptyList(),

    // ── Legacy fields (Round 1–3 slot-based model) ─────────────────────
    val slots: List<PtmSlot> = emptyList(),
    val totalCapacity: Int = 0,

    val createdAt: Any? = null,
    val updatedAt: Any? = null
)

/**
 * Section-level routing: each section the PTM serves carries the snapshot
 * of its class teacher at save time. Snapshotting (vs. live lookup) means
 * a later change of `sections.{schoolId}_{className}_{section}.classTeacherId`
 * does not retroactively reroute already-submitted RSVPs.
 */
data class PtmSectionAssignment(
    val className: String = "",          // "Class 8th"
    val section: String = "",            // "Section A"
    val sectionKey: String = "",         // "Class 8th/Section A"
    val classTeacherId: String = "",
    val classTeacherName: String = ""
)

/**
 * One bookable slot inside a PTM event. The teacher field is optional —
 * blank means "any teacher", which the school-wide PTMs may use.
 */
data class PtmSlot(
    val slotIndex: Int = 0,
    val startTime: String = "",        // "16:00"
    val endTime: String = "",          // "16:15"
    val teacherId: String = "",
    val teacherName: String = "",
    val capacity: Int = 1
)

/**
 * Parent's response to a PTM event. One doc per (PTM, student) pair.
 *
 * Collection: `ptmRsvps`
 * Doc ID:     `{schoolId}_{ptmEventId}_{studentId}`
 *
 * Round-3 evolution: a parent can now book multiple teachers in a single
 * PTM. Each booking lives in [bookings]. The top-level slot/teacher fields
 * are kept as a legacy mirror (populated from `bookings[0]` on write) so
 * older app builds continue to render something sensible during rollout.
 * Use [normalizedBookings] when reading — it returns `bookings` if present,
 * otherwise materialises a single-element list from the legacy fields.
 */
data class PtmRsvpDoc(
    @DocumentId
    val id: String = "",
    val ptmEventId: String = "",
    val schoolId: String = "",
    val sectionKey: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val rollNo: String = "",
    val parentName: String = "",
    val parentPhone: String = "",
    val parentEmail: String = "",

    // ── New canonical shape (Round 3) ─────────────────────────────────
    val bookings: List<PtmBooking> = emptyList(),

    // ── Phase-A (section-wise PTM) ─────────────────────────────────────
    /** Token / queue number assigned at apply-time, scoped per
     *  (PTM × section). `null` (the default) means "not yet assigned" —
     *  distinguished from a hypothetical valid 0. Only the Phase-B
     *  apply-flow writes this field. */
    val queueNumber: Int? = null,
    /** Server-set when status transitions to applied. */
    val appliedAt: Any? = null,
    /** Server-set when class teacher marks delivered. */
    val deliveredAt: Any? = null,

    // ── Legacy mirror (Round 1–2). One release cycle of dual-write,
    //    then dropped. Reader code MUST go through normalizedBookings()
    //    rather than touching these directly. In the Phase-A model these
    //    same root fields are reused for the simplified shape:
    //      teacherId/teacherName = the section's class teacher
    //      status                = applied / delivered / no-show / declined
    val slotIndex: Int = -1,
    val slotStartTime: String = "",
    val slotEndTime: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    /** Legacy: pending/confirmed/declined/attended/no-show.
     *  Phase-A: applied/delivered/no-show/declined. Read via
     *  [normalizedStatus] to get the unified Phase-A vocabulary. */
    val status: String = "pending",
    val note: String = "",
    val respondedAt: Any? = null,
    val respondedBy: String = "",
    val markedBy: String = ""
)

/**
 * One leg of a multi-teacher RSVP. Created when a parent picks a slot for
 * a particular teacher inside a PTM. The fields mirror the slot's snapshot
 * at the moment of booking; do not derive these from the live PTM doc, so
 * a later admin edit doesn't silently mutate a parent's response history.
 */
data class PtmBooking(
    val slotIndex: Int = -1,
    val teacherId: String = "",
    val teacherName: String = "",
    val slotStartTime: String = "",
    val slotEndTime: String = "",
    /** "pending" / "confirmed" / "declined" / "attended" / "no-show" */
    val status: String = "confirmed",
    val note: String = "",
    val respondedAt: Any? = null,
    val markedBy: String = "",
    val markedAt: Any? = null
)

/**
 * Returns the bookings to render. New docs ship a non-empty `bookings`
 * list directly; legacy docs are materialised into a one-element list
 * from the top-level fields so the rest of the app speaks one shape.
 * Returns an empty list when neither path has data (truly empty doc).
 */
fun PtmRsvpDoc.normalizedBookings(): List<PtmBooking> {
    if (bookings.isNotEmpty()) return bookings
    val hasLegacyBooking =
        teacherId.isNotBlank() || slotStartTime.isNotBlank() || slotIndex >= 0
    if (!hasLegacyBooking) return emptyList()
    return listOf(
        PtmBooking(
            slotIndex     = slotIndex,
            teacherId     = teacherId,
            teacherName   = teacherName,
            slotStartTime = slotStartTime,
            slotEndTime   = slotEndTime,
            status        = status,
            note          = note,
            respondedAt   = respondedAt
        )
    )
}

/**
 * Computed roll-up across [normalizedBookings]. Replaces the previously
 * proposed `overallStatus` field on the doc — derived on read so we
 * cannot end up with a stale stored value after a teacher updates a
 * single booking's status.
 */
fun PtmRsvpDoc.computedOverallStatus(): String {
    val list = normalizedBookings()
    if (list.isEmpty()) return "pending"
    val statuses = list.map { it.status.ifBlank { "pending" } }.toSet()
    return when {
        statuses == setOf("attended")                  -> "attended"
        statuses.all { it == "declined" }              -> "all_declined"
        statuses.all { it == "confirmed" || it == "attended" } -> "all_confirmed"
        statuses.contains("confirmed") || statuses.contains("attended") -> "partial"
        else                                           -> "pending"
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Phase-A normalizers — section-wise PTM model, back-compat with legacy
// ════════════════════════════════════════════════════════════════════════

/**
 * The section assignments served by this PTM. New-shape docs return
 * [PtmEventDoc.sections] directly. Legacy slot-based docs are synthesised
 * from `sectionKey` ONLY — the slot's teacherId is intentionally NOT
 * carried over here, because slot[0]'s teacher is typically a subject
 * teacher, not the section's class teacher. Phase-B/C consumers that
 * need a class teacher must fall back to a real lookup if this returns
 * a blank `classTeacherId`. Returns empty when neither shape has data.
 */
fun PtmEventDoc.activeSections(): List<PtmSectionAssignment> {
    if (sections.isNotEmpty()) return sections
    if (sectionKey.isBlank() && className.isBlank()) return emptyList()
    return listOf(
        PtmSectionAssignment(
            className        = className,
            section          = section,
            sectionKey       = sectionKey.ifBlank { "$className/$section" }.ifBlank { "ALL" },
            classTeacherId   = "",
            classTeacherName = ""
        )
    )
}

/** Window start. Prefers root [PtmEventDoc.startTime]; falls back to first slot. */
fun PtmEventDoc.windowStartTime(): String =
    if (startTime.isNotBlank()) startTime else slots.firstOrNull()?.startTime.orEmpty()

/** Window end. Prefers root [PtmEventDoc.endTime]; falls back to last slot. */
fun PtmEventDoc.windowEndTime(): String =
    if (endTime.isNotBlank()) endTime else slots.lastOrNull()?.endTime.orEmpty()

/**
 * Locate the section assignment that serves a particular student. The
 * student passes their normalised class+section; we match case-
 * insensitively against [PtmSectionAssignment.sectionKey] OR the legacy
 * "ALL" sentinel (which means every parent applies to the same listing).
 */
fun PtmEventDoc.assignmentFor(className: String, section: String): PtmSectionAssignment? {
    val list = activeSections()
    if (list.isEmpty()) return null
    val targetKey = "$className/$section"
    return list.firstOrNull { it.sectionKey.equals(targetKey, ignoreCase = true) }
        ?: list.firstOrNull { it.sectionKey == "ALL" }
}

/**
 * Unified status in the Phase-A vocabulary: `applied`, `delivered`,
 * `no-show`, `declined`, or empty for "no response yet". Legacy docs
 * with bookings-array-based statuses are mapped:
 *   confirmed → applied   attended → delivered
 *   no-show   → no-show   declined → declined
 *   pending   → "" (no response)
 */
fun PtmRsvpDoc.normalizedStatus(): String {
    val root = status.lowercase()
    if (root in setOf("applied", "delivered", "no-show", "declined")) return root
    val list = normalizedBookings()
    if (list.isEmpty()) return ""
    val st = list.first().status.lowercase()
    return when (st) {
        "confirmed" -> "applied"
        "attended"  -> "delivered"
        "no-show"   -> "no-show"
        "declined"  -> "declined"
        else        -> ""
    }
}

/**
 * Token / queue number to render, or null when unassigned. Treats any
 * non-positive value (legacy 0 from older deserialisers) as "not set"
 * so a stray 0 doesn't poison the ordering on the teacher screen.
 */
fun PtmRsvpDoc.assignedQueueNumber(): Int? =
    queueNumber?.takeIf { it > 0 }
