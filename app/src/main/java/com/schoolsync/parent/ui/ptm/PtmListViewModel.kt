package com.schoolsync.parent.ui.ptm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.PtmEventDoc
import com.schoolsync.parent.data.model.firestore.PtmRsvpDoc
import com.schoolsync.parent.data.model.firestore.normalizedStatus
import com.schoolsync.parent.data.repository.firestore.PtmFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * One row in the parent-facing PTM list. Combines the PTM event metadata
 * with the parent's own Phase-C RSVP status. RSVP doc may be null when
 * the parent hasn't responded yet.
 */
data class PtmListRow(
    val ptm: PtmEventDoc,
    val rsvp: PtmRsvpDoc?,
    /** Phase-A vocabulary used by the row badge:
     *  `applied`, `delivered`, `no-show`, `declined`, or `none`
     *  (no RSVP doc / no response yet). Legacy `confirmed`/`attended`
     *  are mapped to `applied`/`delivered` by `normalizedStatus()`. */
    val rsvpStatus: String,
    /** Whether [ptm] is upcoming (`date >= today` or blank). */
    val isUpcoming: Boolean
)

data class PtmListUiState(
    val isLoading: Boolean = true,
    val upcoming: List<PtmListRow> = emptyList(),
    val past: List<PtmListRow> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class PtmListViewModel @Inject constructor(
    private val ptmRepo: PtmFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(PtmListUiState())
    val state: StateFlow<PtmListUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val user = tokenManager.user.firstOrNull()
                val cls = user?.className.orEmpty()
                val sec = user?.section.orEmpty()
                val sid = user?.userId.orEmpty()
                if (cls.isBlank() || sec.isBlank()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Class or section missing on profile."
                        )
                    }
                    return@launch
                }

                // Fetch list + RSVPs in parallel-ish (sequential awaits but
                // reads are cached aggressively by Firestore SDK).
                val ptms = ptmRepo.getAllVisiblePtms(cls, sec).getOrNull().orEmpty()
                val rsvps = if (sid.isNotBlank()) {
                    ptmRepo.getAllRsvpsForStudent(sid).getOrNull().orEmpty()
                } else emptyList()

                // Map RSVPs by ptmEventId for O(1) row lookup.
                val rsvpByPtm = rsvps.associateBy { it.ptmEventId }

                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val upcoming = mutableListOf<PtmListRow>()
                val past = mutableListOf<PtmListRow>()
                ptms.forEach { p ->
                    val rsvp = rsvpByPtm[p.ptmEventId]
                    // Phase-A vocab: applied / delivered / no-show / declined,
                    // OR "" (no response). Legacy bookings[] are normalised
                    // through the same helper. Collapse "" to "none" so the
                    // badge can distinguish "haven't RSVP'd" from a real
                    // status.
                    val rsvpStatus = rsvp?.normalizedStatus()
                        ?.takeIf { it.isNotBlank() }
                        ?: "none"
                    val isUpcoming = p.date.isBlank() || p.date >= today
                    val row = PtmListRow(p, rsvp, rsvpStatus, isUpcoming)
                    if (isUpcoming) upcoming.add(row) else past.add(row)
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        upcoming = upcoming,   // already sorted DESC by date — keep
                        past = past
                    )
                }
            } catch (e: Exception) {
                Log.e("PtmListVM", "load failed", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load PTMs."
                    )
                }
            }
        }
    }
}
