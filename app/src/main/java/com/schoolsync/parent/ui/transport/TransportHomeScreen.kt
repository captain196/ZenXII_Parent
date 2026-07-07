package com.schoolsync.parent.ui.transport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.StudentDoc
import com.schoolsync.parent.data.repository.firestore.MyBusInfo
import com.schoolsync.parent.data.repository.firestore.StudentFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.TransportFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TransportHomeScreen — Parent App transport landing surface (F10, LC-20).
 *
 * Refinement additional (2026-07-07) — families with MULTIPLE children
 * see a per-child card layout. Each child's card renders their own bus
 * status independently, reusing the same TransportFirestoreRepository
 * calls (getMyStudentBusInfo per studentId). No child picker; the
 * parent sees all their children at a glance and taps a card to drill
 * into that child's transport surface.
 *
 * Below the per-child cards, a set of module cards routes to the
 * cross-child feature screens (History, Events, Fees, Preferences).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportHomeScreen(
    onNavigateMyBus: (studentId: String) -> Unit,
    onNavigateHistory: (studentId: String) -> Unit,
    onNavigateEvents: () -> Unit,
    onNavigateFees: () -> Unit,
    onNavigatePreferences: () -> Unit,
    onBack: () -> Unit,
    viewModel: TransportHomeViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transport") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error, color = Color.Red)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "My children's bus",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (state.children.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("No children linked to this account.")
                                }
                            }
                        }
                    } else {
                        items(state.children, key = { it.child.id }) { row ->
                            ChildBusCard(
                                row = row,
                                onClick = { onNavigateMyBus(row.child.id) }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                    item {
                        Text(
                            "More",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    item {
                        ModuleCard(icon = "🕓", title = "Attendance history",
                            subtitle = "See when your child boarded and got off") {
                            state.children.firstOrNull()?.let { onNavigateHistory(it.child.id) }
                        }
                    }
                    item {
                        ModuleCard(icon = "🎒", title = "Upcoming events",
                            subtitle = "Field trips, sports, tours + consent status") { onNavigateEvents() }
                    }
                    item {
                        ModuleCard(icon = "💳", title = "Transport fees",
                            subtitle = "Pending demands and payment history") { onNavigateFees() }
                    }
                    item {
                        ModuleCard(icon = "🔔", title = "Notification preferences",
                            subtitle = "Choose which transport notices you want") { onNavigatePreferences() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildBusCard(row: ChildBusRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.width(32.dp).height(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            row.child.name.take(1),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(row.child.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        row.child.className.ifBlank { "Student" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (row.bus == null) {
                Text("No bus assigned yet.", color = Color.Gray)
            } else {
                val bus = row.bus
                Text("Route: ${bus.route?.name ?: bus.studentRoute.routeName.ifBlank { "—" }}")
                Text("Stop: ${bus.studentRoute.stopName.ifBlank { "—" }}")
                Text("Vehicle: ${bus.vehicle?.registrationNo ?: bus.studentRoute.vehicleNo.ifBlank { "—" }}")
                Text("Driver: ${bus.studentRoute.driverName.ifBlank { "—" }}")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap for live status and details",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF3B82F6)
                )
            }
        }
    }
}

@Composable
private fun ModuleCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  ViewModel + state
// ═════════════════════════════════════════════════════════════════════

data class ChildBusRow(
    val child: StudentDoc,
    val bus: MyBusInfo?
)

data class TransportHomeState(
    val loading: Boolean = true,
    val children: List<ChildBusRow> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class TransportHomeViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val studentRepo: StudentFirestoreRepository,
    private val transportRepo: TransportFirestoreRepository
) : ViewModel() {

    private val _state = androidx.compose.runtime.mutableStateOf(TransportHomeState())
    val state: State<TransportHomeState> = _state

    fun load() {
        if (!_state.value.loading && _state.value.children.isNotEmpty()) return
        viewModelScope.launch {
            _state.value = TransportHomeState(loading = true)
            try {
                val children = discoverChildren()
                // Fetch each child's bus info in parallel — one Firestore
                // read per child; Refinement additional (multi-child cards)
                val rows = children.map { child ->
                    async {
                        val bus = transportRepo.getMyStudentBusInfo(
                            child.userId.ifBlank { child.studentId }.ifBlank { child.id }
                        ).getOrNull()
                        ChildBusRow(child = child, bus = bus)
                    }
                }.awaitAll()
                _state.value = TransportHomeState(loading = false, children = rows)
            } catch (e: Exception) {
                _state.value = TransportHomeState(loading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    /**
     * Discover all children linked to this parent account by:
     *   1. building a primary StudentDoc from tokenManager.user (the
     *      currently-active child),
     *   2. querying siblings via StudentFirestoreRepository.findSiblings,
     *   3. returning primary + siblings deduped by id.
     * Mirrors DashboardViewModel.loadSiblings pattern.
     */
    private suspend fun discoverChildren(): List<StudentDoc> {
        val user = tokenManager.user.firstOrNull() ?: return emptyList()
        val primary = StudentDoc(
            id         = "${user.schoolId}_${user.userId}",
            studentId  = user.userId,
            userId     = user.userId,
            schoolId   = user.schoolId,
            name       = user.name,
            className  = user.className,
            section    = user.section,
            rollNo     = user.rollNo,
            fatherName = user.fatherName,
            motherName = user.motherName,
            phone      = user.phone,
            parentDbKey= user.parentDbKey
        )
        val siblings = studentRepo.findSiblings(primary).getOrNull().orEmpty()
        val all = mutableListOf(primary)
        val seen = mutableSetOf(primary.id)
        for (s in siblings) if (seen.add(s.id)) all.add(s)
        return all
    }
}
