package com.schoolsync.parent.ui.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.Event
import com.schoolsync.parent.data.model.EventMedia
import com.schoolsync.parent.data.model.firestore.EventDoc
import com.schoolsync.parent.data.model.firestore.PtmEventDoc
import com.schoolsync.parent.data.repository.firestore.EventFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.PtmFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventsUiState(
    val isLoading: Boolean = true,
    val events: List<Event> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

data class EventDetailUiState(
    val isLoading: Boolean = true,
    val event: Event? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventFirestoreRepo: EventFirestoreRepository,
    private val ptmFirestoreRepo: PtmFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(EventDetailUiState())
    val detailState: StateFlow<EventDetailUiState> = _detailState.asStateFlow()

    init {
        loadEvents()
    }

    private fun loadEvents() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Fetch upcoming PTMs in parallel — they get rendered as event
            // rows alongside school events. Failure on the PTM side never
            // blocks events from showing; the Events screen is the more
            // important fallback if Firestore is partially down.
            val ptmRows = runCatching { fetchPtmsAsEvents() }.getOrDefault(emptyList())

            eventFirestoreRepo.getEvents().fold(
                onSuccess = { eventDocs ->
                    val events = eventDocs.map { it.toEvent() } + ptmRows
                    val sorted = events.sortedByDescending { it.startDate }
                    _uiState.update { it.copy(isLoading = false, events = sorted) }
                },
                onFailure = { e ->
                    Log.e("EventsVM", "Failed to load events", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            // If events failed but PTMs loaded, still show the PTMs.
                            events = if (ptmRows.isNotEmpty()) ptmRows else emptyList(),
                            errorMessage = if (ptmRows.isEmpty()) (e.message ?: "Failed to load events") else null
                        )
                    }
                    return@fold
                }
            )
            return@launch
        }
    }

    /**
     * Map every upcoming visible PTM to an [Event] row so the Events
     * screen can render them alongside school events. Category is set
     * to `"ptm"` so the screen's row click handler can route to the
     * PTM detail screen instead of the regular event detail.
     */
    private suspend fun fetchPtmsAsEvents(): List<Event> {
        val user = tokenManager.user.firstOrNull() ?: return emptyList()
        val cls = user.className
        val sec = user.section
        if (cls.isBlank() || sec.isBlank()) return emptyList()
        val ptms = ptmFirestoreRepo.getUpcomingPtms(cls, sec).getOrNull().orEmpty()
        return ptms.map { it.toEvent() }
    }

    private fun PtmEventDoc.toEvent(): Event = Event(
        eventId      = ptmEventId.ifBlank { id },
        title        = title.ifBlank { "Parent-Teacher Meeting" },
        description  = description,
        category     = "ptm",
        startDate    = date,
        endDate      = date,
        location     = location,
        status       = status,
        mediaUrls    = emptyList()
    )

    fun loadEventDetail(eventId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true, errorMessage = null) }

            eventFirestoreRepo.getEvent(eventId).fold(
                onSuccess = { eventDoc ->
                    val event = eventDoc?.toEvent()
                    _detailState.update { it.copy(isLoading = false, event = event) }
                },
                onFailure = { e ->
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load event details"
                        )
                    }
                }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val ptmRows = runCatching { fetchPtmsAsEvents() }.getOrDefault(emptyList())
            eventFirestoreRepo.getEvents().fold(
                onSuccess = { eventDocs ->
                    val events = (eventDocs.map { it.toEvent() } + ptmRows)
                        .sortedByDescending { it.startDate }
                    _uiState.update { it.copy(isRefreshing = false, events = events) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            events = if (ptmRows.isNotEmpty()) ptmRows else it.events,
                            errorMessage = if (ptmRows.isEmpty()) e.message else null
                        )
                    }
                }
            )
        }
    }

    /** Pull-to-refresh: reload events with min spinner time. */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            val ptmRows = runCatching { fetchPtmsAsEvents() }.getOrDefault(emptyList())
            try {
                eventFirestoreRepo.getEvents().fold(
                    onSuccess = { eventDocs ->
                        val events = (eventDocs.map { it.toEvent() } + ptmRows)
                            .sortedByDescending { it.startDate }
                        _uiState.update { it.copy(events = events) }
                    },
                    onFailure = { e ->
                        Log.w("EventsVM", "pullRefresh failed", e)
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.w("EventsVM", "pullRefresh failed", e)
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minSpinnerMs) {
                kotlinx.coroutines.delay(minSpinnerMs - elapsed)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun EventDoc.toEvent(): Event = Event(
        eventId = id,
        title = title,
        description = description,
        category = category,
        startDate = startDate,
        endDate = endDate,
        location = location,
        status = status,
        mediaUrls = mediaUrls.map { url ->
            EventMedia(url = url)
        }
    )
}
