package com.tingbili.app.player

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 定时关闭计时器。
 *
 *  - 倒计时（[start]）：到点触发 [onFire]；进入最后 [fadeOutMs] 毫秒时音量线性淡出
 *  - 听完本集（[startEndOfTrack]）：交给播放器自己的"本集末尾暂停"（[setPauseAtEnd]），
 *    播完由播放器主线程回调 [onPlaybackStopped] 收口
 *
 * 为什么不轮询 isPlaying：ExoPlayer 的**每一个 getter**（含 `isPlaying`）都会
 * `verifyApplicationThread()`，从后台线程读会抛 IllegalStateException；这里又是裸的
 * `CoroutineScope(Dispatchers.Default)`，异常没人接 → 直接闪退。
 * 用户点「听完本集停止」必崩就是这个原因（倒计时不读播放器，所以只有它炸）。
 */
class SleepTimer(
    private val onFire: () -> Unit,
    private val setVolume: (Float) -> Unit = {},
    private val setPauseAtEnd: (Boolean) -> Unit = {},
    private val fadeOutMs: Long = 30_000L
) {
    @Volatile private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var endOfTrackMode = false
    private var totalMs: Long = 0L

    /** 所有回调必须落在主线程：ExoPlayer 的 pause()/setVolume()/setPauseAtEndOfMediaItems() 都校验线程 */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    fun start(minutes: Int) {
        stop()
        endOfTrackMode = false
        totalMs = minutes * 60_000L
        job = scope.launch {
            val fadeStart = (totalMs - fadeOutMs).coerceAtLeast(0L)
            delay(fadeStart)
            // 进入淡出区间：每秒把音量逐步降低
            val steps = 30
            val stepDelay = (fadeOutMs / steps).coerceAtLeast(100L)
            for (i in steps downTo 0) {
                onMain { setVolume(i / steps.toFloat()) }
                delay(stepDelay)
            }
            onMain {
                setVolume(0f)
                onFire()
            }
        }
    }

    /**
     * 听完本集停止：让播放器在本集末尾自己停下（不跨进下一集的占位项），
     * 真正的触发点是播放器主线程回调 [onPlaybackStopped]。
     */
    fun startEndOfTrack() {
        stop()
        endOfTrackMode = true
        onMain { setPauseAtEnd(true) }
    }

    /**
     * 播放器报告「本集播完了」。必须从主线程调用（播放器监听回调本来就是主线程）。
     * 只在听完本集模式下生效；用户手动暂停不会走到这里。
     */
    fun onPlaybackStopped() {
        if (!endOfTrackMode) return
        endOfTrackMode = false
        job?.cancel()
        job = null
        onMain { setPauseAtEnd(false) }
        onFire()
    }

    fun stop() {
        job?.cancel()
        job = null
        endOfTrackMode = false
        onMain {
            setPauseAtEnd(false)
            // 停止时把音量恢复到 1.0，避免静音残留
            setVolume(1f)
        }
    }

    /** 取消时也不恢复音量（用于被新计时器覆盖） */
    fun stopWithoutRestore() {
        job?.cancel()
        job = null
        endOfTrackMode = false
        onMain { setPauseAtEnd(false) }
    }

    fun isRunning(): Boolean = job?.isActive == true
    fun isEndOfTrack(): Boolean = endOfTrackMode
    fun isFading(): Boolean {
        val j = job ?: return false
        return endOfTrackMode.not() && j.isActive && totalMs > fadeOutMs
    }
}
