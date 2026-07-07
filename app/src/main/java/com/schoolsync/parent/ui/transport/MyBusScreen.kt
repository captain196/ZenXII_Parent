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
import com.schoolsync.parent.data.repository.firestore.MyBusInfo
import com.schoolsync.parent.data.repository.firestore.TransportFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
                item { LiveTrackingCard(gpsSessionId = state.activeTrip?.gpsSessionId ?: "") }
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
 * "Live tracking coming soon" placeholder. When the dedicated GPS phase
 * populates tripLogs.gps_session_id, this composable transparently
 * switches to a live map (Google Maps SDK already added at F10 to
 * prepare the dependency graph). At F10 we render only the copy.
 */
@Composable
private fun LiveTrackingCard(gpsSessionId: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Live tracking", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (gpsSessionId.isBlank())
                    "Live tracking coming soon. We'll show your child's bus on a map here once GPS tracking goes live."
                else
                    "Live tracking session ID: $gpsSessionId (map will render when GPS phase populates position).",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
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
    val error: String? = null
)

@HiltViewModel
class MyBusViewModel @Inject constructor(
    private val transportRepo: TransportFirestoreRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = androidx.compose.runtime.mutableStateOf(MyBusState())
    val state: State<MyBusState> = _state

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
            } catch (e: Exception) {
                _state.value = MyBusState(loading = false, error = e.message ?: "Failed to load")
            }
        }
    }
}
