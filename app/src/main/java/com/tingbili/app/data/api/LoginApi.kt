package com.tingbili.app.data.api

import android.util.Log
import com.tingbili.app.data.api.dto.QrGenerateData
import com.tingbili.app.data.api.dto.QrPollData
import java.net.URLDecoder

/** 轮询状态码（data.code） */
object QrPollCode {
    const val WAITING = 86101
    const val SCANNED = 86090
    const val EXPIRED = 86038
    const val SUCCESS = 0
}

/**
 * B站扫码登录。流程：generate 出二维码内容 → 用户 B站 App 扫码确认 → poll 到
 * code=0，data.url 里带整套登录 cookie 参数 → 解出 cookie 字符串交给 CookieStore。
 */
class LoginApi(private val service: BiliApiService) {

    suspend fun generate(): QrGenerateData {
        val resp = service.qrGenerate()
        if (resp.code != 0) error("二维码生成失败 code=${resp.code}")
        return resp.data ?: error("二维码生成失败：空响应")
    }

    /**
     * 轮询一次。第二个返回值是从 `set-cookie` 头里摘出来的 cookie（新版端点靠头下发登录态）；
     * 头里没有就留空串，调用方再退回解析 `data.url`。
     */
    suspend fun pollOnce(key: String): Pair<QrPollData, String> {
        val raw = service.qrPoll(qrcodeKey = key)
        val resp = raw.body() ?: error("轮询失败：空响应 HTTP ${raw.code()}")
        if (resp.code != 0) error("轮询失败 code=${resp.code}")
        val fromHeaders = raw.headers().values("set-cookie")
            .joinToString("; ") { it.substringBefore(';').trim() }
        return (resp.data ?: error("轮询失败：空响应")) to fromHeaders
    }

    companion object {
        const val TAG = "LoginApi"

        /** 登录态字段：只有这几个允许覆盖用户手贴的值，其它字段一律不动（用户明确要求） */
        private val LOGIN_KEYS = setOf(
            "sessdata", "bili_jct", "dedeuserid", "dedeuserid__ckmd5", "sid"
        )

        /**
         * 从整段 cookie（比如 WebView 的 CookieManager 返回的全集）里只挑登录字段。
         * 不挑的话，web 端的 buvid3 之类会被当作新值并到手贴 cookie 后面，出现同名两份。
         */
        fun loginFieldsOnly(raw: String): String = raw.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.substringBefore('=').lowercase() in LOGIN_KEYS }
            .joinToString("; ")

        /**
         * 合并 cookie：existing 的所有字段保留，login 的登录字段（SESSDATA 等）覆盖同名项。
         * 扫码登录是第二种登录方式——用户已粘贴 cookie 里的非登录字段绝不能动（用户明确要求）。
         */
        fun mergeCookies(existing: String, login: String): String {
            if (existing.isBlank()) return login
            val loginKeys = LOGIN_KEYS

            fun parse(raw: String): MutableList<Pair<String, String>> = raw
                .split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map {
                    val i = it.indexOf('=')
                    if (i <= 0) it to "" else it.substring(0, i) to it.substring(i + 1)
                }
                .toMutableList()

            val result = parse(existing)
            result.removeAll { it.first.lowercase() in loginKeys }
            result.addAll(parse(login))
            return result.joinToString("; ") { if (it.second.isEmpty()) it.first else "${it.first}=${it.second}" }
        }

        /**
         * 登录成功后 data.url 是一个带 query 的跨域跳转地址
         * （DedeUserID / SESSDATA / bili_jct / DedeUserID__ckMd5 / sid），
         * 值是 URL 编码过的，拼 cookie 前要解回原文。
         */
        fun cookiesFromUrl(url: String): String? {
            val params = url.substringAfter('?', "")
                .split('&')
                .mapNotNull {
                    val i = it.indexOf('=')
                    if (i <= 0) null
                    else it.substring(0, i) to runCatching { URLDecoder.decode(it.substring(i + 1), "UTF-8") }.getOrDefault(it.substring(i + 1))
                }.toMap()
            val sessdata = params["SESSDATA"]
            val jct = params["bili_jct"] ?: return null
            val dede = params["DedeUserID"] ?: return null
            if (sessdata.isNullOrBlank()) {
                Log.w(TAG, "登录回调缺 SESSDATA")
                return null
            }
            val parts = mutableListOf("SESSDATA=$sessdata", "bili_jct=$jct", "DedeUserID=$dede")
            params["DedeUserID__ckMd5"]?.let { parts.add("DedeUserID__ckMd5=$it") }
            params["sid"]?.let { parts.add("sid=$it") }
            return parts.joinToString("; ")
        }
    }
}
