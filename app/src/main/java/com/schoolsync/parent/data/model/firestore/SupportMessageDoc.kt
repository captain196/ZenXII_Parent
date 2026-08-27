package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * One message in a support thread.
 *
 * Document id is a Firestore AUTO-ID, deliberately. An earlier design used
 * `{schoolId}_{ticketId}_M003`, which is unsafe: a client cannot allocate a
 * sequence number without a read-then-write race, and two people replying in
 * the same second would collide on one key and silently overwrite each other.
 * Ordering is by [createdAt] alone.
 *
 * Messages are IMMUTABLE. The rules allow create only — no update, no delete —
 * so a thread is an append-only record of what was actually said.
 *
 * Note there is no `internal` or `private` flag here. Staff-internal notes live
 * in a separate `supportNotes` collection that is denied to every client, so a
 * parent cannot reach staff commentary about them even if a rule were wrong.
 * That is a stronger guarantee than a boolean on this document.
 */
data class SupportMessageDoc(
    @DocumentId
    val id: String = "",

    val schoolId: String = "",
    val ticketId: String = "",

    /**
     * Denormalised from the ticket so the READ rule needs no `get()`.
     *
     * Without it, reading a 50-message thread costs 50 extra document reads and
     * gives 50 chances to hit the failure the Stories module already shipped
     * once — a rules `get()` against a missing parent document, which made
     * views impossible for a month.
     */
    val reporterId: String = "",

    /** parent | staff | system */
    val senderType: String = "",

    val senderId: String = "",
    val senderName: String = "",

    val body: String = "",

    /** Filenames only, same rule as the ticket. */
    val attachments: List<String> = emptyList(),

    val createdAt: Any? = null
) {
    val isFromParent: Boolean get() = senderType == "parent"
    val isSystem: Boolean get() = senderType == "system"
}
