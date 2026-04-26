package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class SectionDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val className: String = "",
    val section: String = "",
    val classTeacherId: String = "",
    val studentCount: Int = 0,
    val subjects: List<SubjectAssignment> = emptyList(),
    val createdAt: Any? = null,
    val updatedAt: Any? = null
)

data class SubjectAssignment(
    val name: String = "",
    val teacherId: String = ""
)
