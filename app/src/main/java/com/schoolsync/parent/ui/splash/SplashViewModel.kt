package com.schoolsync.parent.ui.splash

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.StudentDoc
import com.schoolsync.parent.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class SplashState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val hasSeenOnboarding: Boolean = false,
    /** Phase A — true when the cached user has the force-change flag set.
     *  Splash → ForceChangePassword instead of Splash → Main on cold start.
     *  Survives the user closing the app mid-force-change. */
    val mustChangePassword: Boolean = false,
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val firestoreService: FirestoreService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object { private const val TAG = "SplashVM" }

    private val _state = MutableStateFlow(SplashState())
    val state = _state.asStateFlow()

    private val prefs by lazy {
        context.getSharedPreferences("schoolsync_onboarding", Context.MODE_PRIVATE)
    }

    init {
        viewModelScope.launch {
            val loggedIn = tokenManager.isLoggedIn.first()
            val seenOnboarding = prefs.getBoolean("onboarding_seen", false)
            val cachedUser = tokenManager.user.first()

            // Force-refresh the Firebase ID token on cold start so the
            // security-rules claims (school_id, role, parent_db_key) are
            // always current. Custom claims set/backfilled AFTER the user's
            // last fresh login only reach the token on refresh; a session
            // restored with a stale token has NO school_id claim, so
            // tenantActive()/isSameSchool() reject EVERY read (stories,
            // attendance, fees, flags…) with PERMISSION_DENIED until the
            // token happens to auto-refresh. Refreshing here closes that
            // window. Best-effort: offline / transient failures fall through
            // to the cached session rather than blocking the splash.
            // Also captured here: the must_change_password CLAIM off the freshly-
            // refreshed token. OR-ing it with the Firestore re-check below means an
            // admin reset still gates even when the Firestore doc read fails/offline
            // (the claim rides the locally-cached token) — never falling open.
            var claimMustChange = false
            if (loggedIn) {
                try {
                    val tokenResult = com.google.firebase.auth.FirebaseAuth.getInstance()
                        .currentUser?.getIdToken(true)?.await()
                    Log.d(TAG, "Splash: ID token force-refreshed")
                    if (tokenResult != null) {
                        claimMustChange = when (val v = tokenResult.claims["must_change_password"]) {
                            is Boolean -> v
                            is String  -> v.equals("true", ignoreCase = true)
                            else       -> false
                        }
                    }
                } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                    // Session is dead — account disabled/deleted or refresh token
                    // revoked (e.g. by an admin password reset). Sign out cleanly and
                    // route to Login instead of a logged-in-but-broken PERMISSION_DENIED
                    // state. (Offline throws FirebaseNetworkException → generic catch.)
                    Log.w(TAG, "Splash: session invalid — signing out", e)
                    try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() } catch (_: Exception) { }
                    tokenManager.clearAll()
                    _state.value = SplashState(
                        isLoading = false,
                        isLoggedIn = false,
                        hasSeenOnboarding = seenOnboarding,
                        mustChangePassword = false,
                    )
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "Splash: token refresh failed (using cached session)", e)
                }
            }

            // Authoritative re-check from Firestore. The cached User can
            // be stale (e.g. user logged in before this field existed in
            // DataStore, or admin set the flag after the parent's last
            // login). Fetching the latest students doc on every launch
            // means the gate can't be bypassed by a stale cache.
            var mustChange = cachedUser.mustChangePassword
            if (loggedIn && cachedUser.userId.isNotBlank() && cachedUser.schoolId.isNotBlank()) {
                try {
                    val docId = "${cachedUser.schoolId}_${cachedUser.userId}"
                    val doc = firestoreService.getDocumentAs<StudentDoc>(
                        Constants.Firestore.STUDENTS, docId
                    )
                    if (doc != null) {
                        Log.d(TAG, "Splash Firestore re-check: docId=$docId mustChangePassword=${doc.mustChangePassword} (cache was=$mustChange)")
                        mustChange = doc.mustChangePassword
                        // Sync the cached User if it diverges so the rest
                        // of the app sees the truth too.
                        if (doc.mustChangePassword != cachedUser.mustChangePassword) {
                            tokenManager.saveUserDirect(cachedUser.copy(mustChangePassword = doc.mustChangePassword))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Splash Firestore re-check failed; falling back to cache", e)
                }
            }

            _state.value = SplashState(
                isLoading = false,
                isLoggedIn = loggedIn,
                hasSeenOnboarding = seenOnboarding,
                mustChangePassword = loggedIn && (mustChange || claimMustChange),
            )
        }
    }

    fun markOnboardingSeen() {
        prefs.edit().putBoolean("onboarding_seen", true).apply()
    }
}
