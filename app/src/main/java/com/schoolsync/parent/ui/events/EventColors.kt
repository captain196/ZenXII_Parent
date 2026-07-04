package com.schoolsync.parent.ui.events

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.schoolsync.parent.ui.theme.AppColors

/**
 * Shared event category / status color helpers.
 *
 * Previously duplicated verbatim between EventsScreen and EventDetailScreen;
 * consolidated here so the category → color and status → color mappings stay
 * in one place.
 */

internal data class CategoryColorSet(
    val main: Color,
    val gradStart: Color,
    val gradEnd: Color
)

/** Single accent color for a category (used by the detail screen pills). */
@Composable
internal fun getCategoryColor(category: String, c: AppColors): Color {
    return when (category.lowercase()) {
        "cultural" -> c.purple
        "sports" -> c.success
        "academic" -> c.info
        "exam" -> c.error
        "holiday" -> c.warning
        else -> c.accent
    }
}

/** Category color + gradient endpoints (used by the list-card accent bar). */
@Composable
internal fun getCategoryColors(category: String, c: AppColors): CategoryColorSet {
    return when (category.lowercase()) {
        "cultural" -> CategoryColorSet(c.purple, c.banner3Start, c.banner3End)
        "sports" -> CategoryColorSet(c.success, c.banner2Start, c.banner2End)
        "academic" -> CategoryColorSet(c.info, c.banner1Start, c.banner1End)
        "exam" -> CategoryColorSet(c.error, c.error.copy(alpha = 0.5f), c.error.copy(alpha = 0.2f))
        "holiday" -> CategoryColorSet(c.warning, c.warning.copy(alpha = 0.5f), c.warning.copy(alpha = 0.2f))
        // PTM = synthesized event from `ptmEvents`. Distinct dark-blue tint
        // so the row visibly reads as a meeting and not a regular event.
        "ptm" -> CategoryColorSet(Color(0xFF1565C0), Color(0xFF1565C0).copy(alpha = 0.5f), Color(0xFF1565C0).copy(alpha = 0.2f))
        else -> CategoryColorSet(c.accent, c.banner1Start, c.banner1End)
    }
}

@Composable
internal fun getStatusColor(status: String, c: AppColors): Color {
    return when (status.lowercase()) {
        "scheduled" -> c.info
        "ongoing", "active" -> c.success
        "completed", "finished" -> c.textTertiary
        "cancelled" -> c.error
        "postponed" -> c.warning
        else -> c.textSecondary
    }
}
