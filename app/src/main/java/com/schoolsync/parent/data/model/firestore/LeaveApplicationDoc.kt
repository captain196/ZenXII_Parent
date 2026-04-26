package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class LeaveApplicationDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val applicantType: String = "",  // "staff" or "student"
    val applicantId: String = "",
    val applicantName: String = "",
    val sectionKey: String = "",     // for students
    val leaveType: String = "",      // "SL", "CL", "EL", "ML", "LWP"
    val startDate: String = "",
    val endDate: String = "",
    val numberOfDays: Int = 0,
    val reason: String = "",
    val attachments: List<String> = emptyList(),
    val status: String = "pending",  // "pending", "approved", "rejected", "cancelled"
    val appliedAt: Any? = null,
    val approvedBy: String = "",
    val approvedAt: Any? = null,
    val remarks: String = ""
)
