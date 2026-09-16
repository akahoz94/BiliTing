package com.tingbili.app.data.api

import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.data.local.CookieStore

class SearchApi(
    private val service: BiliApiService,
    private val keys: WbiKeyStore,
    private val cookies: CookieStore
) {
    suspend fun search(keyword: String, searchType: String = "video", page: Int = 1): List<SearchItem> {
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
