package com.schoolsync.parent.ui.homework

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.Homework
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.isActionNeeded
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
    // FIX 3: removed always-0 `aPlusCount` — `grade` was never populated on the
    // live path, so the stat was permanently 0 and had no UI consumer.
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
    val subjects: List<String> = emptyList(),
    val selectedSubject: String? = null,          // null = "All"
    val selectedTab: String = "all",               // "all" | "pending" | "submitted" | "graded". Default "all" — opening on "pending" caused a visible flicker when the autoSelectTab flipped it to "all" after the first data load.
    val selectedHomework: Homework? = null,        // non-null = detail view
    val completionStats: CompletionStats = CompletionStats(),
    val userName: String = "",
    val className: String = "",
    val section: String = "",
    val errorMessage: String? = null,
    val markingDone: Boolean = false,
    val showSubmitDialog: Boolean = false,
    // Dialog-local error so a failed submit is visible ON the dialog itself,
    // not just on the list banner hidden behind it.
    val submitError: String? = null
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
            // Collect (not firstOrNull) so the header — incl. the active child's
            // name (issue 9) — stays correct if the VM outlives a child switch,
            // mirroring the listener's re-keying.
            tokenManager.user.collect { user ->
                _uiState.update {
                    it.copy(
                        userName = user.name.split(" ").firstOrNull() ?: user.name,
                        className = user.className,
                        section = user.section
                    )
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun startLiveListener() {
        listenerJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        listenerJob = viewModelScope.launch {
            // Re-key on (className, section, studentId) so a child-switch while
            // this VM is alive tears down the previous child's listener and
            // re-subscribes for the new child. observeHomework only reacts to
            // schoolId internally, so without re-keying here a sibling switch
            // would keep showing the previous child's homework.
            tokenManager.user
                .map { Triple(it.className, it.section, it.userId) }
                .distinctUntilChanged()
                .flatMapLatest { (className, section, studentId) ->
                    if (className.isNotBlank() && section.isNotBlank()) {
                        homeworkFirestoreRepo.observeHomework(className, section)
                            .map { docs -> studentId to docs }
                    } else {
                        flowOf(null)
                    }
                }
                .catch { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("HomeworkVM", "Firestore listener failed", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load homework"
                        )
                    }
                }
                .collect { keyed ->
                    if (keyed == null) {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Class/section not available")
                        }
                        return@collect
                    }
                    val (studentId, homeworkDocs) = keyed
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
        }
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
    }

    // ── Subject filter ──────────────────────────────────────────────────────
    fun selectSubject(subject: String?) {
        _uiState.update { it.copy(selectedSubject = subject) }
    }

    // ── Detail selection ────────────────────────────────────────────────────
    fun selectHomework(homework: Homework?) {
        _uiState.update { it.copy(selectedHomework = homework) }
    }

    // ── Submit homework with text response ──────────────────────────────────
    fun markAsDone(homework: Homework, submissionText: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(markingDone = true, submitError = null) }
            try {
                val user = tokenManager.user.firstOrNull() ?: User.empty()
                // Gate on schoolId — that's what the repo's submitHomework
                // actually requires (getSchoolCode() reads schoolId, not the
                // login schoolCode). Gating on schoolCode could be blank even
                // when submit would succeed, and with no else branch markingDone
                // never reset → undismissable spinner.
                if (user.isLoggedIn && user.schoolId.isNotBlank()) {
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
                            // Surface the real reason on the dialog so a rule
                            // denial ("PERMISSION_DENIED") or network error is
                            // visible instead of the dialog appearing to do nothing.
                            _uiState.update {
                                it.copy(
                                    markingDone = false,
                                    submitError = friendlySubmitError(e)
                                )
                            }
                        }
                    )
                } else {
                    // Identifiers missing — reset the spinner and surface an
                    // error so the submit dialog can be dismissed (dismissal is
                    // gated on !isSubmitting).
                    _uiState.update {
                        it.copy(markingDone = false, submitError = "Cannot submit — your account has no school linked. Log out and back in.")
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeworkVM", "Failed to mark as done", e)
                _uiState.update { it.copy(markingDone = false, submitError = friendlySubmitError(e)) }
            }
        }
    }

    fun showSubmitDialog(homework: Homework) {
        _uiState.update { it.copy(showSubmitDialog = true, selectedHomework = homework, submitError = null) }
    }

    fun hideSubmitDialog() {
        _uiState.update { it.copy(showSubmitDialog = false, submitError = null) }
    }

    /** Map a submit exception to a short, user-readable reason. */
    private fun friendlySubmitError(e: Throwable): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("PERMISSION_DENIED", true) ->
                "Submission blocked by permissions. Log out and back in, then retry."
            msg.contains("UNAVAILABLE", true) || msg.contains("network", true) ->
                "Network problem — check your connection and try again."
            else -> "Couldn't submit: ${msg.ifBlank { "unknown error" }}"
        }
    }

    // ── Internal helpers ────────────────────────────────────────────────────
    private fun recomputeAll() {
        computeStats()
        computeCounts()
    }

    private fun computeStats() {
        _uiState.update { state ->
            val all = state.allHomework
            val total = all.size
            val completed = all.count { isCompleted(it) }
            val pct = if (total > 0) completed.toFloat() / total else 0f

            // Week stats: homework with due date this week
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            val weekStart = calendar.time
            calendar.add(Calendar.DAY_OF_WEEK, 7)
            val weekEnd = calendar.time

            // FIX 9: only homework that actually falls within this week counts
            // toward the weekly bucket. Undated homework (blank/unparseable
            // dueDate) is NOT "due this week" and was wrongly inflating the
            // weekly total before.
            var weekTotal = 0
            var weekCompleted = 0
            all.forEach { hw ->
                val dueDate = parseDueDate(hw.dueDate)
                if (dueDate != null && !dueDate.before(weekStart) && dueDate.before(weekEnd)) {
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
                    weekTotal = weekTotal,
                    weekCompleted = weekCompleted
                )
            )
        }
    }

    private fun computeCounts() {
        val all = _uiState.value.allHomework
        // FIX 2: use the shared isActionNeeded() predicate (pending OR
        // incomplete) so the nav badge agrees with the Dashboard/Profile counts.
        val actionNeeded = all.count { isActionNeeded(it.studentStatus) }
        badgeBus.setCount("homework", actionNeeded)
    }

    // ── Static helpers ──────────────────────────────────────────────────────
    companion object {

        /**
         * Filter + sort for a single tab. Used directly by the pager pages
         * so each tab's view is computed identically without needing to
         * round-trip through the ViewModel during a swipe.
         */
        fun filterForTab(
            all: List<Homework>,
            tab: String,
            subject: String?
        ): List<Homework> {
            var filtered = when (tab) {
                "pending" -> all.filter { it.studentStatus.lowercase().trim() == "pending" }
                "incomplete" -> all.filter { it.studentStatus.lowercase().trim() == "incomplete" }
                "submitted" -> all.filter { it.studentStatus.lowercase().trim() == "submitted" }
                "graded" -> all.filter {
                    val s = it.studentStatus.lowercase().trim()
                    // "complete" is a dead branch — no writer (parent/teacher/
                    // admin) emits it; kept harmless for forward-compat.
                    s == "reviewed" || s == "complete"
                }
                else -> all // "all"
            }
            if (subject != null) {
                filtered = filtered.filter { it.subject.equals(subject, ignoreCase = true) }
            }
            // Pending AND incomplete are both action-needed queues, so order
            // them by urgency (priority, then soonest due) rather than recency.
            return if (tab == "pending" || tab == "incomplete") {
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

        // Time-bearing ISO shapes — the embedded offset / 'Z' fixes the instant,
        // so we use the parsed moment exactly as written.
        // isLenient=false is CRITICAL: lenient SimpleDateFormat mangles a
        // wrong-separator input (e.g. "15-05-2026" against dd/MM/yyyy) into a
        // garbage date instead of throwing, so the correct format later in the
        // list never gets tried. Non-lenient makes each format reject inputs it
        // doesn't truly match, letting parseDueDate fall through to the right one.
        private val isoDateTimeFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { isLenient = false },  // ISO 8601 with offset (e.g. +05:30)
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC"); isLenient = false }
        )
        // Date-only shapes — parsed in IST so the calendar day is unambiguous;
        // we then push the instant to END OF DAY IST (see parseDueDate).
        private val dateOnlyFormats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = IST_TZ; isLenient = false },
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = IST_TZ; isLenient = false },
            SimpleDateFormat("dd-MM-yyyy", Locale.US).apply { timeZone = IST_TZ; isLenient = false },
            SimpleDateFormat("dd MMM yyyy", Locale.US).apply { timeZone = IST_TZ; isLenient = false }
        )

        /**
         * IST calendar-day bucket for an instant (days since epoch in
         * Asia/Kolkata). Shared by [derivePriority] and [dueDateLabel] so their
         * day-boundary math agrees — a raw millis / toDays() truncation drifts
         * across the IST day boundary and mislabels "due tomorrow" vs "in 2 days".
         */
        internal fun istDay(d: Date): Long {
            val c = Calendar.getInstance(IST_TZ).apply {
                time = d
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return c.timeInMillis / 86_400_000L
        }

        /** Shift an IST-midnight instant to 23:59:59.999 of that same IST day. */
        internal fun toEndOfDayIst(midnight: Date): Date {
            val c = Calendar.getInstance(IST_TZ).apply {
                time = midnight
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return c.time
        }

        /**
         * Parse a stored dueDate. The contract: teacher/admin write end-of-day
         * IST ISO ("YYYY-MM-DDT23:59:59+05:30"), but a bare "YYYY-MM-DD" may
         * also appear. If a time component is present we use it verbatim;
         * otherwise we treat the date as END OF DAY IST (23:59:59), NOT
         * midnight — so an item due "today" isn't already overdue this morning.
         */
        fun parseDueDate(dateStr: String): Date? {
            if (dateStr.isBlank()) return null
            // Time-bearing ISO — use the instant as written.
            if (dateStr.contains('T')) {
                for (fmt in isoDateTimeFormats) {
                    try {
                        val d = fmt.parse(dateStr)
                        if (d != null) return d
                    } catch (_: Exception) {}
                }
            }
            // Date-only — treat as end of day IST.
            for (fmt in dateOnlyFormats) {
                try {
                    val d = fmt.parse(dateStr)
                    if (d != null) return toEndOfDayIst(d)
                } catch (_: Exception) {}
            }
            return null
        }

        fun isOverdue(homework: Homework): Boolean {
            if (isCompleted(homework)) return false
            if (homework.dueDate.isBlank()) return false
            val due = parseDueDate(homework.dueDate) ?: return false
            // Date comparison is an absolute-instant compare, so IST end-of-day
            // due dates only flip overdue once the IST day has actually ended.
            return due.before(Date())
        }

        fun isCompleted(homework: Homework): Boolean {
            val s = homework.studentStatus.lowercase().trim()
            // "complete" / "done" are dead branches — no writer emits them;
            // retained as harmless forward-compat.
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

            // FIX 9: bucket by IST calendar day (same normalization dueDateLabel
            // uses) instead of TimeUnit.MILLISECONDS.toDays(), whose truncation
            // drifts the HIGH/MEDIUM boundary depending on the time of day.
            val diffDays = istDay(dueDate) - istDay(now)
            return when {
                diffDays <= 1L -> Priority.HIGH     // due today or tomorrow
                diffDays <= 3L -> Priority.MEDIUM   // within 3 days
                else -> Priority.LOW
            }
        }

        fun dueDateLabel(homework: Homework): String {
            val due = parseDueDate(homework.dueDate) ?: return homework.dueDate
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
