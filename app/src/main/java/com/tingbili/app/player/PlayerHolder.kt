package com.tingbili.app.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.tingbili.app.data.local.BookRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlayerHolder(context: Context) {
    private val appContext = context.applicationContext

    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://www.bilibili.com/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )
        )

    private val trackSelector = DefaultTrackSelector(appContext).apply {
        // 默认开启"仅音频"渲染：忽略视频轨 → 省流量/电
        parameters = buildUponParameters()
            .setRendererDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
    }

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setTrackSelector(trackSelector)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _record = MutableStateFlow<BookRecord?>(null)
    val record: StateFlow<BookRecord?> = _record

    private var queue: List<PartItem> = emptyList()
    private var queueIndex = 0

    /** 动态切换"仅音频 / 视频"渲染模式（用户从设置页控制） */
    fun setAudioOnly(audioOnly: Boolean) {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setRendererDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
            .build()
    }

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

    fun currentQueue(): List<PartItem> = queue
    fun currentQueueIndex(): Int = queueIndex

    fun moveQueueTo(index: Int) {
        queueIndex = index.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    }

    fun updateRecord(record: BookRecord) { _record.value = record }

    fun togglePlay() = if (player.isPlaying) player.pause() else player.play()
    fun setSpeed(speed: Float) { player.playbackParameters = PlaybackParameters(speed) }
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun pause() = player.pause()
}