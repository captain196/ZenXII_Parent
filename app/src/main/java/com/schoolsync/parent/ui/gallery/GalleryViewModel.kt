package com.schoolsync.parent.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.model.GalleryAlbum
import com.schoolsync.parent.data.repository.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val galleryRepository: GalleryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(AlbumDetailUiState())
    val detailState: StateFlow<AlbumDetailUiState> = _detailState.asStateFlow()

    init {
        loadAlbums()
    }

    private fun loadAlbums() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val albums = galleryRepository.getAlbums()
                _uiState.update { it.copy(isLoading = false, albums = albums) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load gallery")
                }
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun loadAlbumDetail(albumId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val album = galleryRepository.getAlbumWithMedia(albumId)
                _detailState.update { it.copy(isLoading = false, album = album) }
            } catch (e: Exception) {
                _detailState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load album")
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val albums = galleryRepository.getAlbums()
                _uiState.update { it.copy(isRefreshing = false, albums = albums) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, errorMessage = e.message) }
            }
        }
    }
}
