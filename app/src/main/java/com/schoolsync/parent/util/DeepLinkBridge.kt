package com.schoolsync.parent.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 8: one-shot deep-link channel for FCM-tapped notifications.
 *
 * FCMService (push receiver) can't touch Compose navigation directly.
 * Instead, MainActivity reads the tapped notification's intent extras
 * and calls [publish] with the target screen. The nav graph observes
 * [pending] and, once the user is on the post-login main scaffold,
 * navigates there and clears the flag.
 *
 * Targets are single strings matching `Route.route` names (e.g.
 * "fees", "messages") — keeps the surface area small and typo-obvious.
 */
object DeepLinkBridge {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Push a new target (e.g. from MainActivity.onCreate / onNewIntent). */
    fun publish(route: String) {
        _pending.value = route
    }

    /** Nav graph calls this after handling the route so it only fires once. */
    fun consume() {
        _pending.value = null
    }
}
