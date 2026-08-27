package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * A support ticket, as the Parent app sees it.
 *
 * Document key is `{schoolId}_{ticketId}` — e.g. `SCH_D94FE8F7AD_TKT_01J8F2K9`.
 *
 * ── Fields this app writes on create ─────────────────────────────────────────
 * Only the subset the security rules permit. The rules explicitly REJECT a
 * create carrying `assignedTo` or `ticketNo`, and require `status == "open"`,
 * `lane == "normal"`, and server-anchored timestamps. Adding a field here that
 * the rules do not expect will fail the whole write, not just drop the field.
 *
 * ── Fields this app only ever READS ──────────────────────────────────────────
 * ticketNo, assignedTo/assignedName, resolvedAt, closureReason, keywords, and
 * the reply timestamps are all server-owned. `ticketNo` in particular arrives a
 * moment AFTER creation — a Cloud Function assigns it from a transactional
 * counter — so the UI must tolerate it being 0. See E-05: a missing number is
 * cosmetic, because ticketId is the real key.
 */
data class SupportTicketDoc(
    @DocumentId
    val id: String = "",

    val schoolId: String = "",
    val ticketId: String = "",

    /** Server-assigned, sequential, human-facing. 0 until the CF patches it. */
    val ticketNo: Int = 0,

    /** Recorded for reporting; deliberately NOT filtered on. See the repository. */
    val sessionId: String = "",

    /**
     * Ships in its final four-value shape — normal | general | posh |
     * safeguarding — even though v1 only ever writes "normal". The queue index
     * leads on this column, and index composition is the most expensive thing
     * to change later.
     */
    val lane: String = "normal",

    val category: String = "",
    val subject: String = "",

    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",

    val reporterId: String = "",
    val reporterName: String = "",

    /**
     * Pseudonymous, not anonymous. reporterId is still stored — it has to be,
     * or the school cannot reply and abuse cannot be rate-limited. This flag
     * only means "do not show my name to school staff", and the app copy must
     * say exactly that rather than the word "anonymous".
     */
    val isAnonymous: Boolean = false,

    /** open | assigned | reopened | resolved | closed. Server-owned after create. */
    val status: String = "open",

    val assignedTo: String = "",
    val assignedName: String = "",

    /**
     * FILENAMES ONLY — "1.jpg", never a full path or a download URL.
     *
     * The full object path is always derived server-side as
     * `schools/{schoolId}/support/{ticketId}/{name}`. Storing only the leaf
     * makes a cross-tenant path *inexpressible* rather than merely rejected:
     * there is no string a client can put here that points at another school.
     */
    val attachments: List<String> = emptyList(),

    val messageCount: Int = 0,
    val lastMessageAt: Any? = null,

    /** Each side tracked separately, so "awaiting a reply" needs no thread read. */
    val lastStaffReplyAt: Any? = null,
    val lastParentReplyAt: Any? = null,
    val firstStaffReplyAt: Any? = null,

    val createdAt: Any? = null,
    val resolvedAt: Any? = null,
    val reopenableUntil: Any? = null,
    val closedAt: Any? = null,
    val closureReason: String = ""
)
