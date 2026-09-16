package com.tingbili.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.tingbili.app.data.local.BookRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局播放器持有者：ExoPlayer 实例 + 当前播放的 BookRecord + 播放队列。
 */
class PlayerHolder(context: Context) {
    private val appContext = context.applicationContext
    val player: ExoPlayer = ExoPlayer.Builder(appContext).build()

    private val _record = MutableStateFlow<BookRecord?>(null)
    val record: StateFlow<BookRecord?> = _record

    private var queue: List<Pair<String, Long>> = emptyList() // (bvid, cid) 或 (auid, 0)

    fun play(
        record: BookRecord,
        audioUrl: String,
        queue: List<Pair<String, Long>>,
        positionMs: Long,
        speed: Float
    ) {
        _record.value = record
        this.queue = queue
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

    fun togglePlay() = if (player.isPlaying) player.pause() else player.play()
    fun setSpeed(speed: Float) { player.playbackParameters = PlaybackParameters(speed) }
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun pause() = player.pause()
}
