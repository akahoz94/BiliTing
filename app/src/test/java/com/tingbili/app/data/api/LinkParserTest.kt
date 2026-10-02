package com.tingbili.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkParserTest {

    @Test
    fun `完整视频 URL 提取 bvid`() {
        val r = LinkParser.parse("https://www.bilibili.com/video/BV1xx411c7mD")
        assertEquals(LinkResult.Bvid("BV1xx411c7mD"), r)
    }

    @Test
    fun `纯 BV 号也能识别`() {
        val r = LinkParser.parse("BV1xy4y1p7Qj")
        assertEquals(LinkResult.Bvid("BV1xy4y1p7Qj"), r)
    }

    @Test
    fun `带分享文案的视频文本`() {
        val text = "快来看看这个视频 https://www.bilibili.com/video/BV1GJ411x7h7 超好看"
        assertEquals(LinkResult.Bvid("BV1GJ411x7h7"), LinkParser.parse(text))
    }

    @Test
    fun `音频链接提取 auid`() {
        assertEquals(
            LinkResult.Auid(123456L),
            LinkParser.parse("https://www.bilibili.com/audio/au123456")
        )
        assertEquals(
            LinkResult.Auid(98765L),
            LinkParser.parse("au98765 来听歌")
        )
    }

    @Test
    fun `b23 短链本身解析不出标识（需先 resolveShortLink）`() {
        // 短链里没有 bvid/auid，纯 parse 返回 null —— 真实流程会先 resolve 再 parse
        assertNull(LinkParser.parse("快来看看这个视频 https://b23.tv/abcDEF 超好看"))
    }

    @Test
    fun `无效链接返回 null`() {
        assertNull(LinkParser.parse("https://www.baidu.com/search?q=hello"))
        assertNull(LinkParser.parse("今天天气不错"))
        assertNull(LinkParser.parse(""))
    }

    @Test
    fun `从 og video meta 里抠 BV`() {
        val html = """
            <html><head>
            <meta property="og:title" content="测试视频"/>
            <meta property="og:url" content="https://www.bilibili.com/video/BV1xx411c7mD/"/>
            </head><body></body></html>
        """.trimIndent()
        assertEquals("BV1xx411c7mD", LinkParser.extractBvFromHtml(html))
    }

    @Test
    fun `从 JS 变量里抠 BV`() {
        val html = """
            <html><body>
            <script>var __INITIAL_STATE__={"bvid":"BV1GJ411x7h7"};</script>
            </body></html>
        """.trimIndent()
        assertEquals("BV1GJ411x7h7", LinkParser.extractBvFromHtml(html))
    }

    @Test
    fun `没有 BV 的 HTML 返回 null`() {
        val html = """
            <html><head><title>412 Precondition Failed</title></head>
            <body>风控拦截页面，没有任何 BV 号</body></html>
        """.trimIndent()
        assertNull(LinkParser.extractBvFromHtml(html))
    }
}
