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
import com.schoolsync.parent.data.model.firestore.TransportAttendanceDoc
import com.schoolsync.parent.data.repository.firestore.TransportFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TransportHistoryScreen — LC-20 P-B: parent-facing transport
 * attendance history (last 30 days by default). Reads
 * transportAttendance (F7 collection); Parent never writes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportHistoryScreen(
    studentId: String,
    onBack: () -> Unit,
    viewModel: TransportHistoryViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    LaunchedEffect(studentId) { viewModel.load(studentId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Boarding history") },
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
            state.entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No boarding history yet.", color = Color.Gray)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.entries, key = { it.id }) { EntryRow(it) }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: TransportAttendanceDoc) {
    val (label, tint) = when (entry.eventType) {
        "boarded_pickup"    -> "Boarded"      to Color(0xFF16A34A)
        "dropped_home"      -> "Dropped"      to Color(0xFF3B82F6)
        "no_show"           -> "Did not board" to Color(0xFFDC2626)
        "alternate_pickup"  -> "Alt. stop"    to Color(0xFF7C3AED)
        else                -> entry.eventType to Color.Gray
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(entry.tripDate.ifBlank { "—" }, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Surface(shape = RoundedCornerShape(50), color = tint) {
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Direction: ${entry.tripDirection.ifBlank { "—" }} · Trip ${entry.tripId.take(12)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            if (entry.markedByRole.isNotBlank()) {
                Text(
                    "Recorded by: ${if (entry.markedByRole == "driver") "Driver" else "School office"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

data class TransportHistoryState(
    val loading: Boolean = true,
    val entries: List<TransportAttendanceDoc> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class TransportHistoryViewModel @Inject constructor(
    private val transportRepo: TransportFirestoreRepository
) : ViewModel() {
    private val _state = androidx.compose.runtime.mutableStateOf(TransportHistoryState())
    val state: State<TransportHistoryState> = _state

    fun load(studentId: String) {
        viewModelScope.launch {
            _state.value = TransportHistoryState(loading = true)
            try {
                val entries = transportRepo.getMyStudentAttendance(studentId).getOrDefault(emptyList())
                _state.value = TransportHistoryState(loading = false, entries = entries)
            } catch (e: Exception) {
                _state.value = TransportHistoryState(loading = false, error = e.message ?: "Failed to load")
            }
        }
    }
}
