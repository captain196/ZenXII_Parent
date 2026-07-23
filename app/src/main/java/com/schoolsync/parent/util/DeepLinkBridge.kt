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
    /**
     * A pending deep-link target. [arg] optionally carries a resource id from
     * the push payload (e.g. the tapped notice's id) so the destination screen
     * can auto-select/scroll to it. Null [arg] ⇒ plain tab navigation.
     */
    data class Target(val route: String, val arg: String? = null)

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending.asStateFlow()

    /** Push a new target (e.g. from MainActivity.onCreate / onNewIntent). */
    fun publish(route: String, arg: String? = null) {
        _pending.value = Target(route, arg)
    }

    /** Nav graph calls this after handling the route so it only fires once. */
    fun consume() {
        _pending.value = null
    }
}
