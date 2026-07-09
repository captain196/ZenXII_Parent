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

    /**
     * Whole-school sentinel stored INSIDE audienceClassKeys so the parent's
     * server-side `array-contains-any` query can match school-wide stories
     * without a bypassable client filter (SEC-1). Writers emit `[AUDIENCE_ALL]`
     * for a whole-school story; the parent query passes `[myKeys…, AUDIENCE_ALL]`.
     *   ⚠ MUST stay byte-identical to teacher StorySharedConfig + admin Stories.php.
     */
    const val AUDIENCE_ALL = "*"

    /**
     * Canonical "is this a whole-school story?" test for READ-side code.
     * Whole-school = legacy empty list (pre-v1 docs) OR carries the
     * [AUDIENCE_ALL] sentinel (current writers emit `["*"]`). Any reader that
     * used to check `audienceClassKeys.isEmpty()` must use this instead, or
     * `["*"]` docs render a raw "*" chip / get mis-classified.
     *   ⚠ MUST stay byte-identical to teacher StorySharedConfig + admin PHP.
     */
    fun isWholeSchool(audienceClassKeys: List<String>): Boolean =
        audienceClassKeys.isEmpty() || AUDIENCE_ALL in audienceClassKeys

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
