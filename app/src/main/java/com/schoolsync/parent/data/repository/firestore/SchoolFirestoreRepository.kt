package com.schoolsync.parent.data.repository.firestore

import android.util.Log
import com.schoolsync.parent.data.firebase.FirebaseAuthManager
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.SchoolDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading school data from Firestore.
 * Collection: schools/{schoolCode}
 */
@Singleton
class SchoolFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager,
    private val authManager: FirebaseAuthManager
) {

    /**
     * Fetch the current school document from Firestore.
     * Uses the school code stored in TokenManager to identify the document.
     */
    suspend fun getSchool(): Result<SchoolDoc> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val doc = firestoreService.getDocumentAs<SchoolDoc>(
                Constants.Firestore.SCHOOLS,
                schoolCode
            )
            if (doc != null) {
                Result.success(doc)
            } else {
                Result.failure(Exception("School document not found for code: $schoolCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the school document as a raw map for flexible field access.
     * Useful when consuming fields not yet modelled in [SchoolDoc].
     */
    suspend fun getSchoolConfig(): Map<String, Any?>? {
        val schoolCode = getSchoolCode() ?: return null

        return try {
            firestoreService.getDocumentMap(Constants.Firestore.SCHOOLS, schoolCode)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Observe the school document for real-time updates.
     * Reacts to school code changes in the user profile via [flatMapLatest],
     * and emits `null` when the document does not exist or the school code is unavailable.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSchool(): Flow<SchoolDoc?> {
        return tokenManager.user
            .map { user -> user.schoolCode.takeIf { it.isNotBlank() } }
            .flatMapLatest { schoolCode ->
                if (schoolCode == null) {
                    flowOf(null)
                } else {
                    firestoreService.observeDocumentAs<SchoolDoc>(
                        Constants.Firestore.SCHOOLS,
                        schoolCode
                    )
                }
            }
    }

    /**
     * 2026-05-15 Phase 1 — refresh the parent → school binding from the
     * authoritative Firestore user doc.
     *
     * Problem this solves: TokenManager.user was previously frozen at login
     * time. If an admin re-assigned the parent to a different school, the
     * Parent app continued to query `schools/{stale-code}` indefinitely
     * until the parent logged out and back in (often weeks). Snapshot-
     * listening on `users/{uid}` would be ideal, but Phase 1 keeps the
     * blast radius minimal: this method is a manual refresh that the
     * app's foreground / network-reconnect hook can call cheaply. It:
     *
     *   1. Reads `users/{currentUid}` (the auth-bound profile doc).
     *   2. Extracts `schoolCode` (with `school_code` snake_case fallback
     *      for transitional records).
     *   3. Compares with the locally cached `tokenManager.user.schoolCode`
     *      and calls `tokenManager.saveSchoolCode(...)` only when they
     *      differ — avoids unnecessary DataStore writes.
     *   4. Returns the resolved code (or null on no-op / not-signed-in).
     *
     * NO new collection introduced. NO existing field semantics changed.
     * `users` is already present in the project — see Constants.USERS.
     *
     * @return the freshly-read schoolCode, or null when the user is not
     *         signed in / the user doc lacks a schoolCode / Firestore
     *         is unreachable. A non-null return whose value matches the
     *         cached code means "no change"; a non-null return that
     *         differs means the cache was updated.
     */
    suspend fun refreshSchoolBinding(): String? {
        val uid = authManager.uid ?: run {
            Log.i(TAG, "ACC_PARENT_BINDING_REFRESH skipped reason=not_signed_in")
            return null
        }
        val map = try {
            firestoreService.getDocumentMap(Constants.Firestore.USERS, uid)
        } catch (e: Exception) {
            Log.e(TAG, "ACC_PARENT_BINDING_REFRESH read_failed uid=$uid err=${e.message}")
            return null
        }
        if (map == null) {
            Log.w(TAG, "ACC_PARENT_BINDING_REFRESH no_user_doc uid=$uid")
            return null
        }
        val fresh = (map["schoolCode"] as? String
            ?: map["school_code"] as? String
            ?: "").trim()
        if (fresh.isEmpty()) {
            Log.w(TAG, "ACC_PARENT_BINDING_REFRESH empty_school_code uid=$uid")
            return null
        }
        val cached = tokenManager.user.firstOrNull()?.schoolCode.orEmpty()
        if (fresh != cached) {
            tokenManager.saveSchoolCode(fresh)
            Log.i(TAG, "ACC_PARENT_BINDING_CHANGED uid=$uid from=$cached to=$fresh")
        }
        return fresh
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "SchoolFsRepo"
    }
}
