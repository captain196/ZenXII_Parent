package com.schoolsync.parent.data.model.firestore

// SW4-companion-A (2026-05-26): removed `import com.google.firebase.firestore.DocumentId`
// after dropping the @DocumentId annotation from `schoolId` below. Reason:
// Firestore schools/{id} documents written by admin web carry an explicit
// `schoolId` data field whose value equals the document ID. Firebase's
// CustomClassMapper.populateDocumentIdProperties enforces single-source-of-
// truth and threw RuntimeException when the dormant observeSchool() path
// was activated by SW4. Reading `schoolId` from the doc data is identical
// in value to reading it from the doc ID, so dropping the annotation is
// behaviour-neutral and unblocks the snapshot listener.
// Teacher app contains the same latent pattern — deferred to SW5 forensic.

data class SchoolDoc(
    val schoolId: String = "",
    val name: String = "",
    val schoolCode: String = "",
    val city: String = "",
    val email: String = "",
    val phone: String = "",
    val logoUrl: String = "",
    val status: String = "",
    val currentSession: String = "",
    val subscription: SubscriptionInfo = SubscriptionInfo(),
    val createdAt: Any? = null,
    val updatedAt: Any? = null
)

data class SubscriptionInfo(
    val plan: String = "",
    val status: String = "",
    val expiryDate: String = ""
)
