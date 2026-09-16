package com.tingbili.app.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val numResults: Int = 0,
    val result: List<SearchItem> = emptyList()
)

@Serializable
data class SearchItem(
    val type: String = "video",
    val title: String = "",            // 含 <em class="keyword"> 高亮标签，展示时需去除
    val author: String = "",
    val bvid: String = "",
    val aid: Long = 0,
    @SerialName("duration") val durationStr: String = "",  // "mm:ss" 格式
    val length: Long = 0,              // 视频时长（秒，音频区）
    val play: Long = 0,                // 播放数（字符串可能带"万"）
    val upic: String = "",
    @SerialName("mid") val uid: Long = 0
)
