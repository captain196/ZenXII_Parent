package com.schoolsync.parent.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.model.Event
import com.schoolsync.parent.data.model.Notice
import com.schoolsync.parent.data.model.TeacherStoryGroup
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.DashboardSummary
import com.schoolsync.parent.data.repository.EventRepository
import com.schoolsync.parent.data.repository.HomeworkRepository
import com.schoolsync.parent.data.repository.NoticeRepository
import com.schoolsync.parent.data.repository.RedFlagRepository
import com.schoolsync.parent.data.repository.StoryRepository
import com.schoolsync.parent.data.repository.StudentRepository
import com.schoolsync.parent.data.repository.firestore.AttendanceFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.HomeworkFirestoreRepository
import com.schoolsync.parent.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val todayAttendance: String? = null,
    val attendancePercentage: Float = 0f,
    val attendanceChange: Float? = null,
    val pendingFeeAmount: Double = 0.0,
    val pendingHomeworkCount: Int = 0,
    val recentNotices: List<Notice> = emptyList(),
    val upcomingEvents: List<Event> = emptyList(),
    val activeFlagCount: Int = 0,
    val storyGroups: List<TeacherStoryGroup> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val noticeRepository: NoticeRepository,
    private val eventRepository: EventRepository,
    private val homeworkRepository: HomeworkRepository,
    private val redFlagRepository: RedFlagRepository,
    private val storyRepository: StoryRepository,
    private val attendanceFirestoreRepo: AttendanceFirestoreRepository,
    private val feeFirestoreRepo: FeeFirestoreRepository,
    private val communicationFirestoreRepo: CommunicationFirestoreRepository,
    private val homeworkFirestoreRepo: HomeworkFirestoreRepository,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Load user from DataStore
            val user = studentRepository.currentUser.firstOrNull()
            _uiState.update { it.copy(user = user) }

            // Primary: load from Firestore
            loadFromFirestore(user)

            // Load upcoming events (no Firestore equivalent yet, keep RTDB)
            try {
                val events = eventRepository.getEvents(withMedia = true)
                _uiState.update { it.copy(upcomingEvents = events) }
            } catch (_: Exception) { }

            // Load active flag count (no Firestore equivalent yet, keep RTDB)
            try {
                val flagCount = redFlagRepository.getActiveFlagCount()
                _uiState.update { it.copy(activeFlagCount = flagCount) }
            } catch (_: Exception) { }

            // Load stories (no Firestore equivalent yet, keep RTDB)
            try {
                val stories = storyRepository.getAllActiveStories()
                _uiState.update { it.copy(storyGroups = stories) }
            } catch (_: Exception) { }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // TODO: Remove RTDB fallback after Firestore validation
    private suspend fun loadFromFirestore(user: User?) {
        val studentId = user?.userId ?: return
        val className = user.className
        val section = user.section

        // ── Attendance percentage from Firestore ──
        try {
            val summaryResult = attendanceFirestoreRepo.getAttendanceSummary(studentId)
            summaryResult.fold(
                onSuccess = { summaries ->
                    val totalPresent = summaries.sumOf { it.present }
                    val totalWorking = summaries.sumOf { it.workingDays }
                    val overallPercent = if (totalWorking > 0) {
                        (totalPresent.toFloat() / totalWorking.toFloat()) * 100f
                    } else 0f

                    // Determine today's attendance from the current month summary
                    val now = java.time.YearMonth.now()
                    val currentMonthSummary = summaries.find {
                        it.month.contains(now.month.name, ignoreCase = true) ||
                            it.month.contains("${now.monthValue}", ignoreCase = false)
                    }
                    val todayDay = java.time.LocalDate.now().dayOfMonth
                    val todayStatus = currentMonthSummary?.dayWise?.getOrNull(todayDay - 1)?.let { code ->
                        when (code) {
                            'P' -> "Present"
                            'A' -> "Absent"
                            'L' -> "Leave"
                            'H' -> "Holiday"
                            'V' -> "Vacation"
                            'T' -> "Trip"
                            else -> null
                        }
                    }

                    _uiState.update {
                        it.copy(
                            attendancePercentage = overallPercent,
                            todayAttendance = todayStatus
                        )
                    }
                },
                onFailure = { e ->
                    Log.w("DashboardVM", "Firestore attendance failed, falling back to RTDB", e)
                    loadAttendanceFromRtdb()
                }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "Firestore attendance exception, falling back to RTDB", e)
            loadAttendanceFromRtdb()
        }

        // ── Pending fees from Firestore ──
        try {
            val pendingResult = feeFirestoreRepo.getPendingDemands(studentId)
            pendingResult.fold(
                onSuccess = { demands ->
                    val totalPending = demands.sumOf { it.netAmount - it.paidAmount }
                    _uiState.update { it.copy(pendingFeeAmount = totalPending) }
                },
                onFailure = { e ->
                    Log.w("DashboardVM", "Firestore fees failed, falling back to RTDB", e)
                    loadFeesFromRtdb()
                }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "Firestore fees exception, falling back to RTDB", e)
            loadFeesFromRtdb()
        }

        // ── Notices from Firestore ──
        try {
            val circularsResult = communicationFirestoreRepo.getCirculars(limit = 3)
            circularsResult.fold(
                onSuccess = { circulars ->
                    val notices = circulars.map { doc ->
                        Notice(
                            noticeId = doc.id,
                            title = doc.title,
                            body = doc.body,
                            author = doc.author,
                            category = doc.category,
                            priority = doc.priority,
                            attachmentUrl = doc.attachmentUrl,
                            date = doc.sentAt?.toDate()?.let {
                                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(it)
                            } ?: "",
                            timestamp = doc.sentAt?.toDate()?.time ?: 0L
                        )
                    }
                    _uiState.update { it.copy(recentNotices = notices) }
                },
                onFailure = { e ->
                    Log.w("DashboardVM", "Firestore notices failed, falling back to RTDB", e)
                    loadNoticesFromRtdb()
                }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "Firestore notices exception, falling back to RTDB", e)
            loadNoticesFromRtdb()
        }

        // ── Homework count from Firestore ──
        try {
            if (className.isNotBlank() && section.isNotBlank()) {
                val hwResult = homeworkFirestoreRepo.getActiveHomework(className, section)
                hwResult.fold(
                    onSuccess = { homeworkDocs ->
                        // All active homework without a submission is considered pending
                        _uiState.update { it.copy(pendingHomeworkCount = homeworkDocs.size) }
                    },
                    onFailure = { e ->
                        Log.w("DashboardVM", "Firestore homework failed, falling back to RTDB", e)
                        loadHomeworkCountFromRtdb()
                    }
                )
            } else {
                loadHomeworkCountFromRtdb()
            }
        } catch (e: Exception) {
            Log.w("DashboardVM", "Firestore homework exception, falling back to RTDB", e)
            loadHomeworkCountFromRtdb()
        }
    }

    // TODO: Remove RTDB fallback after Firestore validation
    private suspend fun loadAttendanceFromRtdb() {
        try {
            val summary = studentRepository.getDashboardSummary()
            _uiState.update {
                it.copy(
                    todayAttendance = summary.todayAttendance,
                    attendancePercentage = summary.monthlyAttendancePercent,
                    attendanceChange = summary.attendanceChange
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = e.message ?: "Failed to load dashboard")
            }
        }
    }

    // TODO: Remove RTDB fallback after Firestore validation
    private suspend fun loadFeesFromRtdb() {
        try {
            val summary = studentRepository.getDashboardSummary()
            _uiState.update { it.copy(pendingFeeAmount = summary.pendingFeeAmount) }
        } catch (_: Exception) { }
    }

    // TODO: Remove RTDB fallback after Firestore validation
    private suspend fun loadNoticesFromRtdb() {
        try {
            val notices = noticeRepository.getNotices(limit = 3)
            _uiState.update { it.copy(recentNotices = notices) }
        } catch (_: Exception) { }
    }

    // TODO: Remove RTDB fallback after Firestore validation
    private suspend fun loadHomeworkCountFromRtdb() {
        try {
            val allHw = homeworkRepository.observeAllHomework().firstOrNull() ?: emptyList()
            val pending = allHw.count {
                val s = it.studentStatus.lowercase().trim()
                s == "pending" || s == "not submitted" || s == ""
            }
            _uiState.update { it.copy(pendingHomeworkCount = pending) }
        } catch (_: Exception) { }
    }

    fun markStoryViewed(storyId: String) {
        viewModelScope.launch {
            storyRepository.markAsViewed(storyId)
            // Refresh stories to update viewed status
            try {
                val stories = storyRepository.getAllActiveStories()
                _uiState.update { it.copy(storyGroups = stories) }
            } catch (_: Exception) { }
        }
    }

    fun refresh() {
        loadDashboard()
    }
}
