package com.schoolsync.parent.data.model

/**
 * Three-state wrapper for realtime Firestore data so the UI can
 * distinguish between:
 *   - Loading         (listener attaching, no data yet)
 *   - Data(payload)   (snapshot arrived, even if empty)
 *   - Error(cause)    (snapshot failed — show banner + retry)
 *
 * Replaces the prior "emit emptyList() on failure" pattern, which hid
 * errors behind a fake empty state (Test 2 CRITICAL FAIL scenario).
 */
sealed class FeeDataState<out T> {
    data object Loading : FeeDataState<Nothing>()

    data class Data<T>(val value: T) : FeeDataState<T>()

    data class Error(
        val cause: Throwable,
        val message: String = cause.message ?: "Failed to load.",
    ) : FeeDataState<Nothing>()

    /** True for Data(_). Convenience for callers that want to treat
     *  Loading and Error equivalently (both = "no data yet"). */
    val hasData: Boolean get() = this is Data
}

/** Safe unwrap — returns null for Loading / Error. */
fun <T> FeeDataState<T>.valueOrNull(): T? = (this as? FeeDataState.Data<T>)?.value

/** Returns the enclosed list, or empty for Loading / Error.
 *  Avoids repeating `valueOrNull() ?: emptyList()` at call sites. */
fun <T> FeeDataState<List<T>>.valueOrEmpty(): List<T> =
    (this as? FeeDataState.Data<List<T>>)?.value ?: emptyList()
