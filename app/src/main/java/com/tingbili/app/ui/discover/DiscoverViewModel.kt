package com.tingbili.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.data.local.SettingsStore
import com.tingbili.app.data.repo.SearchRepository
import com.tingbili.app.util.ErrorBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 发现页的分类：label 给用户看，keywords 是丢给搜索的检索词（可以多个 —— 一个分类
 * 结果太少时，就用同义词/大 IP 名多搜几路再合起来）。
 */
data class DiscoverCategory(val label: String, val keywords: List<String>)

/** 同一条内容的去重键（与列表 key 保持一致） */
fun SearchItem.stableKey(): String = bvid.ifBlank { "audio:$aid" }

/** 单个分类页的状态：自己滚自己的，互不干扰 */
data class CategoryFeed(
    val items: List<SearchItem> = emptyList(),
    val loading: Boolean = true,
    val endReached: Boolean = false,
    val page: Int = 0
)

class DiscoverViewModel(
    private val searchRepo: SearchRepository,
    private val settings: SettingsStore
) : ViewModel() {

    /**
     * 类型：听什么品类。
     * 「播客」是冷门词、单搜结果太少，所以把播客向的同义词（对话/对谈/视频播客）
     * 和 B 站几个长音频大 IP 一起并进来，多路搜索后合并——用户看到的还是「播客」一个标签。
     */
    val categories: List<DiscoverCategory> = listOf(
        DiscoverCategory("有声书", listOf("有声书 全集")),
        DiscoverCategory(
            "播客",
            listOf(
                "播客 电台",
                "对话 播客",
                "对谈 播客",
                "视频播客",
                "陈鲁豫 慢谈",
                "窦文涛"
            )
        ),
        DiscoverCategory("评书", listOf("评书 全集")),
        DiscoverCategory("相声", listOf("相声 全集")),
        DiscoverCategory(
            "名著",
            listOf(
                "名著 精读",
                "红楼梦 精读",
                "三国演义 精读",
                "西游记 精读",
                "水浒传 精读",
                "百年孤独 精读"
            )
        )
    )

    private val _feeds = MutableStateFlow(
        categories.associate { it.label to CategoryFeed() }
    )
    val feeds: StateFlow<Map<String, CategoryFeed>> = _feeds

    /** 已经发起过首页请求的分类，避免滑动来回重复拉 */
    private val started = mutableSetOf<String>()

    private val keywords = settings.keywords
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** 该分类页第一次露面时调用；失败后由 retry 重新进入 */
    fun ensureLoaded(category: DiscoverCategory) {
        if (!started.add(category.label)) return
        load(category, 1)
    }

    /** 滚到底自动续页 */
    fun loadMore(category: DiscoverCategory) {
        val feed = _feeds.value[category.label] ?: return
        if (feed.loading || feed.endReached) return
        load(category, feed.page + 1)
    }

    fun retry(category: DiscoverCategory) {
        val feed = _feeds.value[category.label] ?: return
        if (feed.loading) return
        load(category, if (feed.items.isEmpty()) 1 else feed.page + 1)
    }

    /**
     * 「换一批」：清掉当前分类的内容，从**上一批的下一页**继续拉。
     *
     * 注意不能实现成"重新拉第 1 页" —— 同一个检索词的第 1 页永远返回同样那批内容，
     * 点下去等于什么都没变。用户点这个按钮的意图就是"给我没见过的"。
     */
    fun shuffle(category: DiscoverCategory) {
        val feed = _feeds.value[category.label] ?: return
        if (feed.loading) return
        val nextPage = (feed.page + 1).coerceAtLeast(2)
        update(category.label) { it.copy(items = emptyList(), endReached = false, loading = false) }
        load(category, nextPage, replace = true)
    }

    private fun update(label: String, transform: (CategoryFeed) -> CategoryFeed) {
        _feeds.value = _feeds.value.toMutableMap().apply {
            this[label] = transform(this[label] ?: CategoryFeed())
        }
    }

    /**
     * 一个分类可能带多个检索词：并行走完再合并。
     * 合并顺序按"轮转交错"而不是一路接一路，免得同一个词的结果扎堆在前半屏。
     */
    private fun load(category: DiscoverCategory, page: Int, replace: Boolean = false) {
        val label = category.label
        update(label) { it.copy(loading = true) }
        viewModelScope.launch {
            val keywordsNow = keywords.value
            val results = category.keywords
                .map { kw ->
                    async(Dispatchers.IO) {
                        runCatching { searchRepo.search(kw, "video", page, keywordsNow, false) }
                    }
                }
                .awaitAll()
            val lists = results.mapNotNull { it.getOrNull() }.filter { it.isNotEmpty() }

            if (lists.isEmpty()) {
                update(label) { it.copy(loading = false) }
                val err = results.firstNotNullOfOrNull { it.exceptionOrNull() }
                if (err != null) {
                    ErrorBus.post(
                        message = "没拉到「$label」：${err.message ?: "网络异常"}",
                        retry = { retry(category) }
                    )
                }
                return@launch
            }

            val batch = interleave(lists)
            update(label) { old ->
                val fresh = if (page <= 1 || replace) {
                    batch.distinctBy { it.stableKey() }
                } else {
                    val seen = old.items.map { it.stableKey() }.toSet()
                    batch.filter { it.stableKey() !in seen }.distinctBy { it.stableKey() }
                }
                old.copy(
                    items = if (page <= 1 || replace) fresh else old.items + fresh,
                    loading = false,
                    // 换批是"跳着往后取"，这一批空不等于后面没内容，别把续页锁死
                    endReached = if (replace) false else fresh.isEmpty(),
                    page = page
                )
            }
        }
    }

    /** 轮转交错：a1,b1,c1,a2,b2,c2… 长度不齐时短的那几路就自然跳过 */
    private fun interleave(lists: List<List<SearchItem>>): List<SearchItem> {
        val max = lists.maxOfOrNull { it.size } ?: return emptyList()
        return (0 until max).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                DiscoverViewModel(app.container.searchRepo, app.settingsStore)
            }
        }
    }
}
