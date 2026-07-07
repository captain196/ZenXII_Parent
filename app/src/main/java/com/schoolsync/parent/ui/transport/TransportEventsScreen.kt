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
import com.schoolsync.parent.data.model.firestore.EventTripAssignmentDoc
import com.schoolsync.parent.data.model.firestore.StudentDoc
import com.schoolsync.parent.data.model.firestore.TransportEventDoc
import com.schoolsync.parent.data.repository.firestore.StudentFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.TransportFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TransportEventsScreen — F10 LC-20 P-E: shows F8 transport events the
 * child is on the roster for. Consent status is display-only at F10
 * (Refinement 1 from F8 — parent self-service consent is a future
 * feature). Multi-child parents see aggregated event assignments across
 * all their children.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportEventsScreen(
    onBack: () -> Unit,
    viewModel: TransportEventsViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upcoming events") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No upcoming event trips.", color = Color.Gray)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.entries, key = { "${it.assignment.id}_${it.childId}" }) { EventRow(it) }
            }
        }
    }
}

@Composable
private fun EventRow(row: EventRowState) {
    val (cs, tint) = when (row.assignment.consentStatus) {
        "Approved" -> "Approved" to Color(0xFF16A34A)
        "Rejected" -> "Rejected" to Color(0xFFDC2626)
        else       -> "Pending"  to Color(0xFFF59E0B)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(row.event?.title ?: row.assignment.eventId, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "For: ${row.childName} · ${row.event?.eventType?.replace("_", " ") ?: "Event"}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(4.dp))
            row.event?.let {
                Text("Date: ${it.eventDate}${if (it.eventEndDate.isNotBlank() && it.eventEndDate != it.eventDate) " → ${it.eventEndDate}" else ""}")
                Text("Destination: ${it.destination.ifBlank { "—" }}")
            }
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(50), color = tint) {
                Text(
                    "Consent: $cs",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            if (row.assignment.consentStatus == "Pending") {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Please contact the school office to confirm consent.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

data class EventRowState(
    val assignment: EventTripAssignmentDoc,
    val event: TransportEventDoc?,
    val childId: String,
    val childName: String
)

data class TransportEventsState(
    val loading: Boolean = true,
    val entries: List<EventRowState> = emptyList()
)

@HiltViewModel
class TransportEventsViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val studentRepo: StudentFirestoreRepository,
    private val transportRepo: TransportFirestoreRepository
) : ViewModel() {
    private val _state = androidx.compose.runtime.mutableStateOf(TransportEventsState())
    val state: State<TransportEventsState> = _state

    fun load() {
        viewModelScope.launch {
            _state.value = TransportEventsState(loading = true)
            try {
                val children = discoverChildren()
                val rows = children.flatMap { child ->
                    val cid = child.userId.ifBlank { child.studentId }.ifBlank { child.id }
                    val assigns = transportRepo.getMyStudentEventAssignments(cid).getOrDefault(emptyList())
                    assigns.map { a -> a to cid to child.name }
                }
                // Hydrate event details in parallel; unique event ids
                val eventIds = rows.map { it.first.first.eventId }.distinct().filter { it.isNotBlank() }
                val eventDocs = eventIds.map { eid ->
                    async { transportRepo.getTransportEvent(eid).getOrNull() }
                }.awaitAll()
                val eventMap = eventIds.zip(eventDocs).toMap()
                val entries = rows.map { (pair, name) ->
                    val (assign, cid) = pair
                    EventRowState(
                        assignment = assign,
                        event = eventMap[assign.eventId],
                        childId = cid,
                        childName = name
                    )
                }
                _state.value = TransportEventsState(loading = false, entries = entries)
            } catch (e: Exception) {
                _state.value = TransportEventsState(loading = false)
            }
        }
    }

    private suspend fun discoverChildren(): List<StudentDoc> {
        val user = tokenManager.user.firstOrNull() ?: return emptyList()
        val primary = StudentDoc(
            id         = "${user.schoolId}_${user.userId}",
            studentId  = user.userId,
            userId     = user.userId,
            schoolId   = user.schoolId,
            name       = user.name,
            parentDbKey= user.parentDbKey
        )
        val siblings = studentRepo.findSiblings(primary).getOrNull().orEmpty()
        return (listOf(primary) + siblings).distinctBy { it.id }
    }
}
