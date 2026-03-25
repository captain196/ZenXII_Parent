package com.schoolsync.parent.ui.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.model.TeacherStoryGroup
import com.schoolsync.parent.data.repository.StoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoryUiState(
    val isLoading: Boolean = true,
    val storyGroups: List<TeacherStoryGroup> = emptyList()
)

@HiltViewModel
class StoryViewModel @Inject constructor(
    private val storyRepository: StoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    init {
        loadStories()
    }

    fun loadStories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val groups = storyRepository.getAllActiveStories()
                _uiState.update { it.copy(isLoading = false, storyGroups = groups) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun markStoryViewed(storyId: String) {
        viewModelScope.launch {
            storyRepository.markAsViewed(storyId)
            // Refresh to update viewed status
            try {
                val groups = storyRepository.getAllActiveStories()
                _uiState.update { it.copy(storyGroups = groups) }
            } catch (_: Exception) { }
        }
    }
}
