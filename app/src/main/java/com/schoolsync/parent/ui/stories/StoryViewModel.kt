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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await
import com.schoolsync.parent.util.PendingStoryViews
import com.schoolsync.parent.util.debugLog
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
    private val tokenManager: TokenManager,
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val appContext: android.content.Context
) : ViewModel() {

    companion object {
        private const val TAG = "StoryVM"
        /** Cap on waiting for a non-blank viewer id; a logged-out session
         *  must not hang the coroutine forever. */
        private const val IDENTITY_TIMEOUT_MS = 3_000L
    }

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    /** LIVE set of storyIds this parent has viewed — fed by a real-time
     *  `viewers` collection-group listener in the repo. Because it's live
     *  (not a one-shot hydrate), a view recorded by the SEPARATE full-screen
     *  viewer VM propagates here within ~100ms and greys the Dashboard ring
     *  immediately — the two VMs no longer need to share an instance. */
    private val seenIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        // SEC-1: (re)build this parent's SERVER-SIDE audience entitlement doc
        // (storyAudience/{uid}) on app-start AND whenever the active child's
        // class/section changes, BEFORE/independently of the stories listener,
        // so class-targeted stories become visible. Fire-and-forget: never
        // block the UI, ignore failure (the parent then temporarily sees only
        // whole-school stories — fail-safe, never a leak). The first emit
        // covers app-start/login; subsequent distinct emits cover child-switch.
        // A bare `runCatching {}` here was silent AND unretryable. If the call
        // failed — which it reliably did during first login, while the forced
        // password change had invalidated the refresh token — the exception was
        // swallowed and `distinctUntilChanged()` then suppressed any re-emit,
        // because school/class/section had not changed. Nothing retried until
        // the process was restarted, so every other module recovered on the
        // second login while Stories alone stayed empty.
        viewModelScope.launch {
            tokenManager.user
                .map { Triple(it.schoolId.ifBlank { it.schoolCode }, it.className, it.section) }
                // Don't act on the pre-hydration blank User that DataStore emits
                // before the real one; calling with no school just burns a retry
                // on a request the Cloud Function rejects outright.
                .filter { (school, _, _) -> school.isNotBlank() }
                .distinctUntilChanged()
                .collect { ensureAudienceIndex() }
        }
        // Live seen-state → drives the ring's grey/colored transition.
        viewModelScope.launch { storyRepo.observeSeenStoryIds().collect { seenIds.value = it } }

        observeMyReactions()
        observeStories()
        // Land any view whose write previously failed.
        retryPendingViews()
    }

    /**
     * Build `storyAudience/{uid}`, retrying until it sticks.
     *
     * Without a retry this is a single shot per (school, class, section)
     * change: one failure and the parent is limited to whole-school stories
     * for the rest of the process lifetime, with nothing logged. The window
     * this matters most in is first login, where the forced password change
     * invalidates the refresh token and every callable fails for a few seconds.
     *
     * Deliberately still non-blocking and non-fatal — Stories must render
     * whole-school content while this settles — but it is now noisy in logcat
     * and it gives up only after a bounded set of attempts.
     */
    private suspend fun ensureAudienceIndex() {
        val delaysMs = longArrayOf(0, 1_000, 3_000, 8_000, 20_000)
        for ((attempt, wait) in delaysMs.withIndex()) {
            if (wait > 0) delay(wait)
            val result = storyRepo.refreshAudienceIndex()
            if (result.isSuccess) {
                if (attempt > 0) debugLog("Story.audience rebuilt after ${attempt + 1} attempts")
                return
            }
            val e = result.exceptionOrNull()
            Log.w(TAG, "audience index build failed (attempt ${attempt + 1}/${delaysMs.size})", e)
        }
        // Not fatal: whole-school stories still render. Class-targeted ones
        // stay hidden until the next app start or child switch.
        Log.e(TAG, "audience index could not be built — class-targeted stories will be hidden")
        debugLog("Story.audience FAILED after ${delaysMs.size} attempts")
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

    /** Live registration for this parent's reactions collection-group listener. */
    private var reactionsListener: com.google.firebase.firestore.ListenerRegistration? = null

    // LIVE stream of this parent's reactions (storyId → emoji) so the tray
    // shows their current pick highlighted. Previously a one-shot .get() at
    // init, which meant a reaction toggled on ANOTHER device (or the server
    // reconciling the optimistic write) never reflected until app restart.
    // A real-time collection-group listener keeps myReactions in sync.
    private fun observeMyReactions() {
        viewModelScope.launch {
            // Same trap as markStoryViewed: TokenManager.user emits a blank
            // pre-hydration User first, so a plain firstOrNull() can return
            // early and this listener is then NEVER set up — the parent's own
            // reactions silently stop highlighting. Wait for a real user.
            val user = withTimeoutOrNull(IDENTITY_TIMEOUT_MS) {
                tokenManager.user.first { it.userId.isNotBlank() }
            }
            val userId = user?.userId.orEmpty()
            if (userId.isBlank()) return@launch
            // SEC-3: the reactions collection-group READ rule is tenant-bound
            // (reaction.schoolId == caller's school_id claim), so the query
            // MUST carry the same schoolId filter or Firestore rejects it
            // wholesale. If we can't resolve the school, listen to nothing.
            val school = (user?.schoolId ?: "").ifBlank { user?.schoolCode ?: "" }
            if (school.isBlank()) return@launch
            reactionsListener?.remove()
            reactionsListener = FirebaseFirestore.getInstance()
                .collectionGroup("reactions")
                .whereEqualTo("schoolId", school)
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.w(TAG, "observeMyReactions failed (non-fatal)", err)
                        return@addSnapshotListener
                    }
                    if (snap == null) return@addSnapshotListener
                    val map = mutableMapOf<String, String>()
                    for (doc in snap.documents) {
                        val storyId = doc.reference.parent.parent?.id ?: continue
                        val emoji = doc.getString("emoji").orEmpty()
                        if (emoji.isNotBlank()) map[storyId] = emoji
                    }
                    _uiState.update { it.copy(myReactions = map) }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        reactionsListener?.remove()
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
        viewModelScope.launch {
            storyRepo.reactToStory(storyId, emoji).onFailure { e ->
                // The optimistic tick above already re-drew the tray, so a
                // silently-dropped write left the parent believing they had
                // reacted while the teacher's insights showed nothing. Roll the
                // local state back to what the server actually holds.
                Log.w(TAG, "reaction write failed for $storyId", e)
                _uiState.update { state ->
                    val m = state.myReactions.toMutableMap()
                    if (current.isNullOrBlank()) m.remove(storyId) else m[storyId] = current
                    state.copy(myReactions = m)
                }
            }
        }
    }

    /**
     * Record that this parent watched a story — DURABLY.
     *
     * The ring greys instantly (optimistic, so the UI stays snappy), but the
     * viewer-doc write is now CHECKED: a failure parks the story id in
     * [PendingStoryViews] and it is retried on the next VM start until it
     * lands.
     *
     * This closes a permanent data-loss bug. The old version marked the story
     * seen and discarded the write's Result, so a write that failed (offline,
     * stale token, a transient denial before a claims refresh) left the story
     * in `seenIds` — and the idempotence guard on the first line then
     * guaranteed it would NEVER be attempted again. That parent simply never
     * appeared in the teacher's "who viewed" list, with nothing to say so.
     */
    fun markStoryViewed(storyId: String) {
        val alreadySeen = seenIds.value.contains(storyId)
        // Optimistic: grey the ring instantly; the live listener confirms
        // once the viewer doc write lands.
        seenIds.update { it + storyId }

        viewModelScope.launch {
            // Resolve the viewer id AT CALL TIME, waiting for a non-blank one.
            //
            // This was a one-shot `firstOrNull()` cached in init, and it broke
            // every parent view: TokenManager.user maps DataStore with
            // `userId = prefs[USER_ID] ?: ""`, so the FIRST emission on a cold
            // start is a pre-hydration BLANK user. The cached blank then made
            // the policy return SKIP_INVALID forever and the write was never
            // even attempted — silent, and indistinguishable from "nobody
            // viewed". Waiting for a non-blank value removes the race; the
            // timeout stops a logged-out session hanging the coroutine.
            val me = withTimeoutOrNull(IDENTITY_TIMEOUT_MS) {
                tokenManager.user.map { it.userId }.first { it.isNotBlank() }
            }.orEmpty()

            // Parents never author stories, so SKIP_SELF_VIEW can't occur here
            // — but the SAME policy runs in both apps so the decision (and its
            // log line) is identical wherever a view is considered.
            val decision = StoryViewPolicy.decide(
                storyId = storyId,
                authorId = "",
                currentUserId = me,
                alreadySeen = alreadySeen
            )
            debugLog("Story.view DECISION=$decision story=$storyId me=$me")
            if (decision != ViewRecordDecision.RECORD) return@launch
            persistView(storyId)
        }
    }


    /**
     * Record that this parent watched a story to the END (image timer elapsed,
     * or video reached its last frame). Tapping forward doesn't count, so the
     * teacher can tell "opened it" from "actually watched it".
     *
     * Best-effort — see markAsCompleted for why completions aren't queued for
     * retry the way views are.
     */
    fun markStoryCompleted(storyId: String) {
        if (storyId.isBlank()) return
        viewModelScope.launch {
            // Logged on EVERY path, like the view write. Completion produced
            // `completed: undefined` on every viewer doc during UAT with no way
            // to tell whether the callback never fired or the write was
            // rejected — that ambiguity is what this removes.
            debugLog("Story.complete WRITING story=$storyId")
            storyRepo.markAsCompleted(storyId).fold(
                onSuccess = { debugLog("Story.complete OK story=$storyId") },
                onFailure = { e ->
                    debugLog("Story.complete FAILED story=$storyId ${e.javaClass.simpleName}: ${e.message}")
                    Log.w(TAG, "completion write failed for $storyId", e)
                }
            )
        }
    }

    /** Write one viewer doc, parking it for retry if the write doesn't land. */
    private suspend fun persistView(storyId: String) {
        debugLog("Story.view WRITING story=$storyId")
        storyRepo.markAsViewed(storyId).fold(
            onSuccess = {
                debugLog("Story.view OK story=$storyId")
                PendingStoryViews.remove(appContext, storyId)
            },
            onFailure = { e ->
                // The exception TYPE is the whole diagnosis: PERMISSION_DENIED
                // means the payload can't satisfy the rule (wrong schoolId or
                // userId); UNAVAILABLE means offline and the retry will fix it.
                debugLog("Story.view FAILED story=$storyId ${e.javaClass.simpleName}: ${e.message}")
                Log.w(TAG, "view write failed for $storyId, queued for retry", e)
                PendingStoryViews.add(appContext, storyId)
            }
        )
    }

    /**
     * Drain the retry queue on VM start — i.e. every time the stories surface
     * is opened, which is frequent enough to land a deferred view well inside
     * a story's 24h life without a WorkManager job.
     */
    private fun retryPendingViews() {
        viewModelScope.launch {
            val pending = PendingStoryViews.all(appContext)
            if (pending.isEmpty()) return@launch
            Log.d(TAG, "retrying ${pending.size} deferred story view(s)")
            pending.forEach { persistView(it) }
        }
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

    /**
     * Parse an ISO-8601 timestamp to epoch millis, or 0 when unparseable
     * (rendered as "no time" rather than a wrong one). Needed because the
     * admin panel writes story createdAt as a STRING via date('c').
     */
    private fun parseIsoMillis(s: String): Long = runCatching {
        java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
    }.recoverCatching {
        java.time.Instant.parse(s).toEpochMilli()
    }.getOrDefault(0L)

    private fun StoryDoc.toStory(viewed: Boolean): Story {
        val createdMillis = when (val ts = createdAt) {
            is com.google.firebase.Timestamp -> ts.seconds * 1000L + ts.nanoseconds / 1_000_000L
            is Number -> ts.toLong()
            // Admin-panel stories carry an ISO-8601 string, not a Timestamp.
            // Coercing to 0 blanked the timestamp on every admin-posted story.
            is String -> parseIsoMillis(ts)
            else -> 0L
        }
        return Story(
            storyId    = id,
            teacherId  = effectiveAuthorId,
            teacherName = effectiveAuthorName,
            teacherPic  = effectiveAuthorPic,
            mediaUrl   = mediaUrl,
            type       = type,
            thumbnailUrl = thumbnailUrl,
            caption    = caption,
            createdAt  = createdMillis,
            expiresAt  = expiresAtMillis,
            isViewed   = viewed
        )
    }
}
