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

        /** Create a virtual album from an Event that has media. */
        fun fromEvent(event: Event): GalleryAlbum {
            return GalleryAlbum(
                albumId     = "event_${event.eventId}",
                title       = event.title,
                description = event.description,
                coverImage  = event.mediaUrls.firstOrNull { it.type == "image" }?.url
                    ?: event.mediaUrls.firstOrNull()?.thumbnail ?: "",
                source      = "event",
                eventId     = event.eventId,
                session     = "",
                category    = event.category,
                mediaCount  = event.mediaUrls.size,
                isArchived  = false,
                createdBy   = "",
                createdAt   = event.startDate,
                updatedAt   = "",
                media       = event.mediaUrls.map { GalleryMedia.fromEventMedia(it) }
            )
        }
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

        /** Convert an EventMedia into a GalleryMedia. */
        fun fromEventMedia(eventMedia: EventMedia): GalleryMedia {
            return GalleryMedia(
                mediaId   = eventMedia.mediaId,
                url       = eventMedia.url,
                thumbnail = eventMedia.thumbnail,
                type      = eventMedia.type,
                duration  = eventMedia.duration
            )
        }
    }
}
