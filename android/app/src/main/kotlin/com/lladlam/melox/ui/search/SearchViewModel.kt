package com.lladlam.melox.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lladlam.melox.core.model.SearchSong
import com.lladlam.melox.core.network.MeloXSearchKind
import com.lladlam.melox.core.network.MeloXSearchMediaItem
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.network.NeteaseUniversalSearchClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SearchSong> = emptyList(),
    val universalResults: List<MeloXSearchMediaItem> = emptyList(),
    val searchKind: MeloXSearchKind = MeloXSearchKind.Songs,
    val errorMessage: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchClient: NeteaseSearchClient,
    private val universalClient: NeteaseUniversalSearchClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        debounceSearch(query)
    }

    fun setSearchKind(kind: MeloXSearchKind) {
        _uiState.value = _uiState.value.copy(searchKind = kind)
        if (_uiState.value.query.isNotBlank()) {
            debounceSearch(_uiState.value.query)
        }
    }

    private fun debounceSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                results = emptyList(),
                universalResults = emptyList(),
                isSearching = false,
            )
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            delay(300)
            try {
                val kind = _uiState.value.searchKind
                if (kind == MeloXSearchKind.Songs) {
                    val results = searchClient.searchSongs(query, limit = 30)
                    _uiState.value = _uiState.value.copy(
                        results = results,
                        isSearching = false,
                        errorMessage = null,
                    )
                } else {
                    val results = universalClient.searchMedia(query, kind = kind, limit = 30)
                    _uiState.value = _uiState.value.copy(
                        universalResults = results,
                        isSearching = false,
                        errorMessage = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = e.message ?: "搜索失败",
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = SearchUiState()
    }
}
