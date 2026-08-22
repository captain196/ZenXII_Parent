package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.EventTripAssignmentDoc
import com.schoolsync.parent.data.model.firestore.RouteDoc
import com.schoolsync.parent.data.model.firestore.RouteSuspensionDoc
import com.schoolsync.parent.data.model.firestore.SosAlertDoc
import com.schoolsync.parent.data.model.firestore.StudentRouteDoc
import com.schoolsync.parent.data.model.firestore.TransportAttendanceDoc
import com.schoolsync.parent.data.model.firestore.TransportEventDoc
import com.schoolsync.parent.data.model.firestore.TripLogDoc
import com.schoolsync.parent.data.model.firestore.VehicleDoc
import com.schoolsync.parent.data.model.firestore.VehicleLivePositionDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parent-side Transport repository — READ-ONLY consumer of the F1-F9
 * transport platform. Parent NEVER writes to any transport collection.
 *
 * F10 (2026-07-07) removed the pre-existing RTDB observeVehicleLive
 * function (RTDB is forbidden project-wide; observeVehicleLive was
 * dead code — grep confirmed zero callers before removal). Future
 * live tracking will use Firestore `tripLogs.gps_session_id` +
 * a dedicated live-positions collection populated by the GPS phase.
 *
 * F10 collections read:
 *   - studentRoutes (existing)          — per-student route assignment
 *   - routes (existing)                 — route definitions
 *   - vehicles (existing)               — vehicle metadata
 *   - sosAlerts (existing)              — school-wide alerts
 *   - tripLogs (NEW at F10)             — today's trips + status
 *   - transportAttendance (NEW at F10)  — child's boarding history
 *   - transportEvents (NEW at F10)      — upcoming event trips
 *   - eventTripAssignments (NEW at F10) — child's event roster + consent
 *   - routeSuspensions (NEW at F10)     — route change history
 */
@Singleton
class TransportFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    // ═════════════════════════════════════════════════════════════════════
    //  Existing reads (pre-F10, kept unchanged)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * TRN-SESS-B (2026-08-15) — the CURRENT transport assignment, converging on
     * the backend's canonical definition.
     *
     * This used to filter on schoolId + studentId only, with limit(1) and no
     * ordering. An unordered limit(1) returns an ARBITRARY matching row, so a
     * student carrying a prior-session or deactivated studentRoutes document
     * could be shown the wrong route and wrong stop — a parent told to send
     * their child to a stop the bus no longer serves.
     *
     * The backend has always been stricter (Transport_service.php:6914-6921):
     *     [studentId == X, status == 'Active']  then  session == sessionYear
     * so this query adopts that definition rather than inventing a second one.
     * The session comes from the existing TokenManager path, which mirrors
     * schools/{schoolId}.currentSession — no new session source.
     *
     * FAIL CLOSED on a blank session: the same decision, for the same reason,
     * HomeworkFirestoreRepository already makes. Running the session-less query
     * as a fallback would surface every session's data; an un-sessioned parent
     * must see nothing rather than the wrong bus.
     */
    suspend fun getStudentRoute(studentId: String): Result<StudentRouteDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val session = tokenManager.user.firstOrNull()?.session.orEmpty()
        if (session.isBlank()) return Result.failure(SessionNotSetException())
        return try {
            val docs = firestoreService.queryDocumentsAs<StudentRouteDoc>(
                Constants.Firestore.STUDENT_ROUTES
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .whereEqualTo("status", "Active")
                    .whereEqualTo("session", session)
                    .limit(1)
            }
            Result.success(docs.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRoute(routeId: String): Result<RouteDoc?> {
        return try {
            val doc = firestoreService.getDocumentAs<RouteDoc>(Constants.Firestore.ROUTES, routeId)
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVehicle(vehicleId: String): Result<VehicleDoc?> {
        return try {
            val doc = firestoreService.getDocumentAs<VehicleDoc>(Constants.Firestore.VEHICLES, vehicleId)
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    // ═════════════════════════════════════════════════════════════════════
    //  F10 (2026-07-07) — new reads for the Parent Transport surface
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Aggregate "My Bus" info for one child. Combines StudentRoute +
     * Route + Vehicle in a single call. Multi-child parents call this
     * per-child so the Home screen can render an independent card per
     * child (Refinement additional 2026-07-07).
     */
    suspend fun getMyStudentBusInfo(studentId: String): Result<MyBusInfo?> {
        return try {
            val studentRoute = getStudentRoute(studentId).getOrNull() ?: return Result.success(null)
            val routeDoc = if (studentRoute.routeId.isNotBlank()) {
                getRoute(studentRoute.routeId).getOrNull()
            } else null
            val vehicleDoc = if (studentRoute.vehicleId.isNotBlank()) {
                getVehicle(studentRoute.vehicleId).getOrNull()
            } else null
            Result.success(
                MyBusInfo(
                    studentId = studentId,
                    studentRoute = studentRoute,
                    route = routeDoc,
                    vehicle = vehicleDoc
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Today's trips on a student's route. Query uses composite index
     * (schoolId, route_id, date DESC) provisioned by TMS Phase 0.
     * Fetches Ready/InProgress/Completed for the current calendar day.
     */
    suspend fun getMyStudentTripsToday(studentId: String, dateIso: String): Result<List<TripLogDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        val bus = getMyStudentBusInfo(studentId).getOrNull()
        val routeId = bus?.studentRoute?.routeId ?: return Result.success(emptyList())
        return try {
            val trips = firestoreService.queryDocumentsAs<TripLogDoc>(
                Constants.Firestore.TRIP_LOGS
            ) { ref ->
                // whereArrayContains scopes the query to trips this student is
                // in the frozen roster of — required for release-cert C-1 rule
                // alignment (firestore.rules:1401-1420). Without this filter a
                // pickup-only student's afternoon-drop trip on the same route
                // would fail rule allow and Firestore denies the WHOLE query.
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("route_id", routeId)
                    .whereArrayContains("roster_student_ids", studentId)
                    .whereEqualTo("date", dateIso)
                    .orderBy("date", Query.Direction.DESCENDING)
                    .limit(20)
            }
            Result.success(trips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parent-facing transport attendance history — last N days of the
     * child's boarding events (LC-20 P-B). Backed by the F7 composite
     * index (schoolId, studentId, trip_date DESC).
     */
    suspend fun getMyStudentAttendance(studentId: String, limit: Int = 30): Result<List<TransportAttendanceDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        return try {
            val events = firestoreService.queryDocumentsAs<TransportAttendanceDoc>(
                Constants.Firestore.TRANSPORT_ATTENDANCE
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .orderBy("trip_date", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
            }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upcoming event-transport assignments for this child + consent
     * status (LC-20 P-E context). Composite index
     * (schoolId, studentId, event_id) is Phase-0-provisioned.
     */
    suspend fun getMyStudentEventAssignments(studentId: String): Result<List<EventTripAssignmentDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        return try {
            val assigns = firestoreService.queryDocumentsAs<EventTripAssignmentDoc>(
                Constants.Firestore.EVENT_TRIP_ASSIGNMENTS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .limit(50)
            }
            Result.success(assigns)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTransportEvent(eventId: String): Result<TransportEventDoc?> {
        return try {
            val doc = firestoreService.getDocumentAs<TransportEventDoc>(
                Constants.Firestore.TRANSPORT_EVENTS, eventId
            )
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Route change (suspension) history for the child's route (LC-20 P-E).
     */
    suspend fun getRouteChanges(routeId: String, limit: Int = 20): Result<List<RouteSuspensionDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))
        return try {
            val rows = firestoreService.queryDocumentsAs<RouteSuspensionDoc>(
                Constants.Firestore.ROUTE_SUSPENSIONS
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("route_id", routeId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
            }
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── F13.1 M4 — live bus position ────────────────────────────────────────

    /**
     * Stream the live position of one vehicle.
     *
     * A snapshot listener on exactly ONE document — never a query — which is
     * what holds the parent read cost to one document per position change. The
     * driver app overwrites that document at most once every 15 seconds, so
     * this emits at most that often.
     *
     * Emits `null` when the document does not exist. That is the normal state
     * for most of the day: the server creates it at trip start and DELETES it
     * on completion or cancellation, so absence is how "no trip running" is
     * represented. It is also what a parent sees when they are not on the
     * vehicle's authorized-viewer list, because the rule denies the read —
     * indistinguishable by design, so absence must never be shown as an error.
     *
     * The caller owns the subscription lifetime: attach only while the trip is
     * InProgress and the map is on screen, and drop it otherwise.
     */
    fun observeLivePosition(vehicleId: String): Flow<VehicleLivePositionDoc?> = flow {
        val schoolCode = getSchoolCode()
        if (schoolCode.isNullOrBlank() || vehicleId.isBlank()) {
            emit(null)
            return@flow
        }
        emitAll(
            firestoreService.observeDocumentAs<VehicleLivePositionDoc>(
                VehicleLivePositionDoc.COLLECTION,
                VehicleLivePositionDoc.docId(schoolCode, vehicleId)
            )
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }
}

/**
 * MyBusInfo — snapshot aggregate used by TransportHomeScreen for the
 * per-child card. Refinement additional (2026-07-07) — each child
 * gets an independent MyBusInfo so families with multiple children
 * see per-child transport status without a child picker.
 */
data class MyBusInfo(
    val studentId: String,
    val studentRoute: StudentRouteDoc,
    val route: RouteDoc?,
    val vehicle: VehicleDoc?
)
