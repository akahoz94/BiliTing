package com.tingbili.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.data.local.SettingsStore
import com.tingbili.app.data.repo.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchRepo: SearchRepository,
    private val settings: SettingsStore,
    private val keywordsFlow: Flow<Set<String>>
) : ViewModel() {
    data class UiState(
        val keyword: String = "",
        val type: String = "video",
        val listeningOnly: Boolean = false,
        val results: List<SearchItem> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val page: Int = 1,
        val hasMore: Boolean = true,
        val history: List<String> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var keywords: Set<String> = emptySet()

    init {
        viewModelScope.launch { keywordsFlow.collect { keywords = it } }
        viewModelScope.launch {
            settings.searchHistory.collect { list ->
                _state.value = _state.value.copy(history = list)
            }
        }
    }

    fun onKeywordChange(k: String) { _state.value = _state.value.copy(keyword = k) }
    fun onTypeChange(t: String) { _state.value = _state.value.copy(type = t); search(1) }
    fun onListeningOnlyChange(v: Boolean) { _state.value = _state.value.copy(listeningOnly = v); search(1) }

    fun applyHistory(k: String) {
        _state.value = _state.value.copy(keyword = k)
        search(1)
    }

    fun clearHistory() = viewModelScope.launch { settings.clearSearchHistory() }

    fun search(page: Int = 1) {
        val s = _state.value
        if (s.keyword.isBlank() || s.loading) return
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            settings.pushSearchHistory(s.keyword)
            runCatching {
                searchRepo.search(s.keyword, s.type, page, keywords, s.listeningOnly)
            }.onSuccess { list ->
                val items = if (page == 1) list else _state.value.results + list
                _state.value = _state.value.copy(
                    loading = false, results = items, page = page,
                    hasMore = list.size >= 30
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun loadMore() { val s = _state.value; if (s.hasMore && !s.loading) search(s.page + 1) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                SearchViewModel(app.container.searchRepo, app.settingsStore, app.settingsStore.keywords)
            }
        }
    }
}