package com.schoolsync.parent.data.model

data class TeacherStoryGroup(
    val teacherId: String,
    val teacherName: String,
    val teacherPic: String,
    val stories: List<Story>,
    val hasUnviewed: Boolean,
    /** Phase C — drives the StoriesRow ring color: admin posts get
     *  red/gold gradient; teacher posts get the existing teal one. */
    val authorType: String = "teacher",
    /** "high" | "normal". Admin high-priority posts pin to row top. */
    val priority: String = "normal"
)

data class Story(
    val storyId: String,
    val teacherId: String,
    val teacherName: String,
    val teacherPic: String,
    val mediaUrl: String,
    val type: String, // image, video
    /** Poster frame for video stories; "" for images. See StoryDoc.thumbnailUrl. */
    val thumbnailUrl: String = "",
    val caption: String,
    val createdAt: Long,
    val expiresAt: Long,
    val isViewed: Boolean = false
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}
