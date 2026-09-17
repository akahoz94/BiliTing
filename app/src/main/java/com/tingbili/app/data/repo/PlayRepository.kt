package com.tingbili.app.data.repo

import com.tingbili.app.data.api.AudioApi
import com.tingbili.app.data.api.BiliApiService
import com.tingbili.app.data.api.PlayUrlApi
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.player.PartItem

class PlayRepository(
    private val playUrlApi: PlayUrlApi,
    private val audioApi: AudioApi,
    private val service: BiliApiService
) {
    /**
     * 解析音频 URL。
     *
     * 优先级（避免音频区 auid 直链风控）：
     *   1) bvid + cid  → DASH 直链（首选，绝大多数有声书 type=video 走这里）
     *   2) auid        → 音频区直链（部分 ASMR/单集音频走这里）
     *
     * 当 (1) 失败但 auid 不为空时，**额外尝试**回退到 auid 通道。
     */
    suspend fun resolveAudioUrl(bvid: String?, cid: Long?, auid: Long?): String? {
        // 视频 DASH 优先
        if (bvid != null && cid != null && cid > 0L) {
            val dash = playUrlApi.audioUrl(bvid, cid)
            if (!dash.isNullOrBlank()) return dash
            // DASH 失败时尝试 legacy durl（HTML5 mp4）
            val legacy = playUrlApi.legacyDurl(bvid, cid)
            if (!legacy.isNullOrBlank()) return legacy
        }
        // 音频区
        if (auid != null && auid > 0L) {
            return audioApi.url(auid)
        }
        return null
    }

    /**
     * 解析视频的多个 DASH 候选 URL —— 给 DownloadManager 重试使用。
     */
    suspend fun candidates(bvid: String?, cid: Long?): List<String> {
        if (bvid == null || cid == null || cid <= 0L) return emptyList()
        return playUrlApi.candidates(bvid, cid)
    }

    /**
     * 视频分P 信息 → 播放记录 + 播放队列。
     * record.id = "video:$bvid"，currentCid = 第一P cid，队列为 PartItem（bvid, cid, part 名, duration）。
     * 标题/作者/封面由调用方回填（搜索结果自带，续播记录已存）。失败返回 null。
     */
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
}