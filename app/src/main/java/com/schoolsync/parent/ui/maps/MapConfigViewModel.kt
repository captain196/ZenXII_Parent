package com.schoolsync.parent.ui.maps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.maps.MapProviderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Active provider + its public credentials for this surface. */
data class MapConfigState(
    val providerId: String = "",
    val credentials: Map<String, String> = emptyMap(),
    val loaded: Boolean = false,
)

/**
 * Exposes the centralized map configuration to the UI.
 *
 * Deliberately SEPARATE from `MyBusViewModel`. That class owns the F13.1 GPS
 * listener, which the Phase B brief requires to remain exactly as shipped —
 * so rather than bolt provider state onto it and have to argue about whether
 * the GPS path was disturbed, the map configuration lives here and the GPS
 * ViewModel is not edited at all.
 *
 * It also scopes correctly: this concern is "which renderer", which is shared
 * by every future map screen, while `MyBusViewModel` is about one child's bus.
 */
@HiltViewModel
class MapConfigViewModel @Inject constructor(
    private val manager: MapProviderManager,
) : ViewModel() {

    private val _state = MutableStateFlow(MapConfigState())
    val state: StateFlow<MapConfigState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // `providerFor` is distinct-until-changed, so this collector wakes
            // only when the ACTIVE provider changes — not on every unrelated
            // configuration edit. That is what stops a live map being torn down
            // and rebuilt for someone else's zoom tweak.
            // No surface argument: the server pins it to `parent`, so passing
            // one here would only look like it could be changed.
            manager.providerFor().collect { provider ->
                _state.value = MapConfigState(
                    providerId = provider,
                    credentials = if (provider.isBlank()) emptyMap() else manager.credentialsFor(provider),
                    loaded = true,
                )
            }
        }
    }
}
