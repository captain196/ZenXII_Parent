package com.schoolsync.parent.ui.search

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.schoolsync.parent.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.repository.StudentRepository
import com.schoolsync.parent.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.EventFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.ExamFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.HomeworkFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.MyTeachersFirestoreRepository
import com.schoolsync.parent.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Buckets a search hit falls into. `order` drives the section order on screen. */
enum class SearchCategory(val label: String, val emoji: String) {
    FEATURE("Categories", "🧭"),
    HOMEWORK("Homework", "📝"),
    NOTICE("Notices", "📢"),
    EVENT("Events", "🎉"),
    TEACHER("Teachers", "👩‍🏫"),
    RESULT("Results", "📊"),
}

/** One row in the search results — carries the route to navigate to on tap. */
data class SearchResult(
    val id: String,
    val category: SearchCategory,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val route: String,
)

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = true,
    val results: List<SearchResult> = emptyList(),        // query non-blank
    val browseFeatures: List<SearchResult> = emptyList(), // shown when query blank
)

/**
 * Global in-app search. Everything is filtered CLIENT-SIDE over data fetched
 * once on entry (Firestore has no substring search), plus a static catalogue of
 * app features. Categories: Features, Homework, Notices, Events, Teachers,
 * Results.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    
    @ApplicationContext private val appContext: Context,private val studentRepository: StudentRepository,
    private val homeworkRepo: HomeworkFirestoreRepository,
    private val communicationRepo: CommunicationFirestoreRepository,
    private val eventRepo: EventFirestoreRepository,
    private val examRepo: ExamFirestoreRepository,
    private val myTeachersRepo: MyTeachersFirestoreRepository,
) : ViewModel() {

    /** A result plus its lowercased searchable text ("haystack"). */
    private data class Indexed(val result: SearchResult, val haystack: String)

    private val featureIndex: List<Indexed> = buildFeatureIndex()
    private var dynamicIndex: List<Indexed> = emptyList()

    private val _uiState = MutableStateFlow(
        SearchUiState(browseFeatures = featureIndex.map { it.result })
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init { loadDynamic() }

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
        recompute(q)
    }

    fun clearQuery() = onQueryChange("")

    private fun recompute(raw: String) {
        val query = raw.trim().lowercase()
        if (query.isEmpty()) {
            _uiState.update { it.copy(results = emptyList()) }
            return
        }
        val matched = (featureIndex + dynamicIndex)
            .filter { it.haystack.contains(query) }
            .sortedWith(
                // Section order first, then title-prefix matches ahead of
                // mid-word matches so the most relevant hit leads each group.
                compareBy(
                    { it.result.category.ordinal },
                    { !it.result.title.lowercase().startsWith(query) },
                    { it.result.title.lowercase() }
                )
            )
            .map { it.result }
        _uiState.update { it.copy(results = matched) }
    }

    private fun loadDynamic() {
        viewModelScope.launch {
            val user = studentRepository.currentUser.firstOrNull()
            val items = mutableListOf<Indexed>()

            // ── Homework ──
            val cls = user?.className
            val sec = user?.section
            if (!cls.isNullOrBlank() && !sec.isNullOrBlank()) {
                homeworkRepo.getActiveHomework(cls, sec).getOrNull()?.forEach { hw ->
                    items += Indexed(
                        SearchResult(
                            id = "hw_${hw.id}",
                            category = SearchCategory.HOMEWORK,
                            title = hw.title.ifBlank { "(Untitled)" },
                            subtitle = listOfNotNull(
                                hw.subject.ifBlank { null },
                                hw.teacherName.ifBlank { null }
                            ).joinToString(" · "),
                            emoji = SearchCategory.HOMEWORK.emoji,
                            route = Route.Homework.createRoute(hwId = hw.id),
                        ),
                        haystack = "${hw.title} ${hw.subject} ${hw.teacherName}".lowercase()
                    )
                }
            }

            // ── Notices ──
            communicationRepo.getCirculars(limit = 50).getOrNull()?.forEach { n ->
                items += Indexed(
                    SearchResult(
                        id = "notice_${n.id}",
                        category = SearchCategory.NOTICE,
                        title = n.title.ifBlank { "(Untitled notice)" },
                        subtitle = n.author.ifBlank { n.category },
                        emoji = SearchCategory.NOTICE.emoji,
                        route = Route.Notices.route,
                    ),
                    haystack = "${n.title} ${n.body} ${n.description} ${n.author} ${n.category}".lowercase()
                )
            }

            // ── Events ──
            eventRepo.getEvents().getOrNull()?.forEach { e ->
                val bareId = if (e.schoolId.isNotBlank() && e.id.startsWith("${e.schoolId}_"))
                    e.id.removePrefix("${e.schoolId}_") else e.id
                items += Indexed(
                    SearchResult(
                        id = "event_${e.id}",
                        category = SearchCategory.EVENT,
                        title = e.title.ifBlank { "(Untitled event)" },
                        subtitle = listOfNotNull(
                            e.startDate.ifBlank { null },
                            e.location.ifBlank { null }
                        ).joinToString(" · "),
                        emoji = SearchCategory.EVENT.emoji,
                        route = Route.EventDetail.createRoute(bareId),
                    ),
                    haystack = "${e.title} ${e.description} ${e.location} ${e.category}".lowercase()
                )
            }

            // ── Teachers ──
            myTeachersRepo.getMyTeachers().getOrNull()?.forEach { t ->
                val name = (t.staff?.name?.takeIf { it.isNotBlank() }
                    ?: t.assignment.teacherName)
                val subject = t.assignment.subjectName.ifBlank { t.assignment.subjectCode }
                items += Indexed(
                    SearchResult(
                        id = "teacher_${t.assignment.teacherId}_${t.assignment.subjectCode}",
                        category = SearchCategory.TEACHER,
                        title = name.ifBlank { "(Teacher)" },
                        subtitle = subject,
                        emoji = SearchCategory.TEACHER.emoji,
                        route = Route.MyTeachers.route,
                    ),
                    haystack = "$name $subject".lowercase()
                )
            }

            // ── Results / Exams ──
            examRepo.getAvailableExams().getOrNull()?.forEach { ex ->
                items += Indexed(
                    SearchResult(
                        id = "exam_${ex.id}",
                        category = SearchCategory.RESULT,
                        title = ex.examName.ifBlank { "(Exam)" },
                        subtitle = ex.examType,
                        emoji = SearchCategory.RESULT.emoji,
                        route = Route.Results.route,
                    ),
                    haystack = "${ex.examName} ${ex.examType}".lowercase()
                )
            }

            // Dedup teachers that repeat across class-wide + section rows.
            dynamicIndex = items.distinctBy { it.result.id }
            _uiState.update { it.copy(isLoading = false) }
            recompute(_uiState.value.query)
        }
    }

    /** Static catalogue of app features, each with synonyms for fuzzy recall. */
    private fun buildFeatureIndex(): List<Indexed> {
        fun feature(
            title: String,
            emoji: String,
            route: String,
            vararg keywords: String
        ): Indexed = Indexed(
            SearchResult(
                id = "feat_$route",
                category = SearchCategory.FEATURE,
                title = title,
                subtitle = "Open $title",
                emoji = emoji,
                route = route,
            ),
            haystack = (listOf(title) + keywords).joinToString(" ").lowercase()
        )
        return listOf(
            feature("Attendance", "📅", Route.Attendance.route,
                "present", "absent", "attendance", "leave record", "days"),
            feature(appContext.getString(R.string.dash_pay_fees), "💳", Route.Fees.route,
                "fees", "fee", "payment", "pay", "due", "invoice", "receipt", "challan"),
            feature("Homework", "📝", Route.Homework.route,
                "homework", "assignment", "assignments", "diary", "task", "work"),
            feature("Results", "📊", Route.Results.route,
                "results", "marks", "grades", "grade", "report card", "score", "exam"),
            feature("Timetable", "🗓️", Route.Timetable.route,
                "timetable", "schedule", "routine", "periods", "class schedule"),
            feature("Events", "🎉", Route.Events.route,
                "events", "event", "holiday", "celebration", "calendar"),
            feature("Gallery", "🖼️", Route.Gallery.route,
                "gallery", "photos", "photo", "pictures", "album", "images"),
            feature("Library", "📚", Route.Library.route,
                "library", "books", "book", "borrow", "catalogue"),
            feature("Notices", "📢", Route.Notices.route,
                "notices", "notice", "circular", "circulars", "announcement", "news"),
            feature("Leave", "🏖️", Route.Leave.route,
                "leave", "absence", "apply leave", "sick"),
            feature("PTM", "👥", Route.PtmList.route,
                "ptm", "parent teacher meeting", "meeting", "appointment"),
            feature(appContext.getString(R.string.tch_title), "👩‍🏫", Route.MyTeachers.route,
                "teachers", "teacher", "staff", "faculty", "contact"),
            feature(appContext.getString(R.string.rf_title), "🚩", Route.RedFlags.route,
                "red flags", "flags", "discipline", "warning", "behaviour", "behavior"),
            // Support Desk. Synonyms are deliberately wide: a parent looking for
            // this will search for the PROBLEM ("complaint", "wrong fee") far
            // more often than for the feature's name.
            feature(appContext.getString(R.string.support_title), "🎫", Route.Support.route,
                "support", "help", "helpdesk", "ticket", "tickets", "complaint",
                "complain", "grievance", "issue", "problem", "query", "contact school"),
        )
    }
}
