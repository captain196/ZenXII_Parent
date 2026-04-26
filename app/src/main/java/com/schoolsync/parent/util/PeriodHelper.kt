package com.schoolsync.parent.util

/**
 * Canonical period -> month label conversion. Mirrors
 * Fee_firestore_txn::periodToMonth on the PHP side.
 *
 * Demand `period` strings carry a trailing year token: "April 2026",
 * "Yearly Fees 2026-27", "May 2026-2027". The previous
 * `period.substringBefore(' ')` chopped multi-word labels — "Yearly Fees"
 * became "Yearly" and never matched a receipt's `feeMonths` (which the
 * server emits as "Yearly Fees"). Result: the success screen reported a
 * Yearly partial as "cleared" and `remainingByMonth` excluded it.
 *
 * Strip ONLY the trailing year token; keep multi-word month labels intact.
 */
private val YEAR_SUFFIX = Regex("""\s+\d{4}(-\d{2,4})?$""")

fun periodToMonth(period: String?): String =
    YEAR_SUFFIX.replace((period ?: "").trim(), "").trim()
