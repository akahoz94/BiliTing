package com.tingbili.app.player

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

    @Test
    fun 计时结束回调() {
        var fired = false
        val timer = SleepTimer { fired = true }
        timer.start(0)  // 0 分钟 → delay(0) 立即触发
        runBlocking { delay(200) }
        assertTrue(fired)
    }

    @Test
    fun stop取消回调() {
        var fired = false
        val timer = SleepTimer { fired = true }
        timer.start(30)
        timer.stop()
        runBlocking { delay(200) }
        assertFalse(fired)
    }
}
