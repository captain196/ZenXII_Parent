package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Lesson plan — Phase 6/7 Academic Planner (parent-side, read-only).
 *
 * Collection: `lessonPlans`
 * Doc ID:     "{schoolId}_{session}_{teacherId}_{date}_P{periodIndex}"
 *
 * Written by Lesson_plan_service (admin) and the Teacher app's
 * LessonPlanFirestoreRepository. Parent app only reads.
 */
data class LessonPlanDoc(
    @DocumentId
    val id: String = "",

    val schoolId: String = "",
    val session: String = "",
    val planId: String = "",
    val version: Long = 0,

    val className: String = "",
    val section: String = "",
    val classSection: String = "",     // "Class 8th/Section A"
    val subject: String = "",

    val teacherId: String = "",
    val teacherName: String = "",

    val date: String = "",             // YYYY-MM-DD
    val dayOfWeek: String = "",
    val periodIndex: Int = 0,
    val periodNumber: Int = 0,

    val topicId: String = "",
    val topicTitle: String = "",

    val notes: String = "",
    val status: String = "planned",    // planned | completed | skipped | rescheduled
    val rescheduledTo: String = "",
    val completedAt: String = ""
)

/**
 * Per-(class, subject) plan completion counters — Phase 8.
 *
 * Collection: `subjectPlanProgress`
 * Doc ID:     "{schoolId}_{session}_{classSlug}_{subjectSlug}"
 *
 * Maintained by the admin Lesson_plan_service write hook; nightly rebuild
 * script reconciles drift. Parent app reads to show the per-subject
 * progress strip.
 */
data class SubjectPlanProgressDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val className: String = "",
    val section: String = "",
    val classSection: String = "",
    val subject: String = "",
    val totalPlans: Long = 0,
    val plannedCount: Long = 0,
    val completedCount: Long = 0,
    val skippedCount: Long = 0,
    val rescheduledCount: Long = 0,
    val percentComplete: Double = 0.0
)
