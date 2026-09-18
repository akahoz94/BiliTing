package com.tingbili.app.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
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

class PlayerHolder(
    context: Context,
    private val settingsStore: com.tingbili.app.data.local.SettingsStore? = null
) {
    private val appContext = context.applicationContext

    private var _player: ExoPlayer? = null
    private var _trackSelector: DefaultTrackSelector? = null
    private var _mediaControllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    val player: ExoPlayer
        get() = _player ?: ensurePlayer()

    fun ensurePlayer(): ExoPlayer {
        if (_player != null) return _player!!
        synchronized(this) {
            if (_player != null) return _player!!
            val trackSel = runCatching {
                DefaultTrackSelector(appContext).apply {
                    parameters = buildUponParameters()
                        .setRendererDisabled(C.TRACK_TYPE_VIDEO, true)
                        .build()
                }
            }.getOrElse {
                Log.w("PlayerHolder", "DefaultTrackSelector 创建失败：${it.message}")
                null
            }
            val p = runCatching {
                val b = ExoPlayer.Builder(appContext)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                if (trackSel != null) b.setTrackSelector(trackSel)
                b.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true
                )
                    .setHandleAudioBecomingNoisy(true)
                    .build()
            }.getOrElse {
                Log.e("PlayerHolder", "ExoPlayer 创建失败", it)
                ExoPlayer.Builder(appContext).build()
            }
            _player = p
            _trackSelector = trackSel
            attachPlayerListener(p)
            startTracking(p)
            ensureMediaController()
            return p
        }
    }

    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://www.bilibili.com/",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
        )

    private fun ensureMediaController() {
        if (_mediaControllerFuture != null) return
        runCatching {
            val fut = MediaController.Builder(
                appContext,
                SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            ).buildAsync()
            _mediaControllerFuture = fut
            fut.addListener({
                runCatching {
                    mediaController = fut.get()
                }.onFailure {
                    Log.w("PlayerHolder", "MediaController connect failed: ${it.message}")
                }
            }, MoreExecutors.directExecutor())
        }.onFailure {
            Log.w("PlayerHolder", "MediaController 异步连接失败：${it.message}")
        }
    }

    private val _record = MutableStateFlow<BookRecord?>(null)
    val record: StateFlow<BookRecord?> = _record

    private val _coverFile = MutableStateFlow<File?>(null)
    val coverFile: StateFlow<File?> = _coverFile

    private var queue: List<PartItem> = emptyList()
    private var queueIndex = 0

    private val trackingScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    @Volatile private var shouldTrack = true
    @Volatile private var accMs = 0L

    var onSeekToNextRequested: (() -> Unit)? = null
    var onSeekToPreviousRequested: (() -> Unit)? = null

    fun setAudioOnly(audioOnly: Boolean) {
        val sel = _trackSelector ?: return
        sel.parameters = sel.buildUponParameters()
            .setRendererDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
            .build()
    }

    private fun attachPlayerListener(p: ExoPlayer) {
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                queueIndex = p.currentMediaItemIndex.coerceAtLeast(0)
                val mid = mediaItem?.mediaId.orEmpty()
                if (mid.contains("#placeholder") || mid.contains("#sentinel") || mid.contains("#part")) {
                    onSeekToNextRequested?.invoke()
                }
                if (mid.contains("#placeholder-prev") || mid.contains("#sentinel-prev")) {
                    onSeekToPreviousRequested?.invoke()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w("PlayerHolder", "ExoPlayer 错误：${error.errorCodeName} ${error.message}")
            }
        })
    }

    private fun startTracking(p: ExoPlayer) {
        trackingScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (shouldTrack) {
                    runCatching { p.isPlaying }.getOrNull()?.let { playing ->
                        if (playing) {
                            accMs += 1000
                            if (accMs >= 3000) {
                                val s = settingsStore
                                if (s != null) {
                                    runCatching { s.addListeningMs(accMs) }
                                    accMs = 0L
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 开始播放。startIndex 指定队列中要播的集索引，audioUrl 是该集已解析的真实 URL。
     * 其他集用 placeholder，切到时再异步解析。
     */
    fun play(
        record: BookRecord,
        audioUrl: String,
        queue: List<PartItem>,
        positionMs: Long,
        speed: Float,
        startIndex: Int = 0
    ) {
        _record.value = record
        this.queue = queue
        this.queueIndex = startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        val coverUrl = com.tingbili.app.util.CoverUtil.normalize(record.cover)

        val metaBuilder = MediaMetadata.Builder()
            .setTitle(record.title.ifBlank { "听书" })
            .setArtist(record.owner.ifBlank { "未知作者" })
            .setAlbumTitle("BiliTing")
        if (coverUrl.isNotBlank()) {
            metaBuilder.setArtworkUri(android.net.Uri.parse(coverUrl))
        }

        if (queue.size > 1) {
            val items = mutableListOf<MediaItem>()
            for (i in queue.indices) {
                val p = queue[i]
                if (i == startIndex) {
                    items.add(
                        MediaItem.Builder()
                            .setUri(audioUrl)
                            .setMediaId(record.id)
                            .setMediaMetadata(metaBuilder.build())
                            .build()
                    )
                } else {
                    items.add(
                        MediaItem.Builder()
                            .setMediaId("${record.id}#part${i}")
                            .setUri("biliting://placeholder/${record.id}/${i}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(p.part)
                                    .setArtist(record.owner)
                                    .setAlbumTitle("BiliTing")
                                    .build()
                            )
                            .build()
                    )
                }
            }
            player.setMediaItems(items, startIndex, positionMs)
        } else {
            val firstItem = MediaItem.Builder()
                .setUri(audioUrl)
                .setMediaId(record.id)
                .setMediaMetadata(metaBuilder.build())
                .build()
            player.setMediaItem(firstItem)
            player.seekTo(positionMs)
        }
        player.playbackParameters = PlaybackParameters(speed)
        player.prepare()
        player.play()

        CoroutineScope(Dispatchers.IO).launch {
            val f = CoverDownloader.ensureLocalFile(appContext, coverUrl)
            _coverFile.value = f
        }
    }

    fun currentQueue(): List<PartItem> = queue
    fun currentQueueIndex(): Int = queueIndex

    fun moveQueueTo(index: Int) {
        queueIndex = index.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    }

    fun seekToQueue(index: Int, positionMs: Long) {
        runCatching { player.seekTo(index, positionMs) }
    }

    fun updateRecord(record: BookRecord) { _record.value = record }

    fun togglePlay() = if (player.isPlaying) player.pause() else player.play()
    fun setSpeed(speed: Float) { player.playbackParameters = PlaybackParameters(speed) }

    /** seekTo 后如果之前在播放就继续播放，暂停状态保持暂停 */
    fun seekTo(ms: Long) {
        val wasPlaying = runCatching { player.isPlaying }.getOrDefault(false)
        player.seekTo(ms)
        if (wasPlaying) player.play()
    }

    fun pause() = player.pause()
    fun isPlaying(): Boolean = runCatching { player.isPlaying }.getOrDefault(false)
    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }
    fun setVolume(v: Float) { player.volume = v.coerceIn(0f, 1f) }
}
