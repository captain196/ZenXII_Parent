package com.schoolsync.parent.ui.notices

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
    private val communicationFirestoreRepo: CommunicationFirestoreRepository,
    private val badgeBus: com.schoolsync.parent.util.BadgeBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoticesUiState())
    val uiState: StateFlow<NoticesUiState> = _uiState.asStateFlow()

    init {
        loadNotices()
        observeNotices()
    }

    /** Notices newer than 24h are surfaced as the "new" badge count. */
    private fun publishBadge(notices: List<Notice>) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val recent = notices.count { it.timestamp > cutoff }
        badgeBus.setCount("notices", recent)
    }

    private fun loadNotices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            communicationFirestoreRepo.getCirculars().fold(
                onSuccess = { circulars ->
                    val notices = circulars.map { it.toNotice() }
                    publishBadge(notices)
                    _uiState.update { it.copy(isLoading = false, notices = notices) }
                },
                onFailure = { e ->
                    Log.e("NoticesVM", "Failed to load notices", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load notices"
                        )
                    }
                }
            )
        }
    }

    private fun observeNotices() {
        viewModelScope.launch {
            try {
                communicationFirestoreRepo.observeCirculars().collect { circulars ->
                    val notices = circulars.map { it.toNotice() }
                    publishBadge(notices)
                    _uiState.update { it.copy(notices = notices, isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("NoticesVM", "Notices observer failed", e)
            }
        }
    }

    fun toggleExpanded(noticeId: String) {
        _uiState.update {
            it.copy(
                expandedNoticeId = if (it.expandedNoticeId == noticeId) null else noticeId
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            communicationFirestoreRepo.getCirculars().fold(
                onSuccess = { circulars ->
                    val notices = circulars.map { it.toNotice() }
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
                        val notices = circulars.map { it.toNotice() }
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

    private fun CircularDoc.toNotice(): Notice {
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
            timestamp = sentAt.toEpochMillisOrNull() ?: 0L
        )
    }
}
