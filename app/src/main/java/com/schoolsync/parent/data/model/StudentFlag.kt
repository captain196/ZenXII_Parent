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
                // Tolerate legacy schema: prefer canonical `createdAtMs`,
                // fall back to legacy `timestamp` (RTDB-era), then to a
                // Firestore Timestamp object, then to an ISO string. Returns
                // 0L only if NO date hint exists — UI must check for 0L
                // and show "date unavailable" instead of 1970-01-01.
                createdAtMs = parseEpochMs(
                    data["createdAtMs"] ?: data["timestamp"] ?: data["createdAt"]
                ),
                status      = (data["status"] ?: "active").toString(),
                resolvedAtMs = (data["resolvedAtMs"] as? Number)?.toLong(),
                hwId        = data["hwId"]?.toString(),
                studentName = (data["studentName"] ?: "").toString()
            )
        }

        private fun parseEpochMs(raw: Any?): Long {
            if (raw == null) return 0L
            // Most common — already epoch ms (Long / Int / Double from Firestore).
            if (raw is Number) {
                val v = raw.toLong()
                // Heuristic: <10 digits ≈ epoch seconds, treat as such.
                return if (v in 1L..9_999_999_999L) v * 1000L else v
            }
            // Firestore Timestamp object (when the field was written via
            // FieldValue.serverTimestamp() and read raw without binding).
            try {
                val cls = raw.javaClass
                if (cls.name == "com.google.firebase.Timestamp") {
                    val toDate = cls.getMethod("toDate")
                    val date   = toDate.invoke(raw) as? java.util.Date
                    if (date != null) return date.time
                }
            } catch (_: Throwable) {}
            // ISO 8601 string fallback — try a few formats.
            if (raw is String && raw.isNotBlank()) {
                val patterns = listOf(
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd"
                )
                for (p in patterns) {
                    try {
                        val sdf = java.text.SimpleDateFormat(p, java.util.Locale.US)
                        if (p.endsWith("'Z'")) sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        val d = sdf.parse(raw)
                        if (d != null) return d.time
                    } catch (_: Throwable) {}
                }
            }
            return 0L
        }
    }
}
