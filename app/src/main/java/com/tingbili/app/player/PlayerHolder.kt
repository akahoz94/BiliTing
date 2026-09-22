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
            // 新建的播放器默认 pauseAtEndOfMediaItems=false；如果此刻策略要求"末尾停住"，
            // 必须补一次，否则冷启动那一次播放会漏掉（订阅回调可能早于建播放器跑完）
            runCatching { if (stopAtEndArmed()) p.setPauseAtEndOfMediaItems(true) }
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

    /**
     * 「重新解析当前这一集的播放地址并重装」的出口，参数 = 要恢复到的进度。
     * 只在 [togglePlay] 认定"旧地址已经废了"时调用，见那里的注释。
     */
    var onReloadRequested: ((Long) -> Unit)? = null

    // ---------------- 睡眠定时（App 级） ----------------
    // 放在这里而不是播放页的 ViewModel 里：页面一退出 ViewModel 就 onCleared，
    // 定时器跟着没了 —— "听完本集停止/倒计时"会随着切 tab 静默失效。
    private val _sleepRemainSec = MutableStateFlow(-1)
    val sleepRemainSec: StateFlow<Int> = _sleepRemainSec
    private val _sleepEndOfTrack = MutableStateFlow(false)
    val sleepEndOfTrack: StateFlow<Boolean> = _sleepEndOfTrack
    private var sleepTotalMs: Long = 0L
    private var sleepStartElapsed: Long = 0L
    /** 本次睡眠定时是"听完本集"还是"倒计时"：只用于触发后给一句不同的提示 */
    @Volatile private var sleepKindEndOfTrack = false

    private val sleepTimer = SleepTimer(
        onFire = {
            pause()
            setVolume(1f)
            sleepTotalMs = 0L
            _sleepRemainSec.value = -1
            _sleepEndOfTrack.value = false
            // 定时真的生效了要给个提示：以前"听完本集停止"触发时悄无声息，
            // 用户根本不知道是定时停了还是播放出错了。
            com.tingbili.app.util.ErrorBus.post(
                if (sleepKindEndOfTrack) "本集已播完，已停止播放"
                else "定时时间到，已暂停播放"
            )
        },
        setVolume = { v -> setVolume(v) },
        setPauseAtEnd = { armed -> setSleepArmed(armed) },
        fadeOutMs = 30_000L
    )

    fun startSleep(minutes: Int) {
        sleepKindEndOfTrack = false
        sleepTotalMs = minutes * 60_000L
        sleepStartElapsed = System.currentTimeMillis()
        _sleepRemainSec.value = minutes * 60
        _sleepEndOfTrack.value = false
        sleepTimer.start(minutes)
    }

    fun startSleepEndOfTrack() {
        sleepKindEndOfTrack = true
        sleepTimer.startEndOfTrack()
        sleepTotalMs = 0L
        _sleepRemainSec.value = -1
        _sleepEndOfTrack.value = true
    }

    fun stopSleep() {
        sleepTimer.stop()
        setVolume(1f)
        sleepTotalMs = 0L
        _sleepRemainSec.value = -1
        _sleepEndOfTrack.value = false
    }

    /**
     * 听完本集停止的开关：交给 ExoPlayer 的"本集末尾暂停"（内部 = setPauseAtEndOfWindow）。
     * 打开后本集播完就地停住，不会切进下一集的占位项，也就不会顺着占位继续播下一集。
     * 同样只能在主线程调用（getter/setter 都有线程校验）。
     *
     * 注意：这是"睡眠定时"用的一次性开关，[SleepTimer] 独占调用；设置里的两个常开策略
     * （自动下一集 / 播完自动停止）走 [setAutoNextEnabled] / [setStopAtEndEnabled]。
     */
    fun setSleepArmed(armed: Boolean) {
        sleepArmed = armed
        applyEndPolicy()
    }

    /** 设置项「播完本集自动下一集」（默认 true）。关掉 = 播完停在末尾 */
    fun setAutoNextEnabled(enabled: Boolean) {
        autoNextEnabled = enabled
        applyEndPolicy()
    }

    /** 设置项「播完本集自动停止」（默认 false）。打开 = 播完停在末尾（优先级高于自动下一集） */
    fun setStopAtEndEnabled(enabled: Boolean) {
        stopAtEndEnabled = enabled
        applyEndPolicy()
    }

    /**
     * 本集末尾是否就地停住。三个来源任一成立都要停：
     *  1) 睡眠定时点了"听完本集停止"（一次性）
     *  2) 设置里开了"播完本集自动停止"（常开）
     *  3) 设置里关掉了"播完本集自动下一集"
     */
    private fun stopAtEndArmed(): Boolean = sleepArmed || stopAtEndEnabled || !autoNextEnabled

    /** 播完这一集该不该自动续下一集（PlayerViewModel 的兜底分支也用它） */
    fun shouldAutoAdvance(): Boolean = !stopAtEndArmed()

    /** 把当前策略落进播放器：只改一次属性，避免三处各自为政 */
    private fun applyEndPolicy() {
        val arm = stopAtEndArmed()
        onMain { runCatching { player.setPauseAtEndOfMediaItems(arm) } }
    }

    @Volatile private var sleepArmed = false
    @Volatile private var stopAtEndEnabled = false
    @Volatile private var autoNextEnabled = true
    @Volatile private var lastIsPlaying = false
    @Volatile private var lastMediaItemCount = 0

    /**
     * 设置里的两个「播完本集」策略是常开的、跨页面跨启动都要生效，所以在播放器层订阅，
     * 而不是挂在某个页面的 ViewModel 上（页面一退订就没了）。
     */
    init {
        settingsStore?.let { s ->
            trackingScope.launch {
                s.autoNextEnabled.collect { setAutoNextEnabled(it) }
            }
            trackingScope.launch {
                s.sleepEndOfTrack.collect { setStopAtEndEnabled(it) }
            }
        }
    }

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
                lastMediaItemCount = p.mediaItemCount
                val mid = mediaItem?.mediaId.orEmpty()
                Log.i("PlayerHolder", "transition idx=$queueIndex reason=$reason mid=$mid")
                // 占位项 mediaId 形如 "<id>#part3"：下标就是该播的集数。
                // 之前在这里调 nextPart()（内部拿 currentQueueIndex()+1），而此刻
                // queueIndex 已经是占位项下标了 —— 等于连跳两集，第 2 集被整段吞掉。
                val target = mid.substringAfterLast("#part", "").toIntOrNull() ?: return
                // 这里**不要**再用"末尾停住"去拦截：末尾停住是由播放器自己完成的
                // （setPauseAtEndOfMediaItems），正常播到末尾根本不会走到这个回调。
                // 真正能走到这里的是"人为"切下一项：点 ⏭、通知栏/锁屏切集、或停住后再点播放。
                // 那种情况下用户就是要走，拦下来反而会卡在不可播的占位项上报错。
                onSeekToPartRequested?.invoke(target)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                lastIsPlaying = playing
                if (playing) return
                trackingScope.launch { persistProgress(p) }
                // 播到末尾的一律给个说法，别让用户猜"是停了还是卡了"
                if (isAtEndOfCurrentItem(p)) {
                    when {
                        // 听完本集停止：由计时器收口（它自己也会提示）
                        sleepArmed -> sleepTimer.onPlaybackStopped()
                        stopAtEndEnabled -> com.tingbili.app.util.ErrorBus.post("本集已播完，已按设置停止播放")
                        !autoNextEnabled -> com.tingbili.app.util.ErrorBus.post("本集已播完，已停止（自动下一集已关闭）")
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w("PlayerHolder", "ExoPlayer 错误：${error.errorCodeName} ${error.message}")
                // 占位项（biliting://placeholder/...）拉不动是设计内的信号（见 buildItems），
                // 每集都会来一次，不能拿它去骚扰用户。别的错误是真中断了，必须给个说法 ——
                // 以前只写日志，用户看到的是"点了没反应、也不知道是不是卡了"。
                if (!isCurrentItemPlaceholder(p)) {
                    com.tingbili.app.util.ErrorBus.post(message = "播放中断，按播放键会重新加载")
                }
            }
        })
    }

    /**
     * 只在播放器回调（主线程）里调用：确认是"播到本集末尾"而不是中途暂停。
     * 位置/时长都是媒体时间轴上的值，不受倍速影响。
     */
    private fun isAtEndOfCurrentItem(p: ExoPlayer): Boolean = runCatching {
        val d = p.duration
        d > 0L && p.currentPosition >= d - 1_000L
    }.getOrDefault(false)

    /**
     * 当前装载的是不是"设计内的占位项"（见 [buildItems]：占位项 mediaId 形如 `<id>#partN`）。
     * 占位项只是为了撑起 timeline 让通知栏有 ⏭，它本来就拉不动，报错是预期行为 ——
     * 拿它去给用户弹提示的话，每切一集都会弹一次。
     * 只在播放器回调（主线程）里调用。
     */
    private fun isCurrentItemPlaceholder(p: ExoPlayer): Boolean =
        runCatching { p.currentMediaItem?.mediaId?.contains("#part") == true }
            .getOrDefault(false)

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
                // 睡眠定时状态每秒刷一次（StateFlow 写入，UI 直接 collect）
                _sleepRemainSec.value = if (sleepTotalMs > 0L) {
                    ((sleepTotalMs - (System.currentTimeMillis() - sleepStartElapsed)) / 1000L)
                        .toInt().coerceAtLeast(0)
                } else -1
                _sleepEndOfTrack.value = sleepTimer.isEndOfTrack()
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
            lastMediaItemCount = player.mediaItemCount
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
            lastMediaItemCount = player.mediaItemCount
        }
    }

    fun seekToQueue(index: Int, positionMs: Long) {
        onMain { runCatching { player.seekTo(index, positionMs) } }
    }

    fun updateRecord(record: BookRecord) { _record.value = record }

    /**
     * 播放器里是否已装载音频（冷启动恢复态下为 false）。
     * 读缓存而不是直读播放器：这个 getter 也有线程校验，后台线程读会被
     * runCatching 吞成 false，表现为"明明装着音频却当成没装载"。
     */
    fun hasMedia(): Boolean = lastMediaItemCount > 0

    /**
     * 空播放列表时 player.play() 是空操作，迷你条会变成"死条"。
     * 冷启动只恢复了展示用的 record，此时点播放要先真正装载音频。
     */
    var onEmptyPlayRequested: (() -> Unit)? = null

    fun togglePlay() {
        onMain {
            if (player.isPlaying) { player.pause(); return@onMain }
            if (hasMedia()) {
                // 上次加载失败会停在 IDLE。分两种情况：
                //  1) 网络瞬断这类**可重试**的：重新 prepare 用原地址就拉得起来；
                //  2) 地址本身已经废了：B 站音频地址带有效期，暂停久了 / 长时间退后台
                //     之后会过期；换集时源报错、或播放器跨进占位项后没被救回来也是这种。
                //     此时 prepare 只会拿**同一个废地址**再失败一次，每次都静默回到 IDLE，
                //     用户看到的就是"点播放毫无反应，只能杀进程重开"（重开之所以有效，
                //     是因为冷启动走的是 resumeCurrent()，那里会重新解析地址）。
                //     这种情况必须让上层重新解析地址、重装当前这一集。
                if (player.playbackState == Player.STATE_IDLE) {
                    val err = runCatching { player.playerError }.getOrNull()
                    val reload = onReloadRequested
                    if (err != null && reload != null) {
                        Log.i("PlayerHolder", "地址疑似失效（${err.errorCodeName}），改为重新解析后重装")
                        reload(runCatching { player.currentPosition }.getOrDefault(0L))
                        return@onMain
                    }
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
    /** 播放态读缓存（由播放器回调在主线程维护）：任何线程都能安全读，不会触发线程校验 */
    fun isPlaying(): Boolean = lastIsPlaying
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