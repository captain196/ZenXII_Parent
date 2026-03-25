package com.schoolsync.parent.ui.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.model.Event
import com.schoolsync.parent.data.model.firestore.EventDoc
import com.schoolsync.parent.data.repository.firestore.EventFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val eventFirestoreRepo: EventFirestoreRepository
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

            eventFirestoreRepo.getEvents().fold(
                onSuccess = { eventDocs ->
                    val events = eventDocs.map { it.toEvent() }
                    _uiState.update { it.copy(isLoading = false, events = events) }
                },
                onFailure = { e ->
                    Log.e("EventsVM", "Failed to load events", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load events"
                        )
                    }
                }
            )
        }
    }

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
            eventFirestoreRepo.getEvents().fold(
                onSuccess = { eventDocs ->
                    val events = eventDocs.map { it.toEvent() }
                    _uiState.update { it.copy(isRefreshing = false, events = events) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isRefreshing = false, errorMessage = e.message)
                    }
                }
            )
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
        mediaUrls = mediaUrls
    )
}
