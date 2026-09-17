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
    /** 返回播放音频 URL；bvid+cid 走视频 DASH，auid 走音频区，否则 null */
    suspend fun resolveAudioUrl(bvid: String?, cid: Long?, auid: Long?): String? = when {
        bvid != null && cid != null -> playUrlApi.audioUrl(bvid, cid)
        auid != null -> audioApi.url(auid)
        else -> null
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
