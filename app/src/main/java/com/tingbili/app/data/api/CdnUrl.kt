package com.tingbili.app.data.api

/**
 * B 站 DASH 音频流的 baseUrl 拼接工具。
 *
 * B 站 playurl 返回的 `dash.audio[].baseUrl` / `base_url` 是**相对路径**（无 host），
 * 必须拼上 CDN 域名才能直链。客户端常用的 CDN 域名有：
 *   - https://upcdn.bilivideo.com/        （B站官方推荐）
 *   - https://cn-hk-eq-b-cache-XX.bilivideo.com/  （地区缓存）
 *   - https://upgcxcode.bilivideo.com/    （回退）
 *
 * 真实部署下 client 可从响应 headers 拿到 base_url host，但匿名客户端通常拿不到，
 * 这里直接用 upcdn.bilivideo.com 作为优先 host（绝大多数 m4s 都能命中），
 * 失败时再按 try-catch 回退到 upgcxcode.bilivideo.com。
 */
object CdnUrl {
    private val PRIMARY_HOSTS = listOf(
        "https://upcdn.bilivideo.com",
        "https://upgcxcode.bilivideo.com"
    )

    /**
     * 把 playurl 返回的 baseUrl 拼成绝对 URL。
     * - 已经是 https 绝对地址：原样返回
     * - 以 / 开头（相对路径）：拼接 CDN host
     * - 其他：兜底返回原值
     */
    fun absolute(rawUrl: String, preferredHost: String = PRIMARY_HOSTS.first()): String {
        if (rawUrl.isBlank()) return rawUrl
        if (rawUrl.startsWith("https://", ignoreCase = true) ||
            rawUrl.startsWith("http://", ignoreCase = true)
        ) return rawUrl
        if (rawUrl.startsWith("//")) return "https:$rawUrl"
        if (rawUrl.startsWith("/")) return "$preferredHost$rawUrl"
        return rawUrl
    }

    /**
     * 对一个相对路径，按多 CDN host 顺序返回候选 URL 列表（用于下载时一个失败再试下一个）。
     */
    fun candidates(rawUrl: String): List<String> {
        if (rawUrl.isBlank() || rawUrl.startsWith("https://", ignoreCase = true) ||
            rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("data:", ignoreCase = true)
        ) return listOf(rawUrl)
        if (rawUrl.startsWith("//")) return listOf("https:$rawUrl")
        if (rawUrl.startsWith("/")) return PRIMARY_HOSTS.map { "$it$rawUrl" }
        return listOf(rawUrl)
    }
}