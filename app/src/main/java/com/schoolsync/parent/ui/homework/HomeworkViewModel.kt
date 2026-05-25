package com.schoolsync.parent.ui.homework

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.Homework
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.firestore.HomeworkFirestoreRepository
import com.schoolsync.parent.util.Constants
import com.schoolsync.parent.util.debugLog
import com.schoolsync.parent.util.toDateOrNull
import com.schoolsync.parent.util.toEpochMillisOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ── Subject visual mapping ──────────────────────────────────────────────────
data class SubjectInfo(
    val emoji: String,
    val colorKey: String // mapped to AppColors in the UI
)

val subjectInfoMap: Map<String, SubjectInfo> = mapOf(
    "mathematics" to SubjectInfo("\uD83D\uDCD0", "accent"),
    "maths"       to SubjectInfo("\uD83D\uDCD0", "accent"),
    "math"        to SubjectInfo("\uD83D\uDCD0", "accent"),
    "science"     to SubjectInfo("\uD83D\uDD2C", "success"),
    "english"     to SubjectInfo("\uD83D\uDCD6", "purple"),
    "hindi"       to SubjectInfo("\u270F\uFE0F", "coral"),
    "geography"   to SubjectInfo("\uD83C\uDF0D", "teal"),
    "history"     to SubjectInfo("\uD83C\uDFDB\uFE0F", "warning"),
    "social science" to SubjectInfo("\uD83C\uDF0D", "teal"),
    "sst"         to SubjectInfo("\uD83C\uDF0D", "teal"),
    "computer"    to SubjectInfo("\uD83D\uDCBB", "info"),
    "computers"   to SubjectInfo("\uD83D\uDCBB", "info"),
    "art"         to SubjectInfo("\uD83C\uDFA8", "coral"),
    "music"       to SubjectInfo("\uD83C\uDFB5", "purple"),
    "physics"     to SubjectInfo("\u269B\uFE0F", "info"),
    "chemistry"   to SubjectInfo("\u2697\uFE0F", "success"),
    "biology"     to SubjectInfo("\uD83E\uDDEC", "success"),
    "evs"         to SubjectInfo("\uD83C\uDF3F", "success"),
    "gk"          to SubjectInfo("\uD83D\uDCA1", "warning"),
    "moral science" to SubjectInfo("\uD83D\uDCD6", "info")
)

fun getSubjectInfo(subject: String): SubjectInfo {
    val key = subject.lowercase().trim()
    return subjectInfoMap[key] ?: SubjectInfo("\uD83D\uDCDA", "accent") // default: books emoji + accent
}

// ── Completion stats ────────────────────────────────────────────────────────
data class CompletionStats(
    val total: Int = 0,
    val completed: Int = 0,
    val percentage: Float = 0f,
    val streak: Int = 0,
    val aPlusCount: Int = 0,
    val weekTotal: Int = 0,
    val weekCompleted: Int = 0
)

// ── Priority derivation ─────────────────────────────────────────────────────
enum class Priority { HIGH, MEDIUM, LOW }

// ── UI State ────────────────────────────────────────────────────────────────
data class HomeworkUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val allHomework: List<Homework> = emptyList(),
    val filteredHomework: List<Homework> = emptyList(),
    val subjects: List<String> = emptyList(),
    val selectedSubject: String? = null,          // null = "All"
    val selectedTab: String = "all",               // "all" | "pending" | "submitted" | "graded". Default "all" — opening on "pending" caused a visible flicker when the autoSelectTab flipped it to "all" after the first data load.
    val selectedHomework: Homework? = null,        // non-null = detail view
    val completionStats: CompletionStats = CompletionStats(),
    val pendingCount: Int = 0,
    val completedCount: Int = 0,
    val userName: String = "",
    val className: String = "",
    val section: String = "",
    val errorMessage: String? = null,
    val markingDone: Boolean = false,
    val showSubmitDialog: Boolean = false
)

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val homeworkFirestoreRepo: HomeworkFirestoreRepository,
    private val tokenManager: TokenManager,
    private val badgeBus: com.schoolsync.parent.util.BadgeBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeworkUiState())
    val uiState: StateFlow<HomeworkUiState> = _uiState.asStateFlow()
    private var listenerJob: Job? = null

    init {
        loadUserInfo()
        startLiveListener()
    }

    override fun onCleared() {
        // Belt-and-braces. viewModelScope cancellation already propagates to
        // the live-listener coroutine, which tears down the underlying
        // Firestore SnapshotListener via callbackFlow's awaitClose. Cancelling
        // the job explicitly here guarantees that the FCM/badge cleanup
        // path runs even if a future refactor pulls the listener out of
        // viewModelScope (e.g. into a process-lifetime scope).
        listenerJob?.cancel()
        super.onCleared()
    }

    // Removed autoSelectTab(): default is now "all" so the prior
    // pending→all auto-flip caused a visible flicker on screen open.
    // If the user explicitly picks "pending", we keep them there even
    // when count drops to 0 — the system shouldn't override their choice.

    private fun loadUserInfo() {
        viewModelScope.launch {
            val user = tokenManager.user.firstOrNull() ?: User.empty()
            _uiState.update {
                it.copy(
                    userName = user.name.split(" ").firstOrNull() ?: user.name,
                    className = user.className,
                    section = user.section
                )
            }
        }
    }

    private fun startLiveListener() {
        listenerJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        listenerJob = viewModelScope.launch {
            val user = tokenManager.user.firstOrNull() ?: User.empty()
            val className = user.className
            val section = user.section
            val studentId = user.userId

            if (className.isNotBlank() && section.isNotBlank()) {
                // Primary: Firestore live listener
                try {
                    homeworkFirestoreRepo.observeHomework(className, section).collect { homeworkDocs ->
                        Log.d("HomeworkVM", "Firestore live update: ${homeworkDocs.size} homework items")

                        // Bulk-fetch THIS student's submissions + teacherMarks
                        // ONCE per refresh — replaces the per-homework N+1
                        // pattern. Both queries are indexed (schoolId+studentId).
                        val submissionsMap: Map<String, com.schoolsync.parent.data.model.firestore.SubmissionDoc> =
                            if (studentId.isNotBlank())
                                homeworkFirestoreRepo.getSubmissionsForStudent(studentId)
                            else emptyMap()
                        val teacherMarksMap: Map<String, Pair<Int, String>> =
                            if (studentId.isNotBlank())
                                homeworkFirestoreRepo.getTeacherMarksForStudent(studentId)
                            else emptyMap()

                        // Map HomeworkDoc → existing Homework model, checking submission status
                        val items = homeworkDocs.map { doc ->
                            // O(1) lookup — same status-extraction logic as before.
                            val submissionDoc = submissionsMap[doc.id]
                            val submissionStatus = submissionDoc?.status ?: "pending"

                            // No submission of their own — fall back to a
                            // teacherMark if the teacher recorded one (e.g.
                            // student was absent but evaluated).
                            val teacherMark =
                                if (submissionDoc == null) teacherMarksMap[doc.id] else null

                            Homework(
                                homeworkId = doc.id,
                                hwId = doc.id,
                                title = doc.title,
                                description = doc.description,
                                subject = doc.subject,
                                teacherName = doc.teacherName,
                                teacherId = doc.teacherId,
                                date = doc.createdAt.toDateOrNull()?.let {
                                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
                                } ?: "",
                                dueDate = doc.dueDate,
                                timestamp = doc.createdAt.toEpochMillisOrNull() ?: 0L,
                                className = doc.className,
                                section = doc.section,
                                studentStatus = submissionStatus,
                                isFromNewPath = true,
                                attachments = doc.attachments,
                                score = submissionDoc?.score ?: -1,
                                feedback = submissionDoc?.remark ?: "",
                                hasTeacherMark    = teacherMark != null,
                                teacherMarkScore  = teacherMark?.first  ?: -1,
                                teacherMarkRemark = teacherMark?.second ?: ""
                            )
                        }

                        val subjects = items.map { it.subject }.filter { it.isNotBlank() }.distinct().sorted()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                allHomework = items,
                                subjects = subjects
                            )
                        }
                        recomputeAll()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.d("HomeworkVM", "Firestore listener cancelled (normal)")
                } catch (e: Exception) {
                    Log.e("HomeworkVM", "Firestore listener failed", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load homework"
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Class/section not available")
                }
            }
        }
    }

    fun loadHomework() {
        startLiveListener()
    }

    /** Pull-to-refresh: re-trigger listener with min spinner time. */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            try {
                startLiveListener()
            } catch (e: Exception) {
                // BUG-025 — route through debugLog (OEM-strip-immune via
                // cache-file sink) per F1 lesson; iQOO/OnePlus/Xiaomi strip
                // Log.w from logcat, leaving operator triage blind on the
                // dominant Indian Android OEM substrate.
                debugLog("ACC_HW_PARENT_VM_PULL_REFRESH_FAILED err=${e.javaClass.simpleName}:${e.message}")
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minSpinnerMs) {
                kotlinx.coroutines.delay(minSpinnerMs - elapsed)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // ── Tab switching ───────────────────────────────────────────────────────
    fun setTab(tab: String) {
        _uiState.update { it.copy(selectedTab = tab) }
        applyFilters()
    }

    // ── Subject filter ──────────────────────────────────────────────────────
    fun selectSubject(subject: String?) {
        _uiState.update { it.copy(selectedSubject = subject) }
        applyFilters()
    }

    // ── Detail selection ────────────────────────────────────────────────────
    fun selectHomework(homework: Homework?) {
        _uiState.update { it.copy(selectedHomework = homework) }
    }

    // ── Submit homework with text response ──────────────────────────────────
    fun markAsDone(homework: Homework, submissionText: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(markingDone = true) }
            try {
                val user = tokenManager.user.firstOrNull() ?: User.empty()
                if (user.isLoggedIn && user.schoolCode.isNotBlank()) {
                    homeworkFirestoreRepo.submitHomework(
                        homeworkId = homework.hwId,
                        studentId = user.userId,
                        studentName = user.name,
                        sectionKey = "${Constants.Firebase.classKey(user.className)}/${Constants.Firebase.sectionKey(user.section)}",
                        text = submissionText,
                        files = emptyList()
                    ).fold(
                        onSuccess = {
                            Log.d("HomeworkVM", "Submitted ${homework.hwId} for ${user.userId}")
                            // Update local state so UI reflects "submitted" immediately
                            // without waiting for the homework live listener to re-fire
                            _uiState.update { state ->
                                val updated = state.allHomework.map { hw ->
                                    if (hw.hwId == homework.hwId) {
                                        hw.copy(studentStatus = "submitted")
                                    } else hw
                                }
                                state.copy(
                                    allHomework = updated,
                                    selectedHomework = null,
                                    markingDone = false,
                                    showSubmitDialog = false
                                )
                            }
                            recomputeAll()
                        },
                        onFailure = { e ->
                            Log.e("HomeworkVM", "Failed to submit homework", e)
                            _uiState.update { it.copy(markingDone = false, errorMessage = "Failed to submit") }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeworkVM", "Failed to mark as done", e)
                _uiState.update { it.copy(markingDone = false, errorMessage = "Failed to submit") }
            }
        }
    }

    fun showSubmitDialog(homework: Homework) {
        _uiState.update { it.copy(showSubmitDialog = true, selectedHomework = homework) }
    }

    fun hideSubmitDialog() {
        _uiState.update { it.copy(showSubmitDialog = false) }
    }

    // ── Backward compatibility ──────────────────────────────────────────────
    fun setStatusFilter(filter: String) {
        val tab = when (filter) {
            "completed" -> "completed"
            else -> "pending"
        }
        setTab(tab)
    }

    // ── Internal helpers ────────────────────────────────────────────────────
    private fun recomputeAll() {
        computeStats()
        computeCounts()
        applyFilters()
    }

    private fun computeStats() {
        _uiState.update { state ->
            val all = state.allHomework
            val total = all.size
            val completed = all.count { isCompleted(it) }
            val pct = if (total > 0) completed.toFloat() / total else 0f
            val aPlusCount = all.count { it.grade.equals("A+", ignoreCase = true) }

            // Week stats: homework with due date this week
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            val weekStart = calendar.time
            calendar.add(Calendar.DAY_OF_WEEK, 7)
            val weekEnd = calendar.time

            var weekTotal = 0
            var weekCompleted = 0
            all.forEach { hw ->
                val dueDate = parseDueDate(hw.dueDate)
                if (dueDate != null && !dueDate.before(weekStart) && dueDate.before(weekEnd)) {
                    weekTotal++
                    if (isCompleted(hw)) weekCompleted++
                } else if (hw.dueDate.isBlank()) {
                    // Include homework without due dates in the weekly count
                    weekTotal++
                    if (isCompleted(hw)) weekCompleted++
                }
            }

            // Streak: count consecutive completed homeworks sorted by due date desc
            val sortedByDue = all
                .filter { it.dueDate.isNotBlank() }
                .sortedByDescending { parseDueDate(it.dueDate)?.time ?: 0L }
            var streak = 0
            for (hw in sortedByDue) {
                if (isCompleted(hw)) streak++ else break
            }

            state.copy(
                completionStats = CompletionStats(
                    total = total,
                    completed = completed,
                    percentage = pct,
                    streak = streak,
                    aPlusCount = aPlusCount,
                    weekTotal = weekTotal,
                    weekCompleted = weekCompleted
                )
            )
        }
    }

    private fun computeCounts() {
        val all = _uiState.value.allHomework
        val pending = all.count { it.studentStatus.lowercase().trim() == "pending" }
        val completed = all.count {
            val s = it.studentStatus.lowercase().trim()
            s == "complete" || s == "submitted" || s == "reviewed"
        }
        badgeBus.setCount("homework", pending)
        _uiState.update { it.copy(pendingCount = pending, completedCount = completed) }
    }

    private fun applyFilters() {
        _uiState.update { state ->
            state.copy(
                filteredHomework = filterForTab(state.allHomework, state.selectedTab, state.selectedSubject)
            )
        }
    }

    // ── Static helpers ──────────────────────────────────────────────────────
    companion object {

        /**
         * Filter + sort for a single tab. Used by applyFilters() AND by the
         * pager pages so each tab's view is computed identically without
         * needing to round-trip through the ViewModel during a swipe.
         */
        fun filterForTab(
            all: List<Homework>,
            tab: String,
            subject: String?
        ): List<Homework> {
            var filtered = when (tab) {
                "pending" -> all.filter { it.studentStatus.lowercase().trim() == "pending" }
                "submitted" -> all.filter { it.studentStatus.lowercase().trim() == "submitted" }
                "graded" -> all.filter {
                    val s = it.studentStatus.lowercase().trim()
                    s == "reviewed" || s == "complete"
                }
                else -> all // "all"
            }
            if (subject != null) {
                filtered = filtered.filter { it.subject.equals(subject, ignoreCase = true) }
            }
            return if (tab == "pending") {
                filtered.sortedWith(compareBy<Homework> {
                    when (derivePriority(it)) {
                        Priority.HIGH -> 0
                        Priority.MEDIUM -> 1
                        Priority.LOW -> 2
                    }
                }.thenBy { parseDueDate(it.dueDate)?.time ?: Long.MAX_VALUE })
            } else {
                filtered.sortedByDescending { it.timestamp }
            }
        }

        private val IST_TZ: java.util.TimeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        private val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),  // ISO 8601 with offset (e.g. +05:30)
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        )

        fun parseDueDate(dateStr: String): Date? {
            if (dateStr.isBlank()) return null
            for (fmt in dateFormats) {
                try {
                    val d = fmt.parse(dateStr)
                    if (d != null) return d
                } catch (_: Exception) {}
            }
            return null
        }

        fun isOverdue(homework: Homework): Boolean {
            if (isCompleted(homework)) return false
            if (homework.dueDate.isBlank()) return false
            val due = parseDueDate(homework.dueDate) ?: return false
            return due.before(Date())
        }

        fun isCompleted(homework: Homework): Boolean {
            val s = homework.studentStatus.lowercase().trim()
            return s == "complete" || s == "submitted" || s == "done" || s == "reviewed"
        }

        /**
         * Derive priority from explicit field or due date:
         * - overdue or due tomorrow = HIGH
         * - within 3 days = MEDIUM
         * - else LOW
         */
        fun derivePriority(homework: Homework): Priority {
            // Explicit priority takes precedence
            if (homework.priority.isNotBlank()) {
                return when (homework.priority.lowercase()) {
                    "high" -> Priority.HIGH
                    "medium" -> Priority.MEDIUM
                    "low" -> Priority.LOW
                    else -> Priority.LOW
                }
            }

            val dueDate = parseDueDate(homework.dueDate) ?: return Priority.LOW
            val now = Date()
            if (dueDate.before(now)) return Priority.HIGH // overdue

            val diffMs = dueDate.time - now.time
            val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
            return when {
                diffDays <= 1 -> Priority.HIGH     // due today or tomorrow
                diffDays <= 3 -> Priority.MEDIUM   // within 3 days
                else -> Priority.LOW
            }
        }

        fun dueDateLabel(homework: Homework): String {
            val due = parseDueDate(homework.dueDate) ?: return homework.dueDate
            fun istDay(d: Date): Long {
                val c = java.util.Calendar.getInstance(IST_TZ).apply {
                    time = d
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                return c.timeInMillis / 86_400_000L
            }
            val diffDays = istDay(due) - istDay(Date())
            val timeFmt  = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = IST_TZ }
            val dateFmt  = SimpleDateFormat("dd MMM", Locale.US).apply { timeZone = IST_TZ }
            return when (diffDays) {
                0L   -> "Due today (${timeFmt.format(due)})"
                1L   -> "Due tomorrow"
                else -> "Due on ${dateFmt.format(due)}"
            }
        }

        /**
         * Full date+time label for surfaces (e.g. Submit Homework dialog)
         * where we want absolute "16 May 2026, 11:59 PM IST" rather than
         * the relative form. Mirrors the Teacher app's `dueDateDisplay`
         * shape so notification body strings and on-screen text agree.
         * Falls back to the raw stored value only if it cannot be parsed,
         * so blank / legacy non-ISO strings still surface something.
         */
        fun dueDateFullLabel(homework: Homework): String {
            val due = parseDueDate(homework.dueDate) ?: return homework.dueDate
            val out = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).apply {
                timeZone = IST_TZ
            }
            return "${out.format(due)} IST"
        }

        /** Subject-specific study tip */
        fun subjectTip(subject: String): String {
            return when (subject.lowercase().trim()) {
                "mathematics", "maths", "math" ->
                    "Show all working steps clearly -- partial marks are given for method even if the final answer is wrong."
                "science", "physics", "chemistry", "biology", "evs" ->
                    "Draw labelled diagrams wherever possible. They often carry separate marks."
                "english" ->
                    "Underline key vocabulary and proofread for grammar before submission."
                "hindi" ->
                    "Practice writing neatly and check for matra errors before submitting."
                "history" ->
                    "Use dates and names of key people to strengthen your answers."
                "geography", "social science", "sst" ->
                    "Reference maps and data wherever the question allows it."
                else ->
                    "Read the instructions carefully and manage your time well."
            }
        }
    }
}
