package com.tingbili.app.data.api

import com.tingbili.app.data.api.dto.AudioUrlData

class AudioApi(private val service: BiliApiService) {
    suspend fun info(sid: Long) = service.audioInfo(sid).data

    suspend fun url(sid: Long): String? {
        val data: AudioUrlData? = service.audioUrl(sid).data
        return data?.url?.takeIf { it.isNotBlank() } ?: data?.cdns?.firstOrNull()
    }
}
