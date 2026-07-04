package com.schoolsync.parent.data.model.firestore

/**
 * Single source of truth for Stories feature constants on the parent
 * side. Must stay byte-equivalent to:
 *   - teacher app : data/model/firestore/StorySharedConfig.kt
 *   - admin PHP   : application/controllers/Stories.php constants
 *
 * Parent app only reads stories so it cares about a narrower subset
 * (collection name, rate-limit bounds are teacher/admin concern only).
 */
object StorySharedConfig {

    const val COLLECTION = "stories"
    const val VIEWERS_SUBCOLLECTION = "viewers"
    /** Per-user reaction docs: stories/{id}/reactions/{userId} = {emoji, reactedAt}. */
    const val REACTIONS_SUBCOLLECTION = "reactions"

    const val EXPIRY_MILLIS = 86_400_000L

    // ── Reactions (v1) ─────────────────────────────────────────────
    /** Fixed emoji palette. Parents react; teacher/admin see counts. */
    val ALLOWED_REACTIONS = listOf("❤️", "👍", "😍", "👏", "🎉")

    // ── Audience scoping (v1) ──────────────────────────────────────
    // [audienceKey] reduces ANY representation of class+section to one
    // canonical token so a teacher's targeted write matches the parent's
    // read even though the two apps' Constants.classKey differ (teacher
    // adds an ordinal "8"→"Class 8th", parent does not). All of
    // "Class 8th"/"8th"/"8" → "8"; "Section A"/"A" → "a".
    //   ⚠ MUST stay byte-identical to teacher StorySharedConfig +
    //     admin Stories.php.

    /** Canonical class+section token, e.g. ("Class 8th","Section A") → "8-a". */
    fun audienceKey(className: String, section: String): String =
        "${canonClassToken(className)}-${canonSectionToken(section)}"

    private fun canonClassToken(raw: String): String {
        var s = raw.trim().lowercase()
        if (s.startsWith("class ")) s = s.removePrefix("class ").trim()
        Regex("^(\\d+)(st|nd|rd|th)$").find(s)?.let { s = it.groupValues[1] }
        return s
    }

    private fun canonSectionToken(raw: String): String {
        var s = raw.trim().lowercase()
        if (s.startsWith("section ")) s = s.removePrefix("section ").trim()
        return s
    }

    const val AUTHOR_TEACHER = "teacher"
    const val AUTHOR_ADMIN   = "admin"

    const val PRIORITY_HIGH   = "high"
    const val PRIORITY_NORMAL = "normal"

    const val STATUS_ACTIVE  = "active"
    const val STATUS_FLAGGED = "flagged"
    const val STATUS_REMOVED = "removed"
}
