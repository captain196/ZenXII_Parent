package com.schoolsync.parent.ui.notices

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.schoolsync.parent.R
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.model.Notice
import com.schoolsync.parent.data.model.firestore.CircularDoc
import com.schoolsync.parent.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.parent.util.toDateOrNull
import com.schoolsync.parent.util.toEpochMillisOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class NoticesUiState(
    val isLoading: Boolean = true,
    val notices: List<Notice> = emptyList(),
    val expandedNoticeId: String? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class NoticesViewModel @Inject constructor(
    
    @ApplicationContext private val appContext: Context,private val communicationFirestoreRepo: CommunicationFirestoreRepository,
    private val badgeBus: com.schoolsync.parent.util.BadgeBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoticesUiState())
    val uiState: StateFlow<NoticesUiState> = _uiState.asStateFlow()

    /** IDs the parent has opened (from circularReads), updated optimistically. */
    private var readIds: Set<String> = emptySet()

    init {
        observeNotices()
    }

    /** Badge = genuinely UNREAD notices (from read receipts), not a 24h clock. */
    private fun publishBadge(notices: List<Notice>) {
        badgeBus.setCount("notices", notices.count { !it.isRead })
    }

    /**
     * Single source of truth for the notice LIST: the real-time
     * [observeCirculars] listener. Read receipts are bootstrapped once before
     * the first emission so unread dots/badges paint correctly, then folded in
     * on every emit. (Previously we ALSO fired a one-shot [getCirculars] on
     * open — a redundant double read of two collections. Removed.)
     *
     * A post-initial listener failure (e.g. PERMISSION_DENIED after a token
     * refresh) is routed into [NoticesUiState.errorMessage] instead of only
     * being logged, so the UI can show a "couldn't refresh" hint rather than
     * silently keeping stale success.
     */
    private fun observeNotices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // Best-effort read-state bootstrap (never a hard dependency).
            readIds = communicationFirestoreRepo.getReadCircularIds()

            communicationFirestoreRepo.observeCirculars()
                .catch { e ->
                    Log.e("NoticesVM", "Notices observer failed", e)
                    // Keep whatever list is already shown; surface the failure.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: appContext.getString(R.string.notices_refresh_failed_short)
                        )
                    }
                }
                .collect { circulars ->
                    val notices = circulars.map { it.toNotice(readIds) }
                    publishBadge(notices)
                    _uiState.update {
                        it.copy(notices = notices, isLoading = false, errorMessage = null)
                    }
                }
        }
    }

    /**
     * Force-open a notice (idempotent) — used by the deep-link auto-open path.
     * Unlike [toggleExpanded], calling it on an already-open notice keeps it
     * open rather than collapsing it. Marks the notice read like a manual open.
     */
    fun expandNotice(noticeId: String) {
        if (_uiState.value.expandedNoticeId != noticeId) {
            toggleExpanded(noticeId)
        }
    }

    fun toggleExpanded(noticeId: String) {
        val opening = _uiState.value.expandedNoticeId != noticeId
        _uiState.update {
            it.copy(
                expandedNoticeId = if (it.expandedNoticeId == noticeId) null else noticeId
            )
        }
        // Opening a notice marks it read: optimistic local update + best-effort
        // receipt write (idempotent). Badge recomputes from unread.
        if (opening && noticeId !in readIds) {
            readIds = readIds + noticeId
            _uiState.update { st ->
                val updated = st.notices.map { if (it.noticeId == noticeId) it.copy(isRead = true) else it }
                publishBadge(updated)
                st.copy(notices = updated)
            }
            viewModelScope.launch { communicationFirestoreRepo.markCircularRead(noticeId) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            communicationFirestoreRepo.getCirculars().fold(
                onSuccess = { circulars ->
                    val notices = circulars.map { it.toNotice(readIds) }
                    _uiState.update { it.copy(isRefreshing = false, notices = notices) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isRefreshing = false, errorMessage = e.message)
                    }
                }
            )
        }
    }

    /** Pull-to-refresh: reload notices with min spinner time. */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            try {
                communicationFirestoreRepo.getCirculars().fold(
                    onSuccess = { circulars ->
                        val notices = circulars.map { it.toNotice(readIds) }
                        _uiState.update { it.copy(notices = notices) }
                    },
                    onFailure = { e ->
                        Log.w("NoticesVM", "pullRefresh failed", e)
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.w("NoticesVM", "pullRefresh failed", e)
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minSpinnerMs) {
                kotlinx.coroutines.delay(minSpinnerMs - elapsed)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun CircularDoc.toNotice(read: Set<String>): Notice {
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return Notice(
            noticeId = id,
            title = title,
            body = body,
            // Carry HTML only when description has markup AND differs from body
            // (avoids rendering a redundant WebView for plain notices)
            bodyHtml = description
                .takeIf { it.contains('<') && it != body }
                .orEmpty(),
            author = author,
            authorRole = authorRole,
            category = category,
            priority = priority,
            attachmentUrl = attachmentUrl,
            date = sentAt.toDateOrNull()?.let { dateFormatter.format(it) } ?: "",
            timestamp = sentAt.toEpochMillisOrNull() ?: 0L,
            isRead = id in read
        )
    }
}
