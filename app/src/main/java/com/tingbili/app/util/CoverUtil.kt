package com.tingbili.app.util

/**
 * 封面 URL 规范化：B 站 pic/cover/upic 字段可能返回以下多种形态：
 *   - https://i0.hdslb.com/...     直接可用
 *   - //i0.hdslb.com/...           协议相对，需要补 https:
 *   - i0.hdslb.com/...             无协议且无 //，Coil 会当成相对路径 404
 *   - data:image/...;base64,...    base64 内嵌图
 *   - 空串                         占位用
 *
 * 本工具把上面所有形态归一为绝对 https URL（base64 例外，原样保留）。
 */
object CoverUtil {
    private val HOST_PREFIX = Regex("^(?:[a-z0-9-]+\\.)+[a-z]{2,}/", RegexOption.IGNORE_CASE)

    fun normalize(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val raw = url.trim()
        if (raw.isEmpty()) return ""

        // base64 内嵌图：原样返回（Coil 支持）
        if (raw.startsWith("data:", ignoreCase = true)) return raw

        // 已经是 https
        if (raw.startsWith("https://", ignoreCase = true)) return raw

        // 协议相对
        if (raw.startsWith("//")) return "https:$raw"

        // http:// 升级为 https
        if (raw.startsWith("http://", ignoreCase = true)) {
            return "https://${raw.substring("http://".length)}"
        }

        // 既无协议也无 // 但是看起来像 host/... → 补 https://
        // 例：i0.hdslb.com/bfs/cover/xxx.jpg
        if (HOST_PREFIX.containsMatchIn(raw)) return "https://$raw"

        // 兜底：原样返回（Coil 会失败，由 UI 兜底）
        return raw
    }

    /** 用于日志/调试：判定 URL 是否绝对地址 */
    fun isAbsolute(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("data:", ignoreCase = true)
    }
}