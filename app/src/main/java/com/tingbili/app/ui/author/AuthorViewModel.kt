package com.tingbili.app.ui.author

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.api.AuthorApi
import com.tingbili.app.data.api.dto.AuthorVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthorUiState(
    val videos: List<AuthorVideo> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false
)

class AuthorViewModel(
    private val api: AuthorApi,
    private val mid: Long
) : ViewModel() {
    private val _state = MutableStateFlow(AuthorUiState())
    val state: StateFlow<AuthorUiState> = _state
    private var page = 0
    private var autoLoaded = false

    fun loadFirst() {
        if (autoLoaded) return
        autoLoaded = true
        loadNext()
    }

    fun loadNext() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        _state.value = if (page == 0) s.copy(loading = true) else s.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val (list, total) = api.videos(mid, page + 1)
                page++
                val all = _state.value.videos + list
                val knownTotal = if (total > 0) total else _state.value.total
                _state.value = _state.value.copy(
                    videos = all,
                    total = knownTotal,
                    loading = false,
                    loadingMore = false,
                    error = null,
                    endReached = list.isEmpty() || knownTotal > 0 && all.size >= knownTotal
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    error = "加载失败：${e.message ?: "网络错误"}"
                )
            }
        }
    }

    companion object {
        fun factory(mid: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                AuthorViewModel(app.container.authorApi, mid)
            }
        }
    }
}