package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Represents an exam definition in Firestore.
 *
 * Collection: `exams`
 * Doc ID: auto-generated or admin-set.
 */
data class ExamDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val examName: String = "",
    val examType: String = "",           // "Unit Test", "Mid-Term", "Final"
    val gradingScale: String = "",       // "percentage", "a_f", "o_e", "10_point", "pass_fail"
    val passingPercent: Int = 33,
    val maxTheory: Double = 80.0,
    val maxPractical: Double = 20.0,
    val maxTotal: Double = 100.0,
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "",             // "Draft", "Published", "Completed"
    val weight: Double = 0.0,
    val applicableClasses: List<String> = emptyList(),
    val createdAt: Any? = null
) {
    companion object {
        /**
         * The exam statuses a parent is allowed to see. Drafts are hidden
         * (HIGH-1: an unreadable Draft would reject the whole query), while
         * "Completed" past exams still surface because they carry results.
         * This is the single source of truth for both the Firestore `whereIn`
         * query and the pure client-side filter below.
         */
        val PARENT_VISIBLE_STATUSES: List<String> = listOf("Published", "Completed")

        /** Pure predicate: is this exam status visible to a parent? */
        fun isParentVisible(status: String): Boolean = status in PARENT_VISIBLE_STATUSES

        /** Pure filter: keep only parent-visible exams from a mixed list. */
        fun filterParentVisible(exams: List<ExamDoc>): List<ExamDoc> =
            exams.filter { isParentVisible(it.status) }
    }
}
