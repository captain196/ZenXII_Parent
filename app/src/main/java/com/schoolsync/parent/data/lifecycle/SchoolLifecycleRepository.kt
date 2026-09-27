package com.schoolsync.parent.data.lifecycle

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes the school's lifecycle state from `tenantPublic/{schoolId}`.
 *
 * `tenantPublic` is the EXISTING thin, client-readable mirror the backend
 * already maintains — `B2_registry_service::write_lifecycle_state()` and
 * `set_admin_disabled()` fan the commercial signals into it, and
 * `Institution_lifecycle::transition()` fans in `institutionState`. This
 * repository introduces NO new mirror, NO new collection, and no RTDB.
 *
 * It is also the one collection that stays readable when the rest of the
 * tenant is gated (firestore.rules keeps `tenantPublic` client-readable for
 * one's own school), which is exactly what lets the app EXPLAIN a suspension
 * instead of dying on PERMISSION_DENIED everywhere else.
 */
@Singleton
class SchoolLifecycleRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager,
) {

    /**
     * Live school access state.
     *
     * Emits [SchoolAccess.Unknown] rather than throwing when the mirror cannot
     * be read (offline, absent doc, listener error) — a deliberate FAIL-OPEN,
     * because the server and firestore.rules remain the real enforcement and
     * an unreadable convenience mirror must never lock out a legitimate user.
     */
    // NOTE: this app's TokenManager exposes the profile as `user: Flow<User>`
    // rather than a standalone schoolId flow (the Teacher app has the latter),
    // so the id is projected out of the user record here. Same source of
    // truth, different accessor — no new storage is introduced.
    private val schoolIdFlow: Flow<String?> = tokenManager.user.map { it.schoolId }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSchoolAccess(): Flow<SchoolAccess> =
        schoolIdFlow
            .flatMapLatest { schoolId ->
                if (schoolId.isNullOrBlank()) {
                    flowOf(SchoolAccess.Unknown)
                } else {
                    firestoreService.observeDocument(TENANT_PUBLIC, schoolId)
                        .map { snapshot -> snapshot?.let(::mapSnapshot) ?: SchoolAccess.Unknown }
                }
            }
            .catch { emit(SchoolAccess.Unknown) }

    /** One-shot read, for cold-start routing where a listener is overkill. */
    suspend fun currentSchoolAccess(): SchoolAccess {
        val schoolId = schoolIdFlow.first()
        if (schoolId.isNullOrBlank()) return SchoolAccess.Unknown
        return try {
            val doc = firestoreService.getDocument(TENANT_PUBLIC, schoolId)
            doc?.let(::mapSnapshot) ?: SchoolAccess.Unknown
        } catch (e: Exception) {
            SchoolAccess.Unknown
        }
    }

    private fun mapSnapshot(snapshot: com.google.firebase.firestore.DocumentSnapshot): SchoolAccess {
        val institution = snapshot.getString(FIELD_INSTITUTION_STATE)
        val commercial = snapshot.getString(FIELD_LIFECYCLE_STATE)
        val adminDisabled = snapshot.getBoolean(FIELD_ADMIN_DISABLED)
        val resolved = SchoolLifecycle.resolve(institution, commercial, adminDisabled)

        // Carry the effective date through when closing, so the notice can
        // name a real date instead of a vague warning.
        return if (resolved is SchoolAccess.Closing) {
            SchoolAccess.Closing(snapshot.getString(FIELD_EFFECTIVE_DATE))
        } else {
            resolved
        }
    }

    private companion object {
        const val TENANT_PUBLIC = "tenantPublic"
        const val FIELD_INSTITUTION_STATE = "institutionState"
        const val FIELD_LIFECYCLE_STATE = "lifecycleState"
        const val FIELD_ADMIN_DISABLED = "adminDisabled"
        const val FIELD_EFFECTIVE_DATE = "institutionEffectiveDate"
    }
}
