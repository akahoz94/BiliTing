package com.tingbili.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * wbi 签名编码往返自检：AuthorApi 的 dm 指纹参数（dm_img_inter 等）是含引号/花括号的
 * JSON 串，签名侧用 WbiSigner.encode()，线上走 Retrofit/OkHttp 自己的编码。只要
 * 「编码→解码→再编码」幂等且与签名一致，服务端解码后重新规范化校验就能对上。
 * 这是 dm 参数签名正确性的最小检查（依赖网络的部分归 ApiSmokeTest）。
 */
class WbiSignerRoundTripTest {
    private val dmValues = listOf(
        "[]" to "dm_img_list",
        "{\"ds\":[],\"wh\":[3387,1554,103],\"of\":[320,675,320]}" to "dm_img_inter",
        "YWJjZGVm" to "dm_img_str",
        "0" to "wts"
    )

    @Test
    fun dm参数编码往返一致() {
        for ((value, name) in dmValues) {
            val encoded = WbiSigner.encode(value)
            val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
            assertEquals("值应在编码往返后不变: $name", value, decoded)
            assertEquals("编码应幂等: $name", encoded, WbiSigner.encode(decoded))
        }
    }

    @Test
    fun 签名结果包含w_rid且参数不变() {
        // wbi 真实 key 是两段 32 位 hex，mixin 表最大下标 63，key 过短会越界
        val imgKey = "7cd084941338484aae1ad9425b84077c"
        val subKey = "4932caff0ff746eab6f01bf08b70ac45"
        val params = mapOf("mid" to "506451771", "ps" to "30", "pn" to "1", "dm_img_inter" to "{\"ds\":[]}")
        val signed = WbiSigner.sign(params, imgKey, subKey)
        assertEquals(params.keys + "w_rid" + "wts", signed.keys)
        check(signed["w_rid"]?.length == 32) { "w_rid 应为 32 位 md5" }
    }
}
