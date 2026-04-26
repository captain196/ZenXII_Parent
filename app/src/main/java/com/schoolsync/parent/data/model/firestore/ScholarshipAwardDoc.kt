package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Scholarship award for a student.
 * Collection: `scholarshipAwards`
 * Doc ID: `{schoolId}_{awardId}`
 */
data class ScholarshipAwardDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val scholarshipId: String = "",
    val scholarshipName: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val amount: Double = 0.0,
    val awardedDate: String = "",
    val awardedBy: String = "",
    val status: String = ""     // active, revoked
)
