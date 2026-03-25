package com.schoolsync.parent.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class AdmissionApplicationDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val applicantName: String = "",
    val dob: String = "",
    val gender: String = "",
    val applyingForClass: String = "",
    val parentName: String = "",
    val parentPhone: String = "",
    val parentEmail: String = "",
    val address: String = "",
    val documents: Map<String, DocumentInfoDoc> = emptyMap(),
    val stage: String = "inquiry",         // inquiry, application, document_verification, entrance_test, interview, offer, enrolled
    val entranceTestScore: Double = 0.0,
    val interviewScore: Double = 0.0,
    val meritRank: Int = 0,
    val ageValid: Boolean = true,
    val source: String = "walk_in",
    val status: String = "active",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
)

data class DocumentInfoDoc(
    val url: String = "",
    val verified: Boolean = false
)
