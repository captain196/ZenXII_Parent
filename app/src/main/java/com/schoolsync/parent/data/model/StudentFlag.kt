package com.schoolsync.parent.data.model

/**
 * Canonical Firestore shape for `studentFlags/{schoolId}_{flagId}`.
 * Mirror of the Teacher/Admin models — keep field names in sync if the
 * canonical schema changes.
 */
data class StudentFlag(
    val flagId: String,
    val type: String,                 // homework, behavior, performance
    val message: String,
    val subject: String,
    val teacherId: String,
    val teacherName: String,
    val severity: String,             // low, medium, high
    val createdAtMs: Long,
    val status: String,               // active, resolved
    val resolvedAtMs: Long? = null,
    val hwId: String? = null,
    val studentName: String = ""
) {
    companion object {
        fun fromMap(flagId: String, data: Map<String, Any?>): StudentFlag {
            return StudentFlag(
                flagId      = flagId,
                type        = (data["type"] ?: "").toString(),
                message     = (data["message"] ?: "").toString(),
                subject     = (data["subject"] ?: "").toString(),
                teacherId   = (data["teacherId"] ?: "").toString(),
                teacherName = (data["teacherName"] ?: "").toString(),
                severity    = (data["severity"] ?: "low").toString(),
                createdAtMs = (data["createdAtMs"] as? Number)?.toLong() ?: 0L,
                status      = (data["status"] ?: "active").toString(),
                resolvedAtMs = (data["resolvedAtMs"] as? Number)?.toLong(),
                hwId        = data["hwId"]?.toString(),
                studentName = (data["studentName"] ?: "").toString()
            )
        }
    }
}
