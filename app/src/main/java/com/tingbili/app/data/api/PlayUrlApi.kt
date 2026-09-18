package com.tingbili.app.data.api

import android.util.Log

class PlayUrlApi(
    private val service: BiliApiService,
    private val keys: WbiKeyStore
) {
    @Volatile var lastError: String = ""

    /**
     * 返回视频 DASH 音频流 URL；按音频质量 id 降序选择（30280->30232->30216->30251 等）。
     *
     * B 站返回的 baseUrl 是**相对路径**（如 `/upgcxcode/00/00/...m4s`），必须拼 CDN host；
     * 这里返回**首个候选 URL**，DownloadManager 失败时按 CdnUrl.candidates() 顺序重试。
     *
     * 同步提供 [candidates] 用于下载/播放失败重试。
     */
    suspend fun audioUrl(bvid: String, cid: Long): String? {
        return candidates(bvid, cid).firstOrNull()
    }

    /** 返回同一音频流的多 CDN 候选 URL（下载/重试用） */
    suspend fun candidates(bvid: String, cid: Long): List<String> {
        val (imgKey, subKey) = keys.keys()
        val params = mapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to "30280",       // 320k 高清优先（id 越大质量越高）
            "fnval" to "16",       // DASH
            "fourk" to "0",
            "platform" to "html5"
        )
        val signed = WbiSigner.sign(params, imgKey, subKey)
        val resp = try {
            service.playUrl(
                bvid = bvid, cid = cid, qn = 30280, fnval = 16, fourk = 0,
                wRid = signed.getValue("w_rid"), wts = signed.getValue("wts")
            )
        } catch (t: Throwable) {
            lastError = "网络异常: ${t.message}"
            Log.w(TAG, "[DL] $lastError bvid=$bvid cid=$cid")
            return emptyList()
        }
        // 业务码非 0：B 站返回"未登录/风控/视频不存在"等
        if (resp.code != 0) {
            lastError = "playUrl code=${resp.code}: ${resp.message}"
            Log.w(TAG, "[DL] $lastError bvid=$bvid cid=$cid")
            return emptyList()
        }
        val audios = resp.data?.dash?.audio.orEmpty().sortedByDescending { it.id }
        val first = audios.firstOrNull() ?: run {
            lastError = "playUrl 返回了但无 dash.audio"
            Log.w(TAG, "[DL] $lastError bvid=$bvid cid=$cid")
            return emptyList()
        }
        val raw = first.baseUrl.takeIf { it.isNotBlank() } ?: first.baseUrl2
        if (raw.isBlank()) {
            Log.w(TAG, "[DL] playUrl audio.baseUrl/baseUrl2 都空 bvid=$bvid cid=$cid")
            return emptyList()
        }
        return CdnUrl.candidates(raw)
    }

    /**
     * 降级通道：playUrl 返回 durl（非 DASH，HTML5 老路径）。这种 URL 通常是**绝对地址**，
     * 直接用即可。返回 null 表示服务端没给 durl。
     */
    suspend fun legacyDurl(bvid: String, cid: Long): String? {
        val (imgKey, subKey) = keys.keys()
        val params = mapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to "80",
            "fnval" to "1",
            "fourk" to "0",
            "platform" to "html5"
        )
        val signed = WbiSigner.sign(params, imgKey, subKey)
        val resp = try {
            service.playUrl(
                bvid = bvid, cid = cid, qn = 80, fnval = 1, fourk = 0,
                wRid = signed.getValue("w_rid"), wts = signed.getValue("wts")
            )
        } catch (t: Throwable) {
            Log.w(TAG, "[DL] legacyDurl 网络异常: ${t.message}")
            return null
        }
        if (resp.code != 0) {
            Log.w(TAG, "[DL] legacyDurl 业务码异常 code=${resp.code} msg=${resp.message}")
            return null
        }
        return resp.data?.durl?.firstOrNull()?.url?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "PlayUrlApi"
    }
}