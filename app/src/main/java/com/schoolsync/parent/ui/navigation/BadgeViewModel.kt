package com.schoolsync.parent.ui.navigation

import androidx.lifecycle.ViewModel
import com.schoolsync.parent.util.BadgeBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Tiny VM that exposes the app-wide [BadgeBus] flow to the bottom bar.
 * Used purely as a Compose-friendly read handle — does not own any state.
 */
@HiltViewModel
class BadgeViewModel @Inject constructor(
    bus: BadgeBus,
) : ViewModel() {
    val counts: StateFlow<Map<String, Int>> = bus.counts
}
