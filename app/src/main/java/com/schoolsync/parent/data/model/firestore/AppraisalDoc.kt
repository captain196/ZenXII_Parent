package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class AppraisalDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val department: String = "",
    val kras: List<KraDoc> = emptyList(),
    val overallSelfRating: Double = 0.0,
    val overallManagerRating: Double = 0.0,
    val overallRating: Double = 0.0,
    val status: String = "pending",        // pending, self_review, manager_review, finalized
    val selfSubmittedAt: Any? = null,
    val finalizedAt: Any? = null
)

data class KraDoc(
    val id: String = "",
    val area: String = "",
    val weight: Int = 0,
    val target: String = "",
    val selfScore: Double = 0.0,
    val managerScore: Double = 0.0
)
