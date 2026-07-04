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
    val attachments: List<String> = emptyList(), // file URLs or names (legacy)
    // Step 2 (2026-05-15) backward-compatibility addition. Rich Attachment
    // objects parsed from EITHER the legacy attachments: List<String> OR
    // the new attachmentObjects: List<Map> field on the Firestore doc.
    // Existing UI code that reads `attachments` (List<String>) continues
    // to work unchanged. New code wanting full metadata reads this field.
    val attachmentObjects: List<Attachment> = emptyList(),
    val rubric: List<RubricItem> = emptyList(),
    val score: Int = -1,                 // -1 = not graded
    val totalMarks: Int = 10,
    val grade: String = "",
    val feedback: String = "",
    // ── teacherMark fallback (no submission was made) ──
    val hasTeacherMark: Boolean = false,
    val teacherMarkScore: Int = -1,
    val teacherMarkRemark: String = ""
)

/**
 * FIX 2: single source of truth for "does this homework need the child to
 * act?". A homework is action-needed when it's still pending OR the teacher
 * bounced it back for a redo (incomplete). Extracted here so the nav badge
 * (HomeworkViewModel), Dashboard and Profile counts all agree instead of each
 * hand-rolling a slightly different rule.
 */
fun isActionNeeded(status: String): Boolean {
    val s = status.lowercase().trim()
    return s == "pending" || s == "incomplete"
}

// FIX 4: the RTDB-era `Homework.fromMap(...)` map parser (~200 lines) was
// removed as dead code — nothing called it. The live path builds [Homework]
// directly from com.schoolsync.parent.data.model.firestore.HomeworkDoc in
// HomeworkViewModel. Git history preserves the old parser if a legacy RTDB
// shape ever needs reviving.

data class RubricItem(val item: String = "", val marks: Int = 0)
