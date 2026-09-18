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

    /** 音量增益处理器：注入 DefaultAudioSink 的音频链路（与倍速用的 Sonic 处理器共存） */
    private val gainProcessor = GainAudioProcessor()

    private val renderersFactory by lazy {
        object : androidx.media3.exoplayer.DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ) = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(gainProcessor))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
    }

    private var _player: ExoPlayer? = null
    private var _trackSelector: DefaultTrackSelector? = null
    private var _mediaControllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    var currentAudioUrl: String = ""

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
                val b = ExoPlayer.Builder(appContext, renderersFactory)
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
                    // 熄屏/退后台继续播的关键：ExoPlayer 默认 WAKE_MODE_NONE，屏幕一黑
                    // CPU 就可能被挂起，音频流拉不动就"自动停了"。NETWORK 档会在播放期间
                    // 持一个 partial wake lock，保住 CPU + 网络（需 WAKE_LOCK 权限，已声明）。
                    .setWakeMode(C.WAKE_MODE_NETWORK)
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
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
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

    /**
     * ExoPlayer 的所有读写（连 `currentPosition` / `duration` / `playbackParameters`
     * 这种 getter 都在内）都会 `verifyApplicationThread()`：非应用主线程访问直接抛
     * IllegalStateException("Player is accessed on the wrong thread")，在后台协程里
     * 就是整个进程闪退。所以这里统一收口：已在主线程就直调，否则 post 过去。
     */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private fun onMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    /** 播放器自己跨进了占位项：参数 = 该占位项的下标 = 真正该播的那一集 */
    var onSeekToPartRequested: ((Int) -> Unit)? = null
    var onProgressPersist: (suspend (BookRecord) -> Unit)? = null

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
                Log.i("PlayerHolder", "transition idx=$queueIndex reason=$reason mid=$mid")
                // 占位项 mediaId 形如 "<id>#part3"：下标就是该播的集数。
                // 之前在这里调 nextPart()（内部拿 currentQueueIndex()+1），而此刻
                // queueIndex 已经是占位项下标了 —— 等于连跳两集，第 2 集被整段吞掉。
                val target = mid.substringAfterLast("#part", "").toIntOrNull()
                if (target != null) onSeekToPartRequested?.invoke(target)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                if (!playing) trackingScope.launch { persistProgress(p) }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w("PlayerHolder", "ExoPlayer 错误：${error.errorCodeName} ${error.message}")
            }
        })
    }

    /**
     * 进度落盘不能只靠播放器页的 ViewModel：从迷你条 / 通知栏 / 锁屏听的时候
     * 根本不进播放器页，听单里的进度条会一直是空的。
     * 读播放器一律回主线程（getter 也有线程校验），否则 runCatching 会把
     * IllegalStateException 吞掉，表现为"进度永远是 0 / 统计不涨"。
     */
    private suspend fun persistProgress(p: ExoPlayer) {
        val r = _record.value ?: return
        val sink = onProgressPersist ?: return
        val snapshot = kotlinx.coroutines.withContext(Dispatchers.Main) {
            val pos = runCatching { p.currentPosition }.getOrDefault(0L)
            if (pos <= 0L) return@withContext null
            val dur = runCatching { p.duration }.getOrDefault(0L).coerceAtLeast(0L)
            val sp = runCatching { p.playbackParameters.speed }.getOrDefault(1f)
            r.copy(progressMs = pos, durationMs = dur, speed = sp)
        } ?: return
        try {
            sink(snapshot)
        } catch (t: Throwable) {
            Log.w("PlayerHolder", "进度落盘失败：${t.message}")
        }
    }

    private fun startTracking(p: ExoPlayer) {
        trackingScope.launch {
            var sinceSave = 0L
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (!shouldTrack) continue
                val playing = kotlinx.coroutines.withContext(Dispatchers.Main) {
                    runCatching { p.isPlaying }.getOrDefault(false)
                }
                if (!playing) continue
                accMs += 1000
                if (accMs >= 3000) {
                    val s = settingsStore
                    if (s != null) {
                        runCatching { s.addListeningMs(accMs) }
                        accMs = 0L
                    }
                }
                sinceSave += 1000
                if (sinceSave >= 5000) {
                    sinceSave = 0L
                    persistProgress(p)
                }
            }
        }
    }

    /**
     * 当前项用真实音频地址，其余项用占位 uri：MediaSession 靠 timeline 有多少项
     * 决定通知栏/锁屏要不要给 ⏭（只有一项就没有下一集按钮）。
     */
    private fun buildItems(
        record: BookRecord,
        audioUrl: String,
        queue: List<PartItem>,
        startIndex: Int
    ): List<MediaItem> {
        val coverUrl = com.tingbili.app.util.CoverUtil.normalize(record.cover)
        val meta = MediaMetadata.Builder()
            .setTitle(record.title.ifBlank { "听书" })
            .setArtist(record.owner.ifBlank { "未知作者" })
            .setAlbumTitle("BiliTing")
            .apply { if (coverUrl.isNotBlank()) setArtworkUri(android.net.Uri.parse(coverUrl)) }
            .build()
        // 音频稿件 / 本地文件 / 新收藏还没解析分P时，queue 是空的。
        // 这里必须兜出一个单集条目：map 空列表会得到 0 个媒体项，
        // 播放器里等于什么都没装（点播放毫无反应，通知栏也没有内容）。
        if (queue.isEmpty()) {
            return listOf(
                MediaItem.Builder()
                    .setUri(audioUrl)
                    .setMediaId(record.id)
                    .setMediaMetadata(meta)
                    .build()
            )
        }
        return queue.mapIndexed { i, p ->
            if (i == startIndex) {
                MediaItem.Builder()
                    .setUri(audioUrl)
                    .setMediaId(record.id)
                    .setMediaMetadata(meta)
                    .build()
            } else {
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
            }
        }
    }

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
        currentAudioUrl = audioUrl

        onMain {
            player.setMediaItems(buildItems(record, audioUrl, queue, queueIndex), queueIndex, positionMs)
            player.playbackParameters = PlaybackParameters(speed)
            player.prepare()
            player.play()
        }

        CoroutineScope(Dispatchers.IO).launch {
            val f = CoverDownloader.ensureLocalFile(appContext, com.tingbili.app.util.CoverUtil.normalize(record.cover))
            _coverFile.value = f
        }
    }

    fun currentQueue(): List<PartItem> = queue
    fun currentQueueIndex(): Int = queueIndex

    fun moveQueueTo(index: Int) {
        queueIndex = index.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    }

    /**
     * 后台补全分P后回写播放器 timeline：只有 timeline 里真的有"下一项"，
     * 通知栏 / 锁屏才会给出 ⏭ 按钮。
     */
    fun updateQueue(newQueue: List<PartItem>, newIndex: Int) {
        val idx = newIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
        queue = newQueue
        queueIndex = idx
        if (newQueue.size <= 1) return
        // 调用方通常在 IO 协程里（解析分P 是网络请求），改 timeline 必须回主线程
        onMain {
            val r = _record.value ?: return@onMain
            if (runCatching { player.mediaItemCount }.getOrDefault(0) >= newQueue.size) return@onMain
            val pos = runCatching { player.currentPosition }.getOrDefault(0L)
            val wasPlaying = runCatching { player.isPlaying }.getOrDefault(false)
            player.setMediaItems(buildItems(r, currentAudioUrl, newQueue, idx), idx, pos)
            player.prepare()
            if (wasPlaying) player.play()
        }
    }

    fun seekToQueue(index: Int, positionMs: Long) {
        onMain { runCatching { player.seekTo(index, positionMs) } }
    }

    fun updateRecord(record: BookRecord) { _record.value = record }

    /** 播放器里是否已装载音频（冷启动恢复态下为 false） */
    fun hasMedia(): Boolean = runCatching { player.mediaItemCount > 0 }.getOrDefault(false)

    /**
     * 空播放列表时 player.play() 是空操作，迷你条会变成"死条"。
     * 冷启动只恢复了展示用的 record，此时点播放要先真正装载音频。
     */
    var onEmptyPlayRequested: (() -> Unit)? = null

    fun togglePlay() {
        onMain {
            if (player.isPlaying) { player.pause(); return@onMain }
            if (hasMedia()) {
                // 上一次是加载失败（比如换集时源报错）停在 IDLE：此时直接 play() 毫无反应，
                // 表现为"点播放键没动静"。必须重新 prepare 才会真正去拉流。
                if (player.playbackState == Player.STATE_IDLE) {
                    runCatching { player.prepare() }
                }
                player.play()
                return@onMain
            }
            onEmptyPlayRequested?.invoke()
        }
    }
    fun setSpeed(speed: Float) {
        onMain { player.playbackParameters = PlaybackParameters(speed) }
    }

    fun seekTo(ms: Long) {
        onMain {
            // 冷启动后播放器是空的（只有 record）：此时拖进度条是空操作，直接跳过，
            // 别把 seekTo 打到一个没有 media 的 player 上。
            if (!hasMedia()) return@onMain
            val wasPlaying = runCatching { player.isPlaying }.getOrDefault(false)
            player.seekTo(ms)
            if (wasPlaying) player.play()
        }
    }

    fun pause() = onMain { player.pause() }
    fun isPlaying(): Boolean = runCatching { player.isPlaying }.getOrDefault(false)
    fun togglePlayPause() {
        onMain { if (player.isPlaying) player.pause() else player.play() }
    }
    fun setVolume(v: Float) {
        onMain { player.volume = v.coerceIn(0f, 1f) }
    }

    /**
     * 音量增益：1.0 = 原始音量，>1 直接放大 PCM 样本（最高 3.0）。
     * 与 [setVolume] 不同，这是唯一能"超过原始音量"的手段（会带来削波风险）。
     */
    fun setGain(gain: Float) {
        gainProcessor.gain = gain.coerceIn(MIN_GAIN, MAX_GAIN)
    }

    fun gain(): Float = gainProcessor.gain

    companion object {
        const val MIN_GAIN = 0.5f
        const val MAX_GAIN = 3.0f
    }
}