package com.tingbili.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * B 站外链分享导入的解析结果。
 */
sealed class LinkResult {
    /** B 站视频（分P 稿件），bvid 形如 BV1xxxx...（共 12 位）。 */
    data class Bvid(val bvid: String) : LinkResult()

    /** B 站音频（音频稿件），auid 是纯数字 sid。 */
    data class Auid(val auid: Long) : LinkResult()
}

/**
 * 从任意分享文案里提取 B 站媒体标识。纯函数、可单测，不碰网络。
 *
 * - bvid：正则 `BV[0-9A-Za-z]{10}`（BV 开头 + 10 位 = 12 位）；
 * - 音频 auid：`audio/au123` 或单独 `au123`。
 *
 * b23.tv 短链本身不含 bvid/auid，需先调 [resolveShortLink] 跟一次重定向拿到真实 URL 再 parse。
 */
object LinkParser {
    private val bvidRegex = Regex("BV[0-9A-Za-z]{10}")
    private val auidRegex = Regex("(?:audio/au|\\bau)(\\d+)")

    fun parse(text: String): LinkResult? {
        bvidRegex.find(text)?.let { return LinkResult.Bvid(it.value) }
        auidRegex.find(text)?.let { m ->
            m.groupValues[1].toLongOrNull()?.let { return LinkResult.Auid(it) }
        }
        return null
    }

    /**
     * 从 HTML 正文里抠第一个 BV 号（og:video、meta、JS 变量都可能藏着）。
     * 用于 B 站 412 风控页/短链兜底：裸请求不返回 302，但 HTML 里常带 og:url 里的 BV。
     */
    fun extractBvFromHtml(html: String): String? =
        bvidRegex.find(html)?.value

    /**
     * b23.tv 短链解析：跟随重定向，返回最终落地 URL（通常是 www.bilibili.com/video/BV...）。
     * 失败返回 null。用项目现有 buildHttpClient（默认 followRedirects=true）。
     *
     * 风控兜底：B 站对裸 OkHttp 请求可能返回 412（不给 302，落地 URL 仍是 b23.tv），
     * 此时从响应体 HTML 里正则抠 BV，命中就拼成标准视频页 URL 返回。
     */
    suspend fun resolveShortLink(url: String, cookieProvider: () -> String = { "" }): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = buildHttpClient(cookieProvider)
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { response ->
                    val landed = response.request.url.toString()
                    if (landed.contains("bilibili.com")) return@use landed
                    val bv = runCatching {
                        response.peekBody(Long.MAX_VALUE)?.string()?.let { extractBvFromHtml(it) }
                    }.getOrNull()
                    if (bv != null) "https://www.bilibili.com/video/$bv" else landed
                }
            }.getOrNull()
        }
}
