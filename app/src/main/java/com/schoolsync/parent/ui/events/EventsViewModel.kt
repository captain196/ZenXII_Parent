package com.schoolsync.parent.ui.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.Event
import com.schoolsync.parent.data.model.EventMedia
import com.schoolsync.parent.data.model.hasUsableCover
import com.schoolsync.parent.data.model.GalleryAlbum
import com.schoolsync.parent.data.model.firestore.EventDoc
import com.schoolsync.parent.data.model.firestore.PtmEventDoc
import com.schoolsync.parent.data.repository.firestore.EventFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.GalleryFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.PtmFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    // The gallery album generated for this event, if any. Non-null enables the
    // "View Photos" jump into the full album; null (the common case) hides it.
    val eventAlbum: GalleryAlbum? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventFirestoreRepo: EventFirestoreRepository,
    private val ptmFirestoreRepo: PtmFirestoreRepository,
    private val galleryFirestoreRepo: GalleryFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(EventDetailUiState())
    val detailState: StateFlow<EventDetailUiState> = _detailState.asStateFlow()

    // Live events subscription. Held so refresh/pull-to-refresh can re-subscribe
    // (and so a config change / retry cancels the previous listener).
    private var eventsJob: Job? = null

    init {
        observeEvents()
    }

    /**
     * Subscribe to the live events feed. Each Firestore snapshot re-merges the
     * (parallel) PTM rows and album covers, so newly published events appear
     * without a manual refresh. [showLoader] is false when a pull-to-refresh
     * spinner is already visible (avoid flashing the full-screen loader over
     * existing content).
     */
    private fun observeEvents(showLoader: Boolean = true) {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            if (showLoader) _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Fetch upcoming PTMs once per subscription — they get rendered as
            // event rows alongside school events. Failure on the PTM side never
            // blocks events from showing; the Events screen is the more
            // important fallback if Firestore is partially down.
            val ptmRows = runCatching { fetchPtmsAsEvents() }.getOrDefault(emptyList())

            eventFirestoreRepo.observeEvents().collect { result ->
                result.fold(
                    onSuccess = { eventDocs ->
                        val events = (withAlbumCovers(eventDocs.map { it.toEvent() }) + ptmRows)
                            .sortedByDescending { it.startDate }
                        _uiState.update {
                            it.copy(isLoading = false, events = events, errorMessage = null)
                        }
                    },
                    onFailure = { e ->
                        Log.e("EventsVM", "Failed to observe events", e)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                // If events failed but PTMs loaded, still show the PTMs.
                                events = if (ptmRows.isNotEmpty()) ptmRows else emptyList(),
                                errorMessage = if (ptmRows.isEmpty()) (e.message ?: "Failed to load events") else null
                            )
                        }
                    }
                )
            }
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
            _detailState.update { it.copy(isLoading = true, eventAlbum = null, errorMessage = null) }

            eventFirestoreRepo.getEvent(eventId).fold(
                onSuccess = { eventDoc ->
                    val event = eventDoc?.toEvent()
                    _detailState.update { it.copy(isLoading = false, event = event) }
                    // Best-effort lookup of the event's gallery album so the
                    // detail screen can offer a "View Photos" jump. A failure
                    // here (e.g. missing index) just leaves the button hidden;
                    // it must not break the event detail itself.
                    if (event != null) {
                        galleryFirestoreRepo.getEventAlbum(eventId)
                            .onSuccess { album ->
                                _detailState.update { st ->
                                    // If the event has no inline media, borrow the
                                    // album cover so the detail hero shows a photo.
                                    val ev = st.event
                                    val enriched = if (ev != null && !ev.hasUsableCover() &&
                                        !album?.coverImage.isNullOrBlank()
                                    ) {
                                        // Record the borrowed cover ALONGSIDE the event's
                                        // own media. Overwriting mediaUrls here used to
                                        // wipe out video-only events' videos entirely.
                                        ev.copy(borrowedCoverUrl = album!!.coverImage)
                                    } else ev
                                    st.copy(eventAlbum = album, event = enriched)
                                }
                            }
                    }
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

    /** Error-state retry: re-subscribe with the full-screen loader. */
    fun refresh() = observeEvents(showLoader = true)

    /**
     * Pull-to-refresh. The live listener already keeps the list fresh, so this
     * just re-subscribes (recovering from any prior listener error and re-pulling
     * PTMs) while showing the refresh spinner for a minimum duration.
     */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            // Re-subscribe without the full-screen loader (spinner is already shown).
            observeEvents(showLoader = false)
            kotlinx.coroutines.delay(600L)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * For events whose own media is empty, borrow the linked event-album's
     * `coverImage` (the photos live in `galleryMedia`, not on the event doc —
     * e.g. "Annual sport day"). One album query, only when something needs it.
     */
    private suspend fun withAlbumCovers(events: List<Event>): List<Event> {
        if (events.all { it.hasUsableCover() }) return events
        val albums = runCatching { galleryFirestoreRepo.getAlbums().getOrNull() }
            .getOrNull().orEmpty()
            .filter { it.source == "event" && it.coverImage.isNotBlank() }
        if (albums.isEmpty()) return events
        return events.map { e ->
            if (e.hasUsableCover()) e
            else {
                // Album stores the RAW event id ("EVT0001"); Event.eventId is the
                // full "{schoolId}_{EVT...}" doc id.
                val cover = albums.firstOrNull { a ->
                    e.eventId == a.eventId || e.eventId.endsWith("_${a.eventId}")
                }?.coverImage
                // Additive, not destructive — see Event.borrowedCoverUrl.
                if (cover != null) e.copy(borrowedCoverUrl = cover) else e
            }
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
        organizer = organizer,
        status = status,
        mediaUrls = mediaUrls.map { url ->
            // EventDoc.mediaUrls is a bare List<String>, so the media type is
            // not carried on the wire. Infer it from the file extension so the
            // `type == "video"` UI branches (play overlay, external launch)
            // actually fire instead of every item defaulting to "image".
            EventMedia(url = url, type = inferMediaType(url))
        }
    )

    /** Infer "video" / "image" from a media URL's file extension. */
    private fun inferMediaType(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        val videoExts = listOf(".mp4", ".mov", ".webm", ".mkv", ".avi", ".m4v", ".3gp")
        return if (videoExts.any { path.endsWith(it) }) "video" else "image"
    }
}
