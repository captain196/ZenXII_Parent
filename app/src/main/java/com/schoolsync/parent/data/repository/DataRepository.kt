package com.schoolsync.parent.data.repository

import android.util.Log
import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified Data Access Layer (DAL) for the Parent App.
 *
 * Consistent read/write pattern across ALL modules:
 *   READ:  Firestore (primary) → RTDB (fallback) → null
 *   WRITE: Firestore (primary) → RTDB (mirror)
 *
 * Usage in ViewModels:
 *   val student = dataRepository.get<StudentDoc>("students", docId)
 *   val plans = dataRepository.query<PlanDoc>("systemPlans") { ref -> ref.whereEqualTo("status", "Active") }
 */
@Singleton
class DataRepository @Inject constructor(
    @PublishedApi internal val firestoreService: FirestoreService,
    @PublishedApi internal val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {
    @PublishedApi internal companion object {
        const val TAG = "DataRepository"
    }

    /**
     * Get a single document. Firestore first, RTDB fallback.
     */
    suspend inline fun <reified T> get(
        collection: String,
        docId: String,
        rtdbPath: String? = null
    ): Result<T> {
        // 1. Firestore primary
        try {
            val doc = firestoreService.getDocumentAs<T>(collection, docId)
            if (doc != null) {
                Log.d(TAG, "GET $collection/$docId → Firestore OK")
                return Result.success(doc)
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET $collection/$docId → Firestore failed: ${e.message}")
        }

        // 2. RTDB fallback (if path provided)
        if (rtdbPath != null) {
            try {
                val data = firebaseService.readMap(rtdbPath)
                if (data.isNotEmpty()) {
                    Log.d(TAG, "GET $collection/$docId → RTDB fallback OK")
                    // Convert map to object — caller must handle mapping
                    @Suppress("UNCHECKED_CAST")
                    return Result.success(data as T)
                }
            } catch (e: Exception) {
                Log.w(TAG, "GET $collection/$docId → RTDB fallback failed: ${e.message}")
            }
        }

        return Result.failure(Exception("$collection/$docId not found in Firestore or RTDB"))
    }

    /**
     * Query documents from Firestore with RTDB fallback.
     */
    suspend inline fun <reified T> query(
        collection: String,
        noinline queryBuilder: (com.google.firebase.firestore.CollectionReference) -> com.google.firebase.firestore.Query = { it },
        rtdbPath: String? = null
    ): Result<List<T>> {
        // 1. Firestore primary
        try {
            val docs = firestoreService.queryDocumentsAs<T>(collection, queryBuilder)
            if (docs.isNotEmpty()) {
                Log.d(TAG, "QUERY $collection → Firestore OK (${docs.size} docs)")
                return Result.success(docs)
            }
            // Empty result — might mean no data OR not synced yet
            Log.d(TAG, "QUERY $collection → Firestore empty, checking RTDB")
        } catch (e: Exception) {
            Log.w(TAG, "QUERY $collection → Firestore failed: ${e.message}")
        }

        // 2. RTDB fallback (if path provided)
        if (rtdbPath != null) {
            try {
                val data = firebaseService.readMap(rtdbPath)
                if (data.isNotEmpty()) {
                    Log.d(TAG, "QUERY $collection → RTDB fallback (${data.size} entries)")
                    @Suppress("UNCHECKED_CAST")
                    return Result.success(data.values.filterIsInstance<T>())
                }
            } catch (e: Exception) {
                Log.w(TAG, "QUERY $collection → RTDB fallback failed: ${e.message}")
            }
        }

        return Result.success(emptyList()) // empty, not failure
    }

    /**
     * Write to Firestore (primary) + RTDB (mirror).
     */
    suspend fun set(
        collection: String,
        docId: String,
        data: Any,
        merge: Boolean = true,
        rtdbPath: String? = null
    ): Result<Unit> {
        var fsOk = false
        var rtdbOk = false

        // 1. Firestore primary
        try {
            firestoreService.setDocument(collection, docId, data, merge)
            fsOk = true
            Log.d(TAG, "SET $collection/$docId → Firestore OK")
        } catch (e: Exception) {
            Log.e(TAG, "SET $collection/$docId → Firestore failed: ${e.message}")
        }

        // 2. RTDB mirror
        if (rtdbPath != null && data is Map<*, *>) {
            try {
                @Suppress("UNCHECKED_CAST")
                firebaseService.writeValue(rtdbPath, data)
                rtdbOk = true
                Log.d(TAG, "SET $collection/$docId → RTDB mirror OK")
            } catch (e: Exception) {
                Log.w(TAG, "SET $collection/$docId → RTDB mirror failed: ${e.message}")
            }
        }

        return if (fsOk || rtdbOk) Result.success(Unit)
        else Result.failure(Exception("Both Firestore and RTDB writes failed"))
    }

    /**
     * Delete from both Firestore and RTDB.
     */
    suspend fun remove(
        collection: String,
        docId: String,
        rtdbPath: String? = null
    ): Result<Unit> {
        try { firestoreService.deleteDocument(collection, docId) } catch (_: Exception) {}
        if (rtdbPath != null) {
            try { firebaseService.writeValue(rtdbPath, null) } catch (_: Exception) {}
        }
        return Result.success(Unit)
    }

    // ── Helper: resolve school-scoped doc ID ──────────────────────
    suspend fun schoolDocId(entityId: String): String {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode ?: ""
        return if (schoolCode.isNotBlank()) "${schoolCode}_${entityId}" else entityId
    }

    suspend fun schoolCode(): String? =
        tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }

    suspend fun session(): String? =
        tokenManager.user.firstOrNull()?.session?.takeIf { it.isNotBlank() }
}
