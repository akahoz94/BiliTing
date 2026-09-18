package com.tingbili.app.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.data.local.BookRecordDao
import com.tingbili.app.data.local.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val dao: BookRecordDao,
    private val settings: SettingsStore
) : ViewModel() {
    val favorites: StateFlow<List<BookRecord>> = dao.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedFavorites: StateFlow<List<BookRecord>> = dao.observeArchivedFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFinished(id: String, finished: Boolean) = viewModelScope.launch {
        dao.setFinished(id, finished)
    }

    fun moveUp(index: Int) = viewModelScope.launch {
        val list = favorites.value
        if (index <= 0 || index >= list.size) return@launch
        val cur = list[index]
        val prev = list[index - 1]
        dao.setSortOrder(prev.id, cur.sortOrder)
        dao.setSortOrder(cur.id, prev.sortOrder)
    }

    fun moveDown(index: Int) = viewModelScope.launch {
        val list = favorites.value
        if (index < 0 || index >= list.size - 1) return@launch
        val cur = list[index]
        val next = list[index + 1]
        dao.setSortOrder(next.id, cur.sortOrder)
        dao.setSortOrder(cur.id, next.sortOrder)
    }

    fun unfavorite(id: String) = viewModelScope.launch {
        dao.setFavorite(id, false, 0L)
    }

    fun delete(id: String) = viewModelScope.launch {
        dao.delete(id)
    }

    fun clearArchived() = viewModelScope.launch {
        for (item in archivedFavorites.value) {
            dao.setFavorite(item.id, false, 0L)
        }
    }

    val continueTarget: StateFlow<BookRecord?> = kotlinx.coroutines.flow.flow {
        val app = BiliTingApplication.get()
        app?.playerHolder?.record?.collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPlaying: StateFlow<Boolean> = kotlinx.coroutines.flow.flow {
        while (true) {
            val holder = BiliTingApplication.get()?.playerHolder
            emit(holder?.isPlaying() ?: false)
            kotlinx.coroutines.delay(500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _miniExpanded = MutableStateFlow(true)
    val miniExpanded: StateFlow<Boolean> = _miniExpanded.asStateFlow()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                PlaylistViewModel(app.container.dao, app.settingsStore)
            }
        }
    }
}