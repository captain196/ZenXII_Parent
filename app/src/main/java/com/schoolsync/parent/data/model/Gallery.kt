package com.schoolsync.parent.data.model

/**
 * Gallery album — uploaded by teacher (`source="general"`) or auto-generated
 * from an event by the admin (`source="event"`). Single unified Firestore
 * collection `galleryAlbums` (Phase C-2 harmonization).
 *
 * Wire-format invariants:
 *   - `isArchived` is the visibility flag (replaces legacy `status`)
 *   - `coverImage` (NOT `coverUrl`) is the cover URL field
 *   - `createdAt`/`updatedAt` are ISO 8601 strings
 *   - `source` ∈ {"event", "general"}
 */
data class GalleryAlbum(
    val albumId: String = "",
    val schoolId: String = "",
    val title: String = "",
    val description: String = "",
    val coverImage: String = "",
    val source: String = "general",        // "event" | "general"
    val eventId: String = "",
    val session: String = "",
    val category: String = "",             // optional sub-classifier (sports / academic / cultural / …)
    val mediaCount: Int = 0,
    val isArchived: Boolean = false,
    val createdBy: String = "",
    val createdAt: String = "",            // ISO 8601
    val updatedAt: String = "",            // ISO 8601
    val archivedAt: String? = null,
    val archivedBy: String? = null,
    val media: List<GalleryMedia> = emptyList()
) {
    /** True iff this album was generated for an event. UI helper. */
    val isEventAlbum: Boolean get() = source == "event"

    companion object {
        fun fromMap(albumId: String, data: Map<String, Any?>): GalleryAlbum {
            return GalleryAlbum(
                albumId     = albumId,
                schoolId    = data["schoolId"]?.toString() ?: "",
                title       = (data["title"] ?: data["name"] ?: "").toString(),
                description = data["description"]?.toString() ?: "",
                coverImage  = data["coverImage"]?.toString() ?: "",
                source      = (data["source"]?.toString() ?: "general").ifBlank { "general" },
                eventId     = data["eventId"]?.toString() ?: "",
                session     = data["session"]?.toString() ?: "",
                category    = data["category"]?.toString() ?: "",
                mediaCount  = (data["mediaCount"] as? Number)?.toInt() ?: 0,
                isArchived  = (data["isArchived"] as? Boolean) ?: false,
                createdBy   = data["createdBy"]?.toString() ?: "",
                createdAt   = data["createdAt"]?.toString() ?: "",
                updatedAt   = data["updatedAt"]?.toString() ?: "",
                archivedAt  = data["archivedAt"]?.toString(),
                archivedBy  = data["archivedBy"]?.toString()
            )
        }

        // Event→gallery bridging is now a real link: the admin publishes a
        // `galleryAlbums` doc (source="event", eventId=<id>) per event that has
        // photos, and the Parent app jumps to it via
        // GalleryFirestoreRepository.getEventAlbum(). The old client-side
        // fromEvent()/fromEventMedia() virtual-album builders (TODO Wave B) are
        // therefore obsolete and were removed.
    }
}

/**
 * Media item inside a gallery album. Unified Firestore collection `galleryMedia`.
 */
data class GalleryMedia(
    val mediaId: String = "",
    val albumId: String = "",
    val url: String = "",
    val thumbnail: String? = null,
    val type: String = "image",            // "image" | "video"
    val caption: String = "",
    val isArchived: Boolean = false,
    val uploadedBy: String = "",
    val uploadedAt: String = "",           // ISO 8601
    val updatedAt: String = "",
    val duration: String? = null
) {
    companion object {
        fun fromMap(mediaId: String, data: Map<String, Any?>): GalleryMedia {
            return GalleryMedia(
                mediaId    = mediaId,
                albumId    = data["albumId"]?.toString() ?: "",
                url        = data["url"]?.toString() ?: "",
                thumbnail  = data["thumbnail"]?.toString(),
                type       = data["type"]?.toString() ?: "image",
                caption    = data["caption"]?.toString() ?: "",
                isArchived = (data["isArchived"] as? Boolean) ?: false,
                uploadedBy = data["uploadedBy"]?.toString() ?: "",
                uploadedAt = data["uploadedAt"]?.toString() ?: "",
                updatedAt  = data["updatedAt"]?.toString() ?: "",
                duration   = data["duration"]?.toString()
            )
        }
    }
}
