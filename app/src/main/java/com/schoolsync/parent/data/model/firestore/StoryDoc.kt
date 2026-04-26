package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Canonical Story document — IDENTICAL across all 3 systems
 * (Teacher app, Parent app, Admin panel).
 *
 * See teacher/data/model/firestore/StoryDoc.kt for full lifecycle docs.
 *
 * Phase C: authorId/authorName/authorPic + authorType + priority.
 * Hardening #1: single canonical expiry field (expiresAtTs Timestamp).
 * Hardening #2: legacy teacher* fields @Deprecated, removal in v2.0.
 */
data class StoryDoc(
    @DocumentId
    val id: String = "",

    // ── Identity ───────────────────────────────────────────────────
    val schoolId: String = "",

    /** Canonical (Phase C). */
    val authorId: String = "",
    val authorName: String = "",
    val authorPic: String = "",
    /** "teacher" | "admin". */
    val authorType: String = "teacher",

    /** LEGACY aliases — scheduled for removal in **v2.0**. */
    @Deprecated("Use authorId. Remove in v2.0.", ReplaceWith("authorId"))
    val teacherId: String = "",
    @Deprecated("Use authorName. Remove in v2.0.", ReplaceWith("authorName"))
    val teacherName: String = "",
    @Deprecated("Use authorPic. Remove in v2.0.", ReplaceWith("authorPic"))
    val teacherPic: String = "",

    // ── Content ────────────────────────────────────────────────────
    val mediaUrl: String = "",
    val type: String = "image",
    val caption: String = "",
    /** "high" | "normal". Admin priority pins to top of parent list. */
    val priority: String = "normal",

    // ── Lifecycle ──────────────────────────────────────────────────
    val createdAt: Any? = null,
    /** Firestore Timestamp — single canonical expiry. TTL policy
     *  targets this field. Typed Any? because Firestore delivers
     *  `com.google.firebase.Timestamp` but legacy docs may carry Long. */
    val expiresAtTs: Any? = null,
    @Deprecated("Use expiresAtTs. Remove in v2.0.", ReplaceWith("expiresAtTs"))
    val expiresAt: Long = 0,
    val viewCount: Int = 0,

    // ── Moderation (admin-only writes) ─────────────────────────────
    val status: String = "active",
    val moderatedBy: String = "",
    val moderatedByName: String = "",
    val moderatedAt: Long = 0,
    val moderationReason: String = ""
) {
    @Suppress("DEPRECATION")
    val effectiveAuthorId: String get() = authorId.ifBlank { teacherId }
    @Suppress("DEPRECATION")
    val effectiveAuthorName: String get() = authorName.ifBlank { teacherName }
    @Suppress("DEPRECATION")
    val effectiveAuthorPic: String get() = authorPic.ifBlank { teacherPic }

    /** Coerce expiresAtTs to epoch millis. Falls back to legacy
     *  [expiresAt] Long if Timestamp field absent. */
    @Suppress("DEPRECATION")
    val expiresAtMillis: Long get() = when (val ts = expiresAtTs) {
        is com.google.firebase.Timestamp -> ts.seconds * 1000L + ts.nanoseconds / 1_000_000L
        is Number -> ts.toLong()
        null -> expiresAt
        else -> expiresAt
    }
}
