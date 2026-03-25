package com.schoolsync.parent.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages Firebase Authentication state.
 * Uses custom tokens issued by the Node.js backend to sign in,
 * enabling Firebase RTDB Security Rules enforcement.
 */
@Singleton
class FirebaseAuthManager @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirebaseAuthManager"
    }

    /** Current Firebase user, null if not signed in */
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Whether the user is currently signed into Firebase */
    val isSignedIn: Boolean get() = auth.currentUser != null

    /** Firebase UID of the current user */
    val uid: String? get() = auth.currentUser?.uid

    /**
     * Sign in with a custom token received from the backend.
     * This is called after login and after token refresh.
     *
     * @param customToken The Firebase custom token from the API response
     * @return The signed-in FirebaseUser
     * @throws Exception if sign-in fails
     */
    suspend fun signInWithCustomToken(customToken: String): FirebaseUser {
        return suspendCancellableCoroutine { cont ->
            auth.signInWithCustomToken(customToken)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user != null && cont.isActive) {
                        Log.d(TAG, "Firebase sign-in successful: uid=${user.uid}")
                        cont.resume(user)
                    } else if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Firebase sign-in returned null user")
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Firebase sign-in failed", exception)
                    if (cont.isActive) {
                        cont.resumeWithException(exception)
                    }
                }
        }
    }

    /**
     * Try to sign in, returning null on failure instead of throwing.
     * Useful during refresh flows where Firebase sign-in failure shouldn't
     * block the JWT refresh.
     */
    suspend fun trySignInWithCustomToken(customToken: String): FirebaseUser? {
        return try {
            signInWithCustomToken(customToken)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign-in attempt failed (non-fatal)", e)
            null
        }
    }

    /**
     * Sign out from Firebase. Called during logout flow.
     */
    fun signOut() {
        auth.signOut()
        Log.d(TAG, "Firebase signed out")
    }

    /**
     * Observe Firebase auth state changes as a Flow.
     * Emits the current FirebaseUser (or null) whenever auth state changes.
     */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Get the current Firebase ID token (for debugging/verification).
     * This is the Firebase auth token, NOT the custom token from backend.
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): String? {
        val user = auth.currentUser ?: return null
        return suspendCancellableCoroutine { cont ->
            user.getIdToken(forceRefresh)
                .addOnSuccessListener { result ->
                    if (cont.isActive) {
                        cont.resume(result.token)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to get Firebase ID token", e)
                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }
        }
    }
}
