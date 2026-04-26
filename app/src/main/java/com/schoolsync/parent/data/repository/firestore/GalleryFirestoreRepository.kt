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
        type       = type,
        caption    = caption,
        isArchived = isArchived,
        uploadedBy = uploadedBy,
        uploadedAt = uploadedAt,
        updatedAt  = updatedAt
    )

    // ── Reads ──────────────────────────────────────────────────────────

    /**
     * All non-archived albums for this user's school, newest first.
     */
    suspend fun getAlbums(): List<GalleryAlbum> {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return emptyList()

        return try {
            val docs = firestoreService.queryDocumentsAs<GalleryAlbumDoc>(
                "galleryAlbums"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("isArchived", false)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            }
            docs.map { it.toAlbum() }
        } catch (_: Exception) {
            emptyList()
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

        return try {
            val docs = firestoreService.queryDocumentsAs<GalleryMediaDoc>(
                "galleryMedia"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("albumId", albumId)
                    .whereEqualTo("isArchived", false)
                    .orderBy("uploadedAt", Query.Direction.DESCENDING)
            }
            docs.map { it.toMedia() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetch one album with its media populated, for the detail screen.
     * Returns an empty-shell album if the doc isn't found (matches the
     * legacy GalleryRepository.getAlbumWithMedia contract).
     */
    suspend fun getAlbumWithMedia(albumId: String): GalleryAlbum {
        val schoolCode = tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
            ?: return GalleryAlbum(albumId = albumId)

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
            }
            val albumDoc = albumDocs.firstOrNull()
                ?: return GalleryAlbum(albumId = albumId)

            val mediaList = getAlbumMedia(albumId)
            albumDoc.toAlbum(mediaList)
        } catch (_: Exception) {
            GalleryAlbum(albumId = albumId)
        }
    }
}
