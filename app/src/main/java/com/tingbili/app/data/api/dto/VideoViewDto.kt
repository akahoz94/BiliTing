package com.tingbili.app.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * /x/web-interface/view 接口返回的视频详情。
 *
 * 用作播放页→UP主主页跳转的 mid/upic 兜底：
 *   当搜索结果（web 端）里没带 mid 字段时，调用本接口拿 up 主 mid + 头像，
 *   回写到 BookRecord.ownerMid / ownerAvatar，再跳转 AuthorScreen。
 *
 * 端点：GET /x/web-interface/view?bvid=xxx
 */
@Serializable
data class VideoViewData(
    val bvid: String = "",
    val aid: Long = 0,
    val title: String = "",
    @SerialName("pic") val cover: String = "",
    val duration: Long = 0,
    val owner: VideoViewOwner? = null,
    @SerialName("stat") val stat: VideoViewStat? = null,
    @SerialName("pages") val pages: List<PageItem> = emptyList()
)

@Serializable
data class VideoViewOwner(
    @SerialName("mid") val mid: Long = 0,
    @SerialName("name") val name: String = "",
    @SerialName("face") val face: String = ""
)

@Serializable
data class VideoViewStat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0
)