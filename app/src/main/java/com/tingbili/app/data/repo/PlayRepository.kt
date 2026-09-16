package com.tingbili.app.data.repo

import com.tingbili.app.data.api.AudioApi
import com.tingbili.app.data.api.BiliApiService
import com.tingbili.app.data.api.PlayUrlApi
import com.tingbili.app.data.local.BookRecord

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
     * 视频详情 → 分P 播放记录 + 播放队列。
     * record.id = "video:$bvid"，currentCid = 第一P cid，队列 pages.map { bvid to cid }。
     * view 接口调用失败（数据为空）返回 null。
     */
    suspend fun resolveVideo(bvid: String): Pair<BookRecord, List<Pair<String, Long>>>? {
        val data = service.view(bvid).data ?: return null
        val pages = data.pages
        val record = BookRecord(
            id = "video:$bvid",
            title = data.title,
            owner = data.owner.name,
            type = "video",
            totalParts = pages.size,
            currentPart = 1,
            currentCid = pages.firstOrNull()?.cid,
            bvid = bvid
        )
        val queue = pages.map { bvid to it.cid }
        return record to queue
    }
}
