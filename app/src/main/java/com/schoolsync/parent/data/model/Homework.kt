package com.schoolsync.parent.data.model

/**
 * Study material / homework assigned by a teacher.
 * Legacy path: Schools/{schoolCode}/{session}/Teachers/{teacherId}/studyMaterial/{class}/{section}
 * New path:    Schools/{schoolCode}/{session}/Homework/Class {class}/Section {section}/{hwId}
 * Status:      HomeworkStatus/{schoolCode}/{hwId}/{studentId}
 */
data class Homework(
    val homeworkId: String = "",
    val hwId: String = "",
    val title: String = "",
    val description: String = "",
    val subject: String = "",
    val teacherName: String = "",
    val teacherId: String = "",
    val date: String = "",
    val dueDate: String = "",
    val timestamp: Long = 0L,
    val attachmentUrl: String = "",
    val attachmentName: String = "",
    val className: String = "",
    val section: String = "",
    val studentStatus: String = "pending", // pending, submitted, complete, incomplete
    val isFromNewPath: Boolean = false,
    val rawData: Map<String, Any?> = emptyMap(),
    // ── New fields (safe defaults for backward compat) ──
    val priority: String = "",           // high, medium, low (derived from due date if empty)
    val questions: Int = 0,
    val estimatedTime: String = "",
    val attachments: List<String> = emptyList(), // file URLs or names
    val rubric: List<RubricItem> = emptyList(),
    val score: Int = -1,                 // -1 = not graded
    val totalMarks: Int = 10,
    val grade: String = "",
    val feedback: String = "",
    // ── teacherMark fallback (no submission was made) ──
    val hasTeacherMark: Boolean = false,
    val teacherMarkScore: Int = -1,
    val teacherMarkRemark: String = ""
) {
    companion object {
        fun fromMap(homeworkId: String, data: Map<String, Any?>): Homework {
            // Parse attachments list
            val attachmentsList = mutableListOf<String>()
            val rawAttachments = data["attachments"]
            when (rawAttachments) {
                is List<*> -> rawAttachments.forEach { it?.toString()?.let { s -> attachmentsList.add(s) } }
                is Map<*, *> -> rawAttachments.values.forEach { it?.toString()?.let { s -> attachmentsList.add(s) } }
                is String -> if (rawAttachments.isNotBlank()) attachmentsList.add(rawAttachments)
            }
            // Also include legacy single attachment
            val legacyUrl = (data["attachment"] ?: data["Attachment"] ?: data["file_url"] ?: data["url"] ?: "").toString()
            if (legacyUrl.isNotBlank() && legacyUrl !in attachmentsList) {
                attachmentsList.add(0, legacyUrl)
            }

            // Parse rubric
            val rubricList = mutableListOf<RubricItem>()
            val rawRubric = data["rubric"]
            when (rawRubric) {
                is List<*> -> rawRubric.forEach { entry ->
                    if (entry is Map<*, *>) {
                        rubricList.add(
                            RubricItem(
                                item = (entry["item"] ?: entry["name"] ?: "").toString(),
                                marks = when (val m = entry["marks"] ?: entry["mark"]) {
                                    is Number -> m.toInt()
                                    is String -> m.toIntOrNull() ?: 0
                                    else -> 0
                                }
                            )
                        )
                    }
                }
                is Map<*, *> -> rawRubric.forEach { (k, v) ->
                    val marks = when (v) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull() ?: 0
                        is Map<*, *> -> when (val m = v["marks"]) {
                            is Number -> m.toInt()
                            is String -> m.toIntOrNull() ?: 0
                            else -> 0
                        }
                        else -> 0
                    }
                    rubricList.add(RubricItem(item = k.toString(), marks = marks))
                }
            }

            return Homework(
                homeworkId = homeworkId,
                hwId = homeworkId,
                title = (data["title"] ?: data["Title"] ?: "").toString(),
                description = (data["description"] ?: data["Description"] ?: data["content"] ?: "").toString(),
                subject = (data["subject"] ?: data["Subject"] ?: "").toString(),
                teacherName = (data["teacher_name"] ?: data["teacherName"] ?: data["Teacher"] ?: "").toString(),
                teacherId = (data["teacher_id"] ?: data["teacherId"] ?: "").toString(),
                date = (data["date"] ?: data["Date"] ?: "").toString(),
                dueDate = (data["due_date"] ?: data["dueDate"] ?: data["Due_date"] ?: "").toString(),
                timestamp = when (val ts = data["timestamp"] ?: data["Timestamp"] ?: data["createdAt"]) {
                    is Number -> ts.toLong()
                    is String -> ts.toLongOrNull() ?: 0L
                    else -> 0L
                },
                attachmentUrl = legacyUrl,
                attachmentName = (data["attachment_name"] ?: data["file_name"] ?: "").toString(),
                studentStatus = "pending", // Set by HomeworkStatus lookup, not from homework data
                rawData = data,
                priority = (data["priority"] ?: data["Priority"] ?: "").toString(),
                questions = when (val q = data["questions"] ?: data["Questions"] ?: data["question_count"]) {
                    is Number -> q.toInt()
                    is String -> q.toIntOrNull() ?: 0
                    else -> 0
                },
                estimatedTime = (data["estimated_time"] ?: data["estimatedTime"] ?: data["est_time"] ?: "").toString(),
                attachments = attachmentsList,
                rubric = rubricList,
                score = when (val s = data["score"] ?: data["Score"]) {
                    is Number -> s.toInt()
                    is String -> s.toIntOrNull() ?: -1
                    else -> -1
                },
                totalMarks = when (val t = data["total_marks"] ?: data["totalMarks"] ?: data["Total"]) {
                    is Number -> t.toInt()
                    is String -> t.toIntOrNull() ?: 10
                    else -> 10
                },
                grade = (data["grade"] ?: data["Grade"] ?: "").toString(),
                feedback = (data["feedback"] ?: data["Feedback"] ?: data["teacher_feedback"] ?: "").toString()
            )
        }
    }
}

data class RubricItem(val item: String = "", val marks: Int = 0)
