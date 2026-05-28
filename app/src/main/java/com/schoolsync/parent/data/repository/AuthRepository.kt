package com.schoolsync.parent.data.repository

import android.util.Log
import com.schoolsync.parent.data.firebase.FirebaseAuthManager
import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.firestore.StudentDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String, val code: Int = -1) : AuthResult<Nothing>()
}

@Singleton
class AuthRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val firebaseService: FirebaseService,
    private val firestoreService: FirestoreService
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    val isLoggedIn: Flow<Boolean> = tokenManager.isLoggedIn
    val currentUser: Flow<User> = tokenManager.user

    /**
     * Login flow — Phase 2: Firestore-first with RTDB fallback.
     *
     * 1. Firebase Auth sign-in (email/password)
     * 2. Read custom claims (role, school_id, parent_db_key)
     * 3. Read profile from Firestore students/{schoolId}_{userId}
     * 4. If Firestore fails → fallback to RTDB Users/Parents/{parentDbKey}/{userId}
     * 5. Resolve active session
     * 6. Save to TokenManager
     */
    suspend fun login(userId: String, password: String, deviceId: String): AuthResult<User> {
        return try {
            // ── Step 1: Firebase Auth ────────────────────────────────
            val firebaseUser = firebaseAuthManager.signInWithEmailAndPassword(userId, password)
                ?: return AuthResult.Error("Sign-in failed: no user returned")

            Log.d(TAG, "Firebase Auth sign-in OK: uid=${firebaseUser.uid}")

            // ── Step 2: Custom claims ───────────────────────────────
            val tokenResult = firebaseAuthManager.getIdTokenResult(forceRefresh = true)
            val claims = tokenResult?.claims ?: emptyMap()
            val role = claims["role"]?.toString() ?: ""

            val schoolId = claims["school_id"]?.toString()
                ?: claims["schoolId"]?.toString() ?: ""
            val claimParentDbKey = claims["parent_db_key"]?.toString()
                ?: claims["parentDbKey"]?.toString() ?: ""
            val claimSchoolCode = claims["school_code"]?.toString()
                ?: claims["schoolCode"]?.toString() ?: ""

            // schoolCode = the RTDB path key for Schools/{schoolCode}/...
            val schoolCode = schoolId.ifBlank {
                if (claimSchoolCode.isNotBlank()) lookupSchoolCode(claimSchoolCode) ?: claimSchoolCode
                else ""
            }
            val parentDbKey = claimParentDbKey.ifBlank { schoolCode }

            Log.d(TAG, "Claims: schoolId=$schoolId, schoolCode=$schoolCode, parentDbKey=$parentDbKey")

            // ── Step 3: Firestore-first profile read ────────────────
            val user = loadProfileFromFirestore(userId, schoolId, schoolCode, parentDbKey, role)
                ?: loadProfileFromRtdb(userId, schoolId, schoolCode, parentDbKey, role)
                ?: return AuthResult.Error("Student profile not found")

            Log.d(TAG, "Profile loaded: name=${user.name}, source=${if (user.profilePic.isNotBlank()) "Firestore" else "RTDB"}, mustChangePassword=${user.mustChangePassword}, status=${user.status}")

            // ── B2 — Status gate ────────────────────────────────────
            // Reject login for any student whose Firestore doc isn't
            // status='Active' (TC issued, withdrawn, soft-deleted, etc).
            // Pre-fix, a withdrawn student's parent could keep logging
            // in indefinitely on cached credentials and continue to
            // see fees / attendance / leave forms.
            //
            // We sign the Firebase Auth session OUT before returning so
            // the credentials don't auto-rehydrate on next app open —
            // a re-activated student (cancel_tc / Inactive→Active) gets
            // a clean re-login.
            if (!user.status.equals("Active", ignoreCase = true)) {
                Log.w(TAG, "Login blocked — student status='${user.status}' for $userId")
                try { firebaseAuthManager.signOut() } catch (_: Exception) {}
                return AuthResult.Error(
                    "Your student account is no longer active (status: ${user.status}). " +
                    "Please contact the school office.",
                    code = 403
                )
            }

            // ── Step 4: Save to TokenManager ────────────────────────
            tokenManager.saveUserDirect(user)
            tokenManager.saveDeviceId(deviceId)

            // ── Step 5: Resolve active session ──────────────────────
            // SW4 (2026-05-26): removed RTDB Schools/{schoolCode}/Config/ActiveSession
            // read. Session now comes from (a) the Firestore profile's
            // StudentDoc.session at saveUserDirect above, and (b) the live
            // observer in SchoolFirestoreRepository.observeSchool() which
            // mirrors schools/{schoolCode}.currentSession into TokenManager
            // continuously while the app is foregrounded.
            // The lookupActiveSession() helper at line 388-392 is preserved
            // unused (dead-code) so SW4 rollback is a single atomic revert.
            if (schoolCode.isNotBlank()) {
                tokenManager.saveSchoolCode(schoolCode)
            }

            // ── Step 6: Phase 7z — register the current FCM token ───
            // FCMService.onNewToken only fires when FCM rotates the
            // token (usually at install). At that moment the user
            // hasn't logged in yet, so registerFcmToken bails with
            // "no user logged in" and the token is never saved.
            // Pull the current token here on every successful login
            // so Users/Devices/{userId}/{deviceId}/fcmToken is always
            // populated and admin pushes can find it.
            try {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                    .token
                    .await()
                if (token.isNotBlank()) {
                    when (val fcm = registerFcmToken(token, deviceId)) {
                        is AuthResult.Success -> Log.d(TAG, "FCM token registered for $userId on login")
                        is AuthResult.Error   -> Log.w(TAG, "FCM token registration failed on login: ${fcm.message}")
                    }
                } else {
                    Log.w(TAG, "FCM token blank on login — skipping registration")
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM token fetch failed on login", e)
            }

            AuthResult.Success(user)

        } catch (e: Exception) {
            Log.e(TAG, "Login exception", e)
            AuthResult.Error(mapAuthError(e))
        }
    }

    /**
     * PRIMARY: Read student profile from Firestore.
     * DocId: {schoolId}_{userId}
     */
    private suspend fun loadProfileFromFirestore(
        userId: String, schoolId: String, schoolCode: String,
        parentDbKey: String, role: String
    ): User? {
        if (schoolId.isBlank()) return null

        return try {
            val docId = "${schoolId}_${userId}"
            val doc = firestoreService.getDocumentAs<StudentDoc>(
                Constants.Firestore.STUDENTS, docId
            )
            if (doc == null || doc.name.isBlank()) {
                Log.w(TAG, "Firestore profile empty for $docId")
                return null
            }

            Log.d(TAG, "Firestore profile found: $docId")

            // Fetch school display name from the schools collection
            val schoolDisplayName = try {
                val schoolDoc = firestoreService.getDocumentAs<com.schoolsync.parent.data.model.firestore.SchoolDoc>(
                    Constants.Firestore.SCHOOLS, schoolId
                )
                schoolDoc?.name ?: ""
            } catch (_: Exception) { "" }

            User(
                userId = userId,
                name = doc.name,
                email = doc.email,
                phone = doc.phone,
                role = role.ifBlank { "student" },
                schoolId = schoolId,
                schoolDisplayName = schoolDisplayName,
                profilePic = doc.profilePic,
                className = doc.className,
                section = doc.section,
                rollNo = doc.rollNo,
                fatherName = doc.fatherName,
                motherName = doc.motherName,
                dob = doc.dob,
                gender = doc.gender,
                admissionDate = doc.admissionDate,
                parentDbKey = parentDbKey,
                session = doc.session,
                schoolCode = schoolCode,
                mustChangePassword = doc.mustChangePassword,
                status = doc.status.ifBlank { "Active" },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Firestore profile read failed for $userId", e)
            null
        }
    }

    /**
     * FALLBACK: Read student profile from RTDB.
     * Path: Users/Parents/{parentDbKey}/{userId}
     * Will be removed in Phase 3 when RTDB is decommissioned.
     */
    private suspend fun loadProfileFromRtdb(
        userId: String, schoolId: String, schoolCode: String,
        parentDbKey: String, role: String
    ): User? {
        if (parentDbKey.isBlank()) return null

        return try {
            val profilePath = "Users/Parents/$parentDbKey/$userId"
            val data = firebaseService.readMap(profilePath)
            if (data.isEmpty()) {
                Log.w(TAG, "RTDB profile empty at $profilePath")
                return null
            }

            Log.d(TAG, "RTDB fallback profile loaded from $profilePath")
            User(
                userId = userId,
                name = data["Name"]?.toString() ?: data["name"]?.toString() ?: "",
                email = data["Email"]?.toString() ?: data["email"]?.toString() ?: "",
                phone = data["Phone Number"]?.toString() ?: data["Phone"]?.toString() ?: data["phone"]?.toString() ?: "",
                role = role.ifBlank { data["Role"]?.toString() ?: "student" },
                schoolId = schoolId,
                schoolDisplayName = data["SchoolName"]?.toString() ?: "",
                profilePic = data["Profile Pic"]?.toString() ?: data["profilePic"]?.toString() ?: "",
                className = Constants.Firebase.classKey(data["Class"]?.toString() ?: data["className"]?.toString() ?: ""),
                section = Constants.Firebase.sectionKey(data["Section"]?.toString() ?: data["section"]?.toString() ?: ""),
                rollNo = data["Roll No"]?.toString() ?: data["rollNo"]?.toString() ?: "",
                fatherName = data["Father Name"]?.toString() ?: data["fatherName"]?.toString() ?: "",
                motherName = data["Mother Name"]?.toString() ?: data["motherName"]?.toString() ?: "",
                dob = data["DOB"]?.toString() ?: data["dob"]?.toString() ?: "",
                gender = data["Gender"]?.toString() ?: data["gender"]?.toString() ?: "",
                admissionDate = data["Admission Date"]?.toString() ?: data["admissionDate"]?.toString() ?: "",
                parentDbKey = parentDbKey,
                // BUG-065 / SW4 Option A (2026-05-26, conservative):
                // RTDB profile fallback path cannot know the canonical
                // current session. Setting session="" intentionally;
                // SchoolFirestoreRepository.observeSchool() will self-heal
                // this within the first Firestore snapshot delivery after
                // the parent's MainActivity subscribes (typically ms).
                // Hard-fail-on-empty path NOT chosen here to preserve
                // login UX during transient Firestore unavailability.
                session = "",
                schoolCode = schoolCode
            )
        } catch (e: Exception) {
            Log.w(TAG, "RTDB profile read failed for $userId", e)
            null
        }
    }

    suspend fun logout(): AuthResult<Unit> {
        return try {
            firebaseAuthManager.signOut()
            tokenManager.clearAll()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            firebaseAuthManager.signOut()
            tokenManager.clearAll()
            AuthResult.Success(Unit)
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): AuthResult<String> {
        return try {
            val cachedUser = tokenManager.user.firstOrNull()
            val userId = cachedUser?.userId
            if (!userId.isNullOrBlank() && currentPassword.isNotBlank()) {
                try {
                    firebaseAuthManager.signInWithEmailAndPassword(userId, currentPassword)
                } catch (e: Exception) {
                    return AuthResult.Error("Current password is incorrect")
                }
            }
            firebaseAuthManager.changePassword(newPassword)

            // Phase A — clear `mustChangePassword` on the students doc so
            // the next login lands on Dashboard directly. Best-effort: a
            // failure here doesn't block the password change itself.
            //
            // We deliberately do NOT mirror the new password to a
            // Firestore field. Firebase Auth has it securely (hashed);
            // duplicating plaintext into Firestore would create an
            // unnecessary leak surface. Admin can recover credentials
            // via the enrollment SMS / forgot-password flow if needed.
            val schoolId = cachedUser?.schoolId.orEmpty()
            if (!userId.isNullOrBlank() && schoolId.isNotBlank()) {
                try {
                    firestoreService.updateDocument(
                        Constants.Firestore.STUDENTS,
                        "${schoolId}_${userId}",
                        mapOf(
                            "mustChangePassword" to false,
                            "updatedAt" to java.time.OffsetDateTime.now().toString(),
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear mustChangePassword flag", e)
                }
                // Mirror to the cached user profile so MainActivity /
                // dashboard see the cleared flag without a full re-login.
                if (cachedUser != null && cachedUser.mustChangePassword) {
                    try {
                        tokenManager.saveUserDirect(cachedUser.copy(mustChangePassword = false))
                    } catch (_: Exception) { /* best-effort */ }
                }
            }
            AuthResult.Success("Password changed successfully")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Failed to change password")
        }
    }

    suspend fun registerFcmToken(fcmToken: String, deviceId: String): AuthResult<Unit> {
        return try {
            val user = tokenManager.user.firstOrNull()
            val userId = user?.userId
            if (userId.isNullOrBlank()) return AuthResult.Error("No user logged in")

            val schoolId = user.schoolId
            val now = java.time.OffsetDateTime.now().toString()
            // Sanitize for use as a Firestore doc id (collapse anything
            // that's not alphanumeric/dash/underscore to underscore so
            // exotic device IDs from manufacturer ROMs don't break the
            // path).
            val safeDeviceId = deviceId.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            val docId = "${userId}_${safeDeviceId}"

            val payload = mapOf(
                "schoolId"   to schoolId,
                "userId"     to userId,
                "deviceId"   to deviceId,
                "fcmToken"   to fcmToken,
                "platform"   to "android",
                "status"     to "active",
                "lastActive" to now,
                "appRole"    to "parent"
            )

            // ── Firestore FIRST (canonical — rules now allow userDevices writes) ──
            var firestoreOk = false
            try {
                firestoreService.setDocument("userDevices", docId, payload, merge = true)
                firestoreOk = true
                Log.w(TAG, "FCM token written to Firestore userDevices/$docId")
            } catch (e: Exception) {
                Log.e(TAG, "FCM token Firestore write failed", e)
            }

            // ── RTDB mirror (best-effort, stays until Phase 9 cleanup) ──
            try {
                firebaseService.updateChildren(
                    "Users/Devices/$userId/$deviceId",
                    mapOf(
                        "fcmToken"   to fcmToken,
                        "status"     to "active",
                        "platform"   to "android",
                        "lastActive" to now
                    )
                )
                Log.d(TAG, "FCM token mirrored to RTDB")
            } catch (e: Exception) {
                Log.w(TAG, "FCM token RTDB mirror failed (non-fatal)", e)
            }

            if (firestoreOk) {
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error("FCM token write failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerFcmToken exception", e)
            AuthResult.Error(e.message ?: "FCM registration failed")
        }
    }

    suspend fun ensureFirebaseAuth(): Boolean = firebaseAuthManager.isSignedIn

    private suspend fun lookupSchoolCode(id: String): String? {
        return try {
            firebaseService.readString(Constants.Firebase.schoolCodePath(id))
        } catch (e: Exception) { null }
    }

    private suspend fun lookupActiveSession(schoolCode: String): String? {
        return try {
            firebaseService.readString("Schools/$schoolCode/Config/ActiveSession")
        } catch (e: Exception) { null }
    }

    private fun mapAuthError(e: Exception): String = when {
        e.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ||
        e.message?.contains("wrong-password") == true ||
        e.message?.contains("user-not-found") == true -> "Invalid ID or password"
        e.message?.contains("too-many-requests") == true -> "Too many attempts. Try later."
        e.message?.contains("network") == true -> "Network error. Check connection."
        e.message?.contains("disabled") == true -> "Account is disabled. Contact school."
        else -> e.message ?: "Login failed"
    }
}
