package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Firestore subjectAssignments collection — single source of truth for who
 * teaches what to which class/section. Owned by the admin panel via
 * Subject_assignment_service.
 *
 * Doc id format: {schoolId}_{session}_{className}_{section}_{subjectCode}
 * (section may be "_ALL_" for class-wide assignments)
 *
 * `updatedAt` is a String because the PHP backend writes ISO strings, not
 * Firestore Timestamps. Do not change to Timestamp without first updating the
 * backend writer or every read here will throw.
 */
data class SubjectAssignmentDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val className: String = "",
    val section: String = "",
    val subjectCode: String = "",
    val subjectName: String = "",
    val category: String = "",
    val periodsPerWeek: Int = 0,
    val teacherId: String = "",
    val teacherName: String = "",
    val isClassTeacher: Boolean = false,
    val updatedAt: String = "",
)
