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

    const val EXPIRY_MILLIS = 86_400_000L

    const val AUTHOR_TEACHER = "teacher"
    const val AUTHOR_ADMIN   = "admin"

    const val PRIORITY_HIGH   = "high"
    const val PRIORITY_NORMAL = "normal"

    const val STATUS_ACTIVE  = "active"
    const val STATUS_FLAGGED = "flagged"
    const val STATUS_REMOVED = "removed"
}
