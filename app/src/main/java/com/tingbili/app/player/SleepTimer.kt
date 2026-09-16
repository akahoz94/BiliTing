package com.tingbili.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 定时关闭计时器。
 * 内部使用 Dispatchers.Default（而非 Main），保证本地 JVM 单测可用。
 */
class SleepTimer(private val onFire: () -> Unit) {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun start(minutes: Int) {
        stop()
        job = scope.launch {
            delay(minutes * 60_000L)
            onFire()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
