package com.tingbili.app.player

import org.junit.Test

/** 跳过片头只在装载时起播位置落在片头内才推走，其它情况一律别动用户的位置 */
class IntroSkipTest {

    @Test
    fun 没设置就原样返回() {
        assert(introSkipTarget(0L, 0) == 0L)
        assert(introSkipTarget(12_345L, 0) == 12_345L)
    }

    @Test
    fun 落在片头内推到片头结束() {
        assert(introSkipTarget(0L, 25) == 25_000L)
        assert(introSkipTarget(24_999L, 25) == 25_000L)
    }

    @Test
    fun 已在片头之后不动() {
        assert(introSkipTarget(25_000L, 25) == 25_000L)
        assert(introSkipTarget(600_000L, 25) == 600_000L)
    }

    @Test
    fun 脏值兜底() {
        assert(introSkipTarget(-5_000L, 0) == 0L)
        assert(introSkipTarget(-5_000L, 10) == 10_000L)
        assert(introSkipTarget(0L, -3) == 0L)
    }
}
