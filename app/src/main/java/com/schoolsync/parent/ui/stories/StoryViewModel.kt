package com.schoolsync.parent.ui.stories

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.Story
import com.schoolsync.parent.data.model.TeacherStoryGroup
import com.schoolsync.parent.data.model.firestore.StoryDoc
import com.schoolsync.parent.data.repository.firestore.StoryFirestoreRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class StoryUiState(
    val isLoading: Boolean = true,
    val storyGroups: List<TeacherStoryGroup> = emptyList()
)

/**
 * Parent Stories VM — Firestore-only.
 *
 * Subscribes to [StoryFirestoreRepository.observeActiveStories] which
 * is a real-time snapshot listener over the SAME `stories` collection
 * that the teacher app writes to and the admin panel moderates.
 *
 * Cross-system propagation:
 *   • Teacher uploads a story  →  parent sees it within ~100 ms
 *   • Admin flags or removes   →  the row disappears here within ~100 ms
 *   • 24h expiry hits          →  row drops out via the listener's
 *                                  expiresAt > now filter on next emit
 *   • Parent taps a story      →  markAsViewed() bumps server viewCount
 *                                  (admin analytics reflect it instantly)
 */
@HiltViewModel
class StoryViewModel @Inject constructor(
    private val storyRepo: StoryFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object { private const val TAG = "StoryVM" }

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    /** In-memory snapshot of which storyIds the current parent has
     *  already viewed — populated lazily on first observation so the
     *  "unviewed ring" UI is correct on cold start. Updated when
     *  markStoryViewed() runs. */
    private val viewedStoryIds = mutableSetOf<String>()
    private var viewedHydrated = false

    init {
        observeStories()
    }

    /**
     * Real-time stream of active stories, grouped by teacher.
     * Replaces the old one-shot loadStories() / pull-to-refresh
     * pattern — the listener keeps the list fresh without manual
     * refresh.
     */
    private fun observeStories() {
        viewModelScope.launch {
            // Load the parent's previously-viewed story ids from
            // Firestore once so the unviewed-ring is correct from
            // the first emission. Cheap (one-time read).
            hydrateViewedStoryIds()

            storyRepo.observeActiveStories().collect { docs ->
                val groups = groupByTeacher(docs)
                Log.d(TAG, "snapshot: ${docs.size} stories → ${groups.size} teachers")
                _uiState.update { it.copy(isLoading = false, storyGroups = groups) }
            }
        }
    }

    // Look up viewer-docs (subcollection 'viewers' under each story)
    // once on init so we know which stories already have a viewer
    // record for this user. Plain comment to avoid kdoc treating the
    // collection-group path glob as a comment terminator.
    private suspend fun hydrateViewedStoryIds() {
        if (viewedHydrated) return
        viewedHydrated = true
        try {
            val userId = tokenManager.user.firstOrNull()?.userId.orEmpty()
            if (userId.isBlank()) return
            // Collection-group query so we don't have to know the
            // story IDs in advance. Returns all viewer-docs whose
            // userId matches the current parent.
            val snap = FirebaseFirestore.getInstance()
                .collectionGroup("viewers")
                .whereEqualTo("userId", userId)
                .get().await()
            for (doc in snap.documents) {
                // Parent doc id of viewers/{userId} is the story id.
                doc.reference.parent.parent?.id?.let { viewedStoryIds.add(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "hydrateViewedStoryIds failed (non-fatal)", e)
        }
    }

    fun markStoryViewed(storyId: String) {
        if (viewedStoryIds.contains(storyId)) return  // local idempotency
        viewedStoryIds.add(storyId)
        viewModelScope.launch {
            storyRepo.markAsViewed(storyId)
            // Recompute the hasUnviewed flag for the UI without
            // re-fetching the whole list — listener will overwrite
            // on next snapshot anyway.
            _uiState.update { state ->
                state.copy(storyGroups = state.storyGroups.map { g ->
                    g.copy(hasUnviewed = g.stories.any { !viewedStoryIds.contains(it.storyId) })
                })
            }
        }
    }

    // ─── Private mappers ───────────────────────────────────────────

    private fun groupByTeacher(docs: List<StoryDoc>): List<TeacherStoryGroup> {
        return docs.groupBy { it.effectiveAuthorId }
            .map { (_, authorDocs) ->
                val first = authorDocs.first()
                val stories = authorDocs
                    .sortedBy { it.expiresAtMillis }   // oldest-expiring first
                    .map { it.toStory(viewedStoryIds.contains(it.id)) }
                TeacherStoryGroup(
                    teacherId   = first.effectiveAuthorId,
                    teacherName = first.effectiveAuthorName,
                    teacherPic  = first.effectiveAuthorPic,
                    stories     = stories,
                    hasUnviewed = stories.any { !it.isViewed },
                    // Phase C — admin posts are pinned to the top of
                    // the row; high-priority admin posts even higher.
                    authorType  = first.authorType.ifBlank { "teacher" },
                    priority    = first.priority.ifBlank { "normal" }
                )
            }
            // Sort: admin-high → admin-normal → unviewed teacher → viewed teacher
            .sortedWith(
                compareByDescending<TeacherStoryGroup> { it.authorType == "admin" }
                    .thenByDescending { it.authorType == "admin" && it.priority == "high" }
                    .thenByDescending { it.hasUnviewed }
                    .thenBy { it.teacherName }
            )
    }

    private fun StoryDoc.toStory(viewed: Boolean): Story {
        val createdMillis = when (val ts = createdAt) {
            is com.google.firebase.Timestamp -> ts.seconds * 1000L + ts.nanoseconds / 1_000_000L
            is Number -> ts.toLong()
            else -> 0L
        }
        return Story(
            storyId    = id,
            teacherId  = effectiveAuthorId,
            teacherName = effectiveAuthorName,
            teacherPic  = effectiveAuthorPic,
            mediaUrl   = mediaUrl,
            type       = type,
            caption    = caption,
            createdAt  = createdMillis,
            expiresAt  = expiresAtMillis,
            isViewed   = viewed
        )
    }
}
