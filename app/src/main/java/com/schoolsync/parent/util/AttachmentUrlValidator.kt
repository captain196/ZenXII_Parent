package com.schoolsync.parent.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Step 5 (2026-05-15) — Homework Attachment Workstream Phase 1.
 *
 * Click-site validator + safe-open helper for homework attachment URLs
 * surfaced in [com.schoolsync.parent.ui.homework.HomeworkScreen].
 *
 * Closes Finding #30: prior to this step, the attachment Row in
 * HomeworkScreen.kt rendered a "Tap to download" affordance with NO
 * click handler — exploit surface was zero. Step 4 (Teacher upload UI)
 * began emitting real, openable URLs, which means a click handler is
 * now required for the user-visible feature, AND that click handler
 * MUST validate before any [Intent.ACTION_VIEW] dispatch.
 *
 * Trust boundary: the URL string is attacker-influenceable. A hostile
 * or compromised teacher account writes a [Homework] doc with an
 * arbitrary `attachments` value. Without these checks, the Parent app
 * would launch ACTION_VIEW for `file://`, `data:`, `javascript:`, an
 * arbitrary http:// URL, or a Firebase Storage URL pointing at another
 * project's bucket — covering SSRF, phishing, and malware-delivery
 * vectors.
 *
 * Allowlist policy:
 *   • Scheme MUST be `https` (case-insensitive). No `http`, no `gs`,
 *     no `file`, no `content`, no `data`, no `javascript`, no `intent`.
 *   • Host MUST be exactly `firebasestorage.googleapis.com`. This is
 *     the canonical Firebase Storage download host across all GCP
 *     projects; bucket isolation is enforced by the URL token. We do
 *     NOT additionally pin to the project bucket here because
 *     (a) the bucket-name segment is in the path, not the host, and
 *     (b) Storage rules already prevent cross-project leakage at the
 *     read-time token-validation layer. If we ever need to tighten
 *     further, add a path-prefix check rather than a host check.
 *
 * Open policy:
 *   • Dispatch via [Intent.ACTION_VIEW] with [Intent.FLAG_ACTIVITY_NEW_TASK]
 *     so the chosen handler launches into its own task stack (we are
 *     not on an Activity context — we get a Composable's LocalContext).
 *   • [ActivityNotFoundException] surfaces "no app available" rather
 *     than crashing.
 *   • Any other exception surfaces a generic "could not open" message
 *     and is logged.
 *
 * Out of scope for Step 5 (deferred to later phases by explicit
 * authorization):
 *   • Inline PDF rendering
 *   • Image preview gallery
 *   • Offline attachment cache
 *   • DownloadManager integration
 *   • Attachment editing / deletion
 *   • Background prefetching
 */
object AttachmentUrlValidator {

    /** Hosts allowed for [Intent.ACTION_VIEW] dispatch. */
    private val ALLOWED_HOSTS = setOf("firebasestorage.googleapis.com")

    /** Outcome of [validate]. Mutually exclusive. */
    sealed class Result {
        object Valid : Result()
        data class Invalid(val reason: Reason, val userMessage: String) : Result()
    }

    /** Why a URL was rejected. Recorded in telemetry; not user-facing. */
    enum class Reason {
        /** URL was null, empty, or whitespace-only. */
        BLANK,
        /** [Uri.parse] threw, or the parsed Uri had no scheme/host. */
        MALFORMED,
        /** Scheme was something other than `https`. */
        NOT_HTTPS,
        /** Host was not in [ALLOWED_HOSTS]. */
        HOST_NOT_ALLOWED,
    }

    /**
     * Pure validation. No side effects, no Intent dispatch — safe to
     * call from any thread, including a Composable.
     *
     * Trims surrounding whitespace before parsing (defends against
     * teachers pasting a URL with a trailing newline, which Uri.parse
     * tolerates but some Intent handlers reject).
     */
    fun validate(rawUrl: String?): Result {
        val url = rawUrl?.trim().orEmpty()
        if (url.isEmpty()) {
            return Result.Invalid(Reason.BLANK, "Attachment link is empty.")
        }

        val uri: Uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return Result.Invalid(Reason.MALFORMED, "Attachment link is not a valid URL.")
        }

        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme.isEmpty()) {
            return Result.Invalid(Reason.MALFORMED, "Attachment link is missing its scheme.")
        }
        if (scheme != "https") {
            return Result.Invalid(Reason.NOT_HTTPS, "Only secure (https) attachments can be opened.")
        }

        val host = uri.host?.lowercase().orEmpty()
        if (host.isEmpty()) {
            return Result.Invalid(Reason.MALFORMED, "Attachment link is missing its host.")
        }
        if (host !in ALLOWED_HOSTS) {
            return Result.Invalid(
                Reason.HOST_NOT_ALLOWED,
                "This attachment link is not from a trusted source."
            )
        }

        return Result.Valid
    }

    /**
     * Validate then launch [Intent.ACTION_VIEW]. Returns the [Result]
     * so the caller can distinguish dispatch vs. rejection in tests
     * or analytics; the user-facing toast is also handled here so the
     * call site stays a one-liner.
     *
     * [fileName] is for telemetry only — never trusted, never echoed
     * into a Uri.
     */
    fun openAttachmentSafely(
        context: Context,
        rawUrl: String?,
        fileName: String
    ): Result {
        val result = validate(rawUrl)
        when (result) {
            is Result.Invalid -> {
                debugLog(
                    "ACC_HW_ATTACHMENT_OPEN_REJECTED reason=${result.reason} " +
                            "fileName=$fileName urlLen=${rawUrl?.length ?: 0}"
                )
                context.showToast(result.userMessage)
            }
            Result.Valid -> {
                val safeUrl = rawUrl!!.trim()
                debugLog("ACC_HW_ATTACHMENT_OPEN_START fileName=$fileName")
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    debugLog("ACC_HW_ATTACHMENT_OPEN_OK fileName=$fileName")
                } catch (_: ActivityNotFoundException) {
                    debugLog("ACC_HW_ATTACHMENT_OPEN_NO_HANDLER fileName=$fileName")
                    context.showToast("No app available to open this attachment.")
                } catch (e: Exception) {
                    debugLog(
                        "ACC_HW_ATTACHMENT_OPEN_FAIL fileName=$fileName " +
                                "err=${e.javaClass.simpleName}:${e.message}"
                    )
                    context.showToast("Could not open this attachment.")
                }
            }
        }
        return result
    }
}
