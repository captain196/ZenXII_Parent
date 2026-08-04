package com.schoolsync.parent.data.model

/**
 * School event.
 * Path: Schools/{schoolCode}/Events/List/{eventId}
 */
data class Event(
    val eventId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val location: String = "",
    val organizer: String = "",
    val status: String = "",
    val createdAt: String = "",
    val mediaUrls: List<EventMedia> = emptyList(),
    /**
     * Cover borrowed from the linked gallery album when the event has no
     * showable cover of its own. Kept SEPARATE from [mediaUrls] on purpose:
     * this used to be written by replacing mediaUrls with a single synthetic
     * image entry, which silently deleted the event's actual videos (a
     * video-only event never satisfies [hasUsableCover], so it always took
     * that branch and its videos vanished from the strip and the viewer).
     */
    val borrowedCoverUrl: String = ""
) {
    companion object {
        fun fromMap(eventId: String, data: Map<String, Any?>): Event {
            return Event(
                eventId = eventId,
                title = (data["title"] ?: data["Title"] ?: data["name"] ?: data["Name"] ?: "").toString(),
                description = (data["description"] ?: data["Description"] ?: data["details"] ?: "").toString(),
                category = (data["category"] ?: data["Category"] ?: data["type"] ?: "").toString(),
                startDate = (data["start_date"] ?: data["startDate"] ?: data["date"] ?: data["Date"] ?: "").toString(),
                endDate = (data["end_date"] ?: data["endDate"] ?: "").toString(),
                location = (data["location"] ?: data["Location"] ?: data["venue"] ?: data["Venue"] ?: "").toString(),
                organizer = (data["organizer"] ?: data["Organizer"] ?: data["organized_by"] ?: "").toString(),
                status = (data["status"] ?: data["Status"] ?: "").toString(),
                createdAt = (data["created_at"] ?: data["createdAt"] ?: "").toString()
            )
        }
    }
}

/**
 * True when the event carries a directly-showable cover of its own — an image
 * url or a video with a poster thumbnail. Used to decide whether to borrow the
 * linked gallery album's cover: gating on this (not just mediaUrls.isEmpty())
 * means events whose media is only unshowable entries still get a cover.
 */
internal fun Event.hasUsableCover(): Boolean =
    mediaUrls.any { it.type == "image" && it.url.isNotBlank() } ||
        mediaUrls.any { !it.thumbnail.isNullOrBlank() }

/**
 * Media attached to an event.
 * Path: Schools/{schoolCode}/Events/Media/{eventId}/{mediaId}
 */
data class EventMedia(
    val mediaId: String = "",
    val type: String = "", // "image" or "video"
    val url: String = "",
    val thumbnail: String? = null,
    val duration: String? = null
) {
    companion object {
        fun fromMap(mediaId: String, data: Map<String, Any?>): EventMedia {
            return EventMedia(
                mediaId = mediaId,
                type = (data["type"] ?: data["Type"] ?: "image").toString(),
                url = (data["url"] ?: data["Url"] ?: data["URL"] ?: "").toString(),
                thumbnail = (data["thumbnail"] ?: data["Thumbnail"])?.toString(),
                duration = (data["duration"] ?: data["Duration"])?.toString()
            )
        }
    }
}
