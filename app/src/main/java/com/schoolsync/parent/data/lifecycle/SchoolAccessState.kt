package com.schoolsync.parent.data.lifecycle

/**
 * School access state — the app-side half of the ZenX institution lifecycle.
 *
 * ──────────────────────────────────────────────────────────────────────
 *  WHY THIS EXISTS
 * ──────────────────────────────────────────────────────────────────────
 * Before this, the app had no idea a school could be suspended or closed.
 * Every such condition arrived as a raw Firestore PERMISSION_DENIED, which
 * several repositories then swallowed as "no data" — so a suspended school
 * rendered as an empty screen and a closed one looked like a bug.
 *
 * A permission error is NOT a lifecycle signal. It cannot distinguish
 * "the school closed", "the subscription lapsed", "your session expired"
 * and "you genuinely lack access". This type makes that distinction
 * explicit and derives it from the authoritative signal instead:
 * `tenantPublic/{schoolId}`, the thin client-readable mirror the backend
 * already maintains. No second mirror, no RTDB.
 *
 * For a PARENT this matters more than for staff: a parent has no other way
 * to learn that their child's school has closed, and "empty attendance
 * list" is the worst possible way to be told.
 *
 * ──────────────────────────────────────────────────────────────────────
 *  TWO AXES, MOST-RESTRICTIVE-WINS
 * ──────────────────────────────────────────────────────────────────────
 * COMMERCIAL  (`lifecycleState` + `adminDisabled`) — is the tenant entitled
 *             to use ZenX?
 * INSTITUTION (`institutionState`) — does the school still operate?
 *
 * Orthogonal, composed most-restrictive-wins, mirroring
 * Lifecycle_resolver.php and firestore.rules exactly.
 */

/** Institution axis, mirroring `schools.institution.state` server-side. */
enum class InstitutionState {
    PROSPECT, ONBOARDING, ACTIVE, CLOSING, CLOSED, ARCHIVED;

    companion object {
        /**
         * ABSENT ⇒ ACTIVE. The backward-compatibility rule that keeps the axis
         * inert for tenants predating the feature; matches
         * Lifecycle_resolver::normalize_institution() exactly.
         */
        fun from(raw: String?): InstitutionState =
            when (raw?.trim()?.uppercase()) {
                "PROSPECT"   -> PROSPECT
                "ONBOARDING" -> ONBOARDING
                "CLOSING"    -> CLOSING
                "CLOSED"     -> CLOSED
                "ARCHIVED"   -> ARCHIVED
                else         -> ACTIVE
            }
    }
}

/**
 * What the user may actually do, and what to tell them.
 *
 * `canUseApp` gates navigation. `isReadOnly` lets records stay visible while
 * refusing submissions and payments — the CLOSED case, where retention
 * deliberately outlives operations so a parent can still reach receipts,
 * results and certificates.
 */
sealed class SchoolAccess(
    val canUseApp: Boolean,
    val isReadOnly: Boolean,
    val title: String,
    val message: String,
) {
    object Operational : SchoolAccess(true, false, "", "")

    data class Closing(val effectiveDate: String?) : SchoolAccess(
        canUseApp = true,
        isReadOnly = false,
        title = "School closing",
        message = effectiveDate
            ?.let { "This school is closing on $it. The app continues to work as normal until then." }
            ?: "This school is preparing to close. The app continues to work as normal for now.",
    )

    object ClosedReadOnly : SchoolAccess(
        canUseApp = true,
        isReadOnly = true,
        title = "School closed",
        message = "This school has closed. You can still view your child's past records, receipts and documents, but new payments and submissions are no longer accepted.",
    )

    object Archived : SchoolAccess(
        canUseApp = false,
        isReadOnly = true,
        title = "Records archived",
        message = "This school's records have been archived. Please contact the school administrator if you need copies of your child's documents.",
    )

    object NotYetOpen : SchoolAccess(
        canUseApp = false,
        isReadOnly = true,
        title = "School not open yet",
        message = "This school is still being set up and is not open for day-to-day use yet.",
    )

    /**
     * COMMERCIAL restriction — worded so it can never be mistaken for the
     * school having closed. This is an account matter between the school and
     * ZenX, and the parent's records are unaffected.
     */
    object CommercialHold : SchoolAccess(
        canUseApp = false,
        isReadOnly = true,
        title = "Service unavailable",
        message = "Your school's ZenX service is currently on hold. Please contact the school — this is an account matter and your child's records are safe.",
    )

    /**
     * State could not be determined (offline, mirror not yet written).
     * FAIL OPEN: the server and firestore.rules remain the real gate, so an
     * unreadable mirror must not lock a parent out of an app that works.
     */
    object Unknown : SchoolAccess(true, false, "", "")
}

/**
 * Why a data load failed. Keeps four genuinely different conditions apart so
 * the UI never shows one when it means another.
 */
enum class FailureKind {
    /** No or poor connectivity — retryable, not the user's fault. */
    NETWORK,

    /** Session expired or account disabled — needs re-login. */
    AUTHENTICATION,

    /** School lifecycle or commercial state — explained by [SchoolAccess]. */
    SCHOOL_STATE,

    /** A genuine permission boundary for this specific user/action. */
    AUTHORIZATION,

    UNKNOWN,
}

object SchoolLifecycle {

    /**
     * Compose both axes exactly as the server does.
     *
     * The institution axis is reported FIRST when restrictive: telling a
     * parent "this school has closed" is truer and more useful than "the
     * subscription lapsed", even when both are true.
     */
    fun resolve(
        institutionState: String?,
        commercialState: String?,
        adminDisabled: Boolean?,
    ): SchoolAccess {
        when (InstitutionState.from(institutionState)) {
            InstitutionState.ARCHIVED   -> return SchoolAccess.Archived
            InstitutionState.CLOSED     -> return SchoolAccess.ClosedReadOnly
            InstitutionState.PROSPECT,
            InstitutionState.ONBOARDING -> return SchoolAccess.NotYetOpen
            InstitutionState.CLOSING,
            InstitutionState.ACTIVE     -> { /* fall through to the commercial axis */ }
        }

        val commercial = commercialState?.trim()?.lowercase()
        val commercialAllows = commercial in ALLOWED_COMMERCIAL
        if (adminDisabled == true || (commercial != null && commercial.isNotEmpty() && !commercialAllows)) {
            return SchoolAccess.CommercialHold
        }

        return if (InstitutionState.from(institutionState) == InstitutionState.CLOSING) {
            SchoolAccess.Closing(null)
        } else {
            SchoolAccess.Operational
        }
    }

    /** Commercial states that permit access. Mirrors the server allow-list. */
    private val ALLOWED_COMMERCIAL = setOf("trialing", "active", "expiring_soon", "grace")

    /**
     * Classify a data-load failure.
     *
     * [schoolAccess] is consulted FIRST: when the school itself is restricted,
     * a PERMISSION_DENIED is a CONSEQUENCE of that state, not an independent
     * authorization problem, and must be explained as such.
     */
    fun classify(throwable: Throwable?, schoolAccess: SchoolAccess): FailureKind {
        val name = throwable?.let { it::class.java.simpleName } ?: ""
        val text = (throwable?.message ?: "").uppercase()

        if (name.contains("Network", ignoreCase = true) ||
            text.contains("UNAVAILABLE") ||
            text.contains("OFFLINE") ||
            text.contains("DEADLINE_EXCEEDED")
        ) return FailureKind.NETWORK

        if (name.contains("FirebaseAuth", ignoreCase = true) ||
            text.contains("UNAUTHENTICATED") ||
            text.contains("TOKEN")
        ) return FailureKind.AUTHENTICATION

        if (text.contains("PERMISSION_DENIED")) {
            return if (schoolAccess.canUseApp && !schoolAccess.isReadOnly) {
                FailureKind.AUTHORIZATION
            } else {
                FailureKind.SCHOOL_STATE
            }
        }

        return FailureKind.UNKNOWN
    }

    /** A user-facing sentence for a failure. Never exposes internals. */
    fun messageFor(kind: FailureKind, schoolAccess: SchoolAccess): String = when (kind) {
        FailureKind.NETWORK ->
            "You appear to be offline. Please check your connection and try again."
        FailureKind.AUTHENTICATION ->
            "Your session has expired. Please sign in again."
        FailureKind.SCHOOL_STATE ->
            schoolAccess.message.ifEmpty { "This school is not currently available." }
        FailureKind.AUTHORIZATION ->
            "You don't have access to this section. Please contact the school."
        FailureKind.UNKNOWN ->
            "Something went wrong. Please try again."
    }
}
