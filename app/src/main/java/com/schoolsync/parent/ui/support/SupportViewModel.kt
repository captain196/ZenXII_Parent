package com.schoolsync.parent.ui.support

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.SupportMessageDoc
import com.schoolsync.parent.data.model.firestore.SupportTicketDoc
import com.schoolsync.parent.data.repository.firestore.SupportFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Support Desk — Parent app state.
 *
 * Three screens share this: the ticket list, one thread, and the compose form.
 */
data class SupportUiState(
    val isLoading: Boolean = true,
    val tickets: List<SupportTicketDoc> = emptyList(),

    // ── one thread ──
    val activeTicket: SupportTicketDoc? = null,
    val messages: List<SupportMessageDoc> = emptyList(),
    val isThreadLoading: Boolean = false,
    // Resolved download URLs for THIS ticket's attachments, in the ticket's own
    // order. Empty until resolved, and stays empty for any that fail — a photo
    // that will not load must not blank the thread it belongs to.
    val attachmentUrls: List<String> = emptyList(),

    // ── compose ──
    // Captured when the composer opens, NOT read at submit. The app has an
    // app-wide sibling switcher that REPLACES the logged-in user, so reading
    // it at submit would file the ticket against whichever child happened to
    // be selected by then — not the one the parent was writing about.
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val category: String = "",
    val subject: String = "",
    val body: String = "",
    val pickedImages: List<Uri> = emptyList(),
    val isSubmitting: Boolean = false,
    val submittedTicketId: String? = null,

    // ── reply ──
    val replyBody: String = "",
    val isSendingReply: Boolean = false,

    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    /** Composer is valid when a category and a body exist. Subject is optional. */
    val canSubmit: Boolean
        get() = category.isNotBlank() && body.trim().length >= 5 && !isSubmitting
}

/**
 * The offline-first Firestore SDK never server-acks a write while offline, so
 * awaiting one hangs a spinner forever. Every write here races this timeout;
 * a local write is durable, so a timeout is reported as success that will sync.
 * Mirrors LeaveViewModel's PAR-M1 handling.
 */
private const val WRITE_TIMEOUT_MS = 6_000L

/** SavedStateHandle keys — an in-progress ticket survives process death. */
private const val KEY_TICKET_ID = "support_draft_ticket_id"
private const val KEY_CATEGORY = "support_draft_category"
private const val KEY_SUBJECT = "support_draft_subject"
private const val KEY_BODY = "support_draft_body"

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val repository: SupportFirestoreRepository,
    private val tokenManager: TokenManager,
    private val savedState: SavedStateHandle,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupportUiState())
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

    /**
     * The id this compose session will write to.
     *
     * Minted ONCE and persisted, which is what makes creation idempotent
     * (E-02). A double-tap, a retry after a dropped connection, or a rebuild
     * after process death all write the SAME document key — the second write
     * overwrites the first instead of raising a duplicate ticket. Generating it
     * at submit time instead is how you get two tickets from one impatient tap.
     */
    private var draftTicketId: String =
        savedState.get<String>(KEY_TICKET_ID) ?: repository.newTicketId().also {
            savedState[KEY_TICKET_ID] = it
        }

    val categories: List<String> get() = SupportFirestoreRepository.CATEGORIES

    fun categoryLabel(key: String): String = repository.categoryLabel(key)

    init {
        restoreDraft()
        captureActiveStudent()
        observeTickets()
    }

    /**
     * Pin the child this ticket is about, once.
     *
     * Siblings are switched app-wide via the dashboard switcher, which swaps
     * the whole User record. Pinning here means a mid-compose switch cannot
     * silently re-target a half-written ticket.
     */
    private fun captureActiveStudent() {
        viewModelScope.launch {
            val u = tokenManager.user.firstOrNull() ?: return@launch
            _uiState.update {
                it.copy(
                    studentId = u.userId,
                    studentName = u.name,
                    className = listOfNotNull(
                        u.className.takeIf { s -> s.isNotBlank() },
                        u.section.takeIf { s -> s.isNotBlank() }
                    ).joinToString("-")
                )
            }
        }
    }

    // ── list ─────────────────────────────────────────────────────────────────

    private fun observeTickets() {
        viewModelScope.launch {
            repository.observeMyTickets()
                .catch { e ->
                    // A listener error — rules rejection, a missing index — must
                    // surface. Silently emitting an empty list here would render
                    // as "you have no tickets", which is the read-side version of
                    // reporting a failure as success.
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = friendly(e))
                    }
                }
                .collect { list ->
                    _uiState.update { it.copy(isLoading = false, tickets = list, errorMessage = null) }
                }
        }
    }

    // ── thread ───────────────────────────────────────────────────────────────

    fun openTicket(ticketId: String) {
        _uiState.update {
            it.copy(
                isThreadLoading = true, activeTicket = null,
                messages = emptyList(), attachmentUrls = emptyList()
            )
        }
        resolvedFor = null
        viewModelScope.launch {
            repository.observeTicket(ticketId)
                .catch { e -> _uiState.update { it.copy(isThreadLoading = false, errorMessage = friendly(e)) } }
                .collect { t ->
                    _uiState.update { it.copy(activeTicket = t, isThreadLoading = false) }
                    // observeTicket re-emits on every ticket write (a reply moves
                    // lastMessageAt). Resolving on each emission would re-sign the
                    // same objects repeatedly, so key the work to the filename list.
                    val names = t?.attachments.orEmpty()
                    val key = ticketId + '|' + names.joinToString(",")
                    if (names.isNotEmpty() && key != resolvedFor) {
                        resolvedFor = key
                        resolveAttachments(ticketId, names)
                    }
                }
        }
        viewModelScope.launch {
            repository.observeMessages(ticketId)
                .catch { e -> _uiState.update { it.copy(errorMessage = friendly(e)) } }
                .collect { m -> _uiState.update { it.copy(messages = m) } }
        }
    }

    /** ticketId + filename list already resolved, so re-emissions are cheap. */
    private var resolvedFor: String? = null

    /**
     * Resolve every attachment filename to a URL, keeping ticket order.
     *
     * Failures are dropped rather than propagated: an attachment that will not
     * resolve is a missing thumbnail, not a broken thread. Nothing is surfaced
     * as an error banner for the same reason — the parent came here to read the
     * conversation.
     */
    private fun resolveAttachments(ticketId: String, names: List<String>) {
        viewModelScope.launch {
            val urls = names.mapNotNull { name ->
                repository.attachmentUrl(ticketId, name).getOrNull()
            }
            _uiState.update { it.copy(attachmentUrls = urls) }
        }
    }

    fun updateReply(text: String) = _uiState.update { it.copy(replyBody = text) }

    /**
     * Reply on a thread.
     *
     * This is also how a resolved ticket is reopened — a Cloud Function makes
     * the transition when it sees a parent message on a resolved ticket inside
     * the window. The app never touches status, and must not start.
     */
    fun sendReply(ticketId: String) {
        val text = _uiState.value.replyBody.trim()
        if (text.isEmpty() || _uiState.value.isSendingReply) return

        _uiState.update { it.copy(isSendingReply = true, errorMessage = null) }
        viewModelScope.launch {
            val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                repository.appendMessage(ticketId, text)
            }
            when {
                result == null || result.isSuccess -> {
                    // Server-acked, or queued locally. Both are durable.
                    _uiState.update {
                        it.copy(isSendingReply = false, replyBody = "",
                            infoMessage = if (result == null) "Sent — it will sync when you're back online." else null)
                    }
                }
                else -> _uiState.update {
                    it.copy(isSendingReply = false, errorMessage = friendly(result.exceptionOrNull()))
                }
            }
        }
    }

    // ── compose ──────────────────────────────────────────────────────────────

    fun updateCategory(key: String) { _uiState.update { it.copy(category = key) }; savedState[KEY_CATEGORY] = key }
    fun updateSubject(text: String) { _uiState.update { it.copy(subject = text) }; savedState[KEY_SUBJECT] = text }
    fun updateBody(text: String) { _uiState.update { it.copy(body = text) }; savedState[KEY_BODY] = text }

    fun addImages(uris: List<Uri>) {
        val room = SupportFirestoreRepository.MAX_ATTACHMENTS - _uiState.value.pickedImages.size
        if (room <= 0) {
            _uiState.update { it.copy(errorMessage = "You can attach up to ${SupportFirestoreRepository.MAX_ATTACHMENTS} photos.") }
            return
        }
        _uiState.update { it.copy(pickedImages = it.pickedImages + uris.take(room)) }
    }

    fun removeImage(uri: Uri) = _uiState.update { it.copy(pickedImages = it.pickedImages - uri) }

    /**
     * Raise the ticket.
     *
     * Order is deliberate and matches E-04: attachments upload FIRST, and the
     * ticket document is written only if every one succeeded. A failure here
     * therefore leaves NO ticket, and the parent retries a compose screen with
     * their text still in it. The reverse order strands a ticket pointing at a
     * file that was never uploaded — which staff see as an attachment that
     * silently will not open.
     */
    fun submit() {
        val s = _uiState.value
        if (!s.canSubmit) return

        // Guard set synchronously so a second tap returns above rather than
        // racing a second upload.
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            // Pre-check the cap for a decent message. The real gate is in the
            // security rules against a server-maintained counter — this is
            // courtesy, not enforcement, and it must never be the only check.
            val open = repository.openTicketCount()
            if (open >= SupportFirestoreRepository.MAX_OPEN_TICKETS) {
                _uiState.update {
                    it.copy(isSubmitting = false, errorMessage =
                        "You already have ${SupportFirestoreRepository.MAX_OPEN_TICKETS} open tickets. " +
                        "Please wait for one to be resolved before raising another.")
                }
                return@launch
            }

            // ── attachments first ──
            val names = mutableListOf<String>()
            s.pickedImages.forEachIndexed { i, uri ->
                val r = repository.uploadAttachment(appContext, draftTicketId, uri, i + 1)
                r.fold(
                    onSuccess = { names.add(it) },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isSubmitting = false, errorMessage =
                                "Could not upload photo ${i + 1}: ${friendly(e)} Nothing was sent — your message is still here.")
                        }
                        return@launch
                    }
                )
            }

            val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                repository.createTicket(
                    ticketId = draftTicketId,
                    category = s.category,
                    // Falls back to the category label rather than shipping an
                    // empty subject — a blank row in a triager's queue is worse
                    // than a generic one.
                    subject = s.subject.trim().ifBlank { repository.categoryLabel(s.category) },
                    body = s.body.trim(),
                    // Pinned at compose-open by captureActiveStudent(). This
                    // install logs a parent in AS the active child, and the
                    // dashboard switcher swaps that identity wholesale — so
                    // there is no picker to show, but there IS a child to pin.
                    studentId = s.studentId,
                    studentName = s.studentName,
                    className = s.className,
                    attachments = names
                )
            }

            when {
                result == null || result.isSuccess -> {
                    clearDraft()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submittedTicketId = draftTicketId,
                            category = "", subject = "", body = "", pickedImages = emptyList(),
                            infoMessage = if (result == null)
                                "Sent — it will sync when you're back online." else null
                        )
                    }
                    // A NEW id for the next ticket. Reusing it would overwrite
                    // the one just raised.
                    draftTicketId = repository.newTicketId()
                    savedState[KEY_TICKET_ID] = draftTicketId
                }
                else -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = friendly(result.exceptionOrNull()))
                }
            }
        }
    }

    // ── draft persistence ────────────────────────────────────────────────────

    private fun restoreDraft() {
        _uiState.update {
            it.copy(
                category = savedState.get<String>(KEY_CATEGORY).orEmpty(),
                subject = savedState.get<String>(KEY_SUBJECT).orEmpty(),
                body = savedState.get<String>(KEY_BODY).orEmpty()
            )
        }
    }

    private fun clearDraft() {
        savedState.remove<String>(KEY_CATEGORY)
        savedState.remove<String>(KEY_SUBJECT)
        savedState.remove<String>(KEY_BODY)
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
    fun clearInfo() = _uiState.update { it.copy(infoMessage = null) }
    fun consumeSubmitted() = _uiState.update { it.copy(submittedTicketId = null) }

    /**
     * Turn an exception into something a parent can act on.
     *
     * A raw PERMISSION_DENIED tells them nothing and looks like the app is
     * broken. It usually means a claims change needs a re-login, which is
     * something they can actually do.
     */
    private fun friendly(e: Throwable?): String {
        val raw = e?.message.orEmpty()
        return when {
            raw.contains("PERMISSION_DENIED", true) ->
                "The school's system refused that. Try signing out and back in — if it keeps happening, contact the school office."
            raw.contains("FAILED_PRECONDITION", true) ->
                "The school's system isn't ready for this yet. Please try again later."
            raw.contains("UNAVAILABLE", true) || raw.contains("network", true) ->
                "You appear to be offline. It will send when you reconnect."
            raw.isBlank() -> "Something went wrong. Please try again."
            else -> raw
        }
    }
}
