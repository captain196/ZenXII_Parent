package com.schoolsync.parent.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.tasks.await
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.DayTimetable
import com.schoolsync.parent.data.model.Event
import com.schoolsync.parent.data.model.Notice
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.firestore.AttendanceSummaryDoc
import com.schoolsync.parent.data.model.firestore.HomeworkDoc
import com.schoolsync.parent.data.model.firestore.PtmEventDoc
import com.schoolsync.parent.data.model.firestore.ResultDoc
import com.schoolsync.parent.data.repository.StudentRepository
import com.schoolsync.parent.data.repository.firestore.AttendanceFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.EventFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.ExamFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.HomeworkFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.MyTeachersFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.PtmFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.StudentFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.TimetableFirestoreRepository
import com.schoolsync.parent.util.NetworkMonitor
import com.schoolsync.parent.util.toDateOrNull
import com.schoolsync.parent.util.toEpochMillisOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lightweight sibling summary used by the Dashboard switcher. */
data class SiblingSummary(
    val studentId: String,
    val name: String,
    val className: String,
    val section: String,
    val rollNo: String
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    /** True while a pull-to-refresh gesture is in progress; the
     *  spinner overlays existing content rather than swapping it. */
    val isRefreshing: Boolean = false,
    val user: User? = null,
    val schoolName: String = "",
    val todayAttendance: String? = null,
    val attendancePercentage: Float = 0f,
    val attendanceChange: Float? = null,
    val pendingFeeAmount: Double = 0.0,
    /** True when the fees fetch failed — UI shows a retry prompt
     *  instead of the green "All cleared" state, which would be
     *  misleading when data never loaded. */
    val feesLoadFailed: Boolean = false,
    val pendingHomeworkCount: Int = 0,
    /** Top 5 active homework items for the dashboard preview list. */
    val homeworkPreview: List<HomeworkDoc> = emptyList(),
    val recentNotices: List<Notice> = emptyList(),
    val upcomingEvents: List<Event> = emptyList(),
    /** Today's class schedule (ordered slots). null while loading or on error. */
    val todaySchedule: DayTimetable? = null,
    /** Current month's attendance summary — drives the calendar strip. */
    val attendanceMonthSummary: AttendanceSummaryDoc? = null,
    /** Most recent published result for the student. */
    val latestResult: ResultDoc? = null,
    /** Next upcoming PTM the student is invited to (if any). */
    val nextPtm: PtmEventDoc? = null,
    /** Other students under the same parent (parentDbKey / phone /
     *  father+mother name match). Empty when no siblings or lookup
     *  failed. Sorted alphabetically by name. */
    val siblings: List<SiblingSummary> = emptyList(),
    /** The Active class teacher for the student's section (or null
     *  when none is assigned / the loader hasn't finished yet). The
     *  loader leans on MyTeachersFirestoreRepository, which already
     *  filters out archived assignments and Inactive staff, so this
     *  field is by construction either Active or null. */
    val classTeacher: MyTeachersFirestoreRepository.TeacherEntry? = null,
    /** Every subject the class teacher teaches to this student's
     *  section. The class teacher row from `MyTeachers...` only
     *  carries one assignment doc (the row marked isClassTeacher),
     *  but the same teacher usually delivers multiple subjects to
     *  the section — this list aggregates all of them so the
     *  dashboard hero card can display them the same way the My
     *  Teachers screen does. */
    val classTeacherSubjects: List<String> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val attendanceFirestoreRepo: AttendanceFirestoreRepository,
    private val feeFirestoreRepo: FeeFirestoreRepository,
    private val communicationFirestoreRepo: CommunicationFirestoreRepository,
    private val homeworkFirestoreRepo: HomeworkFirestoreRepository,
    private val eventFirestoreRepo: EventFirestoreRepository,
    private val timetableFirestoreRepo: TimetableFirestoreRepository,
    private val examFirestoreRepo: ExamFirestoreRepository,
    private val ptmFirestoreRepo: PtmFirestoreRepository,
    private val myTeachersRepo: MyTeachersFirestoreRepository,
    private val studentFirestoreRepo: StudentFirestoreRepository,
    private val tokenManager: TokenManager,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Live listener for the homework + submissions combined flow. We
     *  cancel-and-restart this every time the active student changes
     *  (sibling switch, profile reload). */
    private var homeworkListenerJob: Job? = null

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val initialUser = studentRepository.currentUser.firstOrNull()
            // Self-heal: if the cached user profile is missing class/section
            // (e.g. saved before the Firestore canonical schema existed),
            // re-fetch from `students/{schoolId}_{uid}` and persist it
            // back to DataStore so every subsequent screen has it.
            val user = healUserProfileIfNeeded(initialUser)
            Log.d("DashboardVM", "user=${user?.userId} class='${user?.className}' sec='${user?.section}' schoolId='${user?.schoolId}'")
            _uiState.update { it.copy(user = user) }

            // Fetch school name directly from Firestore schools collection
            val sid = user?.schoolId ?: user?.schoolCode ?: ""
            if (sid.isNotBlank()) {
                try {
                    val schoolSnap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("schools").document(sid).get().await()
                    val sName = schoolSnap?.getString("name") ?: ""
                    Log.d("DashboardVM", "School name from Firestore: '$sName' (docId=$sid)")
                    if (sName.isNotBlank()) {
                        _uiState.update { it.copy(schoolName = sName) }
                    }
                } catch (e: Exception) {
                    Log.w("DashboardVM", "Failed to fetch school name for $sid", e)
                }
            }

            // Each loader is independent — there are no data dependencies
            // between them. Running sequentially meant Events was the 5th
            // round-trip in line and appeared visibly later than other
            // tiles. coroutineScope launches them concurrently and waits
            // for all to complete before flipping isLoading off.
            // Each loader updates _uiState.copy independently;
            // MutableStateFlow's atomic update handles the concurrent edits.
            coroutineScope {
                launch { loadAttendance(user) }
                launch { loadFees(user) }
                launch { loadNotices() }
                launch { loadHomework(user) }
                launch { loadEvents() }
                launch { loadSiblings(user) }
                launch { loadTodaySchedule(user) }
                launch { loadLatestResult(user) }
                launch { loadNextPtm(user) }
                launch { loadClassTeacher(user) }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Find siblings under the same parent. Populates the switcher in
     * the top bar when a parent has multiple children enrolled.
     */
    private suspend fun loadSiblings(user: User?) {
        if (user?.userId.isNullOrBlank()) return
        try {
            // Build a StudentDoc-ish primary record from the User object
            val primary = com.schoolsync.parent.data.model.firestore.StudentDoc(
                id         = "${user!!.schoolId}_${user.userId}",
                studentId  = user.userId,
                userId     = user.userId,
                schoolId   = user.schoolId,
                name       = user.name,
                className  = user.className,
                section    = user.section,
                rollNo     = user.rollNo,
                fatherName = user.fatherName,
                motherName = user.motherName,
                phone      = user.phone,
                parentDbKey= user.parentDbKey
            )
            val res = studentFirestoreRepo.findSiblings(primary)
            val list = res.getOrNull().orEmpty().map { doc ->
                SiblingSummary(
                    studentId = doc.userId.ifBlank { doc.studentId }.ifBlank { doc.id },
                    name      = doc.name,
                    className = doc.className,
                    section   = doc.section,
                    rollNo    = doc.rollNo
                )
            }
            _uiState.update { it.copy(siblings = list) }
            Log.d("DashboardVM", "siblings=${list.size} for ${user.userId}")
        } catch (e: Exception) {
            Log.w("DashboardVM", "Sibling lookup failed", e)
        }
    }

    /**
     * Switch the active student to one of the siblings. Saves the new
     * User profile to DataStore so every screen reading
     * `tokenManager.user` automatically sees the switch. A reload of
     * dashboard data follows.
     */
    fun switchToSibling(studentId: String) {
        if (studentId.isBlank()) return
        viewModelScope.launch {
            try {
                val result = studentFirestoreRepo.getStudent(studentId)
                val doc = result.getOrNull() ?: run {
                    _uiState.update { it.copy(errorMessage = "Couldn't load that child's profile.") }
                    return@launch
                }
                val current = _uiState.value.user ?: User.empty()
                // Rebuild the User with the sibling's details, keeping
                // school + parent context from the signed-in account.
                val next = current.copy(
                    userId        = doc.userId.ifBlank { doc.studentId }.ifBlank { studentId },
                    name          = doc.name,
                    className     = doc.className,
                    section       = doc.section,
                    rollNo        = doc.rollNo,
                    fatherName    = doc.fatherName,
                    motherName    = doc.motherName,
                    dob           = doc.dob,
                    gender        = doc.gender,
                    admissionDate = doc.admissionDate,
                    profilePic    = doc.profilePic,
                    email         = doc.email.ifBlank { current.email },
                    phone         = current.phone // parent contact stays the same
                )
                tokenManager.saveUserDirect(next)
                _uiState.update { it.copy(user = next) }
                // Reload per-student data so KPI tiles reflect the new kid.
                loadAttendance(next)
                loadFees(next)
                loadHomework(next)
                loadTodaySchedule(next)
                loadLatestResult(next)
                loadNextPtm(next)
            } catch (e: Exception) {
                Log.e("DashboardVM", "switchToSibling failed", e)
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to switch.") }
            }
        }
    }

    private suspend fun healUserProfileIfNeeded(user: User?): User? {
        if (user == null) return null
        // Always read the Firestore student doc on dashboard load — cheap
        // single-doc read — so fields that can change admin-side (dob,
        // className on promotion, etc.) stay fresh. Previously we gated
        // on "if any field blank" which left cached DOB stale forever
        // after admin edited it → birthday banner never appeared.
        return try {
            val result = studentFirestoreRepo.getStudent(user.userId)
            val doc = result.getOrNull() ?: return user
            var healed = user.copy(
                className = user.className.ifBlank { doc.className },
                section   = user.section.ifBlank   { doc.section   },
                rollNo    = user.rollNo.ifBlank    { doc.rollNo    },
                fatherName = user.fatherName.ifBlank { doc.fatherName },
                motherName = user.motherName.ifBlank { doc.motherName },
                // DOB always prefers Firestore's current value — admin edits
                // propagate to the parent app on the next dashboard load.
                dob       = if (doc.dob.isNotBlank()) doc.dob else user.dob
            )
            // Also heal school display name from Firestore schools collection
            if (healed.schoolDisplayName.isBlank() && healed.schoolId.isNotBlank()) {
                try {
                    val schoolDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("schools").document(healed.schoolId).get().await()
                    val schoolName = schoolDoc?.getString("name") ?: ""
                    if (schoolName.isNotBlank()) {
                        healed = healed.copy(schoolDisplayName = schoolName)
                    }
                } catch (_: Exception) {}
            }
            if (healed != user) {
                Log.d("DashboardVM", "Self-heal: cached user was incomplete, rewriting from Firestore (schoolName=${healed.schoolDisplayName})")
                tokenManager.saveUserDirect(healed)
            }
            healed
        } catch (e: Exception) {
            Log.w("DashboardVM", "Self-heal failed, keeping cached user", e)
            user
        }
    }

    private suspend fun loadEvents() {
        try {
            val result = eventFirestoreRepo.getEvents()
            val docs = result.getOrElse {
                Log.w("DashboardVM", "Events load failed", it)
                return
            }
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
            val schoolEvents = docs
                .filter { d ->
                    val st = d.status.lowercase()
                    if (st == "cancelled" || st == "completed") return@filter false
                    val sd = d.startDate
                    if (sd.isBlank()) true else sd >= today
                }
                .map { d ->
                    // Admin writes docId as `{schoolId}_{eventId}`. Strip the
                    // schoolId prefix so navigation passes the bare eventId
                    // (EventFirestoreRepository.getEvent re-adds the prefix).
                    val bareId = if (d.schoolId.isNotBlank() && d.id.startsWith("${d.schoolId}_")) {
                        d.id.removePrefix("${d.schoolId}_")
                    } else d.id
                    Event(
                        eventId = bareId,
                        title = d.title,
                        description = d.description,
                        category = d.category,
                        startDate = d.startDate,
                        endDate = d.endDate,
                        location = d.location,
                        status = d.status
                    )
                }

            // Merge upcoming PTMs into the same list with category="ptm"
            // so the dashboard's Upcoming Events section surfaces them too.
            // The dedicated PTM dashboard tile + Academics → PTM list still
            // exist; this is the third surface that mirrors how parents
            // mentally bucket "things at school I should attend".
            val ptmEvents: List<Event> = try {
                val user = tokenManager.user.firstOrNull()
                val cls = user?.className.orEmpty()
                val sec = user?.section.orEmpty()
                if (cls.isBlank() || sec.isBlank()) emptyList()
                else ptmFirestoreRepo.getUpcomingPtms(cls, sec).getOrNull().orEmpty().map { p ->
                    Event(
                        eventId      = p.ptmEventId.ifBlank { p.id.removePrefix("${p.schoolId}_") },
                        title        = p.title.ifBlank { "Parent-Teacher Meeting" },
                        description  = p.description,
                        category     = "ptm",
                        startDate    = p.date,
                        endDate      = p.date,
                        location     = p.location,
                        status       = p.status
                    )
                }
            } catch (e: Exception) {
                Log.w("DashboardVM", "PTM merge into upcoming events failed", e)
                emptyList()
            }

            val upcoming = (schoolEvents + ptmEvents).sortedBy { it.startDate }
            Log.d("DashboardVM", "Events loaded: events=${schoolEvents.size} ptms=${ptmEvents.size} upcoming=${upcoming.size}")
            _uiState.update { it.copy(upcomingEvents = upcoming.take(5)) }
        } catch (e: Exception) {
            Log.w("DashboardVM", "Events load failed", e)
        }
    }

    private suspend fun loadAttendance(user: User?) {
        val studentId = user?.userId ?: return
        try {
            val result = attendanceFirestoreRepo.getAttendanceSummary(studentId)
            result.fold(
                onSuccess = { summaries ->
                    val now = java.time.YearMonth.now()
                    val canonicalKey = "%d-%02d".format(now.year, now.monthValue)
                    val legacyLabel  = "${now.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} ${now.year}"
                    val currentMonthSummary = summaries.find {
                        it.month == canonicalKey || it.month == legacyLabel
                    }
                    val prevYm = now.minusMonths(1)
                    val prevCanonical = "%d-%02d".format(prevYm.year, prevYm.monthValue)
                    val prevLegacy    = "${prevYm.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} ${prevYm.year}"
                    val prevMonthSummary = summaries.find {
                        it.month == prevCanonical || it.month == prevLegacy
                    }

                    val currentPct = currentMonthSummary?.let { s ->
                        val w = s.present + s.absent + s.leave + s.tardy
                        if (w > 0) (s.present + s.tardy).toFloat() / w * 100f else 0f
                    } ?: 0f
                    val prevPct = prevMonthSummary?.let { s ->
                        val w = s.present + s.absent + s.leave + s.tardy
                        if (w > 0) (s.present + s.tardy).toFloat() / w * 100f else null
                    }
                    val change = if (prevPct != null && currentMonthSummary != null) {
                        currentPct - prevPct
                    } else null

                    val todayDay = java.time.LocalDate.now().dayOfMonth
                    val todayStatus = currentMonthSummary?.dayWise?.getOrNull(todayDay - 1)?.let { code ->
                        when (code) {
                            'P' -> "Present"; 'A' -> "Absent"; 'L' -> "Leave"
                            'H' -> "Holiday"; 'V' -> "Vacation"; 'T' -> "Tardy"
                            else -> null
                        }
                    }

                    _uiState.update {
                        it.copy(
                            attendancePercentage = currentPct,
                            attendanceChange = change,
                            todayAttendance = todayStatus,
                            attendanceMonthSummary = currentMonthSummary
                        )
                    }
                },
                onFailure = { e ->
                    Log.w("DashboardVM", "Firestore attendance failed", e)
                }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "Firestore attendance exception", e)
        }
    }

    private suspend fun loadFees(user: User?) {
        val studentId = user?.userId ?: return
        try {
            val pendingResult = feeFirestoreRepo.getPendingDemands(studentId)
            pendingResult.fold(
                onSuccess = { demands ->
                    val totalPending = demands.sumOf { it.netAmount - it.paidAmount }
                    _uiState.update {
                        it.copy(pendingFeeAmount = totalPending, feesLoadFailed = false)
                    }
                },
                onFailure = { e ->
                    // Critical: a silent failure here used to render
                    // "All cleared" on the dashboard tile, misleading
                    // parents into thinking no dues existed. Now we
                    // flag the load failure so the tile shows a
                    // "Tap to retry" state instead.
                    Log.w("DashboardVM", "Firestore fees failed", e)
                    _uiState.update { it.copy(feesLoadFailed = true) }
                }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "Firestore fees exception", e)
            _uiState.update { it.copy(feesLoadFailed = true) }
        }
    }

    private suspend fun loadNotices() {
        try {
            val result = communicationFirestoreRepo.getCirculars(limit = 3)
            result.fold(
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
                            date = doc.sentAt.toDateOrNull()?.let {
                                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(it)
                            } ?: "",
                            timestamp = doc.sentAt.toEpochMillisOrNull() ?: 0L
                        )
                    }
                    _uiState.update { it.copy(recentNotices = notices) }
                },
                onFailure = { e ->
                    Log.w("DashboardVM", "Firestore notices failed", e)
                }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "Firestore notices exception", e)
        }
    }

    private fun loadHomework(user: User?) {
        val className = user?.className ?: return
        val section = user.section
        val studentId = user.userId
        if (className.isBlank() || section.isBlank()) return

        // Cancel any prior listener (e.g. from a sibling switch) before
        // starting a new one so we never have two flows racing to update
        // pendingHomeworkCount with stale studentIds.
        homeworkListenerJob?.cancel()
        homeworkListenerJob = viewModelScope.launch {
            try {
                // Combine live homework + live submissions for THIS student.
                // Either flow updating triggers a recompute — so the moment
                // a teacher reviews a submission, the dashboard count drops
                // without the user touching anything.
                combine(
                    homeworkFirestoreRepo.observeHomework(className, section),
                    homeworkFirestoreRepo.observeSubmissionsForStudent(studentId)
                ) { homeworkDocs, submissionsByHwId ->
                    // Sort earliest dueDate first so overdue items rise to
                    // the top of the preview. Undated items sink to the
                    // bottom. We deliberately do NOT filter out overdue
                    // homework — for a parent, "overdue and not submitted"
                    // is exactly what the dashboard needs to surface.
                    val activeSorted = homeworkDocs.sortedWith(
                        compareBy(
                            { it.dueDate.isBlank() },
                            { it.dueDate.takeIf { d -> d.isNotBlank() } ?: "9999-12-31" }
                        )
                    )

                    // Pending = student has NOT submitted/reviewed/completed
                    // yet. A submission with status "submitted" or "reviewed"
                    // or "complete" means the parent's task on this homework
                    // is done — should not be on the dashboard prompt.
                    val pending = activeSorted.filter { hw ->
                        val status = (submissionsByHwId[hw.id]?.status ?: "pending")
                            .lowercase().trim()
                        status == "pending"
                    }
                    pending
                }.collect { pending ->
                    _uiState.update {
                        it.copy(
                            pendingHomeworkCount = pending.size,
                            homeworkPreview = pending.take(5)
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation when student switches — silent.
            } catch (e: Exception) {
                Log.w("DashboardVM", "Firestore homework live listener failed", e)
            }
        }
    }

    private suspend fun loadTodaySchedule(user: User?) {
        val cls = user?.className ?: return
        val sec = user.section
        if (cls.isBlank() || sec.isBlank()) return
        try {
            val result = timetableFirestoreRepo.getTodaySchedule(cls, sec)
            result.fold(
                onSuccess = { day ->
                    Log.d("DashboardVM", "Today schedule: ${day.dayName} slots=${day.slots.size}")
                    _uiState.update { it.copy(todaySchedule = day) }
                },
                onFailure = { e ->
                    Log.w("DashboardVM", "Today schedule failed", e)
                }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "Today schedule exception", e)
        }
    }

    private suspend fun loadNextPtm(user: User?) {
        val cls = user?.className ?: return
        val sec = user.section
        if (cls.isBlank() || sec.isBlank()) return
        try {
            val res = ptmFirestoreRepo.getUpcomingPtms(cls, sec)
            res.fold(
                onSuccess = { list ->
                    val next = list.firstOrNull()
                    Log.d("DashboardVM", "PTM next=${next?.ptmEventId ?: "none"} (${list.size} upcoming)")
                    _uiState.update { it.copy(nextPtm = next) }
                },
                onFailure = { e -> Log.w("DashboardVM", "PTM load failed", e) }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "PTM load exception", e)
        }
    }

    /**
     * Pick the student's Active class teacher for the dashboard card.
     * The repository already filters archived assignments and Inactive
     * staff (Phase 3 cascade + Active gate), so any entry returned here
     * is safe to display without further checks. Multiple matches are
     * defended against with `firstOrNull`.
     */
    private suspend fun loadClassTeacher(user: User?) {
        if (user?.userId.isNullOrBlank()) return
        try {
            val res = myTeachersRepo.getMyTeachers()
            res.fold(
                onSuccess = { entries ->
                    val ct = entries.filter { it.assignment.isClassTeacher }.firstOrNull()
                    // Aggregate every subject this class teacher delivers
                    // to the same section so the hero card mirrors the
                    // multi-subject row format used on My Teachers.
                    val subjects = if (ct != null) {
                        entries
                            .filter { it.assignment.teacherId.isNotBlank()
                                && it.assignment.teacherId == ct.assignment.teacherId }
                            .map { it.assignment.subjectName.ifBlank { it.assignment.subjectCode } }
                            .filter { it.isNotBlank() }
                            .distinct()
                    } else emptyList()
                    Log.d("DashboardVM", "Class teacher: ${ct?.staff?.name ?: "none"} subjects=${subjects.size} (${entries.size} total entries)")
                    _uiState.update { it.copy(classTeacher = ct, classTeacherSubjects = subjects) }
                },
                onFailure = { e ->
                    Log.w("DashboardVM", "Class teacher load failed", e)
                }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "Class teacher load exception", e)
        }
    }

    private suspend fun loadLatestResult(user: User?) {
        val sid = user?.userId ?: return
        if (sid.isBlank()) return
        try {
            val result = examFirestoreRepo.getAllResults(sid)
            result.fold(
                onSuccess = { results ->
                    // Pick the most recently computed result (computedAt desc).
                    val latest = results.maxByOrNull { r ->
                        when (val c = r.computedAt) {
                            is com.google.firebase.Timestamp -> c.seconds
                            is Long -> c / 1000L
                            is Number -> c.toLong() / 1000L
                            else -> 0L
                        }
                    }
                    Log.d("DashboardVM", "Latest result: ${latest?.examName ?: "none"} (${results.size} total)")
                    _uiState.update { it.copy(latestResult = latest) }
                },
                onFailure = { e ->
                    Log.w("DashboardVM", "Latest result failed", e)
                }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "Latest result exception", e)
        }
    }

    fun refresh() = loadDashboard()

    /**
     * Pull-to-refresh entry point. Holds the spinner for ≥ 600ms so
     * fast refreshes don't look like nothing happened.
     */
    fun pullRefresh() {
        viewModelScope.launch {
            Log.d("DashboardVM", "pullRefresh: STARTED")
            _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            try {
                val initialUser = studentRepository.currentUser.firstOrNull()
                val user = healUserProfileIfNeeded(initialUser)
                _uiState.update { it.copy(user = user) }
                loadAttendance(user)
                loadFees(user)
                loadNotices()
                loadHomework(user)
                loadEvents()
                loadSiblings(user)
                loadTodaySchedule(user)
                loadLatestResult(user)
                loadNextPtm(user)
                loadClassTeacher(user)
            } catch (e: Exception) {
                Log.w("DashboardVM", "pullRefresh failed", e)
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minSpinnerMs) kotlinx.coroutines.delay(minSpinnerMs - elapsed)
            _uiState.update { it.copy(isRefreshing = false) }
            Log.d("DashboardVM", "pullRefresh: DONE in ${System.currentTimeMillis() - startedAt}ms")
        }
    }
}
