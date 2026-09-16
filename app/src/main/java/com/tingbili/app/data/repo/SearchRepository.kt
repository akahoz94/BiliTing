package com.tingbili.app.data.repo

import com.tingbili.app.data.api.SearchApi
import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.util.KeywordFilter

class SearchRepository(private val searchApi: SearchApi) {

    /** @param listeningOnly true 时仅保留标题命中听书关键词的结果 */
    suspend fun search(keyword: String, type: String, page: Int, keywords: Set<String>, listeningOnly: Boolean): List<SearchItem> {
        val results = searchApi.search(keyword, type, page)
        return if (listeningOnly) results.filter { KeywordFilter.isListeningRelated(stripHtml(it.title), keywords) }
        else results
    }

    companion object {
        fun stripHtml(title: String): String =
            title.replace(Regex("<[^>]+>"), "")
    }
}
