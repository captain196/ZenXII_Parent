package com.schoolsync.parent.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
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
 * Uses signInWithEmailAndPassword with synthetic emails ({userId}@schoolsync.app).
 */
@Singleton
class FirebaseAuthManager @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirebaseAuthManager"
        private const val EMAIL_DOMAIN = "schoolsync.app"
    }

    /** Current Firebase user, null if not signed in */
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Whether the user is currently signed into Firebase */
    val isSignedIn: Boolean get() = auth.currentUser != null

    /** Firebase UID of the current user */
    val uid: String? get() = auth.currentUser?.uid

    /**
     * Sign in with email/password using synthetic email convention.
     *
     * @param userId The user ID (e.g., "STU0001")
     * @param password The user's password
     * @return The signed-in FirebaseUser, or null on failure
     */
    suspend fun signInWithEmailAndPassword(userId: String, password: String): FirebaseUser? {
        val email = "${userId.lowercase()}@$EMAIL_DOMAIN"
        return suspendCancellableCoroutine { cont ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user != null && cont.isActive) {
                        Log.d(TAG, "Firebase sign-in successful: uid=${user.uid}")
                        cont.resume(user)
                    } else if (cont.isActive) {
                        cont.resume(null)
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
     * Change the current user's password.
     *
     * @param newPassword The new password to set
     * @throws Exception if no user is signed in or update fails
     */
    suspend fun changePassword(newPassword: String) {
        val user = auth.currentUser
            ?: throw IllegalStateException("No user signed in")
        return suspendCancellableCoroutine { cont ->
            user.updatePassword(newPassword)
                .addOnSuccessListener {
                    Log.d(TAG, "Password updated successfully")
                    if (cont.isActive) cont.resume(Unit)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Password update failed", exception)
                    if (cont.isActive) cont.resumeWithException(exception)
                }
        }
    }

    /**
     * Send a password reset email to the synthetic email for the given userId.
     *
     * @param userId The user ID (e.g., "STU0001")
     */
    suspend fun resetPassword(userId: String) {
        val email = "${userId.lowercase()}@$EMAIL_DOMAIN"
        return suspendCancellableCoroutine { cont ->
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Log.d(TAG, "Password reset email sent for $userId")
                    if (cont.isActive) cont.resume(Unit)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Password reset email failed", exception)
                    if (cont.isActive) cont.resumeWithException(exception)
                }
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
     * Get the current Firebase ID token.
     * Firebase Auth SDK handles token refresh automatically.
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

    /**
     * Get the full ID token result including custom claims.
     * Used to extract role and school_id after sign-in.
     */
    suspend fun getIdTokenResult(forceRefresh: Boolean = false): GetTokenResult? {
        val user = auth.currentUser ?: return null
        return suspendCancellableCoroutine { cont ->
            user.getIdToken(forceRefresh)
                .addOnSuccessListener { result ->
                    if (cont.isActive) {
                        cont.resume(result)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to get Firebase ID token result", e)
                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }
        }
    }
}
