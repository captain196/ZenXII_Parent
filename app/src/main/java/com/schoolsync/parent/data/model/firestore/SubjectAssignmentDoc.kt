package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

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
 *
 * NOTE on `isClassTeacher`: Kotlin's `is`-prefix Boolean property convention
 * makes Firestore's CustomClassMapper miss the field — its reflective lookup
 * expects `getIsClassTeacher` / `setIsClassTeacher`, but Kotlin generates
 * `isClassTeacher()` and no setter (val). Without [PropertyName] every load
 * logs `No setter/field for isClassTeacher` and the value silently stays
 * `false` — which broke the Dashboard class-teacher card and the My Teachers
 * "Class Teacher" badge.
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
    @get:PropertyName("isClassTeacher")
    val isClassTeacher: Boolean = false,
    val updatedAt: String = "",
    val archived: Boolean = false,
)
