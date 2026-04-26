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
    val caption: String,
    val createdAt: Long,
    val expiresAt: Long,
    val isViewed: Boolean = false
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt

    companion object {
        fun fromMap(storyId: String, data: Map<String, Any?>, teacherId: String): Story {
            return Story(
                storyId = storyId,
                teacherId = teacherId,
                teacherName = (data["teacherName"] ?: data["teacher_name"] ?: "").toString(),
                teacherPic = (data["teacherPic"] ?: data["teacher_pic"] ?: "").toString(),
                mediaUrl = (data["mediaUrl"] ?: data["media_url"] ?: "").toString(),
                type = (data["type"] ?: data["Type"] ?: "image").toString(),
                caption = (data["caption"] ?: data["Caption"] ?: "").toString(),
                createdAt = when (val ts = data["createdAt"] ?: data["created_at"]) {
                    is Number -> ts.toLong()
                    is String -> ts.toLongOrNull() ?: 0L
                    else -> 0L
                },
                expiresAt = when (val ts = data["expiresAt"] ?: data["expires_at"]) {
                    is Number -> ts.toLong()
                    is String -> ts.toLongOrNull() ?: 0L
                    else -> 0L
                }
            )
        }
    }
}
