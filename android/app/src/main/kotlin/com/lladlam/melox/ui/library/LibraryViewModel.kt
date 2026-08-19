package com.lladlam.melox.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.library.NeteaseLibraryCache
import com.lladlam.melox.core.library.NeteaseLibraryClient
import com.lladlam.melox.core.library.NeteaseLibrarySnapshot
import com.lladlam.melox.core.model.SearchSong
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = false,
    val snapshot: NeteaseLibrarySnapshot? = null,
    val downloadedSongs: List<SearchSong> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryClient: NeteaseLibraryClient,
    private val libraryCache: NeteaseLibraryCache,
    val downloadStore: MeloXDownloadStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        refreshDownloadedSongs()
    }

    fun refreshSnapshot(userId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val snapshot = libraryClient.snapshot(userId)
                _uiState.value = _uiState.value.copy(
                    snapshot = snapshot,
                    isLoading = false,
                    errorMessage = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "加载失败",
                )
            }
        }
    }

    fun refreshDownloadedSongs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloadedSongs = downloadStore.downloadedSongs,
            )
        }
    }
}
