package com.schoolsync.parent.ui.ptm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.PtmEventDoc
import com.schoolsync.parent.data.model.firestore.PtmRsvpDoc
import com.schoolsync.parent.data.model.firestore.PtmSectionAssignment
import com.schoolsync.parent.data.model.firestore.assignedQueueNumber
import com.schoolsync.parent.data.model.firestore.assignmentFor
import com.schoolsync.parent.data.model.firestore.normalizedStatus
import com.schoolsync.parent.data.repository.firestore.PtmFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.PtmRsvpException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase-C section-wise PTM model. The parent applies for the meeting and
 * is assigned a queue number; the meeting itself is hosted by their
 * section's class teacher in the time window. There is no slot selection.
 */
data class PtmDetailUiState(
    val isLoading: Boolean = true,
    val ptm: PtmEventDoc? = null,
    val existingRsvp: PtmRsvpDoc? = null,

    /** Resolved class teacher for this student's section (null = no
     *  matching section in PTM.sections, or class teacher unset). */
    val assignment: PtmSectionAssignment? = null,

    /** Phase-A normalized status: applied / declined / delivered /
     *  no-show / "" (no response yet). */
    val rsvpStatus: String = "",
    /** Token / queue number when the parent has already applied. */
    val queueNumber: Int? = null,

    /** Optional note the parent attaches. Persisted across apply/decline. */
    val note: String = "",

    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    /** Convenience flag — parent has applied and is in the queue. */
    val isApplied: Boolean get() = rsvpStatus == "applied"
    /** Parent declined. */
    val isDeclined: Boolean get() = rsvpStatus == "declined"
    /** Class teacher already marked the parent's meeting as delivered. */
    val isDelivered: Boolean get() = rsvpStatus == "delivered"
    /** Parent never responded yet. Show the Apply button. */
    val isFresh: Boolean get() = rsvpStatus.isBlank()
}

@HiltViewModel
class PtmDetailViewModel @Inject constructor(
    private val ptmRepo: PtmFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(PtmDetailUiState())
    val state: StateFlow<PtmDetailUiState> = _state.asStateFlow()

    fun load(ptmEventId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            try {
                val user = tokenManager.user.firstOrNull()
                val cls = user?.className.orEmpty()
                val sec = user?.section.orEmpty()
                val sid = user?.userId.orEmpty()

                // Find this PTM in the parent's visible-upcoming list. The
                // list filter handles status/date/section/window-time.
                val list = ptmRepo.getUpcomingPtms(cls, sec).getOrNull().orEmpty()
                val ptm = list.firstOrNull { p ->
                    p.ptmEventId == ptmEventId
                        || p.id == ptmEventId
                        || p.id.endsWith("_$ptmEventId")
                }
                if (ptm == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "PTM not found or no longer scheduled."
                        )
                    }
                    return@launch
                }

                // Resolve the section assignment for this student. Phase-B
                // PTMs carry sections[]; legacy slot-based PTMs synthesise
                // a single-section list (with blank classTeacherId, since
                // we deliberately don't trust slot.teacherId for the class
                // teacher role).
                val assignment = ptm.assignmentFor(cls, sec)

                // Existing RSVP (may be legacy-shaped with bookings[] or
                // new flat shape with status/queueNumber).
                val existing = if (sid.isNotBlank()) {
                    ptmRepo.getRsvp(ptm.ptmEventId.ifBlank { ptmEventId }, sid).getOrNull()
                } else null
                val status = existing?.normalizedStatus().orEmpty()
                val queue  = existing?.assignedQueueNumber()

                _state.update {
                    it.copy(
                        isLoading    = false,
                        ptm          = ptm,
                        existingRsvp = existing,
                        assignment   = assignment,
                        rsvpStatus   = status,
                        queueNumber  = queue,
                        note         = existing?.note.orEmpty()
                    )
                }
            } catch (e: Exception) {
                Log.e("PtmDetailVM", "load failed", e)
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load.") }
            }
        }
    }

    fun onNoteChange(value: String) {
        if (value.length <= 500) _state.update { it.copy(note = value) }
    }

    /**
     * Send the apply request. Resolves through the repo's transaction so
     * the queue number is allocated atomically. Pre-checks duplicate-apply
     * locally too, but the repo enforces it server-side regardless.
     */
    fun apply() {
        val cur = _state.value
        val ptm = cur.ptm ?: return
        if (cur.assignment == null || cur.assignment.classTeacherId.isBlank()) {
            _state.update { it.copy(errorMessage = "No class teacher assigned for your section. Ask the school office to set one.") }
            return
        }
        if (cur.isApplied) {
            _state.update { it.copy(errorMessage = "You've already applied.") }
            return
        }
        if (cur.isDelivered) {
            _state.update { it.copy(errorMessage = "Your meeting has already been marked delivered by the teacher.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null, successMessage = null) }
            try {
                val user = tokenManager.user.firstOrNull()
                if (user == null || user.userId.isBlank()) {
                    _state.update { it.copy(isSubmitting = false, errorMessage = "Not signed in.") }
                    return@launch
                }
                val parentName = listOf(user.fatherName, user.motherName)
                    .firstOrNull { it.isNotBlank() } ?: user.name

                val res = ptmRepo.applyToPtm(
                    ptm          = ptm,
                    studentId    = user.userId,
                    studentName  = user.name,
                    className    = user.className,
                    section      = user.section,
                    rollNo       = user.rollNo,
                    parentName   = parentName,
                    parentPhone  = user.phone,
                    parentEmail  = user.email,
                    note         = cur.note
                )
                res.fold(
                    onSuccess = { applyResult ->
                        _state.update {
                            it.copy(
                                isSubmitting   = false,
                                rsvpStatus     = "applied",
                                queueNumber    = applyResult.queueNumber,
                                successMessage = "You're #${applyResult.queueNumber} in line. Meet ${applyResult.classTeacherName}."
                            )
                        }
                    },
                    onFailure = { err ->
                        Log.w("PtmDetailVM", "apply failed", err)
                        val msg = if (err is PtmRsvpException) {
                            when (err.code) {
                                "DUPLICATE_APPLY"           -> "You've already applied — refresh to see your queue number."
                                "ALREADY_DELIVERED"         -> err.message
                                "ALREADY_NO_SHOW"           -> err.message
                                "NO_CLASS_TEACHER"          -> err.message
                                "NO_ASSIGNMENT_FOR_SECTION" -> err.message
                                else                         -> err.message
                            }
                        } else err.message
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = msg ?: "Failed to apply."
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("PtmDetailVM", "apply exception", e)
                _state.update { it.copy(isSubmitting = false, errorMessage = e.message ?: "Failed.") }
            }
        }
    }

    /**
     * Decline the PTM. No queue allocation. If the parent had previously
     * applied, the queue number is preserved on the doc but the status
     * flips to declined — the teacher screen filters declined out.
     */
    fun decline() {
        val cur = _state.value
        val ptm = cur.ptm ?: return
        if (cur.isDelivered) {
            _state.update { it.copy(errorMessage = "Your meeting has already been marked delivered.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null, successMessage = null) }
            try {
                val user = tokenManager.user.firstOrNull()
                if (user == null || user.userId.isBlank()) {
                    _state.update { it.copy(isSubmitting = false, errorMessage = "Not signed in.") }
                    return@launch
                }
                val parentName = listOf(user.fatherName, user.motherName)
                    .firstOrNull { it.isNotBlank() } ?: user.name

                val res = ptmRepo.declineFromPtm(
                    ptm          = ptm,
                    studentId    = user.userId,
                    studentName  = user.name,
                    className    = user.className,
                    section      = user.section,
                    rollNo       = user.rollNo,
                    parentName   = parentName,
                    parentPhone  = user.phone,
                    parentEmail  = user.email,
                    note         = cur.note
                )
                res.fold(
                    onSuccess = {
                        _state.update {
                            it.copy(
                                isSubmitting    = false,
                                rsvpStatus      = "declined",
                                successMessage  = "Thanks for letting us know."
                            )
                        }
                    },
                    onFailure = { err ->
                        Log.w("PtmDetailVM", "decline failed", err)
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = err.message ?: "Failed to decline."
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("PtmDetailVM", "decline exception", e)
                _state.update { it.copy(isSubmitting = false, errorMessage = e.message ?: "Failed.") }
            }
        }
    }

    /**
     * Re-enable the Apply / Decline buttons after a submission. Doesn't
     * touch the saved doc — a subsequent re-apply will go through the
     * repo's transaction (which allocates a fresh queue number, since the
     * old one was effectively released when the parent declined).
     */
    fun enterEditMode() {
        _state.update {
            it.copy(
                rsvpStatus     = if (it.isDelivered) it.rsvpStatus else "",
                successMessage = null,
                errorMessage   = null
            )
        }
    }
}
