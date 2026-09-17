package com.tingbili.app.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.util.CoverDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

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

    /**
     * MediaController 连接 PlaybackService 后，系统会自动发布 MediaStyle 通知，
     * 锁屏 / 通知栏 / 系统媒体控件都会出现控制条。
     * 这是锁屏界面出现的必要条件。
     */
    val mediaControllerFuture =
        MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        ).buildAsync()

    init {
        mediaControllerFuture.addListener({
            try {
                mediaControllerFuture.get()
                Log.i("PlayerHolder", "MediaController connected")
            } catch (t: Throwable) {
                Log.w("PlayerHolder", "MediaController connect failed: ${t.message}")
            }
        }, MoreExecutors.directExecutor())
    }

    private val _record = MutableStateFlow<BookRecord?>(null)
    val record: StateFlow<BookRecord?> = _record

    /** 当前封面本地文件（锁屏 / 播放页 fallback 用） */
    private val _coverFile = MutableStateFlow<File?>(null)
    val coverFile: StateFlow<File?> = _coverFile

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
        val coverUrl = com.tingbili.app.util.CoverUtil.normalize(record.cover)

        // 关键：MediaItem 上携带 cover 信息 → 锁屏控件显示封面/标题/作者
        // - setArtworkUri：让 Media3 在需要时再拉取（备用）
        // - 预下载的本地 File 通过 extras 传给 MediaMetadata，锁屏显示更稳定
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(record.title.ifBlank { "听书" })
            .setArtist(record.owner.ifBlank { "未知作者" })
            .setAlbumTitle("BiliTing")
        if (coverUrl.isNotBlank()) {
            metaBuilder.setArtworkUri(android.net.Uri.parse(coverUrl))
        }

        val item = MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaId(record.id)
            .setMediaMetadata(metaBuilder.build())
            .build()
        player.setMediaItem(item)
        player.playbackParameters = PlaybackParameters(speed)
        player.seekTo(positionMs)
        player.prepare()
        player.play()

        // 异步预下载封面到本地缓存，让锁屏控件走本地路径（首播可见）
        // 不阻塞 play()，本地命中或下载成功后通知 coverFile
        CoroutineScope(Dispatchers.IO).launch {
            val f = CoverDownloader.ensureLocalFile(appContext, coverUrl)
            _coverFile.value = f
            Log.d("PlayerHolder", "封面本地缓存：${f?.absolutePath}")
        }
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