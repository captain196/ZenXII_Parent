package com.schoolsync.parent.data.sync

import android.util.Log
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.firestore.StudentDoc
import com.schoolsync.parent.data.repository.firestore.StudentFirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  KEEPS THE CACHED PROFILE HONEST WHILE THE APP IS OPEN
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── THE GAP THIS CLOSES ──────────────────────────────────────────────────
 * Every screen reads class, section, roll number and session from the `User`
 * cached in [TokenManager]. That cache is written exactly once, by
 * `AuthRepository.login()` via `saveUserDirect()`. Nothing ever refreshed it.
 *
 * So when an admin promoted a student, the Firestore document changed and the
 * parent's app went on showing last year's class until they logged out and
 * back in. Same for a section change, a roll change, or a session rollover.
 *
 * ── WHY THIS IS NOT A NEW SOURCE OF TRUTH ────────────────────────────────
 * It adds none. Firestore remains authoritative; this only makes the existing
 * cache follow it.
 *
 * The listener already existed too. `StudentFirestoreRepository.observeStudent()`
 * wraps a real `addSnapshotListener` on the canonical `{schoolCode}_{studentId}`
 * document — and its own docblock recorded that it "had no live callers, but
 * was a trap waiting for the next consumer to wire it up". This is that
 * consumer. Nothing was revived: the retired RTDB/syncVersion machinery stays
 * retired and is not referenced here.
 *
 * Firestore's listener already handles the hard cases — reconnect after a
 * network loss, resume from background, and offline reads from its own cache —
 * so those need no mechanism of their own.
 *
 * ── STATUS, AND AN EXISTING POLICY THIS EXTENDS ──────────────────────────
 * `User.status` carries a rule already written down in its own docblock:
 * login refuses to complete when status is anything other than "Active", so a
 * withdrawn student's parent cannot sign in on stale credentials.
 *
 * That guard only ran at LOGIN. A session already open when the withdrawal
 * happened kept working. Mirroring the live status into the cache extends the
 * rule ZenX already states to the case it did not cover. This class only
 * records the change and signals it — it does not decide what a screen does
 * about it, because forcing a sign-out from a background singleton would take
 * that decision away from the UI.
 *
 * ── THE LOOP HAZARD, HANDLED DELIBERATELY ────────────────────────────────
 * Writing the cache re-emits `tokenManager.user`, which is this flow's own
 * upstream. Unguarded, that is an infinite write loop. Two guards:
 *
 *   1. the upstream is reduced to the student identity and
 *      `distinctUntilChanged`, so a profile write never re-subscribes;
 *   2. the cache is written ONLY when a watched field actually differs.
 */
@Singleton
class StudentProfileSynchronizer @Inject constructor(
    private val tokenManager: TokenManager,
    private val studentRepository: StudentFirestoreRepository,
) {

    // Carries the STATUS, not just a flag. "No longer enrolled" is true of a
    // withdrawal and of a student who finished Class 10, but saying the same
    // sentence to both families is wrong: one left, the other completed. The
    // admin side now graduates at the school's own terminal class, so Alumni
    // is a state parents will actually reach.
    private val _revokedStatus = MutableStateFlow<String?>(null)

    /**
     * The live status once it is one login would have refused, else null.
     * A screen may observe this to end the session; nothing here forces it.
     */
    val revokedStatus: StateFlow<String?> = _revokedStatus

    @Volatile private var started = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        // Idempotent: a second call must not attach a second listener, which
        // would double every write and double the loop risk.
        if (started) return
        started = true

        scope.launch {
            tokenManager.user
                // Only the identity drives subscription. Without this the
                // profile write below would re-enter here and re-subscribe.
                .map { it.userId }
                .distinctUntilChanged()
                .flatMapLatest { studentId ->
                    if (studentId.isBlank()) {
                        kotlinx.coroutines.flow.flowOf(null)
                    } else {
                        studentRepository.observeStudent(studentId)
                            // A listener failure must never take the app down.
                            // The cached profile simply stops updating, which
                            // is the pre-existing behaviour.
                            .catch { e ->
                                Log.w(TAG, "student listener failed; cached profile will not refresh", e)
                                emit(null)
                            }
                    }
                }
                .collect { doc -> if (doc != null) reconcile(doc) }
        }
    }

    private suspend fun reconcile(doc: StudentDoc) {
        val cached: User = tokenManager.user.firstOrNull() ?: return
        val next = decide(cached, doc) ?: return

        Log.i(TAG, "profile changed: class='${cached.className}'->'${next.className}' "
            + "section='${cached.section}'->'${next.section}' "
            + "session='${cached.session}'->'${next.session}' "
            + "status='${cached.status}'->'${next.status}'")

        tokenManager.saveUserDirect(next)

        // Mirrors the rule AuthRepository.login() already applies. Cleared
        // when the student is Active again, so a re-admission or a cancelled
        // TC releases the gate without the parent restarting the app.
        _revokedStatus.value =
            if (next.status.equals(STATUS_ACTIVE, ignoreCase = true)) null else next.status
    }

    companion object {
        private const val TAG = "StudentProfileSync"
        private const val STATUS_ACTIVE = "Active"

        /**
         * The whole decision, as a pure function: given what is cached and what
         * the live document says, what — if anything — should be written?
         *
         * Returning null means "write nothing". That is not an implementation
         * detail, it IS the second loop guard, and lifting it out here is what
         * makes it testable at all: [TokenManager] needs an Android Context, so
         * anything reached only through the suspend path above could be
         * exercised only on a device.
         */
        fun decide(cached: User, doc: StudentDoc): User? {
            // A document for a different student must never overwrite this
            // profile. observeStudent already scopes the docId by school, so
            // this guards a mis-wired caller rather than a cross-tenant read.
            if (doc.studentId.isNotBlank() && cached.userId.isNotBlank()
                && doc.studentId != cached.userId) {
                return null
            }

            // A blank field means "this document does not carry that value",
            // not "the value is now empty": StudentDoc defaults every field to
            // "", so a partial document would otherwise erase a good cache.
            val next = cached.copy(
                className = doc.className.ifBlank { cached.className },
                section   = doc.section.ifBlank { cached.section },
                rollNo    = doc.rollNo.ifBlank { cached.rollNo },
                session   = doc.session.ifBlank { cached.session },
                status    = doc.status.ifBlank { cached.status },
            )

            // No change, no write. Without this, writing the cache re-emits
            // tokenManager.user — this flow's own upstream — and loops forever.
            return if (next == cached) null else next
        }
    }
}
