package com.tingbili.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 定时关闭计时器。
 *  - 倒计时（minutes）：到点触发 onFire
 *  - 听完本集（endOfTrack）：监控 isPlaying 由 true→false 时触发 onFire（用户主动 pause 不会触发）
 */
class SleepTimer(
    private val onFire: () -> Unit,
    private val isPlayingProvider: () -> Boolean = { false }
) {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var endOfTrackMode = false
    private var lastPlaying = false

    fun start(minutes: Int) {
        stop()
        endOfTrackMode = false
        job = scope.launch {
            delay(minutes * 60_000L)
            onFire()
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
                    onFire()
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
    }

    fun isRunning(): Boolean = job?.isActive == true
    fun isEndOfTrack(): Boolean = endOfTrackMode && isRunning()
}