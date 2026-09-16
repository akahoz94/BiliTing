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
        val params = mapOf(
            "keyword" to keyword,
            "search_type" to searchType,
            "page" to page.toString(),
            "page_size" to "30"
        )
        val signed = WbiSigner.sign(params, imgKey, subKey)
        val resp = service.search(
            keyword = keyword, searchType = searchType, page = page, pageSize = 30,
            wRid = signed.getValue("w_rid"), wts = signed.getValue("wts")
        )
        return resp.data?.result ?: emptyList()
    }
}
