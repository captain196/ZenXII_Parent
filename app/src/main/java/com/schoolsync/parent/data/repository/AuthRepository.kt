package com.schoolsync.parent.data.repository

import android.util.Log
import com.schoolsync.parent.data.firebase.FirebaseAuthManager
import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.ChangePasswordRequest
import com.schoolsync.parent.data.model.LoginRequest
import com.schoolsync.parent.data.model.LogoutRequest
import com.schoolsync.parent.data.model.RefreshRequest
import com.schoolsync.parent.data.model.RegisterFcmRequest
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.remote.ApiService
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String, val code: Int = -1) : AuthResult<Nothing>()
}

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val firebaseService: FirebaseService
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    /** Observe login state */
    val isLoggedIn: Flow<Boolean> = tokenManager.isLoggedIn

    /** Observe current user profile */
    val currentUser: Flow<User> = tokenManager.user

    /**
     * Full login flow:
     * 1. Call login API
     * 2. Store JWT tokens in DataStore
     * 3. Store user profile in DataStore
     * 4. Sign into Firebase with custom token
     * 5. Look up schoolCode from Indexes/School_codes/{schoolId}
     * 6. Store schoolCode
     */
    suspend fun login(userId: String, password: String, deviceId: String): AuthResult<User> {
        return try {
            val response = apiService.login(LoginRequest(userId, password, deviceId))

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "Login failed"
                Log.e(TAG, "Login API error: ${response.code()} - $errorBody")
                return AuthResult.Error(
                    message = parseErrorMessage(errorBody),
                    code = response.code()
                )
            }

            val loginResponse = response.body()
            if (loginResponse == null || !loginResponse.success) {
                return AuthResult.Error("Login failed: empty response")
            }

            // Step 2: Store tokens
            tokenManager.saveTokens(
                accessToken = loginResponse.accessToken,
                refreshToken = loginResponse.refreshToken,
                firebaseToken = loginResponse.firebaseToken
            )

            // Step 3: Store user profile
            tokenManager.saveUser(loginResponse.user)
            tokenManager.saveDeviceId(deviceId)

            // Step 4: Sign into Firebase
            try {
                firebaseAuthManager.signInWithCustomToken(loginResponse.firebaseToken)
                Log.d(TAG, "Firebase sign-in successful")
            } catch (e: Exception) {
                Log.e(TAG, "Firebase sign-in failed (non-fatal for login)", e)
                // Don't fail the login — Firebase auth can be retried
            }

            // Step 5: Look up school code from Firebase
            val schoolCode = lookupSchoolCode(loginResponse.user.schoolId)

            // Step 6: Store school code + resolve active session
            if (schoolCode != null) {
                tokenManager.saveSchoolCode(schoolCode)

                // Step 6b: Look up active session from Firebase
                val session = lookupActiveSession(schoolCode)
                if (session != null) {
                    tokenManager.saveSession(session)
                } else {
                    Log.w(TAG, "Could not resolve active session for schoolCode=$schoolCode")
                }
            } else {
                Log.w(TAG, "Could not resolve schoolCode for schoolId=${loginResponse.user.schoolId}")
            }

            val user = User.fromDto(loginResponse.user, schoolCode ?: "")
            AuthResult.Success(user)

        } catch (e: Exception) {
            Log.e(TAG, "Login exception", e)
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    /**
     * Refresh tokens (called by AuthInterceptor automatically,
     * but can also be called manually).
     */
    suspend fun refreshTokens(): AuthResult<Unit> {
        return try {
            val currentRefreshToken = tokenManager.refreshToken.firstOrNull()
                ?: return AuthResult.Error("No refresh token")

            val response = apiService.refreshToken(RefreshRequest(currentRefreshToken))

            if (!response.isSuccessful) {
                return AuthResult.Error("Refresh failed", response.code())
            }

            val refreshResponse = response.body()
            if (refreshResponse == null || !refreshResponse.success) {
                return AuthResult.Error("Refresh failed: empty response")
            }

            tokenManager.saveTokens(
                accessToken = refreshResponse.accessToken,
                refreshToken = refreshResponse.refreshToken,
                firebaseToken = refreshResponse.firebaseToken
            )

            // Re-authenticate with Firebase using new custom token
            firebaseAuthManager.trySignInWithCustomToken(refreshResponse.firebaseToken)

            AuthResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            AuthResult.Error(e.message ?: "Refresh failed")
        }
    }

    /**
     * Logout: revoke refresh token on server, clear local storage, sign out Firebase.
     */
    suspend fun logout(): AuthResult<Unit> {
        return try {
            val refreshToken = tokenManager.refreshToken.firstOrNull()
            val deviceId = tokenManager.deviceId.firstOrNull()
            if (refreshToken != null) {
                try {
                    apiService.logout(LogoutRequest(refreshToken, deviceId))
                } catch (e: Exception) {
                    Log.w(TAG, "Server logout failed (non-fatal)", e)
                }
            }

            // Always clear local state regardless of server response
            firebaseAuthManager.signOut()
            tokenManager.clearAll()

            AuthResult.Success(Unit)
        } catch (e: Exception) {
            // Still clear local state
            firebaseAuthManager.signOut()
            tokenManager.clearAll()
            AuthResult.Success(Unit)
        }
    }

    /**
     * Change password (requires current auth).
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): AuthResult<String> {
        return try {
            val response = apiService.changePassword(
                ChangePasswordRequest(currentPassword, newPassword)
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "Change password failed"
                return AuthResult.Error(parseErrorMessage(errorBody), response.code())
            }

            val body = response.body()
            if (body != null && body.success) {
                AuthResult.Success(body.message ?: "Password changed successfully")
            } else {
                AuthResult.Error(body?.message ?: "Change password failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    /**
     * Register FCM token for push notifications.
     */
    suspend fun registerFcmToken(fcmToken: String, deviceId: String): AuthResult<Unit> {
        return try {
            val response = apiService.registerFcm(RegisterFcmRequest(fcmToken, deviceId))
            if (response.isSuccessful && response.body()?.success == true) {
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error("FCM registration failed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "FCM registration failed", e)
            AuthResult.Error(e.message ?: "FCM registration failed")
        }
    }

    /**
     * Re-authenticate Firebase if needed (e.g., after app restart).
     */
    suspend fun ensureFirebaseAuth(): Boolean {
        if (firebaseAuthManager.isSignedIn) return true

        val firebaseToken = tokenManager.firebaseToken.firstOrNull()
        if (firebaseToken.isNullOrBlank()) return false

        return firebaseAuthManager.trySignInWithCustomToken(firebaseToken) != null
    }

    /**
     * Look up the Firebase school key from Indexes/School_codes/{mongoSchoolId}.
     */
    private suspend fun lookupSchoolCode(mongoSchoolId: String): String? {
        return try {
            val path = Constants.Firebase.schoolCodePath(mongoSchoolId)
            firebaseService.readString(path)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to look up school code", e)
            null
        }
    }

    private suspend fun lookupActiveSession(schoolCode: String): String? {
        return try {
            firebaseService.readString("Schools/$schoolCode/Config/ActiveSession")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to look up active session", e)
            null
        }
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            // Try to extract "message" field from JSON error body
            val regex = """"message"\s*:\s*"([^"]+)"""".toRegex()
            regex.find(errorBody)?.groupValues?.get(1) ?: errorBody
        } catch (_: Exception) {
            errorBody
        }
    }
}
