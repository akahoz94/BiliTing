package com.tingbili.app.data.api

import com.tingbili.app.data.api.dto.SearchItem

class SearchApi(
    private val service: BiliApiService,
    private val keys: WbiKeyStore
) {
    suspend fun search(keyword: String, searchType: String = "video", page: Int = 1): List<SearchItem> {
        var items = searchOnce(keyword, searchType, page)
        // B站匿名搜索有软限流：连续请求偶发返回空结果，重试一次
        if (page == 1 && items.isEmpty()) {
            kotlinx.coroutines.delay(2500)
            items = searchOnce(keyword, searchType, page)
        }
        return items
    }

    private suspend fun searchOnce(keyword: String, searchType: String, page: Int): List<SearchItem> {
        val (imgKey, subKey) = keys.keys()
        // /type 端点只支持特定的 search_type，UI 选项需要映射
        val apiSearchType = when (searchType) {
            "all" -> "video"      // "全部" → 视频（最全面，包含音视频）
            "audio" -> "music"    // "音频" → 音乐（包含音频内容）
            else -> searchType    // "video" 等直接传递
        }
        val params = mapOf(
            "keyword" to keyword,
            "search_type" to apiSearchType,
            "page" to page.toString(),
            "page_size" to "30"
        )
        val signed = WbiSigner.sign(params, imgKey, subKey)
        val resp = service.search(
            keyword = keyword, searchType = apiSearchType, page = page, pageSize = 30,
            wRid = signed.getValue("w_rid"), wts = signed.getValue("wts")
        )
        return resp.data?.result ?: emptyList()
    }
}
