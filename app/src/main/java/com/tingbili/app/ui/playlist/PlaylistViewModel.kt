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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlaylistViewModel(
    private val dao: BookRecordDao,
    private val settings: SettingsStore
) : ViewModel() {

    private val _selectedTag = MutableStateFlow("")
    val selectedTag: StateFlow<String> = _selectedTag

    /**
     * 标签的「意图快照」：id -> 目标标签列表。
     *
     * 为什么必须有它（2026-09-19 模拟器实测复现的 bug）：
     * 原先 `toggleTag` 是「读 allFavorites.value → 算 next → 写库」，而 allFavorites 是
     * Room 的**观察流** —— 写完库到流发射之间隔着几十毫秒（失效通知 + 重新查询 + stateIn）。
     * 用户连点两个 tag chip 时，第二次读到的还是**旧值**，于是把第一次的结果整个覆盖：
     * 表现就是"打两个标签，另一个自动消失"。
     *
     * 现在点击时先同步更新这份草稿（意图），再用草稿去驱动 UI 和落库：
     *  - UI 侧：[allFavorites] 会把草稿叠加上去，所以卡片标签、筛选栏、chip 高亮**立刻**正确；
     *  - 写入侧：落库时读的是草稿的当下最新值，连点也不会互相覆盖。
     * 草稿在「DB 值追上意图」后被自动清掉（见 init），保证 DB 始终是最终真相。
     */
    private val _tagDraft = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    /** DAO 的原始观察流（不带草稿）；用作「草稿是否已经落库」的判断基准 */
    private val dbFavorites: StateFlow<List<BookRecord>> = dao.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 全量听单（不受标签筛选影响），叠加尚未落库的标签草稿。
     * UI 需要按 id 拿"当前最新"的记录时都用它。
     *
     * ⚠️ 一律用 Eagerly，不要改成 WhileSubscribed：
     * 上移/下移、标签聚合都要直接读 `.value`，一旦没有活跃订阅者，
     * WhileSubscribed 会让它们永远是空列表，表现就是"点了一下毫无反应"
     * （和 Settings 里 webdavPass 曾经 401 是同一个坑）。
     */
    val allFavorites: StateFlow<List<BookRecord>> = combine(dbFavorites, _tagDraft) { list, draft ->
        if (draft.isEmpty()) list
        else list.map { r -> draft[r.id]?.let { r.copy(tag = encodeTags(it)) } ?: r }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /** 标签落库的串行锁：见 [scheduleTagWrite] 的说明 */
    private val tagWriteLock = Mutex()

    init {
        // 草稿回收：一旦 DB 里的值追上了意图（或那条记录已经不在听单里），就把草稿摘掉，
        // 让 DB 重新成为唯一真相 —— 否则草稿会一直"压"住云端同步/其他地方写进来的新标签。
        viewModelScope.launch {
            dbFavorites.collect { list ->
                if (_tagDraft.value.isEmpty()) return@collect
                val byId = list.associateBy { it.id }
                _tagDraft.update { draft ->
                    draft.filterNot { (id, tags) ->
                        val db = byId[id]?.tagSet() ?: return@filterNot true
                        db.sorted() == tags.sorted()
                    }
                }
            }
        }
    }

    fun selectTag(tag: String) { _selectedTag.value = tag }

    /** 覆盖式写入标签；传空列表等于清空该书的全部标签 */
    fun setTags(id: String, tags: List<String>) {
        _tagDraft.update { it + (id to tags) }
        scheduleTagWrite(id)
    }

    /** 勾选 / 取消某一个标签（多标签的核心入口） */
    fun toggleTag(id: String, tag: String) {
        val cur = _tagDraft.value[id]
            ?: allFavorites.value.firstOrNull { it.id == id }?.tagSet()
            ?: emptyList()
        val next = if (tag in cur) cur - tag else cur + tag
        _tagDraft.update { it + (id to next) }
        scheduleTagWrite(id)
    }

    /**
     * 落库：加锁串行，且**写入时才读取草稿的当下最新值**。
     *
     * 两个细节都是必需的：
     *  1. 加锁 —— Room 的 UPDATE 跑在自己的线程池上，两个并发写入可能乱序完成，
     *     先发起的那次后到就会把后发起的标签顶掉；
     *  2. 读最新草稿而不是把 next 传进来 —— 即使真的乱序，最后落地的那次写的也是
     *     用户最终的意图，结果依然正确。
     */
    private fun scheduleTagWrite(id: String) = viewModelScope.launch {
        tagWriteLock.withLock {
            val tags = _tagDraft.value[id] ?: return@withLock
            dao.setTags(id, encodeTags(tags))
        }
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
