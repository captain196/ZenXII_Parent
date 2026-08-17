package com.schoolsync.parent.ui.gallery

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.schoolsync.parent.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.model.GalleryAlbum
import com.schoolsync.parent.data.repository.firestore.GalleryFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryUiState(
    val isLoading: Boolean = true,
    val albums: List<GalleryAlbum> = emptyList(),
    val selectedCategory: String = "all",
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
) {
    val categories: List<String>
        get() {
            val cats = albums.map { it.category.lowercase().ifBlank { "general" } }.distinct().sorted()
            return listOf("all") + cats
        }

    val filteredAlbums: List<GalleryAlbum>
        get() = if (selectedCategory == "all") albums
        else albums.filter { it.category.equals(selectedCategory, ignoreCase = true) }
}

data class AlbumDetailUiState(
    val isLoading: Boolean = true,
    val album: GalleryAlbum? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    
    @ApplicationContext private val appContext: Context,private val galleryRepository: GalleryFirestoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(AlbumDetailUiState())
    val detailState: StateFlow<AlbumDetailUiState> = _detailState.asStateFlow()

    // Live albums subscription. Held so retry/pull-to-refresh can re-subscribe.
    private var albumsJob: Job? = null

    init {
        observeAlbums()
    }

    /**
     * Subscribe to the live albums feed so newly published albums appear
     * without a manual refresh. [showLoader] is false when a pull-to-refresh
     * spinner is already visible (avoid flashing the full-screen loader over
     * existing content).
     */
    private fun observeAlbums(showLoader: Boolean = true) {
        albumsJob?.cancel()
        albumsJob = viewModelScope.launch {
            if (showLoader) _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            galleryRepository.observeAlbums().collect { result ->
                result.fold(
                    onSuccess = { albums ->
                        _uiState.update { it.copy(isLoading = false, albums = albums, errorMessage = null) }
                    },
                    onFailure = { e ->
                        android.util.Log.e("GalleryVM", "observeAlbums failed", e)
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = e.message ?: appContext.getString(R.string.gal_load_failed_plain))
                        }
                    }
                )
            }
        }
    }

    /** Retry after a failed load (error-state button). */
    fun retry() = observeAlbums()

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun loadAlbumDetail(albumId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true, errorMessage = null) }
            galleryRepository.getAlbumWithMedia(albumId).fold(
                onSuccess = { album ->
                    _detailState.update { it.copy(isLoading = false, album = album, errorMessage = null) }
                },
                onFailure = { e ->
                    android.util.Log.e("GalleryVM", "loadAlbumDetail failed", e)
                    _detailState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: appContext.getString(R.string.gal_album_load_failed_plain))
                    }
                }
            )
        }
    }

    /** Re-subscribe with the full-screen loader. */
    fun refresh() = observeAlbums(showLoader = true)

    /**
     * Pull-to-refresh. The live listener already keeps the list fresh, so this
     * just re-subscribes (recovering from any prior listener error) while
     * showing the refresh spinner for a minimum duration.
     */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            // Re-subscribe without the full-screen loader (spinner already shown).
            observeAlbums(showLoader = false)
            kotlinx.coroutines.delay(600L)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
