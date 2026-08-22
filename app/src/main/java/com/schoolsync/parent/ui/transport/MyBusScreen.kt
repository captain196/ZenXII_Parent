package com.schoolsync.parent.ui.transport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.model.firestore.TripLogDoc
import com.schoolsync.parent.data.model.firestore.TripProgress
import com.schoolsync.parent.data.model.firestore.VehicleLivePositionDoc
import com.schoolsync.parent.data.repository.firestore.MyBusInfo
import com.schoolsync.parent.ui.maps.MapConfigViewModel
import com.schoolsync.parent.data.repository.firestore.TransportFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * MyBusScreen — LC-20 P-A..D per-child transport details.
 *
 * Content: route + stop + vehicle + driver + today's trip status.
 * When tripLogs.gps_session_id is populated by the future GPS phase,
 * the "Live tracking coming soon" banner will swap for a live map
 * without schema redesign (GPS-native continuity).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBusScreen(
    studentId: String,
    onBack: () -> Unit,
    viewModel: MyBusViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    LaunchedEffect(studentId) { viewModel.load(studentId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My bus") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error, color = Color.Red)
            }
            state.bus == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No bus assigned yet.", color = Color.Gray)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { RouteVehicleCard(state.bus) }
                item { DriverCard(state.bus) }
                item { LiveTrackingCard(state) }
                item { TodayTripCard(state.activeTrip) }
            }
        }
    }
}

@Composable
private fun RouteVehicleCard(bus: MyBusInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Route & Vehicle", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Route: ${bus.route?.name ?: bus.studentRoute.routeName.ifBlank { "—" }}")
            Text("Stop: ${bus.studentRoute.stopName.ifBlank { "—" }}")
            Text("Pickup time: ${bus.studentRoute.pickupTime.ifBlank { "—" }}")
            Text("Drop time: ${bus.studentRoute.dropTime.ifBlank { "—" }}")
            Spacer(Modifier.height(6.dp))
            Text("Vehicle: ${bus.vehicle?.registrationNo ?: bus.studentRoute.vehicleNo.ifBlank { "—" }}")
            Text("Model: ${bus.vehicle?.let { "${it.make} ${it.model}".trim() }?.ifBlank { "—" } ?: "—"}")
        }
    }
}

@Composable
private fun DriverCard(bus: MyBusInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Driver", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Name: ${bus.studentRoute.driverName.ifBlank { "—" }}")
            Text("Phone: ${bus.studentRoute.driverPhone.ifBlank { "—" }}")
            if (bus.studentRoute.conductorName.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Conductor: ${bus.studentRoute.conductorName}")
            }
        }
    }
}

/**
 * Live tracking — the F10 placeholder replaced by a real map (F13.1 M4).
 *
 * Five distinct states, because "no map" has several very different causes and
 * a parent deserves to know which one they are looking at:
 *
 *   1. no active trip      — the bus is not running; tracking has not started
 *   2. tracking not live   — trip running, but the driver's stream is not up
 *   3. awaiting first fix  — session open, no position reported yet
 *   4. stale               — a fix exists but is over two minutes old
 *   5. live                — the map, with a moving bus
 *
 * Collapsing these into one "unavailable" message is the tempting shortcut and
 * the wrong one: a parent watching an empty card needs to distinguish "the bus
 * has not left yet" from "we have lost the signal".
 */
@Composable
private fun LiveTrackingCard(
    state: MyBusState,
    // Phase B — the active provider comes from the centralized configuration,
    // resolved by MapProviderManager (Firestore → cache → BuildConfig). This
    // card never decides which provider is used, and MyBusViewModel — which
    // owns the F13.1 GPS listener — is not involved at all.
    mapConfig: MapConfigViewModel = hiltViewModel(),
) {
    val pos = state.livePosition
    val tripRunning = state.activeTrip?.status == "InProgress"
    val mapState by mapConfig.state.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Live tracking", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))

            when {
                !tripRunning -> Text(
                    "No trip running right now. Tracking starts when the bus departs.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )

                // The document is absent, or the server kill switch is off.
                // Absence is ALSO what a parent sees when they are not on the
                // vehicle's authorized-viewer list, since the rule denies the
                // read — so this copy must not promise that a position exists.
                pos == null || !pos.liveTrackingEnabled -> Text(
                    "Live tracking isn't available for this bus right now.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )

                !pos.hasFix -> Text(
                    "Waiting for the first location from the bus…",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )

                // Phase B: "configured" now means the ADMIN has selected and
                // activated a provider centrally — not that this build happened
                // to be compiled with a style URL.
                !mapState.loaded -> Text(
                    "Loading map settings…",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )

                mapState.providerId.isBlank() -> Text(
                    "No map provider has been set up for this school yet.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )

                else -> {
                    val stale = pos.isStale()
                    LiveBusMap(
                        providerId = mapState.providerId,
                        credentials = mapState.credentials,
                        lat = pos.lat!!,
                        lng = pos.lng!!,
                        heading = pos.heading,
                        isStale = stale,
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                    Spacer(Modifier.height(8.dp))

                    // F13.2 — stop progress from DRIVER-CONFIRMED checkpoints,
                    // the same itinerary the admin fleet map reads. Not derived
                    // from GPS proximity, and deliberately no ETA.
                    val progress = TripProgress.from(
                        state.activeTrip?.checkpoints ?: emptyList(),
                        state.bus?.studentRoute?.stopId.orEmpty()
                    )
                    progress.summaryOrNull()?.let { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium,
                             fontWeight = FontWeight.SemiBold)
                        if (progress.totalStops > 0) {
                            Text(
                                "Stop ${progress.stopsDone} of ${progress.totalStops}" +
                                    if (progress.nextStopName.isNotBlank() && progress.stopsAwayFromMine != 0)
                                        " · next ${progress.nextStopName}" else "",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    if (stale) {
                        Text(
                            "Last seen ${pos.minutesSinceFix()} min ago. Signal may be weak in this area.",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFFB26A00)
                        )
                    } else {
                        Text(
                            buildString {
                                append("Live")
                                pos.speedKmph?.let { append(" · ${it.toInt()} km/h") }
                            },
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTripCard(trip: TripLogDoc?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Today's trip", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (trip == null) {
                Text("No active trip on this route today.", color = Color.Gray)
            } else {
                Text("Trip: ${trip.id.ifBlank { "—" }}")
                Text("Direction: ${trip.tripDirection.ifBlank { trip.tripType }}")
                Text("Status: ${trip.status.ifBlank { "—" }}")
                Text("Progress: ${trip.routeProgressPct}%")
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  ViewModel + state
// ═════════════════════════════════════════════════════════════════════

data class MyBusState(
    val loading: Boolean = true,
    val bus: MyBusInfo? = null,
    val activeTrip: TripLogDoc? = null,
    /** F13.1 M4 — latest live fix; null when no session is open or readable. */
    val livePosition: VehicleLivePositionDoc? = null,
    val error: String? = null
)

@HiltViewModel
class MyBusViewModel @Inject constructor(
    private val transportRepo: TransportFirestoreRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = androidx.compose.runtime.mutableStateOf(MyBusState())
    val state: State<MyBusState> = _state

    /** F13.1 M4 — the live-position subscription; cancelled when it must not run. */
    private var liveJob: Job? = null

    fun load(studentId: String) {
        viewModelScope.launch {
            _state.value = MyBusState(loading = true)
            try {
                val bus = transportRepo.getMyStudentBusInfo(studentId).getOrNull()
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val trips = transportRepo.getMyStudentTripsToday(studentId, today).getOrDefault(emptyList())
                val active = trips.firstOrNull { it.status == "InProgress" }
                    ?: trips.firstOrNull { it.status == "Ready" }
                _state.value = MyBusState(loading = false, bus = bus, activeTrip = active)
                startOrStopLiveTracking(active)
            } catch (e: Exception) {
                _state.value = MyBusState(loading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    /**
     * Subscribe only while a trip is actually InProgress.
     *
     * This is the read-budget control. A listener left attached across the
     * whole school day would bill a document read for every position of every
     * bus a family follows, for hours — the design's stated cap is one document
     * per position change *while watching*, and that only holds if the
     * subscription is this tightly scoped.
     *
     * Re-entrant by design: the previous job is always cancelled first, so
     * calling this on refresh cannot leave two listeners on one document.
     */
    private fun startOrStopLiveTracking(trip: TripLogDoc?) {
        liveJob?.cancel()
        liveJob = null

        val vehicleId = trip?.vehicleId.orEmpty()
        if (trip?.status != "InProgress" || vehicleId.isBlank()) {
            _state.value = _state.value.copy(livePosition = null)
            return
        }
        liveJob = viewModelScope.launch {
            transportRepo.observeLivePosition(vehicleId).collect { pos ->
                _state.value = _state.value.copy(livePosition = pos)
            }
        }
    }

    /**
     * Drop the subscription when the screen leaves composition.
     *
     * viewModelScope would eventually cancel it anyway, but a ViewModel can
     * outlive its screen across configuration changes and back-stack moves, and
     * a map nobody is looking at should not be billing reads.
     */
    override fun onCleared() {
        liveJob?.cancel()
        super.onCleared()
    }
}
