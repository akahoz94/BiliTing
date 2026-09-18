package com.tingbili.app.data.repo

import android.content.Context
import android.util.Log
import com.tingbili.app.data.api.AudioApi
import com.tingbili.app.data.api.BiliApiService
import com.tingbili.app.data.api.PlayUrlApi
import com.tingbili.app.data.api.WebViewAudioSniffer
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.player.PartItem

class PlayRepository(
    private val playUrlApi: PlayUrlApi,
    private val audioApi: AudioApi,
    private val service: BiliApiService,
    private val context: Context? = null
) {
    private val sniffer by lazy { context?.let { WebViewAudioSniffer(it) } }

<<<<<<< HEAD
    /**
     * 解析音频 URL。
     *
     * 优先 WebView 嗅探（仿"我的听书"，浏览器指纹不风控），
     * 失败再回退直接调 playurl API。
     */
    suspend fun resolveAudioUrl(bvid: String?, cid: Long?, auid: Long?): String? {
        // 视频：直连 API 优先（快），失败再 WebView 嗅探兜底
        if (bvid != null && bvid.isNotBlank() && cid != null && cid > 0L) {
            // 1) DASH 直链（首选，快）
=======
    suspend fun resolveAudioUrl(bvid: String?, cid: Long?, auid: Long?): String? {
        if (bvid != null && bvid.isNotBlank() && cid != null && cid > 0L) {
>>>>>>> 82b0fb035abb5855eb79013f3bf6f14979a12864
            val dash = playUrlApi.audioUrl(bvid, cid)
            if (!dash.isNullOrBlank()) {
                Log.i(TAG, "playUrl API 成功: $bvid")
                return dash
            }
<<<<<<< HEAD
            // 2) legacy durl
=======
>>>>>>> 82b0fb035abb5855eb79013f3bf6f14979a12864
            val legacy = playUrlApi.legacyDurl(bvid, cid)
            if (!legacy.isNullOrBlank()) {
                Log.i(TAG, "legacy durl 成功: $bvid")
                return legacy
            }
<<<<<<< HEAD
            // 3) WebView 嗅探兜底（API 被风控时用）
=======
>>>>>>> 82b0fb035abb5855eb79013f3bf6f14979a12864
            sniffer?.let { s ->
                runCatching { s.sniff(bvid, cid) }
                    .onSuccess { url ->
                        if (!url.isNullOrBlank()) {
                            Log.i(TAG, "WebView 嗅探成功(兜底): $bvid")
                            return url
                        }
                    }
                    .onFailure { Log.w(TAG, "WebView 嗅探异常: ${it.message}") }
            }
        }
        if (auid != null && auid > 0L) {
            return audioApi.url(auid)
        }
        return null
    }

    suspend fun candidates(bvid: String?, cid: Long?): List<String> {
        if (bvid == null || cid == null || cid <= 0L) return emptyList()
<<<<<<< HEAD
        // 跟播放一致：API 优先（ExoPlayer 验证过的URL），WebView 兜底
        val apiUrls = playUrlApi.candidates(bvid, cid)
        if (apiUrls.isNotEmpty()) return apiUrls
        // API 失败时用 WebView 嗅探
=======
>>>>>>> 82b0fb035abb5855eb79013f3bf6f14979a12864
        sniffer?.let { s ->
            runCatching { s.sniff(bvid, cid) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return listOf(it) }
        }
<<<<<<< HEAD
        return emptyList()
=======
        return playUrlApi.candidates(bvid, cid)
>>>>>>> 82b0fb035abb5855eb79013f3bf6f14979a12864
    }

    suspend fun resolveVideo(bvid: String): Pair<BookRecord, List<PartItem>>? {
        val pages = service.pagelist(bvid).data.orEmpty().filter { it.cid > 0 }
        if (pages.isEmpty()) return null
        val record = BookRecord(
            id = "video:$bvid",
            title = "",
            owner = "",
            type = "video",
            totalParts = pages.size,
            currentPart = 1,
            currentCid = pages.first().cid,
            bvid = bvid
        )
        val queue = pages.map { PartItem(bvid, it.cid, it.part, it.duration) }
        return record to queue
    }

    companion object {
        private const val TAG = "PlayRepository"
    }
<<<<<<< HEAD
}
=======
}
>>>>>>> 82b0fb035abb5855eb79013f3bf6f14979a12864
