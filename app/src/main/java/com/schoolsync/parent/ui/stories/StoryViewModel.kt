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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class StoryUiState(
    val isLoading: Boolean = true,
    val storyGroups: List<TeacherStoryGroup> = emptyList(),
    /** storyId → the emoji THIS parent reacted with (for highlighting). */
    val myReactions: Map<String, String> = emptyMap()
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

    /** LIVE set of storyIds this parent has viewed — fed by a real-time
     *  `viewers` collection-group listener in the repo. Because it's live
     *  (not a one-shot hydrate), a view recorded by the SEPARATE full-screen
     *  viewer VM propagates here within ~100ms and greys the Dashboard ring
     *  immediately — the two VMs no longer need to share an instance. */
    private val seenIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        // Live seen-state → drives the ring's grey/colored transition.
        viewModelScope.launch { storyRepo.observeSeenStoryIds().collect { seenIds.value = it } }
        viewModelScope.launch { hydrateMyReactions() }
        observeStories()
    }

    /**
     * Real-time stream of active stories, grouped by teacher, recomputed
     * whenever EITHER the story list OR the seen-set changes — so viewing a
     * story greys its ring without any manual refresh.
     */
    private fun observeStories() {
        viewModelScope.launch {
            combine(storyRepo.observeActiveStories(), seenIds) { docs, seen ->
                groupByTeacher(docs, seen)
            }.collect { groups ->
                Log.d(TAG, "snapshot: ${groups.size} teacher groups")
                _uiState.update { it.copy(isLoading = false, storyGroups = groups) }
            }
        }
    }

    // Load this parent's existing reactions once (storyId → emoji) so
    // the ReactionBar shows their current pick highlighted. Mirrors
    // the viewers hydration: collection-group over 'reactions' docs
    // filtered by userId.
    private suspend fun hydrateMyReactions() {
        try {
            val userId = tokenManager.user.firstOrNull()?.userId.orEmpty()
            if (userId.isBlank()) return
            val snap = FirebaseFirestore.getInstance()
                .collectionGroup("reactions")
                .whereEqualTo("userId", userId)
                .get().await()
            val map = mutableMapOf<String, String>()
            for (doc in snap.documents) {
                val storyId = doc.reference.parent.parent?.id ?: continue
                val emoji = doc.getString("emoji").orEmpty()
                if (emoji.isNotBlank()) map[storyId] = emoji
            }
            if (map.isNotEmpty()) _uiState.update { it.copy(myReactions = map) }
        } catch (e: Exception) {
            Log.w(TAG, "hydrateMyReactions failed (non-fatal)", e)
        }
    }

    /**
     * Toggle the parent's emoji reaction on a story. Optimistically
     * updates local state (same toggle rule as the repo) and persists
     * via the transaction; the aggregate `reactionCounts` flows back
     * through the listener.
     */
    fun reactToStory(storyId: String, emoji: String) {
        val current = _uiState.value.myReactions[storyId]
        val next = if (current == emoji) "" else emoji   // tap same = clear
        _uiState.update { state ->
            val m = state.myReactions.toMutableMap()
            if (next.isBlank()) m.remove(storyId) else m[storyId] = next
            state.copy(myReactions = m)
        }
        viewModelScope.launch { storyRepo.reactToStory(storyId, emoji) }
    }

    fun markStoryViewed(storyId: String) {
        if (seenIds.value.contains(storyId)) return  // idempotent
        // Optimistic: grey the ring instantly; the live listener confirms
        // once the viewer doc write lands.
        seenIds.update { it + storyId }
        viewModelScope.launch { storyRepo.markAsViewed(storyId) }
    }

    // ─── Private mappers ───────────────────────────────────────────

    private fun groupByTeacher(docs: List<StoryDoc>, seen: Set<String>): List<TeacherStoryGroup> {
        return docs.groupBy { it.effectiveAuthorId }
            .map { (_, authorDocs) ->
                val first = authorDocs.first()
                val stories = authorDocs
                    .sortedBy { it.expiresAtMillis }   // oldest-expiring first
                    .map { it.toStory(seen.contains(it.id)) }
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
