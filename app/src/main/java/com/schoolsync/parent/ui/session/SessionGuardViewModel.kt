package com.schoolsync.parent.ui.session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.schoolsync.parent.data.firebase.FirebaseAuthManager
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.StudentDoc
import com.schoolsync.parent.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Mid-session enforcement for credential changes.
 *
 * Until this existed, `mustChangePassword` was only ever consulted by
 * SplashViewModel — i.e. on a cold start. An admin who reset a parent's password
 * did not affect a running app at all: the server revokes Firebase refresh
 * tokens, but the ID token already in memory stays valid for up to an hour, and
 * nothing re-read the flag. The parent kept full access until they happened to
 * relaunch.
 *
 * OWASP session management (ASVS V3.3) requires every other session to be
 * invalidated when a credential changes, and an ADMIN-forced reset is the
 * strongest case — it is done precisely to cut off whoever holds the account.
 * Token-based auth cannot revoke an already-issued token without checking a
 * revocation list on every request (Microsoft Entra has the same ~1h window), so
 * the achievable bar is "the user cannot continue once they next touch the app".
 * That is what this enforces.
 *
 * Two independent triggers:
 *
 *   1. App foreground (ON_RESUME) — re-derives the flag from the freshly
 *      refreshed token claims AND the Firestore mirror.
 *   2. Firebase auth state — `observeAuthState()` already existed but nothing
 *      consumed it. When Firebase drops `currentUser` (revoked refresh token,
 *      deleted or disabled account) we end the session instead of sitting in a
 *      dead one where every Firestore read fails with PERMISSION_DENIED.
 *
 * Fails CLOSED on nothing and OPEN on nothing: a transient/offline failure keeps
 * the existing session (it never invents a logout), while any positive signal
 * ends it.
 */
@HiltViewModel
class SessionGuardViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val firestoreService: FirestoreService,
) : ViewModel() {

    companion object { private const val TAG = "SessionGuard" }

    private val _sessionEnded = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Emits a user-facing reason; the host navigates to Login. */
    val sessionEnded = _sessionEnded.asSharedFlow()

    private var running = false

    /**
     * Set once a session has been ended, so the two triggers (auth-state and the
     * foreground re-check) cannot both fire and stack two toasts / two Login
     * navigations. Re-armed on the next successful sign-in, because this
     * ViewModel outlives a logout→login cycle within one app run.
     */
    private var ended = false

    init {
        viewModelScope.launch {
            firebaseAuthManager.observeAuthState().collect { firebaseUser ->
                if (firebaseUser != null) {
                    ended = false            // fresh sign-in — re-arm the guard
                } else if (tokenManager.isLoggedIn.first()) {
                    Log.w(TAG, "Firebase dropped currentUser while still signed in — ending session")
                    end("Your session has ended. Please sign in again.")
                }
            }
        }
    }

    /** Call on app foreground. Cheap no-op when signed out or already gated. */
    fun recheck() {
        if (running) return
        running = true
        viewModelScope.launch {
            try {
                if (!tokenManager.isLoggedIn.first()) return@launch
                val user = tokenManager.user.firstOrNull() ?: return@launch

                // Already inside the legitimate force-change flow: the user signed
                // in WITH the flag and the navigation gate owns them. Re-checking
                // here would log them out in the middle of setting their password.
                if (user.mustChangePassword) return@launch

                val tokenResult = try {
                    FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()
                } catch (e: FirebaseAuthInvalidUserException) {
                    // Revoked / disabled / deleted — unrecoverable, end it.
                    Log.w(TAG, "token refresh rejected — ending session", e)
                    end("Your session has ended. Please sign in again.")
                    return@launch
                } catch (e: Exception) {
                    // Offline or transient: keep the session rather than inventing
                    // a logout the user cannot explain.
                    Log.d(TAG, "token refresh failed transiently; keeping session")
                    return@launch
                }

                val claimMustChange = when (val v = tokenResult?.claims?.get("must_change_password")) {
                    is Boolean -> v
                    is String  -> v.equals("true", ignoreCase = true)
                    else       -> false
                }

                // The Firestore mirror is what an admin reset writes alongside the
                // claim; read it too so a claim that has not propagated yet cannot
                // hide a reset.
                var docMustChange = false
                val userId = user.userId
                if (!userId.isNullOrBlank() && user.schoolId.isNotBlank()) {
                    try {
                        val doc = firestoreService.getDocumentAs<StudentDoc>(
                            Constants.Firestore.STUDENTS, "${user.schoolId}_$userId"
                        )
                        docMustChange = doc?.mustChangePassword == true
                    } catch (e: Exception) {
                        Log.d(TAG, "mirror re-check failed; relying on the claim", e)
                    }
                }

                if (claimMustChange || docMustChange) {
                    Log.w(TAG, "password reset detected mid-session — ending session")
                    end("Your password was reset by your school. Please sign in with your new password.")
                }
            } finally {
                running = false
            }
        }
    }

    private suspend fun end(message: String) {
        if (ended) return
        ended = true
        // clearAll BEFORE signOut. signOut() trips observeAuthState, and its
        // collector re-enters here while isLoggedIn is still true — a second
        // toast and a second Login navigation. Clearing first makes that
        // collector a no-op; the `ended` flag covers the remaining race.
        try { tokenManager.clearAll() } catch (_: Exception) {}
        try { firebaseAuthManager.signOut() } catch (_: Exception) {}
        _sessionEnded.emit(message)
    }
}
