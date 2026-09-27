package com.schoolsync.parent.data.repository

import android.util.Log
import com.schoolsync.parent.data.firebase.FirebaseAuthManager
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.remote.AuthApi
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
    private val firestoreService: FirestoreService,
    private val authApi: AuthApi,
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    val isLoggedIn: Flow<Boolean> = tokenManager.isLoggedIn
    val currentUser: Flow<User> = tokenManager.user

    /**
     * Login flow — Phase 3 (2026-08-13): Firestore-only.
     *
     * 1. Firebase Auth sign-in (email/password)
     * 2. Read custom claims (role, school_id, parent_db_key)
     * 3. Read profile from Firestore students/{schoolId}_{userId}
     * 4. Resolve active session
     * 5. Save to TokenManager
     *
     * The Phase 2 RTDB profile fallback (Users/Parents/{parentDbKey}/{userId})
     * is REMOVED per PARENT_APP_FIRESTORE_ONLY_HARDENING_REPORT.md — it was a
     * proven strict subset of the Firestore fields (no mustChangePassword
     * equivalent, session always blank) and the doc comment above it already
     * called this exact removal "Phase 3." Removing it means a genuine
     * Firestore outage now surfaces as a login error instead of silently
     * degrading to stale/incomplete data — the correct Firestore-only
     * behavior, not a regression.
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

            // ── Step 3: Firestore profile read (Firestore-only, Phase 3) ──
            val loadedProfile = loadProfileFromFirestore(userId, schoolId, schoolCode, parentDbKey, role)
                ?: return AuthResult.Error("Student profile not found")

            // WATERTIGHT (2026-07-20): honour the `must_change_password` CLAIM too,
            // not just the students-doc field. Closes a transient Firestore-read-miss
            // force-change bypass. The claim is set alongside the doc field on both
            // account creation and admin reset, so OR-ing them fails safe toward gating.
            val claimMustChange = when (val v = claims["must_change_password"]) {
                is Boolean -> v
                is String  -> v.equals("true", ignoreCase = true)
                else       -> false
            }
            val user = loadedProfile.copy(
                mustChangePassword = loadedProfile.mustChangePassword || claimMustChange
            )

            Log.d(TAG, "Profile loaded: name=${user.name}, mustChangePassword=${user.mustChangePassword}, status=${user.status}")

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
            // continuously while the app is foregrounded. The dead
            // lookupActiveSession() helper (kept unused since SW4 purely so
            // that revert stayed a single atomic diff) is REMOVED as of this
            // pass — SW4 has been live since 2026-05-26 with no rollback,
            // and this is now Phase 3's Firestore-only cleanup.
            if (schoolCode.isNotBlank()) {
                tokenManager.saveSchoolCode(schoolCode)
            }

            // ── Step 6: register the current FCM token ──────────────
            // FCMService.onNewToken only fires when FCM rotates the
            // token (usually at install). At that moment the user
            // hasn't logged in yet, so registerFcmToken bails with
            // "no user logged in" and the token is never saved.
            // Pull the current token here on every successful login
            // so the canonical Firestore userDevices/{userId}_{safeDeviceId}
            // doc is always populated and admin pushes can find it.
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
                // Phase 11 — already on the wire: this is the same full-document
                // GET, no extra read, no extra query, no extra round trip.
                srNo = doc.srNoText,
                admissionNumber = doc.admissionNumber.trim(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Firestore profile read failed for $userId", e)
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

    /**
     * Finalise an admin-driven password reset by calling the server's
     * /auth/clear_must_change endpoint. The server updates the Firebase
     * Auth password and clears the must_change_password claim atomically;
     * the client then refreshes its ID token + local user cache so the
     * UI sees the cleared state without a re-login.
     *
     * Use this from the force-change screen. For voluntary password
     * changes (settings → change password), use [changePassword] which
     * goes through Firebase client SDK with a recent-login re-auth.
     */
    suspend fun clearMustChange(newPassword: String): AuthResult<String> {
        return try {
            val token = firebaseAuthManager.getIdToken(forceRefresh = false)
                ?: return AuthResult.Error("Not signed in — please log in again.")

            val res = authApi.clearMustChange(
                bearer = "Bearer $token",
                newPassword = newPassword,
            )

            if (!res.isSuccessful) {
                val body = res.errorBody()?.string().orEmpty()
                Log.w(TAG, "clearMustChange HTTP ${res.code()}: $body")
                val msg = try {
                    org.json.JSONObject(body).optString("message").ifBlank { "Reset failed (HTTP ${res.code()})." }
                } catch (_: Exception) {
                    "Reset failed (HTTP ${res.code()})."
                }
                return AuthResult.Error(msg, code = res.code())
            }

            val payload = res.body()
            if (payload?.status != "success") {
                return AuthResult.Error(payload?.message ?: "Reset failed.")
            }

            // Force-refresh the ID token so subsequent calls see the cleared claim.
            try { firebaseAuthManager.getIdTokenResult(forceRefresh = true) } catch (_: Exception) {}

            // Update local cache so the NavGraph gate releases without a full re-login.
            val cachedUser = tokenManager.user.firstOrNull()
            if (cachedUser != null && cachedUser.mustChangePassword) {
                try {
                    tokenManager.saveUserDirect(cachedUser.copy(mustChangePassword = false))
                } catch (_: Exception) { /* best-effort */ }
            }

            AuthResult.Success(payload.message ?: "Password updated.")
        } catch (e: Exception) {
            Log.e(TAG, "clearMustChange exception", e)
            AuthResult.Error(e.message ?: "Network error.")
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

            // Firestore userDevices is the sole canonical store for FCM tokens.
            try {
                firestoreService.setDocument("userDevices", docId, payload, merge = true)
                Log.d(TAG, "FCM token written to Firestore userDevices/$docId")
                AuthResult.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "FCM token Firestore write failed", e)
                AuthResult.Error("FCM token write failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerFcmToken exception", e)
            AuthResult.Error(e.message ?: "FCM registration failed")
        }
    }

    suspend fun ensureFirebaseAuth(): Boolean = firebaseAuthManager.isSignedIn

    // Phase 8.3 (2026-08-13) — migrated from RTDB to Firestore, per
    // PARENT_APP_SCHOOL_CODE_FIRESTORE_MIGRATION_REPORT.md. Prior state (RTDB
    // Indexes/School_codes/{code} -> schoolId, FirebaseService.readString) is
    // preserved in git history and in that report's "pre-change architecture"
    // section; not reproduced here as dead comment weight.
    //
    // Live-data verification (Phase 8.2, real read against the production
    // Firebase project, not inferred) proved Firestore already contains every
    // mapping RTDB served for this lookup, plus 7 more schools RTDB never had
    // — so this migration needed zero backfill and is a strict improvement in
    // coverage, not a downgrade.
    //
    // Resolves ONLY the schoolCode -> schoolId direction (the schoolId-claim-
    // first path above this function is completely unchanged). Distinguishes
    // its failure modes rather than collapsing everything to "not found":
    //   - zero matches            -> null (caller already falls back to the
    //                                raw claim value unchanged, same as the
    //                                RTDB version's null-on-miss behavior)
    //   - query exception         -> null, logged (same fail-safe shape as
    //                                the RTDB version's catch-all)
    //   - MORE than one match     -> should be structurally impossible (Phase
    //                                8.2 verified zero duplicate schoolCode
    //                                values in production) — if it ever
    //                                happens anyway, this is a genuine data-
    //                                integrity violation, not a routine miss,
    //                                so it is logged at ERROR (distinct from
    //                                the ordinary not-found path) rather than
    //                                silently swallowed. The first match is
    //                                still returned so login isn't blocked by
    //                                a backend data bug, but the signal is
    //                                not lost.
    // No RTDB fallback exists anywhere in this function, by design — a
    // migration that quietly keeps the old path as an emergency fallback
    // would not actually remove the dependency it claims to remove.
    private suspend fun lookupSchoolCode(id: String): String? {
        return try {
            val snapshot = firestoreService.queryDocuments(Constants.Firestore.SCHOOLS) { ref ->
                ref.whereEqualTo("schoolCode", id).limit(2)
            }
            when (snapshot.size()) {
                0 -> {
                    Log.w(TAG, "lookupSchoolCode: no Firestore school matches schoolCode=$id")
                    null
                }
                1 -> snapshot.documents.first().id
                else -> {
                    Log.e(TAG, "lookupSchoolCode: DATA INTEGRITY — multiple schools share schoolCode=$id (expected exactly 0 or 1, per Phase 8.2 verification)")
                    snapshot.documents.first().id
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "lookupSchoolCode: Firestore query failed for schoolCode=$id", e)
            null
        }
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
