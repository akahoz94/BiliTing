package com.tingbili.app.data.api

import android.util.Log
import com.tingbili.app.data.api.dto.AuthorVideo

class AuthorApi(
    private val service: BiliApiService,
    private val keys: WbiKeyStore
) {
    /**
     * 拉取 UP 主投稿列表（按时间倒序）。
     * 首选 /x/space/arc/search（旧 API、不需要 wbi 签名、风控宽松）；
     * 失败回退 /x/space/wbi/arc/search（新版、强制 wbi 签名）。
     * 返回 (本页投稿, 投稿总数)。
     */
    suspend fun videos(mid: Long, page: Int, pageSize: Int = 30): Pair<List<AuthorVideo>, Int> {
        if (mid <= 0L) {
            Log.w(TAG, "videos: mid=$mid 不合法，直接返回空")
            return emptyList<AuthorVideo>() to 0
        }
        return try {
            Log.d(TAG, "videos legacy: mid=$mid pn=$page ps=$pageSize")
            val resp = service.authorVideosLegacy(mid = mid, ps = pageSize, pn = page)
            val data = resp.data
            Log.d(TAG, "videos legacy resp: code=${resp.code} msg='${resp.message}' count=${data?.page?.count} vlist=${data?.list?.vlist?.size}")
            if (data != null && data.list.vlist.isNotEmpty()) {
                data.list.vlist to data.page.count
            } else {
                Log.w(TAG, "legacy 返回空 (mid=$mid pn=$page)，回退 wbi 接口")
                videosWbi(mid, page, pageSize)
            }
        } catch (e: Exception) {
            Log.w(TAG, "legacy 请求失败 (${e.message})，回退 wbi 接口")
            try {
                videosWbi(mid, page, pageSize)
            } catch (e2: Exception) {
                Log.e(TAG, "wbi 接口也失败: ${e2.message}")
                emptyList<AuthorVideo>() to 0
            }
        }
    }

    private suspend fun videosWbi(mid: Long, page: Int, pageSize: Int): Pair<List<AuthorVideo>, Int> {
        val (imgKey, subKey) = keys.keys()
        if (imgKey.isBlank()) {
            Log.w(TAG, "wbi keys 尚未就绪，返回空")
            return emptyList<AuthorVideo>() to 0
        }
        val params = mapOf("mid" to mid.toString(), "ps" to pageSize.toString(), "pn" to page.toString())
        val signed = WbiSigner.sign(params, imgKey, subKey)
        Log.d(TAG, "videos wbi: mid=$mid pn=$page")
        val resp = service.authorVideos(
            mid = mid, ps = pageSize, pn = page,
            wRid = signed.getValue("w_rid"), wts = signed.getValue("wts")
        )
        Log.d(TAG, "videos wbi resp: code=${resp.code} msg='${resp.message}'")
        val data = resp.data ?: return emptyList<AuthorVideo>() to 0
        return data.list.vlist to data.page.count
    }

    private companion object { const val TAG = "AuthorApi" }
}