package com.tingbili.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.repo.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StatsViewModel(private val library: LibraryRepository) : ViewModel() {
    data class UiState(
        val totalMs: Long = 0L,
        val monthlyCount: Int = 0,
        val totalCount: Int = 0
    )
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun load() = viewModelScope.launch {
        val all = library.all()
        _state.value = UiState(
            totalMs = all.sumOf { it.progressMs.coerceAtLeast(0L) },
            monthlyCount = library.monthlyPlayedCount(),
            totalCount = all.size
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                StatsViewModel(app.container.libraryRepo)
            }
        }
    }
}