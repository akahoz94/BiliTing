package com.tingbili.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.tingbili.app.data.local.BookRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局播放器持有者：ExoPlayer 实例 + 当前播放的 BookRecord + 播放队列。
 */
class PlayerHolder(context: Context) {
    private val appContext = context.applicationContext
    // B站媒体 CDN 校验 Referer/User-Agent，缺省头会返回 403
    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://www.bilibili.com/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )
        )
    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()

    private val _record = MutableStateFlow<BookRecord?>(null)
    val record: StateFlow<BookRecord?> = _record

    private var queue: List<PartItem> = emptyList()
    private var queueIndex = 0

    fun play(
        record: BookRecord,
        audioUrl: String,
        queue: List<PartItem>,
        positionMs: Long,
        speed: Float
    ) {
        _record.value = record
        this.queue = queue
        this.queueIndex = 0
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(audioUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(record.title)
                        .setArtist(record.owner)
                        .build()
                )
                .build()
        )
        player.playbackParameters = PlaybackParameters(speed)
        player.seekTo(positionMs)
        player.prepare()
        player.play()
    }

    /** 当前播放队列 */
    fun currentQueue(): List<PartItem> = queue

    /** 当前队列下标（0 起） */
    fun currentQueueIndex(): Int = queueIndex

    /** 切换队列下标（越界时钳制到边界） */
    fun moveQueueTo(index: Int) {
        queueIndex = index.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    }

    /** 更新当前播放记录（如收藏状态），供 UI 立即反馈 */
    fun updateRecord(record: BookRecord) { _record.value = record }

    fun togglePlay() = if (player.isPlaying) player.pause() else player.play()
    fun setSpeed(speed: Float) { player.playbackParameters = PlaybackParameters(speed) }
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun pause() = player.pause()
}
