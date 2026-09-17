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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(
    dao: BookRecordDao,
    private val settings: SettingsStore
) : ViewModel() {
    val favorites: StateFlow<List<BookRecord>> = dao.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 最近播放：取收藏中 lastPlayedAt 倒序的前 6 条 */
    val recent: StateFlow<List<BookRecord>> = dao.observeFavorites()
        .let { src ->
            kotlinx.coroutines.flow.flow {
                src.collect { list ->
                    emit(list.sortedByDescending { it.lastPlayedAt }.take(6))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 听单分组 id -> folder（DataStore 键名仍叫 shelf_folders 以兼容旧用户数据） */
    val folders: StateFlow<Map<String, String>> = settings.shelfFoldersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 听单分组模式：0=按文件夹（默认）/ 1=按 UP 主 */
    val groupMode: StateFlow<Int> = settings.playlistGroupMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 当前正在播放的 BookRecord（用于听单顶部"继续播放卡"）。从 PlayerHolder 拉取 */
    val continueTarget: StateFlow<BookRecord?> = kotlinx.coroutines.flow.flow {
        val app = BiliTingApplication.get()
        app?.playerHolder?.record?.collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                PlaylistViewModel(app.container.dao, app.settingsStore)
            }
        }
    }

    fun setGroupMode(v: Int) {
        viewModelScope.launch { settings.setPlaylistGroupMode(v) }
    }
}