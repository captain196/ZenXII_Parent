package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Represents a computed result for one student in one exam.
 *
 * Collection: `results`
 * Doc ID: `{schoolId}_{examId}_{sectionKey}_{studentId}`
 */
data class ResultDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val examId: String = "",
    val examName: String = "",
    val sectionKey: String = "",
    val className: String = "",
    val section: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val rollNo: String = "",
    val subjects: Map<String, SubjectResultDoc> = emptyMap(),
    val totalMarks: Double = 0.0,
    val maxMarks: Double = 0.0,
    // Nullable by contract (CC-8): the admin writer preserves `null` for
    // absentees / not-yet-computed results. Coercing to 0.0 here would make an
    // absent student look like a genuine 0% fail. `null` = "no percentage".
    val percentage: Double? = null,
    val grade: String = "",
    val rank: Int = 0,
    val passFail: String = "",           // authoritative "Pass" / "Fail" (from writer)
    val absent: Boolean = false,         // overall absent flag
    val absentSubjects: List<String> = emptyList(),
    val gradingScale: String = "",       // "percentage", "a_f", … (admin owns grading)
    val passingPercent: Int = 33,        // exam pass threshold, echoed by the writer
    val computedAt: Any? = null
)

/**
 * Per-subject result entry embedded within [ResultDoc.subjects].
 *
 * `total` and `percentage` are nullable by contract: the admin writer preserves
 * `null` for a subject the student was absent for (CC-8), rather than 0.
 */
data class SubjectResultDoc(
    val total: Double? = null,
    val maxMarks: Double = 0.0,
    val percentage: Double? = null,
    val grade: String = "",
    val passFail: String = "",           // authoritative per-subject "Pass" / "Fail"
    val absent: Boolean = false
)
