package com.schoolsync.parent.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firebase Realtime Database helper.
 *
 * Phase 3 RTDB removal (2026-08-13, PARENT_APP_FIRESTORE_ONLY_HARDENING_REPORT.md
 * §7/§8/§17): every method except readValue()/readString() was dead weight
 * (defined, zero callers anywhere in the app — verified by repo-wide grep,
 * not assumed) and has been removed, along with the class's only two real
 * consumers of the write/observe surface (DataRepository, deleted entirely;
 * LeaveFirestoreRepository's RTDB fallbacks, removed; AuthRepository's login-
 * profile RTDB fallback, removed).
 *
 * Phase 8.3 (2026-08-13, PARENT_APP_SCHOOL_CODE_FIRESTORE_MIGRATION_REPORT.md):
 * AuthRepository::lookupSchoolCode() — the one live call site referenced
 * above — was migrated to Firestore. readValue()/readString() now have ZERO
 * remaining callers anywhere in the app.
 *
 * This class is STILL NOT deleted, and the RTDB Gradle dependency /
 * SchoolSyncApp.kt persistence-init are still NOT removed, even though no
 * code calls a method on this class anymore. Reason: StudentRepository.kt and
 * HomeworkRepository.kt both still declare an (already-unused, pre-existing,
 * out-of-scope) `firebaseService: FirebaseService` constructor parameter —
 * deleting this class would break their compilation. That cleanup belongs to
 * whoever addresses those two files' own dead parameter, not to this
 * migration — see PARENT_APP_SCHOOL_CODE_FIRESTORE_MIGRATION_REPORT.md §18
 * for the full reasoning. Do not delete this class in isolation.
 */
@Singleton
class FirebaseService @Inject constructor() {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    /**
     * Read a single value at the given path. Returns null if the path doesn't exist.
     */
    private suspend fun readValue(path: String): DataSnapshot? {
        return suspendCancellableCoroutine { cont ->
            val ref = database.getReference(path)
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (cont.isActive) {
                        cont.resume(if (snapshot.exists()) snapshot else null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) {
                        cont.resumeWithException(error.toException())
                    }
                }
            })
        }
    }

    /**
     * Read a string value at the given path. Returns null if missing.
     * The sole remaining caller is AuthRepository::lookupSchoolCode() — see
     * class docblock.
     */
    suspend fun readString(path: String): String? {
        val snapshot = readValue(path) ?: return null
        return snapshot.value?.toString()
    }
}
