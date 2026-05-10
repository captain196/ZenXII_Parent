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
                mustChangePassword = loggedIn && mustChange,
            )
        }
    }

    fun markOnboardingSeen() {
        prefs.edit().putBoolean("onboarding_seen", true).apply()
    }
}
