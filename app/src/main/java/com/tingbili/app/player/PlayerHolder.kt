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

    // 改为懒加载：ExoPlayer / TrackSelector / MediaController 涉及原生编解码器探测，
    // 在部分设备上构造时会抛；以前在 Application.onCreate 主线程同步实例化，启动即闪退。
    // 现在只在首次 play()/ensurePlayer() 时才创建，期间所有可能抛的代码都包在 try-catch 里。
    private var _player: ExoPlayer? = null
    private var _trackSelector: DefaultTrackSelector? = null
    private var _mediaControllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    val player: ExoPlayer
        get() = _player ?: ensurePlayer()

    private fun ensurePlayer(): ExoPlayer {
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
                Log.w("PlayerHolder", "DefaultTrackSelector 创建失败，降级为最小可用 Player：${it.message}")
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
                    /* handleAudioFocus = */ true
                )
                    .setHandleAudioBecomingNoisy(true)
                    .build()
            }.getOrElse {
                Log.e("PlayerHolder", "ExoPlayer 创建失败，返回本地静音占位 Player，避免启动闪退", it)
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
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )
        )

    /**
     * MediaController 连接 PlaybackService 后，系统会自动发布 MediaStyle 通知，
     * 锁屏 / 通知栏 / 系统媒体控件都会出现控制条。
     * 这是锁屏界面出现的必要条件。
     */
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
                    Log.i("PlayerHolder", "MediaController connected")
                }.onFailure {
                    Log.w("PlayerHolder", "MediaController connect failed: ${it.message}")
                }
            }, MoreExecutors.directExecutor())
        }.onFailure {
            Log.w("PlayerHolder", "MediaController 异步连接创建失败：${it.message}")
        }
    }

    private val _record = MutableStateFlow<BookRecord?>(null)
    val record: StateFlow<BookRecord?> = _record

    /** 当前封面本地文件（锁屏 / 播放页 fallback 用） */
    private val _coverFile = MutableStateFlow<File?>(null)
    val coverFile: StateFlow<File?> = _coverFile

    private var queue: List<PartItem> = emptyList()
    private var queueIndex = 0

    /** D4 收听时长统计 */
    private val trackingScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    @Volatile private var shouldTrack = true
    @Volatile private var accMs = 0L

    /** 系统锁屏 / 通知栏 ⏭ / ⏮ 触发的回调：交给 PlayerLauncher 处理 */
    var onSeekToNextRequested: (() -> Unit)? = null
    var onSeekToPreviousRequested: (() -> Unit)? = null

    /** 动态切换"仅音频 / 视频"渲染模式（用户从设置页控制） */
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
                // 占位项触发：自动切到下一真实分P（由 launcher 异步解析 url）
                // 锁屏 / 通知 ⏭ 点击 → ExoPlayer.seekTo(nextIndex) → 触发 placeholder 项 → 触发此分支
                // Media3 的命令分发会自动执行这条链路，所以锁屏 ⏭ 现在能真正切下一集。
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
        // D4 收听时长统计：后台播放时（进程存活）每秒累加，攒够 5s 落盘到 SettingsStore
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

        val metaBuilder = MediaMetadata.Builder()
            .setTitle(record.title.ifBlank { "听书" })
            .setArtist(record.owner.ifBlank { "未知作者" })
            .setAlbumTitle("BiliTing")
        if (coverUrl.isNotBlank()) {
            metaBuilder.setArtworkUri(android.net.Uri.parse(coverUrl))
        }

        val firstItem = MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaId(record.id)
            .setMediaMetadata(metaBuilder.build())
            .build()

        if (queue.size > 1) {
            // 把整条分P 队列一次性塞给 ExoPlayer，让 MediaSession 自动派生 ⏭ / ⏮：
            //   - 当前真实分P 触发结束 → ExoPlayer 自动 seek 到 nextMediaItemIndex
            //   - 锁屏/通知栏点 ⏭ → MediaSession 把 seekToNext 桥到 ExoPlayer.seekTo(nextIndex)
            //   - 不需要"占位 sentinel"那套（之前占位会让 ⏭ 看上去存在但点了其实不切）
            //
            // 注意：队列里每项的 audioUrl 在 launch 时还未解析，但 ExoPlayer 是 lazy prepare，
            //   只有在用户真正切到下一集时才需要 url；onMediaItemTransition 时再让 launcher 异步
            //   resolveAudioUrl 替换（PlayerHolder 内已 attach 的 listener 转发到 launcher.nextPart）。
            val items = mutableListOf(firstItem)
            for (i in 1 until queue.size) {
                val p = queue[i]
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
            player.setMediaItems(items, /* startIndex = */ 0, positionMs)
        } else {
            player.setMediaItem(firstItem)
            player.seekTo(positionMs)
        }
        player.playbackParameters = PlaybackParameters(speed)
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
    /** 音量 0..1，用于睡眠渐弱淡出 */
    fun setVolume(v: Float) { player.volume = v.coerceIn(0f, 1f) }
}