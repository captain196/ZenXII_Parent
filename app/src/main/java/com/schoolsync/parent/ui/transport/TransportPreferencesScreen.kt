package com.schoolsync.parent.ui.transport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.schoolsync.parent.data.model.firestore.NotificationPreferencesDoc
import com.schoolsync.parent.data.repository.firestore.NotificationPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TransportPreferencesScreen — F10 (2026-07-07) parent notification
 * preferences.
 *
 * Toggles per muteable category from
 * NotificationPreferencesDoc.MUTEABLE. Safety-exempt categories are
 * NOT shown — they cannot be muted regardless of what the client writes
 * (server-side enforcement in PHP Transport_notifier). This screen
 * makes the guarantee visible to parents: safety notices always reach
 * them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportPreferencesScreen(
    onBack: () -> Unit,
    viewModel: TransportPreferencesViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification preferences") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        if (state.loading) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Choose what you want to hear about", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Safety notices — SOS, no-show, cancelled trips — always reach you and cannot be turned off.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
                items(NotificationPreferencesDoc.MUTEABLE) { cat ->
                    ToggleRow(
                        category = cat,
                        checked = cat !in state.muted,   // ON = allowed; OFF = muted
                        onToggle = { allow -> viewModel.setAllowed(cat, allow) }
                    )
                }
                item {
                    if (state.saveError != null) {
                        Text(state.saveError, color = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(category: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(prettyLabel(category), fontWeight = FontWeight.SemiBold)
                Text(
                    prettyHint(category),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors()
            )
        }
    }
}

private fun prettyLabel(category: String): String = when (category) {
    "trip.delay"                        -> "Bus running late"
    "event.scheduled"                   -> "Event scheduled"
    "event.consent_reminder"            -> "Event consent reminders"
    "event.completed"                   -> "Event completed"
    "route.suspension_notice_parents"   -> "Route change / suspension"
    else                                -> category
}

private fun prettyHint(category: String): String = when (category) {
    "trip.delay"                        -> "Get a heads-up when the bus is running behind schedule."
    "event.scheduled"                   -> "Learn about new field trips, sports events, etc."
    "event.consent_reminder"            -> "Reminders when a consent slip is pending."
    "event.completed"                   -> "Wrap-up updates after events complete."
    "route.suspension_notice_parents"   -> "Alerts when the route is suspended or changed."
    else                                -> ""
}

data class TransportPreferencesState(
    val loading: Boolean = true,
    val muted: Set<String> = emptySet(),
    val saveError: String? = null
)

@HiltViewModel
class TransportPreferencesViewModel @Inject constructor(
    private val repo: NotificationPreferencesRepository
) : ViewModel() {
    private val _state = androidx.compose.runtime.mutableStateOf(TransportPreferencesState())
    val state: State<TransportPreferencesState> = _state

    fun load() {
        viewModelScope.launch {
            val doc = repo.getPreferences().getOrNull()
            _state.value = TransportPreferencesState(
                loading = false,
                muted = doc?.mutedCategories?.toSet().orEmpty()
            )
        }
    }

    fun setAllowed(category: String, allow: Boolean) {
        val current = _state.value.muted.toMutableSet()
        if (allow) current.remove(category) else current.add(category)
        _state.value = _state.value.copy(muted = current, saveError = null)
        viewModelScope.launch {
            val r = repo.setMutedCategories(current.toList())
            if (r.isFailure) {
                _state.value = _state.value.copy(saveError = r.exceptionOrNull()?.message)
            }
        }
    }
}
