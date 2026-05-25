package com.schoolsync.parent.data.model

/**
 * Rich attachment metadata for homework attachments (and forward-compat
 * for submission attachments in a future phase).
 *
 * Introduced 2026-05-15 for the Homework Attachment Workstream Phase 1
 * Step 2 dual-shape backward compatibility pass. The Parent app historic-
 * ally read homework's `attachments` field as a `List<String>` of
 * download URLs (see Homework.kt:31). New writers (Teacher app, Step 4)
 * will emit `attachmentObjects: List<Map>` with full metadata. This data
 * class plus the [fromAny] factory below let the Parent app parse either
 * shape without breaking on legacy docs.
 *
 * The legacy `Homework.attachments: List<String>` field is preserved so
 * pre-Step-2 UI code (HomeworkScreen.kt:863-933) continues to render
 * without modification. New consumers wanting full metadata read the
 * `Homework.attachmentObjects: List<Attachment>` field added in this step.
 *
 * Finding #30 still applies: if/when a click handler is wired to open
 * these attachments, URL host + scheme validation MUST be added at the
 * click-handler call site, regardless of which field the URL came from.
 */
data class Attachment(
    val name: String = "",            // sanitized display filename
    val storagePath: String = "",     // for cleanup / audit / orphan detection
    val downloadUrl: String = "",     // for display + open (after validation)
    val contentType: String = "",     // MIME type (image/jpeg, application/pdf, …)
    val sizeBytes: Long = 0L,
    val uploadedBy: String = "",      // Firebase Auth uid of uploader
    val uploadedAtMs: Long = 0L       // epoch milliseconds
) {
    companion object {
        /**
         * Construct an Attachment from a raw Firestore value of either
         * shape:
         *   • String — legacy shape, value is treated as the download URL;
         *              other fields default to blank
         *   • Map<*, *> — new shape, with full metadata fields. Accepts
         *                 both camelCase (downloadUrl, storagePath, …) and
         *                 snake_case (download_url, storage_path, …) keys
         *                 so this parser is forward-compatible with any
         *                 future server-side writer that uses snake_case.
         *
         * Returns null when the input is unparseable (e.g. a Map with
         * neither downloadUrl nor storagePath, a blank String, or an
         * unsupported type). Caller is responsible for filtering nulls.
         */
        fun fromAny(raw: Any?): Attachment? = when (raw) {
            is String -> {
                if (raw.isBlank()) null
                else Attachment(downloadUrl = raw)
            }
            is Map<*, *> -> {
                val url = (raw["downloadUrl"] ?: raw["download_url"] ?: raw["url"] ?: "").toString()
                val path = (raw["storagePath"] ?: raw["storage_path"] ?: "").toString()
                if (url.isBlank() && path.isBlank()) {
                    null  // unusable — no way to display or clean up
                } else {
                    Attachment(
                        name        = (raw["name"] ?: raw["file_name"] ?: "").toString(),
                        storagePath = path,
                        downloadUrl = url,
                        contentType = (raw["contentType"] ?: raw["content_type"] ?: raw["mime"] ?: "").toString(),
                        sizeBytes   = when (val s = raw["sizeBytes"] ?: raw["size_bytes"] ?: raw["size"]) {
                            is Number -> s.toLong()
                            is String -> s.toLongOrNull() ?: 0L
                            else -> 0L
                        },
                        uploadedBy  = (raw["uploadedBy"] ?: raw["uploaded_by"] ?: "").toString(),
                        uploadedAtMs = parseEpochMs(
                            raw["uploadedAtMs"] ?: raw["uploadedAt"] ?: raw["uploaded_at"]
                        )
                    )
                }
            }
            else -> null
        }

        /**
         * Best-effort epoch-millis extraction supporting:
         *   • Long / Int (already epoch ms)
         *   • Numeric String
         *   • Firestore Timestamp serialized as {seconds, nanoseconds} or
         *     {_seconds, _nanoseconds} (REST shape)
         *   • com.google.firebase.Timestamp object (toDate().time)
         */
        private fun parseEpochMs(raw: Any?): Long = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 0L
            is com.google.firebase.Timestamp -> raw.toDate().time
            is Map<*, *> -> {
                val sec = when (val s = raw["_seconds"] ?: raw["seconds"]) {
                    is Number -> s.toLong()
                    else -> 0L
                }
                sec * 1000L
            }
            else -> 0L
        }
    }
}
