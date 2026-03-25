package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.RouteDoc
import com.schoolsync.parent.data.model.firestore.SosAlertDoc
import com.schoolsync.parent.data.model.firestore.StudentRouteDoc
import com.schoolsync.parent.data.model.firestore.VehicleDoc
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
 * Repository for transport-related data: student routes, vehicles, live GPS tracking, and SOS alerts.
 *
 * Collections used (Firestore):
 * - studentRoutes: per-student route assignment
 * - routes: route definitions with stops
 * - vehicles: vehicle metadata
 * - sosAlerts: emergency alerts from transport staff
 *
 * RTDB paths used:
 * - /VehicleLive/{schoolId}/{vehicleId}: real-time GPS location of vehicles
 */
@Singleton
class TransportFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the route assignment for a specific student.
     * Query: schoolId + studentId.
     */
    suspend fun getStudentRoute(studentId: String): Result<StudentRouteDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val docs = firestoreService.queryDocumentsAs<StudentRouteDoc>(
                Constants.Firestore.STUDENT_ROUTES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .limit(1)
            }
            Result.success(docs.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch a route document by its ID.
     */
    suspend fun getRoute(routeId: String): Result<RouteDoc?> {
        return try {
            val doc = firestoreService.getDocumentAs<RouteDoc>(
                Constants.Firestore.ROUTES,
                routeId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch a vehicle document by its ID.
     */
    suspend fun getVehicle(vehicleId: String): Result<VehicleDoc?> {
        return try {
            val doc = firestoreService.getDocumentAs<VehicleDoc>(
                Constants.Firestore.VEHICLES,
                vehicleId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe real-time GPS location of a vehicle from RTDB.
     * Path: /VehicleLive/{schoolId}/{vehicleId}
     * Emits a Map containing lat, lng, speed, heading, updatedAt, etc.
     * Emits null when no live data is available.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeVehicleLive(vehicleId: String): Flow<Map<String, Any?>?> {
        return tokenManager.user
            .map { user ->
                user.schoolCode.takeIf { it.isNotBlank() }
            }
            .flatMapLatest { schoolCode ->
                if (schoolCode == null) {
                    flowOf(null)
                } else {
                    val path = "VehicleLive/$schoolCode/$vehicleId"
                    firebaseService.observeMap(path).map { data ->
                        data.ifEmpty { null }
                    }
                }
            }
    }

    /**
     * Fetch active SOS alerts for the current school, ordered by most recent first.
     */
    suspend fun getSosAlerts(): Result<List<SosAlertDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val alerts = firestoreService.queryDocumentsAs<SosAlertDoc>(
                Constants.Firestore.SOS_ALERTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("active", true)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            }
            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
    }
}
