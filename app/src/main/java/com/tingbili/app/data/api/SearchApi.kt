package com.tingbili.app.data.api

import android.util.Log
import com.tingbili.app.data.api.dto.SearchItem
import okhttp3.FormBody
import okhttp3.RequestBody

class SearchApi(
    private val service: BiliApiService,
    private val keys: WbiKeyStore
) {
    /**
     * 三层 waterfall：
     *   1. /x/web-interface/wbi/search/type（带 wbi 签名）
     *   2. /x/web-interface/search/all/v2（POST，无 wbi）
     *   3. /search（GET，websearch，最宽松）
     * 每层失败都自动 fallback；返回首个非空结果。
     */
    suspend fun search(keyword: String, searchType: String = "video", page: Int = 1): List<SearchItem> {
        val apiSearchType = when (searchType) {
            "all" -> "video"
            "audio" -> "music"
            else -> searchType
        }

        // 1) wbi 端点
        runCatching { searchOnceWbi(keyword, apiSearchType, page) }
            .onSuccess { if (it.isNotEmpty()) return it }
            .onFailure { Log.w(TAG, "wbi 端点失败: ${it.message}") }

        // 2) POST 兜底
        runCatching { searchOnceFallback(keyword, page) }
            .onSuccess { if (it.isNotEmpty()) return it }
            .onFailure { Log.w(TAG, "v2 兜底失败: ${it.message}") }

        // 3) websearch 兜底（最宽松）
        runCatching { searchOnceWeb(keyword, apiSearchType, page) }
            .onSuccess { if (it.isNotEmpty()) return it }
            .onFailure { Log.w(TAG, "websearch 兜底失败: ${it.message}") }

        Log.w(TAG, "三层端点都返回空: keyword='$keyword' type='$apiSearchType'")
        return emptyList()
    }

    private suspend fun searchOnceWbi(keyword: String, searchType: String, page: Int): List<SearchItem> {
        val (imgKey, subKey) = keys.keys()
        val params = mapOf(
            "keyword" to keyword,
            "search_type" to searchType,
            "page" to page.toString(),
            "page_size" to "30"
        )
        val signed = WbiSigner.sign(params, imgKey, subKey)
        Log.d(TAG, "[1.wbi] kw='$keyword' type='$searchType' pn=$page")
        val resp = service.search(
            keyword = keyword, searchType = searchType, page = page, pageSize = 30,
            wRid = signed.getValue("w_rid"), wts = signed.getValue("wts")
        )
        Log.d(TAG, "[1.wbi] resp code=${resp.code} msg='${resp.message}' items=${resp.data?.result?.size}")
        if (resp.code != 0) throw RuntimeException("wbi code=${resp.code} msg='${resp.message}'")
        return resp.data?.result ?: emptyList()
    }

    private suspend fun searchOnceFallback(keyword: String, page: Int): List<SearchItem> {
        val body: RequestBody = FormBody.Builder()
            .add("keyword", keyword)
            .add("page", page.toString())
            .add("page_size", "30")
            .build()
        val raw = service.searchFallback(body = body).string()
        Log.d(TAG, "[2.v2] len=${raw.length}")
        return parseSearchV2(raw)
    }

    private suspend fun searchOnceWeb(keyword: String, searchType: String, page: Int): List<SearchItem> {
        val raw = service.searchWeb(
            searchType = searchType,
            keyword = keyword,
            page = page,
            pageSize = 30
        ).string()
        Log.d(TAG, "[3.web] len=${raw.length}")
        return parseSearchV2(raw)
    }

    /**
     * 通用 JSON 解析：适应 /search/all/v2 与 /search 两种返回结构。
     *   优先取 result.video[*]，其次 result[*]（任意类型）。
     */
    private fun parseSearchV2(raw: String): List<SearchItem> {
        return try {
            val parser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val tree = parser.parseToJsonElement(raw).jsonObject
            val code = tree["code"]?.jsonPrimitive?.intOrNullSafe ?: -1
            if (code != 0) {
                Log.w(TAG, "parse v2: code=$code msg='${tree["message"]?.jsonPrimitive?.contentOrNullSafe}'")
                return emptyList()
            }
            val dataNode = tree["data"]?.jsonObject ?: return emptyList()
            val resultNode = dataNode["result"]?.jsonObject ?: return emptyList()
            // 优先 video 数组
            val videoArr = resultNode["video"]?.jsonArray
            if (videoArr != null && videoArr.isNotEmpty()) {
                return videoArr.mapNotNull { el -> el.toSearchItem() }
            }
            // 否则 result 顶层数组
            val topArr = dataNode["result"]?.jsonArray
            if (topArr != null && topArr.isNotEmpty()) {
                return topArr.mapNotNull { el -> el.toSearchItem() }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "parse v2 failed: ${e.message}")
            emptyList()
        }
    }

    private fun kotlinx.serialization.json.JsonElement.toSearchItem(): SearchItem? {
        val o = this as? kotlinx.serialization.json.JsonObject ?: return null
        return runCatching {
            SearchItem(
                type = o["type"]?.jsonPrimitive?.contentOrNullSafe ?: "video",
                title = o["title"]?.jsonPrimitive?.contentOrNullSafe.orEmpty(),
                author = o["author"]?.jsonPrimitive?.contentOrNullSafe
                    ?: o["uname"]?.jsonPrimitive?.contentOrNullSafe.orEmpty(),
                bvid = o["bvid"]?.jsonPrimitive?.contentOrNullSafe.orEmpty(),
                aid = o["id"]?.jsonPrimitive?.longOrNullSafe
                    ?: o["aid"]?.jsonPrimitive?.longOrNullSafe ?: 0L,
                durationStr = o["duration"]?.jsonPrimitive?.contentOrNullSafe.orEmpty(),
                play = o["play"]?.jsonPrimitive?.longOrNullSafe ?: 0L,
                upic = o["upic"]?.jsonPrimitive?.contentOrNullSafe
                    ?: o["face"]?.jsonPrimitive?.contentOrNullSafe.orEmpty(),
                uid = o["mid"]?.jsonPrimitive?.longOrNullSafe ?: 0L,
                pic = o["pic"]?.jsonPrimitive?.contentOrNullSafe
                    ?: o["arcurl"]?.jsonPrimitive?.contentOrNullSafe.orEmpty(),
                cover = o["cover"]?.jsonPrimitive?.contentOrNullSafe.orEmpty()
            )
        }.getOrNull()
    }

    private companion object { const val TAG = "SearchApi" }
}

private val kotlinx.serialization.json.JsonElement.jsonObject
    get() = (this as kotlinx.serialization.json.JsonObject)
private val kotlinx.serialization.json.JsonElement.jsonArray
    get() = (this as kotlinx.serialization.json.JsonArray)
private val kotlinx.serialization.json.JsonElement.jsonPrimitive
    get() = (this as kotlinx.serialization.json.JsonPrimitive)
private val kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe: String?
    get() = if (this.isString) this.content else null
private val kotlinx.serialization.json.JsonPrimitive.longOrNullSafe: Long?
    get() = this.content.toLongOrNull()
private val kotlinx.serialization.json.JsonPrimitive.intOrNullSafe: Int?
    get() = this.content.toIntOrNull()