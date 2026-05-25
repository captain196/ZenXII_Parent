package com.schoolsync.parent.data.model

/**
 * Study material / homework assigned by a teacher.
 * Legacy path: Schools/{schoolCode}/{session}/Teachers/{teacherId}/studyMaterial/{class}/{section}
 * New path:    Schools/{schoolCode}/{session}/Homework/Class {class}/Section {section}/{hwId}
 * Status:      HomeworkStatus/{schoolCode}/{hwId}/{studentId}
 */
data class Homework(
    val homeworkId: String = "",
    val hwId: String = "",
    val title: String = "",
    val description: String = "",
    val subject: String = "",
    val teacherName: String = "",
    val teacherId: String = "",
    val date: String = "",
    val dueDate: String = "",
    val timestamp: Long = 0L,
    val attachmentUrl: String = "",
    val attachmentName: String = "",
    val className: String = "",
    val section: String = "",
    val studentStatus: String = "pending", // pending, submitted, complete, incomplete
    val isFromNewPath: Boolean = false,
    val rawData: Map<String, Any?> = emptyMap(),
    // ── New fields (safe defaults for backward compat) ──
    val priority: String = "",           // high, medium, low (derived from due date if empty)
    val questions: Int = 0,
    val estimatedTime: String = "",
    val attachments: List<String> = emptyList(), // file URLs or names (legacy)
    // Step 2 (2026-05-15) backward-compatibility addition. Rich Attachment
    // objects parsed from EITHER the legacy attachments: List<String> OR
    // the new attachmentObjects: List<Map> field on the Firestore doc.
    // Existing UI code that reads `attachments` (List<String>) continues
    // to work unchanged. New code wanting full metadata reads this field.
    val attachmentObjects: List<Attachment> = emptyList(),
    val rubric: List<RubricItem> = emptyList(),
    val score: Int = -1,                 // -1 = not graded
    val totalMarks: Int = 10,
    val grade: String = "",
    val feedback: String = "",
    // ── teacherMark fallback (no submission was made) ──
    val hasTeacherMark: Boolean = false,
    val teacherMarkScore: Int = -1,
    val teacherMarkRemark: String = ""
) {
    companion object {
        fun fromMap(homeworkId: String, data: Map<String, Any?>): Homework {
            // Parse attachments list
            //
            // Finding #30 — closed by Step 5 (2026-05-15). Click-site
            // validation now lives in
            // util/AttachmentUrlValidator.kt, called from
            // HomeworkScreen.kt's attachment Row before any
            // Intent.ACTION_VIEW dispatch. Policy: https-only +
            // host == firebasestorage.googleapis.com. URLs that fail
            // either check surface a friendly toast and never reach the
            // OS Intent system. This parser intentionally still ingests
            // URLs verbatim — the trust boundary is the click site, not
            // the parser, so legacy and forward-compat shapes continue
            // to round-trip without loss.
            // Step 2 (2026-05-15) — dual-shape attachment parser.
            //
            // Reads from TWO Firestore fields and merges into BOTH:
            //   • attachmentsList    (List<String>)     — legacy field, URLs only
            //   • attachmentObjList  (List<Attachment>) — rich metadata
            //
            // Source fields on the Firestore doc:
            //   1. `attachments` — historical field, typically List<String>
            //      of download URLs. Could also be a Map (legacy variant)
            //      or a single String (very old single-attachment shape).
            //   2. `attachmentObjects` — new field (Step 4 writers onward)
            //      carrying List<Map> with full metadata (name, contentType,
            //      sizeBytes, uploadedBy, uploadedAtMs, downloadUrl,
            //      storagePath).
            //
            // Merge rule: when the same downloadUrl appears in both fields,
            // the rich entry from `attachmentObjects` wins (keeps full
            // metadata). Legacy URL-only entries that don't appear in the
            // rich field are promoted to Attachment(downloadUrl=url, …).
            //
            // Both output lists are populated in parallel so:
            //   • existing UI consumers reading `homework.attachments`
            //     (List<String>) continue rendering exactly as before
            //   • new consumers reading `homework.attachmentObjects`
            //     (List<Attachment>) get full metadata when available
            val attachmentsList = mutableListOf<String>()
            val attachmentObjList = mutableListOf<Attachment>()

            // Parse new rich field first so rich entries win on URL merge.
            val rawAttachmentObjects = data["attachmentObjects"]
            when (rawAttachmentObjects) {
                is List<*> -> rawAttachmentObjects.forEach { raw ->
                    Attachment.fromAny(raw)?.let { attachmentObjList.add(it) }
                }
                // Maps are unexpected at the field level (we expect List<Map>),
                // but tolerate it as a defensive parse.
                is Map<*, *> -> rawAttachmentObjects.values.forEach { raw ->
                    Attachment.fromAny(raw)?.let { attachmentObjList.add(it) }
                }
            }
            val knownUrls = attachmentObjList
                .map { it.downloadUrl }
                .filter { it.isNotBlank() }
                .toMutableSet()

            // Parse legacy `attachments` field — string list, map, or single string.
            val rawAttachments = data["attachments"]
            when (rawAttachments) {
                is List<*> -> rawAttachments.forEach { raw ->
                    when (raw) {
                        is String -> if (raw.isNotBlank()) {
                            attachmentsList.add(raw)
                            if (raw !in knownUrls) {
                                Attachment.fromAny(raw)?.let {
                                    attachmentObjList.add(it)
                                    knownUrls.add(raw)
                                }
                            }
                        }
                        is Map<*, *> -> {
                            // Forward-compat: a future writer may have put
                            // rich Maps into the legacy `attachments` field
                            // instead of `attachmentObjects`. Parse it and
                            // de-dupe by downloadUrl.
                            Attachment.fromAny(raw)?.let { att ->
                                val url = att.downloadUrl
                                if (url.isNotBlank() && url !in knownUrls) {
                                    attachmentObjList.add(att)
                                    knownUrls.add(url)
                                }
                                if (url.isNotBlank()) attachmentsList.add(url)
                            }
                        }
                        else -> raw?.toString()?.let { s ->
                            if (s.isNotBlank()) attachmentsList.add(s)
                        }
                    }
                }
                is Map<*, *> -> rawAttachments.values.forEach { raw ->
                    raw?.toString()?.let { s ->
                        if (s.isNotBlank()) {
                            attachmentsList.add(s)
                            if (s !in knownUrls) {
                                Attachment.fromAny(s)?.let {
                                    attachmentObjList.add(it)
                                    knownUrls.add(s)
                                }
                            }
                        }
                    }
                }
                is String -> if (rawAttachments.isNotBlank()) {
                    attachmentsList.add(rawAttachments)
                    if (rawAttachments !in knownUrls) {
                        Attachment.fromAny(rawAttachments)?.let {
                            attachmentObjList.add(it)
                            knownUrls.add(rawAttachments)
                        }
                    }
                }
            }
            // Also include legacy single-attachment fields.
            val legacyUrl = (data["attachment"] ?: data["Attachment"] ?: data["file_url"] ?: data["url"] ?: "").toString()
            if (legacyUrl.isNotBlank() && legacyUrl !in attachmentsList) {
                attachmentsList.add(0, legacyUrl)
                if (legacyUrl !in knownUrls) {
                    Attachment.fromAny(legacyUrl)?.let {
                        attachmentObjList.add(0, it)
                        knownUrls.add(legacyUrl)
                    }
                }
            }

            // Parse rubric
            val rubricList = mutableListOf<RubricItem>()
            val rawRubric = data["rubric"]
            when (rawRubric) {
                is List<*> -> rawRubric.forEach { entry ->
                    if (entry is Map<*, *>) {
                        rubricList.add(
                            RubricItem(
                                item = (entry["item"] ?: entry["name"] ?: "").toString(),
                                marks = when (val m = entry["marks"] ?: entry["mark"]) {
                                    is Number -> m.toInt()
                                    is String -> m.toIntOrNull() ?: 0
                                    else -> 0
                                }
                            )
                        )
                    }
                }
                is Map<*, *> -> rawRubric.forEach { (k, v) ->
                    val marks = when (v) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull() ?: 0
                        is Map<*, *> -> when (val m = v["marks"]) {
                            is Number -> m.toInt()
                            is String -> m.toIntOrNull() ?: 0
                            else -> 0
                        }
                        else -> 0
                    }
                    rubricList.add(RubricItem(item = k.toString(), marks = marks))
                }
            }

            return Homework(
                homeworkId = homeworkId,
                hwId = homeworkId,
                title = (data["title"] ?: data["Title"] ?: "").toString(),
                description = (data["description"] ?: data["Description"] ?: data["content"] ?: "").toString(),
                subject = (data["subject"] ?: data["Subject"] ?: "").toString(),
                teacherName = (data["teacher_name"] ?: data["teacherName"] ?: data["Teacher"] ?: "").toString(),
                teacherId = (data["teacher_id"] ?: data["teacherId"] ?: "").toString(),
                date = (data["date"] ?: data["Date"] ?: "").toString(),
                dueDate = (data["due_date"] ?: data["dueDate"] ?: data["Due_date"] ?: "").toString(),
                timestamp = when (val ts = data["timestamp"] ?: data["Timestamp"] ?: data["createdAt"]) {
                    is Number -> ts.toLong()
                    is String -> ts.toLongOrNull() ?: 0L
                    else -> 0L
                },
                attachmentUrl = legacyUrl,
                attachmentName = (data["attachment_name"] ?: data["file_name"] ?: "").toString(),
                studentStatus = "pending", // Set by HomeworkStatus lookup, not from homework data
                rawData = data,
                priority = (data["priority"] ?: data["Priority"] ?: "").toString(),
                questions = when (val q = data["questions"] ?: data["Questions"] ?: data["question_count"]) {
                    is Number -> q.toInt()
                    is String -> q.toIntOrNull() ?: 0
                    else -> 0
                },
                estimatedTime = (data["estimated_time"] ?: data["estimatedTime"] ?: data["est_time"] ?: "").toString(),
                attachments = attachmentsList,
                attachmentObjects = attachmentObjList,
                rubric = rubricList,
                score = when (val s = data["score"] ?: data["Score"]) {
                    is Number -> s.toInt()
                    is String -> s.toIntOrNull() ?: -1
                    else -> -1
                },
                totalMarks = when (val t = data["total_marks"] ?: data["totalMarks"] ?: data["Total"]) {
                    is Number -> t.toInt()
                    is String -> t.toIntOrNull() ?: 10
                    else -> 10
                },
                grade = (data["grade"] ?: data["Grade"] ?: "").toString(),
                feedback = (data["feedback"] ?: data["Feedback"] ?: data["teacher_feedback"] ?: "").toString()
            )
        }
    }
}

data class RubricItem(val item: String = "", val marks: Int = 0)
