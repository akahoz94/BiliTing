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

    /** 已归档（听完）的听单项 */
    val archivedFavorites: StateFlow<List<BookRecord>> = dao.observeArchivedFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 标为听完/取消归档 */
    fun setFinished(id: String, finished: Boolean) = viewModelScope.launch {
        dao.setFinished(id, finished)
    }

    /** 听单里上移一项（交换 sortOrder） */
    fun moveUp(index: Int) = viewModelScope.launch {
        val list = favorites.value
        if (index <= 0 || index >= list.size) return@launch
        val cur = list[index]
        val prev = list[index - 1]
        dao.setSortOrder(prev.id, cur.sortOrder)
        dao.setSortOrder(cur.id, prev.sortOrder)
    }

    /** 听单里下移一项 */
    fun moveDown(index: Int) = viewModelScope.launch {
        val list = favorites.value
        if (index < 0 || index >= list.size - 1) return@launch
        val cur = list[index]
        val next = list[index + 1]
        dao.setSortOrder(next.id, cur.sortOrder)
        dao.setSortOrder(cur.id, next.sortOrder)
    }

    /**
     * 从听单里移除（取消收藏 = is_favorite=false）。
     * 记录本身保留在 book_records 表里，所以"历史"页和"继续播放"进度不会被破坏。
     */
    fun unfavorite(id: String) = viewModelScope.launch {
        dao.setFavorite(id, false, 0L)
    }

    /** 彻底删除（听单 + 历史同时移除） */
    fun delete(id: String) = viewModelScope.launch {
        dao.delete(id)
    }

    /**
     * 当前正在播放的 BookRecord（用于听单底部迷你播放器）。
     * 从 PlayerHolder.record 拉取 —— 当 record != null 时迷你播放器显示。
     */
    val continueTarget: StateFlow<BookRecord?> = kotlinx.coroutines.flow.flow {
        val app = BiliTingApplication.get()
        app?.playerHolder?.record?.collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * 当前是否在播放（控制迷你播放器按钮图标 ▶/⏸）。
     * 从 PlayerHolder.exoPlayer.isPlaying 拉取。
     */
    val isPlaying: StateFlow<Boolean> = kotlinx.coroutines.flow.flow {
        while (true) {
            val holder = BiliTingApplication.get()?.playerHolder
            emit(holder?.isPlaying() ?: false)
            kotlinx.coroutines.delay(500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * 迷你播放器展开状态 —— 真实是否展开由屏幕空间决定；
     * 当 continueTarget != null 时为 true。当 PlayerScreen 全屏时由 NavHost 整体控制。
     * 这里暴露一个占位（保留扩展位）。
     */
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