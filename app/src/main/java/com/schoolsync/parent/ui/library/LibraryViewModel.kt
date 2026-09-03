package com.schoolsync.parent.ui.library

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.schoolsync.parent.R
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.firestore.LibraryBookDoc
import com.schoolsync.parent.data.model.firestore.LibraryFineDoc
import com.schoolsync.parent.data.model.firestore.LibraryIssueDoc
import com.schoolsync.parent.data.repository.firestore.LibraryFirestoreRepository
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.schoolsync.parent.util.localizedString
import com.schoolsync.parent.util.localizedPlural

// ── UI State ────────────────────────────────────────────────────────────────

data class LibraryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,  // 0=Current Books, 1=History, 2=Fines, 3=Catalog
    val currentBooks: List<LibraryIssueDoc> = emptyList(),
    val bookHistory: List<LibraryIssueDoc> = emptyList(),
    val fines: List<LibraryFineDoc> = emptyList(),
    val catalogBooks: List<LibraryBookDoc> = emptyList(),
    val searchQuery: String = "",
    val userName: String = "",
    val totalFines: Double = 0.0
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    
    @ApplicationContext private val appContext: Context,private val libraryRepo: LibraryFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadLibraryData()
    }

    private fun loadLibraryData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val user = tokenManager.user.firstOrNull() ?: User.empty()
            val studentId = user.userId

            if (studentId.isBlank()) {
                _uiState.update {
                    it.copy(isLoading = false, error = appContext.localizedString(R.string.lib_no_student_id))
                }
                return@launch
            }

            _uiState.update {
                it.copy(userName = user.name.split(" ").firstOrNull() ?: user.name)
            }

            // Load issued books
            try {
                libraryRepo.getMyIssuedBooks(studentId).fold(
                    onSuccess = { books ->
                        Log.d("LibraryVM", "Loaded ${books.size} issued books")
                        _uiState.update { it.copy(currentBooks = books) }
                    },
                    onFailure = { e ->
                        Log.e("LibraryVM", "Failed to load issued books", e)
                    }
                )
            } catch (e: Exception) {
                Log.e("LibraryVM", "Error loading issued books", e)
            }

            // Load book history
            try {
                libraryRepo.getMyBookHistory(studentId).fold(
                    onSuccess = { history ->
                        Log.d("LibraryVM", "Loaded ${history.size} history entries")
                        _uiState.update { it.copy(bookHistory = history) }
                    },
                    onFailure = { e ->
                        Log.e("LibraryVM", "Failed to load book history", e)
                    }
                )
            } catch (e: Exception) {
                Log.e("LibraryVM", "Error loading book history", e)
            }

            // Load fines
            try {
                libraryRepo.getMyFines(studentId).fold(
                    onSuccess = { fines ->
                        Log.d("LibraryVM", "Loaded ${fines.size} fines")
                        val total = fines.sumOf { it.fineAmount }
                        _uiState.update { it.copy(fines = fines, totalFines = total) }
                    },
                    onFailure = { e ->
                        Log.e("LibraryVM", "Failed to load fines", e)
                    }
                )
            } catch (e: Exception) {
                Log.e("LibraryVM", "Error loading fines", e)
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refresh() {
        loadLibraryData()
    }

    /** Pull-to-refresh: reload library data with min spinner time. */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            try {
                loadLibraryData()
            } catch (e: Exception) {
                Log.w("LibraryVM", "pullRefresh failed", e)
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minSpinnerMs) {
                kotlinx.coroutines.delay(minSpinnerMs - elapsed)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // ── Tab switching ───────────────────────────────────────────────────────

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
        // Trigger catalog search when switching to catalog tab
        if (tab == 3 && _uiState.value.searchQuery.isNotBlank()) {
            searchCatalog(_uiState.value.searchQuery)
        }
    }

    // ── Catalog search ──────────────────────────────────────────────────────

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length >= 2) {
            searchCatalog(query)
        } else if (query.isEmpty()) {
            _uiState.update { it.copy(catalogBooks = emptyList()) }
        }
    }

    private fun searchCatalog(query: String) {
        viewModelScope.launch {
            try {
                libraryRepo.searchBooks(query).fold(
                    onSuccess = { books ->
                        Log.d("LibraryVM", "Search returned ${books.size} books")
                        _uiState.update { it.copy(catalogBooks = books) }
                    },
                    onFailure = { e ->
                        Log.e("LibraryVM", "Search failed", e)
                    }
                )
            } catch (e: Exception) {
                Log.e("LibraryVM", "Error searching catalog", e)
            }
        }
    }

    // ── Static helpers ──────────────────────────────────────────────────────

    companion object {
        // machine parsers — do not localize. These read server-written strings, so
        // they must stay in the locale the server writes: Locale.ROOT. Under
        // Locale.getDefault() the "dd MMM yyyy" parser cannot read "15 Mar 2026"
        // once the device is in Hindi (it expects "15 मार्च 2026"), so every
        // library due date silently became null and no book showed as due.
        private val dateFormats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT),
            SimpleDateFormat("dd-MM-yyyy", Locale.ROOT),
            SimpleDateFormat("dd MMM yyyy", Locale.ROOT)
        )

        fun parseDate(dateStr: String): Date? {
            if (dateStr.isBlank()) return null
            for (fmt in dateFormats) {
                try {
                    val d = fmt.parse(dateStr)
                    if (d != null) return d
                } catch (_: Exception) {}
            }
            return null
        }

        /**
         * Calculate days remaining until due date.
         * Positive = days left, negative = days overdue.
         */
        fun daysUntilDue(dueDate: String): Long? {
            val due = parseDate(dueDate) ?: return null
            val now = Date()
            val diffMs = due.time - now.time
            return TimeUnit.MILLISECONDS.toDays(diffMs)
        }

        /**
         * Human-readable due date text.
         */
        /**
         * Companion-object helper, so the Context is passed in explicitly —
         * there is no injected appContext in this scope.
         *
         * The day counts use <plurals>; the old `"$days days"` was English-only
         * pluralisation and wrong in every other language.
         */
        fun dueDateLabel(ctx: android.content.Context, dueDate: String): String {
            val days = daysUntilDue(dueDate) ?: return dueDate
            return when {
                days < 0L -> ctx.localizedPlural(
                    R.plurals.lib_days_overdue, (-days).toInt(), (-days).toInt())
                days == 0L -> ctx.getString(R.string.dash_due_today)
                days == 1L -> ctx.getString(R.string.dash_due_tomorrow)
                days <= 7 -> ctx.localizedPlural(
                    R.plurals.lib_due_in_days, days.toInt(), days.toInt())
                else -> {
                    val d = parseDate(dueDate)
                    // Display formatter: localized month name, Latin digits pinned.
                    if (d != null) ctx.getString(R.string.lib_due_on_date,
                        com.schoolsync.parent.util.DisplayFormat.dayMonth(d)) else dueDate
                }
            }
        }

        /**
         * Format a date string for display (e.g. "15 Mar 2026").
         */
        fun formatDisplayDate(dateStr: String): String {
            val d = parseDate(dateStr) ?: return dateStr
            return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(d)
        }
    }
}
