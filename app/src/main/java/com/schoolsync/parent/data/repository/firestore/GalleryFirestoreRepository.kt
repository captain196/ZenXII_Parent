package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.Query
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.GalleryAlbum
import com.schoolsync.parent.data.model.GalleryMedia
import com.schoolsync.parent.data.model.firestore.GalleryAlbumDoc
import com.schoolsync.parent.data.model.firestore.GalleryMediaDoc
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase C-2 canonical gallery repository (Parent — read-only).
 *
 * Reads the unified `galleryAlbums` / `galleryMedia` collections shared with
 * Admin (Events.php) and Teacher. No RTDB.
 *
 * Visibility filter: `isArchived == false` (replaces legacy `status==active`).
 */
@Singleton
class GalleryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    // ── Doc → UI model mapping ─────────────────────────────────────────
    private fun GalleryAlbumDoc.toAlbum(media: List<GalleryMedia> = emptyList()): GalleryAlbum =
        GalleryAlbum(
            albumId     = albumId.ifBlank { id },
            schoolId    = schoolId,
            title       = title,
            description = description,
            coverImage  = coverImage,
            source      = source.ifBlank { "general" },
            eventId     = eventId,
            session     = session,
            category    = category,
            mediaCount  = mediaCount,
            isArchived  = isArchived,
            createdBy   = createdBy,
            createdAt   = createdAt,
            updatedAt   = updatedAt,
            archivedAt  = archivedAt,
            archivedBy  = archivedBy,
            media       = media
        )

    private fun GalleryMediaDoc.toMedia(): GalleryMedia = GalleryMedia(
        mediaId    = id,
        albumId    = albumId,
        url        = url,
        thumbnail  = thumbnail.ifBlank { null },
        type       = type,
        caption    = caption,
        isArchived = isArchived,
        uploadedBy = uploadedBy,
        uploadedAt = uploadedAt,
        updatedAt  = updatedAt,
        duration   = duration.ifBlank { null }
    )

    // ── Reads ──────────────────────────────────────────────────────────

    /**
     * All non-archived albums for this user's school, newest first.
     *
     * Returns [Result] so the caller can distinguish a genuine "no albums"
     * (empty success) from a query failure — an undeployed composite index
     * or PERMISSION_DENIED must surface an error + Retry in the UI, not the
     * same silent empty state as "school has published nothing yet".
     */
    suspend fun getAlbums(): Result<List<GalleryAlbum>> {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School id not available"))

        return try {
            val docs = firestoreService.queryDocumentsAs<GalleryAlbumDoc>(
                "galleryAlbums"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("isArchived", false)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(100)
            }
            Result.success(docs.map { it.toAlbum() })
        } catch (e: Exception) {
            android.util.Log.e("GalleryRepo", "getAlbums failed", e)
            Result.failure(e)
        }
    }

    /**
     * All non-archived media for an album, newest first.
     *
     * The schoolId filter is REQUIRED — Firestore rules check
     * resource.data.schoolId per-doc, so the query must guarantee all
     * returned docs share the auth'd user's school, otherwise the entire
     * query is rejected with PERMISSION_DENIED.
     */
    suspend fun getAlbumMedia(albumId: String): List<GalleryMedia> {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return emptyList()

        // Deliberately NOT swallowing exceptions here — the only caller is
        // getAlbumWithMedia(), which wraps this in a Result so an index /
        // permission failure surfaces as a distinguishable error state.
        val docs = firestoreService.queryDocumentsAs<GalleryMediaDoc>(
            "galleryMedia"
        ) { ref ->
            ref.whereEqualTo("schoolId", schoolCode)
                .whereEqualTo("albumId", albumId)
                .whereEqualTo("isArchived", false)
                .orderBy("uploadedAt", Query.Direction.DESCENDING)
                .limit(300)
        }
        return docs.map { it.toMedia() }
    }

    /**
     * Find the (non-archived) gallery album generated for a given event, if any.
     *
     * Admin's Events flow writes, per event that has photos, one `galleryAlbums`
     * doc with `source="event"` and `eventId == <event id>`. This lets the event
     * detail screen surface a "View Photos" jump into the full album.
     *
     * Returns [Result]: `success(null)` when the event has no album yet (a normal
     * state — most events have no gallery), and `failure` only on a genuine query
     * error (undeployed composite index / PERMISSION_DENIED) so the caller can tell
     * the two apart instead of silently hiding a broken query.
     */
    suspend fun getEventAlbum(eventId: String): Result<GalleryAlbum?> {
        if (eventId.isBlank()) return Result.success(null)
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School id not available"))

        // Event albums store the RAW event id (e.g. "EVT0001"), but callers pass
        // EventDoc.id which is the @DocumentId full doc id "{schoolId}_{EVT...}".
        // Strip the prefix so the query matches the album's eventId.
        val rawEventId = eventId.removePrefix("${schoolCode}_")

        return try {
            val docs = firestoreService.queryDocumentsAs<GalleryAlbumDoc>(
                "galleryAlbums"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("eventId", rawEventId)
                    .whereEqualTo("isArchived", false)
                    .limit(1)
            }
            Result.success(docs.firstOrNull()?.toAlbum())
        } catch (e: Exception) {
            android.util.Log.e("GalleryRepo", "getEventAlbum failed for eventId=$eventId", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch one album with its media populated, for the detail screen.
     * Returns an empty-shell album if the doc isn't found (matches the
     * legacy GalleryRepository.getAlbumWithMedia contract).
     */
    suspend fun getAlbumWithMedia(albumId: String): Result<GalleryAlbum> {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("School id not available"))

        return try {
            // Find the matching album by albumId field (doc-ID format varies
            // between admin "{loginCode}_{albumId}" and teacher "{schoolCode}_{millis}";
            // querying by field is format-agnostic).
            val albumDocs = firestoreService.queryDocumentsAs<GalleryAlbumDoc>(
                "galleryAlbums"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("albumId", albumId)
                    .whereEqualTo("isArchived", false)
                    .limit(1)
            }
            // Not-found is a success with an empty-shell album (the detail
            // screen renders "Album not found" style state); only a genuine
            // query failure below becomes Result.failure.
            val albumDoc = albumDocs.firstOrNull()
                ?: return Result.success(GalleryAlbum(albumId = albumId))

            val mediaList = getAlbumMedia(albumId)
            Result.success(albumDoc.toAlbum(mediaList))
        } catch (e: Exception) {
            android.util.Log.e("GalleryRepo", "getAlbumWithMedia failed for albumId=$albumId", e)
            Result.failure(e)
        }
    }
}
