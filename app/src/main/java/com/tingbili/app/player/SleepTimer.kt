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
 *  - 倒计时（minutes）：到点触发 onFire
 *  - 听完本集（endOfTrack）：监控 isPlaying 由 true→false 时触发 onFire（用户主动 pause 不会触发）
 *  - 渐弱淡出（fade）：倒计时进入最后 [fadeOutMs] 毫秒时，把音量线性从 1.0 降到 0.0，避免突然中断
 */
class SleepTimer(
    private val onFire: () -> Unit,
    private val isPlayingProvider: () -> Boolean = { false },
    private val setVolume: (Float) -> Unit = {},
    private val fadeOutMs: Long = 30_000L
) {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var endOfTrackMode = false
    private var lastPlaying = false
    private var totalMs: Long = 0L

    /** 所有回调必须 post 到主线程：ExoPlayer 的 pause()/setVolume() 非主线程调用直接抛异常 */
    private fun onMain(block: () -> Unit) {
        mainHandler.post(block)
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

    /** 听完本集停止：监听播放状态从 true → false */
    fun startEndOfTrack() {
        stop()
        endOfTrackMode = true
        lastPlaying = isPlayingProvider()
        job = scope.launch {
            while (true) {
                delay(500)
                val now = isPlayingProvider()
                if (lastPlaying && !now) {
                    onMain { onFire() }
                    break
                }
                lastPlaying = now
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        endOfTrackMode = false
        // 停止时把音量恢复到 1.0，避免静音残留
        onMain { setVolume(1f) }
    }

    /** 取消时也不恢复音量（用于被新计时器覆盖） */
    fun stopWithoutRestore() {
        job?.cancel()
        job = null
        endOfTrackMode = false
    }

    fun isRunning(): Boolean = job?.isActive == true
    fun isEndOfTrack(): Boolean = endOfTrackMode && isRunning()
    fun isFading(): Boolean {
        val j = job ?: return false
        return endOfTrackMode.not() && j.isActive && totalMs > fadeOutMs
    }
}