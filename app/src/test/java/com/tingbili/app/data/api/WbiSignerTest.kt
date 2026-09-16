package com.tingbili.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WbiSignerTest {

    @Test fun `mixinKey 由 img和 sub key 重排生成`() {
        // 官方示例：img_key=7cd084941338484aae1ad9425b84077c sub_key=4932caff0ff746eab6f01bf08b70ac45
        val mixin = WbiSigner.mixinKey("7cd084941338484aae1ad9425b84077c", "4932caff0ff746eab6f01bf08b70ac45")
        assertEquals(32, mixin.length)
        assertTrue(mixin.length > 0)
    }

    @Test fun `签名结果包含 w_rid 和 wts`() {
        val params = mapOf("foo" to "114", "bar" to "514", "baz" to "1919810")
        val signed = WbiSigner.sign(params, "7cd084941338484aae1ad9425b84077c", "4932caff0ff746eab6f01bf08b70ac45")
        assertTrue(signed.containsKey("w_rid"))
        assertTrue(signed.containsKey("wts"))
        assertEquals("114", signed["foo"])
    }
}
