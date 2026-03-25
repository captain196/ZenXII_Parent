package com.schoolsync.parent.data.model

/**
 * Gallery album — uploaded by admin or auto-generated from an event.
 * Path: Schools/{schoolCode}/Gallery/Albums/{albumId}
 */
data class GalleryAlbum(
    val albumId: String = "",
    val title: String = "",
    val description: String = "",
    val coverImage: String = "",
    val category: String = "",       // "event", "general", "sports", "academic", "celebration", "cultural"
    val eventId: String = "",        // non-empty if linked to an event
    val createdAt: String = "",
    val mediaCount: Int = 0,
    val status: String = "active",
    val media: List<GalleryMedia> = emptyList(),
    val isEventAlbum: Boolean = false // true for auto-generated event albums
) {
    companion object {
        fun fromMap(albumId: String, data: Map<String, Any?>): GalleryAlbum {
            return GalleryAlbum(
                albumId = albumId,
                title = (data["title"] ?: data["Title"] ?: data["name"] ?: "").toString(),
                description = (data["description"] ?: data["Description"] ?: "").toString(),
                coverImage = (data["coverImage"] ?: data["cover_image"] ?: data["CoverImage"] ?: "").toString(),
                category = (data["category"] ?: data["Category"] ?: "general").toString(),
                eventId = (data["eventId"] ?: data["event_id"] ?: "").toString(),
                createdAt = (data["createdAt"] ?: data["created_at"] ?: data["date"] ?: "").toString(),
                mediaCount = ((data["mediaCount"] ?: data["media_count"] ?: 0).toString().toIntOrNull() ?: 0),
                status = (data["status"] ?: data["Status"] ?: "active").toString()
            )
        }

        /** Create a virtual album from an Event that has media. */
        fun fromEvent(event: Event): GalleryAlbum {
            return GalleryAlbum(
                albumId = "event_${event.eventId}",
                title = event.title,
                description = event.description,
                coverImage = event.mediaUrls.firstOrNull { it.type == "image" }?.url
                    ?: event.mediaUrls.firstOrNull()?.thumbnail ?: "",
                category = event.category.ifBlank { "event" },
                eventId = event.eventId,
                createdAt = event.startDate,
                mediaCount = event.mediaUrls.size,
                status = event.status,
                media = event.mediaUrls.map { GalleryMedia.fromEventMedia(it) },
                isEventAlbum = true
            )
        }
    }
}

/**
 * Media item inside a gallery album.
 * Path: Schools/{schoolCode}/Gallery/Media/{albumId}/{mediaId}
 */
data class GalleryMedia(
    val mediaId: String = "",
    val url: String = "",
    val thumbnail: String? = null,
    val type: String = "image",    // "image" or "video"
    val caption: String = "",
    val uploadedAt: String = "",
    val uploadedBy: String = "",
    val duration: String? = null   // for videos
) {
    companion object {
        fun fromMap(mediaId: String, data: Map<String, Any?>): GalleryMedia {
            return GalleryMedia(
                mediaId = mediaId,
                url = (data["url"] ?: data["Url"] ?: data["URL"] ?: "").toString(),
                thumbnail = (data["thumbnail"] ?: data["Thumbnail"])?.toString(),
                type = (data["type"] ?: data["Type"] ?: "image").toString(),
                caption = (data["caption"] ?: data["Caption"] ?: "").toString(),
                uploadedAt = (data["uploadedAt"] ?: data["uploaded_at"] ?: data["createdAt"] ?: "").toString(),
                uploadedBy = (data["uploadedBy"] ?: data["uploaded_by"] ?: "").toString(),
                duration = (data["duration"] ?: data["Duration"])?.toString()
            )
        }

        /** Convert an EventMedia into a GalleryMedia. */
        fun fromEventMedia(eventMedia: EventMedia): GalleryMedia {
            return GalleryMedia(
                mediaId = eventMedia.mediaId,
                url = eventMedia.url,
                thumbnail = eventMedia.thumbnail,
                type = eventMedia.type,
                duration = eventMedia.duration
            )
        }
    }
}
