package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Firestore staff document. Doc id format: {schoolId}_{userId}.
 *
 * Note: createdAt/updatedAt are typed as Any? because the PHP backend writes
 * them as ISO strings (not Firestore Timestamps). Using Timestamp here would
 * make every read crash with "Failed to convert value of type java.lang.String
 * to Timestamp" — neither field is consumed by the parent UI, so accepting
 * any payload is the safest option.
 */
data class StaffDoc(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "",
    val schoolId: String = "",
    val department: String = "",
    val position: String = "",
    val designation: String = "",
    val qualification: String = "",
    val subjects: List<String> = emptyList(),
    val classesAssigned: List<String> = emptyList(),
    val gender: String = "",
    val dob: String = "",
    val joiningDate: String = "",
    val profilePic: String = "",
    val status: String = "",
    val createdAt: Any? = null,
    val updatedAt: Any? = null
)
