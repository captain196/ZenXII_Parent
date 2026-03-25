package com.schoolsync.parent.data.model

data class StudentFlag(
    val flagId: String,
    val type: String, // homework, behavior, performance
    val message: String,
    val subject: String,
    val teacherId: String,
    val teacherName: String,
    val severity: String, // low, medium, high
    val timestamp: Long,
    val status: String, // active, resolved
    val resolvedAt: Long? = null,
    val hwId: String? = null,
    val studentName: String = ""
) {
    companion object {
        fun fromMap(flagId: String, data: Map<String, Any?>): StudentFlag {
            return StudentFlag(
                flagId = flagId,
                type = (data["type"] ?: data["Type"] ?: "").toString(),
                message = (data["message"] ?: data["Message"] ?: "").toString(),
                subject = (data["subject"] ?: data["Subject"] ?: "").toString(),
                teacherId = (data["teacherId"] ?: data["teacher_id"] ?: "").toString(),
                teacherName = (data["teacherName"] ?: data["teacher_name"] ?: "").toString(),
                severity = (data["severity"] ?: data["Severity"] ?: "low").toString(),
                timestamp = when (val ts = data["timestamp"] ?: data["Timestamp"]) {
                    is Number -> ts.toLong()
                    is String -> ts.toLongOrNull() ?: 0L
                    else -> 0L
                },
                status = (data["status"] ?: data["Status"] ?: "active").toString(),
                resolvedAt = when (val ra = data["resolvedAt"] ?: data["resolved_at"]) {
                    is Number -> ra.toLong()
                    is String -> ra.toLongOrNull()
                    else -> null
                },
                hwId = (data["hwId"] ?: data["hw_id"])?.toString(),
                studentName = (data["studentName"] ?: data["student_name"] ?: "").toString()
            )
        }
    }
}
