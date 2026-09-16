package com.tingbili.app.data.repo

import com.tingbili.app.data.api.AudioApi
import com.tingbili.app.data.api.PlayUrlApi

class PlayRepository(
    private val playUrlApi: PlayUrlApi,
    private val audioApi: AudioApi
) {
    /** 返回播放音频 URL；bvid+cid 走视频 DASH，auid 走音频区，否则 null */
    suspend fun resolveAudioUrl(bvid: String?, cid: Long?, auid: Long?): String? = when {
        bvid != null && cid != null -> playUrlApi.audioUrl(bvid, cid)
        auid != null -> audioApi.url(auid)
        else -> null
    }
}
