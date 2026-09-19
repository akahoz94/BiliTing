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
import com.tingbili.app.data.local.encodeTags
import com.tingbili.app.data.local.tagSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val dao: BookRecordDao,
    private val settings: SettingsStore
) : ViewModel() {

    private val _selectedTag = MutableStateFlow("")
    val selectedTag: StateFlow<String> = _selectedTag

    /**
     * ⚠️ 下面几个 StateFlow 一律用 Eagerly，不要改成 WhileSubscribed：
     * 上移/下移、标签聚合都要直接读 `.value`，一旦没有活跃订阅者，
     * WhileSubscribed 会让它们永远是空列表，表现就是"点了一下毫无反应"
     * （和 Settings 里 webdavPass 曾经 401 是同一个坑）。
     */
    /** 全量听单（不受标签筛选影响）；UI 需要按 id 拿"当前最新"的记录时会用到 */
    val allFavorites: StateFlow<List<BookRecord>> = dao.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 当前筛选后的视图。
     *
     * 标签在 DB 里是**分号分隔的多标签串**，SQL 没法精确匹配单个标签
     * （`LIKE '%武侠%'` 会把"武侠小说"也捞进来），所以全量取出后在内存里过滤。
     * 听单量级很小，这点开销可以忽略。
     */
    val favorites: StateFlow<List<BookRecord>> = combine(allFavorites, _selectedTag) { list, tag ->
        if (tag.isBlank()) list else list.filter { tag in it.tagSet() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 从全量听单聚合出来的标签集合（一条记录挂多个标签会全部展开） */
    val tags: StateFlow<List<String>> = allFavorites
        .map { list -> list.flatMap { it.tagSet() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val archivedFavorites: StateFlow<List<BookRecord>> = dao.observeArchivedFavorites()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectTag(tag: String) { _selectedTag.value = tag }

    /** 覆盖式写入标签；传空列表等于清空该书的全部标签 */
    fun setTags(id: String, tags: List<String>) = viewModelScope.launch {
        dao.setTags(id, encodeTags(tags))
    }

    /** 勾选 / 取消某一个标签（多标签的核心入口） */
    fun toggleTag(id: String, tag: String) = viewModelScope.launch {
        val cur = allFavorites.value.firstOrNull { it.id == id }?.tagSet() ?: emptyList()
        val next = if (tag in cur) cur - tag else cur + tag
        dao.setTags(id, encodeTags(next))
    }

    fun setFinished(id: String, finished: Boolean) = viewModelScope.launch {
        dao.setFinished(id, finished)
    }

    /**
     * 上移 / 下移。
     * 参数是 **书 id** 而不是下标：列表随时可能因数据变化而重排，
     * 传进来的下标在协程真正执行时可能已经指向另一本书了。
     */
    fun moveUp(id: String) = viewModelScope.launch { move(id, -1) }
    fun moveDown(id: String) = viewModelScope.launch { move(id, +1) }

    private suspend fun move(id: String, delta: Int) {
        val visible = favorites.value
        val i = visible.indexOfFirst { it.id == id }
        if (i < 0) return
        val j = i + delta
        if (j < 0 || j >= visible.size) return
        swapOrder(visible[i].id, visible[j].id)
    }

    /**
     * 在全量听单里交换两条记录的位置，然后整体重编号。
     *
     * 两个细节都是踩过坑才有的：
     *  1. 基于**全量**而不是当前筛选视图 —— 筛选视图里相邻的两条在全量里不一定相邻，
     *     只重排筛选结果会让它们的 sortOrder 撞在一起，在"全部"视图里错位；
     *  2. **整体重编号**而不是"交换两条的 sortOrder" —— 历史数据里所有记录的 sortOrder
     *     都是默认值 0，交换 0 和 0 等于什么都没做（"点了没反应"的根因）。
     */
    private suspend fun swapOrder(aId: String, bId: String) {
        val ids = dao.favoritesNow().map { it.id }.toMutableList()
        val a = ids.indexOf(aId)
        val b = ids.indexOf(bId)
        if (a < 0 || b < 0) return
        val tmp = ids[a]; ids[a] = ids[b]; ids[b] = tmp
        dao.reorder(ids)
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                PlaylistViewModel(app.container.dao, app.settingsStore)
            }
        }
    }
}
