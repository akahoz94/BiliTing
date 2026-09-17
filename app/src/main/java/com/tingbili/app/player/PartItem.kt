package com.tingbili.app.player

/**
 * 播放队列条目：用于在播放页/听单展示具体某 P（part 名称 + 时长）。
 * cid 是 B站接口标识，bvid 视频共享同一 bvid（音频则为 auid 占位 0L）。
 */
data class PartItem(
    val bvid: String,
    val cid: Long,
    val part: String,        // 分 P 名称（空字符串表示无命名）
    val duration: Long = 0L // 时长（秒）
)