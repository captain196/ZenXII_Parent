package com.schoolsync.parent.ui.dashboard

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.schoolsync.parent.R
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
import com.schoolsync.parent.data.repository.RedFlagRepository
import com.schoolsync.parent.data.repository.StudentRepository
import com.schoolsync.parent.data.repository.firestore.AttendanceFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.parent.data.model.EventMedia
import com.schoolsync.parent.data.model.hasUsableCover
import com.schoolsync.parent.data.repository.firestore.EventFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.GalleryFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.ExamFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.HomeworkFirestoreRepository
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
import com.schoolsync.parent.util.localizedString

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
    /** True when the attendance fetch failed — distinguishes a real
     *  0% from "data never loaded" so the ring can show a retry hint
     *  instead of a misleading empty arc. */
    val attendanceLoadFailed: Boolean = false,
    val pendingFeeAmount: Double = 0.0,
    /** True when the fees fetch failed — UI shows a retry prompt
     *  instead of the green "All cleared" state, which would be
     *  misleading when data never loaded. */
    val feesLoadFailed: Boolean = false,
    val pendingHomeworkCount: Int = 0,
    /** Live count of ACTIVE red flags for the current child — drives the
     *  dashboard appContext.localizedString(R.string.rf_title) tile badge so a new serious flag is visible
     *  at a glance without opening the screen. */
    val activeFlagCount: Int = 0,
    /**
     * True when the red-flag listener failed, so [activeFlagCount] is the last
     * known value (or 0 on a cold start) rather than a confirmed count. The UI
     * must not present it as "no open concerns" while this is set.
     */
    val flagCountLoadFailed: Boolean = false,
    /** True when the homework listener errored — distinguishes a real
     *  "0 pending" from a failed load. */
    val homeworkLoadFailed: Boolean = false,
    /** True when the latest-result fetch failed. */
    val resultLoadFailed: Boolean = false,
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
    val errorMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    
    @ApplicationContext private val appContext: Context,private val studentRepository: StudentRepository,
    private val attendanceFirestoreRepo: AttendanceFirestoreRepository,
    private val feeFirestoreRepo: FeeFirestoreRepository,
    private val communicationFirestoreRepo: CommunicationFirestoreRepository,
    private val homeworkFirestoreRepo: HomeworkFirestoreRepository,
    private val eventFirestoreRepo: EventFirestoreRepository,
    private val galleryFirestoreRepo: GalleryFirestoreRepository,
    private val timetableFirestoreRepo: TimetableFirestoreRepository,
    private val examFirestoreRepo: ExamFirestoreRepository,
    private val ptmFirestoreRepo: PtmFirestoreRepository,
    private val studentFirestoreRepo: StudentFirestoreRepository,
    private val redFlagRepository: RedFlagRepository,
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

    /** Live listener for the active red-flag count; cancel-and-restart on
     *  every active-student change, same as the homework listener. */
    private var flagListenerJob: Job? = null

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

            fetchSchoolName(user)
            runAllLoaders(user)

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** Fetch the school display name directly from the Firestore
     *  `schools` collection. Best-effort — failures are logged, not
     *  surfaced (the cached name from the user profile is the fallback). */
    private suspend fun fetchSchoolName(user: User?) {
        val sid = user?.schoolId ?: user?.schoolCode ?: ""
        if (sid.isBlank()) return
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

    /**
     * Run every dashboard data loader concurrently and wait for all to
     * finish. Each loader is independent (no cross-loader data deps) and
     * isolates its own failures, so one slow/failing tile never blocks or
     * aborts the others. Shared by both the initial load and pull-to-
     * refresh so the two paths can never drift in behaviour again.
     *
     * Each loader updates `_uiState.copy` independently;
     * MutableStateFlow's atomic update handles the concurrent edits.
     */
    private suspend fun runAllLoaders(user: User?) = coroutineScope {
        launch { loadAttendance(user) }
        launch { loadFees(user) }
        launch { loadNotices() }
        launch { loadHomework(user) }
        loadFlags()
        launch { loadEvents() }
        launch { loadSiblings(user) }
        launch { loadTodaySchedule(user) }
        launch { loadLatestResult(user) }
        launch { loadNextPtm(user) }
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
                    _uiState.update { it.copy(errorMessage = appContext.localizedString(R.string.dash_child_profile_failed)) }
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
                // Clear the previous child's per-student data BEFORE reloading
                // so the dashboard never flashes the wrong kid's marks / PTM /
                // attendance / homework for the beat between switch and reload.
                _uiState.update {
                    it.copy(
                        user = next,
                        todayAttendance = null,
                        attendancePercentage = 0f,
                        attendanceChange = null,
                        attendanceMonthSummary = null,
                        attendanceLoadFailed = false,
                        pendingFeeAmount = 0.0,
                        feesLoadFailed = false,
                        pendingHomeworkCount = 0,
                        homeworkPreview = emptyList(),
                        homeworkLoadFailed = false,
                        activeFlagCount = 0,
                        flagCountLoadFailed = false,
                        todaySchedule = null,
                        latestResult = null,
                        resultLoadFailed = false,
                        nextPtm = null,
                        // Clear events too — the Upcoming Events section merges
                        // in PTM rows scoped to the child's class/section, so a
                        // stale list would show the previous child's PTMs.
                        upcomingEvents = emptyList()
                    )
                }
                // Reload per-student data so KPI tiles reflect the new kid.
                loadAttendance(next)
                loadFees(next)
                loadHomework(next)
                loadTodaySchedule(next)
                loadLatestResult(next)
                loadNextPtm(next)
                // Events reads class/section from the freshly-saved user token
                // (saveUserDirect above) to re-scope the PTM-as-event rows.
                loadEvents()
            } catch (e: Exception) {
                Log.e("DashboardVM", "switchToSibling failed", e)
                _uiState.update { it.copy(errorMessage = e.message ?: appContext.localizedString(R.string.dash_switch_failed)) }
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
                        title        = p.title.ifBlank { appContext.localizedString(R.string.ptm_meeting_title) },
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

            val upcoming = (injectEventCovers(schoolEvents) + ptmEvents).sortedBy { it.startDate }
            Log.d("DashboardVM", "Events loaded: events=${schoolEvents.size} ptms=${ptmEvents.size} upcoming=${upcoming.size}")
            _uiState.update { it.copy(upcomingEvents = upcoming.take(5)) }
        } catch (e: Exception) {
            Log.w("DashboardVM", "Events load failed", e)
        }
    }

    /**
     * Events often carry their photo in the linked gallery album (source=
     * "event") rather than on the event doc — e.g. "Annual sport day". For any
     * event with no inline media, borrow the album's `coverImage` so the
     * dashboard banner shows the picture. Dashboard events use the RAW event id,
     * which is exactly what the album stores. One album query, only when needed.
     */
    private suspend fun injectEventCovers(events: List<Event>): List<Event> {
        if (events.all { it.hasUsableCover() }) return events
        val albums = runCatching { galleryFirestoreRepo.getAlbums().getOrNull() }
            .getOrNull().orEmpty()
            .filter { it.source == "event" && it.coverImage.isNotBlank() }
        if (albums.isEmpty()) return events
        return events.map { e ->
            if (e.hasUsableCover()) e
            else {
                val cover = albums.firstOrNull { a ->
                    e.eventId == a.eventId || e.eventId.endsWith("_${a.eventId}")
                }?.coverImage
                if (cover != null) e.copy(mediaUrls = listOf(EventMedia(url = cover, type = "image"))) else e
            }
        }
    }

    private suspend fun loadAttendance(user: User?) {
        val studentId = user?.userId ?: return
        try {
            val result = attendanceFirestoreRepo.getAttendanceSummary(studentId)
            result.fold(
                onSuccess = { summaries ->
                    val now = java.time.YearMonth.now()
                    val canonicalKey = String.format(java.util.Locale.ROOT, "%d-%02d", now.year, now.monthValue)
                    val legacyLabel  = "${now.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} ${now.year}"
                    val currentMonthSummary = summaries.find {
                        it.month == canonicalKey || it.month == legacyLabel
                    }
                    val prevYm = now.minusMonths(1)
                    val prevCanonical = String.format(java.util.Locale.ROOT, "%d-%02d", prevYm.year, prevYm.monthValue)
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
                            // Display labels for the stored code chars. The CODES
                            // ('P'/'A'/'L'/'H'/'V'/'T') are the wire values and are
                            // untouched; only the rendered text is translated.
                            'P' -> appContext.localizedString(R.string.attendance_status_present)
                            'A' -> appContext.localizedString(R.string.attendance_status_absent)
                            'L' -> appContext.localizedString(R.string.attendance_status_leave)
                            'H' -> appContext.localizedString(R.string.attendance_status_holiday)
                            'V' -> appContext.localizedString(R.string.attendance_status_vacation)
                            'T' -> appContext.localizedString(R.string.attendance_status_tardy)
                            else -> null
                        }
                    }

                    _uiState.update {
                        it.copy(
                            attendancePercentage = currentPct,
                            attendanceChange = change,
                            todayAttendance = todayStatus,
                            attendanceMonthSummary = currentMonthSummary,
                            attendanceLoadFailed = false
                        )
                    }
                },
                onFailure = { e ->
                    Log.w("DashboardVM", "Firestore attendance failed", e)
                    _uiState.update { it.copy(attendanceLoadFailed = true) }
                }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "Firestore attendance exception", e)
            _uiState.update { it.copy(attendanceLoadFailed = true) }
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
                    // Sort by the PARSED dueDate, not the raw string — the raw
                    // string assumed ISO yyyy-MM-dd and mis-ordered any
                    // non-ISO shape (e.g. dd/MM/yyyy) or time-bearing ISO.
                    // Reuse the homework list's robust parse so ordering agrees.
                    val activeSorted = homeworkDocs.sortedWith(
                        compareBy(
                            { it.dueDate.isBlank() },
                            {
                                com.schoolsync.parent.ui.homework.HomeworkViewModel
                                    .parseDueDate(it.dueDate)?.time ?: Long.MAX_VALUE
                            }
                        )
                    )

                    // Pending = student has NOT submitted/reviewed/completed
                    // yet. A submission with status "submitted" or "reviewed"
                    // or "complete" means the parent's task on this homework
                    // is done — should not be on the dashboard prompt.
                    // FIX 2: use the shared isActionNeeded() predicate (pending
                    // OR incomplete) so this count agrees with the homework nav
                    // badge and the profile stat.
                    val pending = activeSorted.filter { hw ->
                        val status = submissionsByHwId[hw.id]?.status ?: "pending"
                        com.schoolsync.parent.data.model.isActionNeeded(status)
                    }
                    pending
                }.collect { pending ->
                    _uiState.update {
                        it.copy(
                            pendingHomeworkCount = pending.size,
                            homeworkPreview = pending.take(5),
                            homeworkLoadFailed = false
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation when student switches — silent.
            } catch (e: Exception) {
                Log.w("DashboardVM", "Firestore homework live listener failed", e)
                _uiState.update { it.copy(homeworkLoadFailed = true) }
            }
        }
    }

    /**
     * Live active-flag count for the dashboard tile badge. The repository's
     * observeFlags() flatMapLatch-es on the active user, so this single
     * listener automatically re-targets on a sibling switch — we just
     * cancel-and-restart defensively so repeat loadDashboard() calls don't
     * stack listeners.
     *
     * Failure must NOT report 0. `badgeCount = 0` renders no badge at all, which
     * is visually identical to "your child has no open concerns" — and since the
     * badge is the only thing prompting a parent to open the screen, the old
     * "the full Red Flags screen is the authoritative error surface" rationale
     * doesn't hold: a parent who sees no badge never goes there. This is the same
     * false-all-clear class that RedFlagViewModel was explicitly hardened against
     * (it refuses to fall through to the empty state on error); the dashboard
     * predates that hardening. On failure we now keep the last known count and
     * raise [flagCountLoadFailed] instead of asserting a reassuring zero.
     */
    private fun loadFlags() {
        flagListenerJob?.cancel()
        flagListenerJob = viewModelScope.launch {
            try {
                redFlagRepository.observeFlags().collect { flags ->
                    val active = flags.count { it.status == "active" }
                    _uiState.update {
                        it.copy(activeFlagCount = active, flagCountLoadFailed = false)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // normal on student switch — silent
            } catch (e: Exception) {
                Log.w("DashboardVM", "Red-flag count listener failed", e)
                // Keep the last known count; never assert a reassuring zero.
                _uiState.update { it.copy(flagCountLoadFailed = true) }
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
                    _uiState.update { it.copy(latestResult = latest, resultLoadFailed = false) }
                },
                onFailure = { e ->
                    Log.w("DashboardVM", "Latest result failed", e)
                    _uiState.update { it.copy(resultLoadFailed = true) }
                }
            )
        } catch (e: Exception) {
            Log.w("DashboardVM", "Latest result exception", e)
            _uiState.update { it.copy(resultLoadFailed = true) }
        }
    }

    fun refresh() = loadDashboard()

    /** Dismiss the transient error banner (e.g. a failed sibling switch). */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Pull-to-refresh entry point. Holds the spinner for ≥ 600ms so
     * fast refreshes don't look like nothing happened.
     */
    fun pullRefresh() {
        viewModelScope.launch {
            Log.d("DashboardVM", "pullRefresh: STARTED")
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            try {
                val initialUser = studentRepository.currentUser.firstOrNull()
                val user = healUserProfileIfNeeded(initialUser)
                _uiState.update { it.copy(user = user) }
                fetchSchoolName(user)
                // Same concurrent, per-loader-isolated fan-out as the initial
                // load — previously this path ran the loaders sequentially
                // under one try/catch, so a single failing loader aborted all
                // the rest and refresh was far slower than cold load.
                runAllLoaders(user)
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
