package com.tingbili.app.data.api

import android.util.Log
import com.tingbili.app.data.api.dto.AuthorVideo
import com.tingbili.app.data.api.dto.AppArchiveData
import com.tingbili.app.data.api.dto.BiliResponse
import com.tingbili.app.data.api.dto.GaiaRegisterReq
import kotlinx.coroutines.delay
import java.util.concurrent.ThreadLocalRandom

class AuthorApi(
    private val service: BiliApiService,
    private val keys: WbiKeyStore,
    private val loginCookie: () -> String
) {
    /**
     * 最近一次探测的逐层结果，给空列表时显示在页面上用。
     * B站风控时回包经常是 code=0 + 空列表，日志里什么都看不出来；把这串数字交给用户截图，
     * 比在电脑前猜"到底是哪一层挂了"快得多。
     */
    @Volatile
    var lastProbe = ""
        private set

    private val slots = Array(4) { "·" }

    /**
     * 拉取 UP 主投稿列表（按时间倒序）。逐层兜底，任一非空即返回：
     *   层1 旧接口 /x/space/arc/search（无签名，风控宽松）＋重试
     *   层2 旧接口 + wbi 签名（部分账号强制要求签名）＋重试
     *   层3 新接口 /x/space/wbi/arc/search（强制 wbi）＋重试
     *   层4 手机端 app.bilibili.com/x/v2/space/archive（独立风控栈）
     */
    suspend fun videos(mid: Long, page: Int, pageSize: Int = 30): Pair<List<AuthorVideo>, Int> {
        for (i in slots.indices) slots[i] = "·"
        if (mid <= 0L) {
            lastProbe = "mid=$mid（不合法，四层都没发）"
            Log.w(TAG, "videos: mid=$mid 不合法，直接返回空")
            return emptyList<AuthorVideo>() to 0
        }
        // 层1：旧接口（无签名），重试 2 次绕过软限流（有时 B 站间歇性返回空）
        tryLegacy(mid, page, pageSize)?.let { lastProbe = probeText(mid); return it }

        // 层2：旧接口 + wbi 签名
        tryLegacyWbi(mid, page, pageSize)?.let { lastProbe = probeText(mid); return it }

        // 层3：新版强制 wbi 接口
        tryNewWbi(mid, page, pageSize)?.let { lastProbe = probeText(mid); return it }

        // 层4：手机端接口
        tryApp(mid, page, pageSize)?.let { lastProbe = probeText(mid); return it }

        lastProbe = probeText(mid)
        Log.e(TAG, "videos 4 层兜底全部为空 (mid=$mid pn=$page)")
        return emptyList<AuthorVideo>() to 0
    }

    /** 数字含义：`code:vlist条数`，`-1`=回包里没数据体，`异常`=请求抛错（如 HTTP 412） */
    private fun probeText(mid: Long) =
        "mid=$mid " + (if (loginCookie().contains("SESSDATA", ignoreCase = true)) "已登录" else "未登录") +
            " 层1=${slots[0]} 层2=${slots[1]} 层3=${slots[2]} 层4=${slots[3]}"

    private suspend fun tryLegacy(mid: Long, page: Int, ps: Int): Pair<List<AuthorVideo>, Int>? =
        withRetry("legacy", 0, page) { pn ->
            val resp = service.authorVideosLegacy(referer = spaceReferer(mid), mid = mid, ps = ps, pn = pn)
            val d = resp.data
            Log.d(TAG, "[1.legacy] code=${resp.code} count=${d?.page?.count} vlist=${d?.list?.vlist?.size}")
            slots[0] = "${resp.code}:${d?.list?.vlist?.size ?: -1}"
            if (d != null && d.list.vlist.isNotEmpty()) d.list.vlist to d.page.count else null
        }

    private suspend fun tryLegacyWbi(mid: Long, page: Int, ps: Int): Pair<List<AuthorVideo>, Int>? {
        val (imgKey, subKey) = keys.keys()
        if (imgKey.isBlank()) return null
        return withRetry("legacy-wbi", 1, page) { pn ->
            val p = mapOf("mid" to mid.toString(), "ps" to ps.toString(), "pn" to pn.toString())
            val signed = WbiSigner.sign(p, imgKey, subKey)
            val resp = service.authorVideosLegacyWbi(
                referer = spaceReferer(mid),
                mid = mid, ps = ps, pn = pn,
                wRid = signed.getValue("w_rid"), wts = signed.getValue("wts")
            )
            val d = resp.data
            Log.d(TAG, "[2.legacy-wbi] code=${resp.code} count=${d?.page?.count} vlist=${d?.list?.vlist?.size}")
            slots[1] = "${resp.code}:${d?.list?.vlist?.size ?: -1}"
            if (d != null && d.list.vlist.isNotEmpty()) d.list.vlist to d.page.count else null
        }
    }

    private suspend fun tryNewWbi(mid: Long, page: Int, ps: Int): Pair<List<AuthorVideo>, Int>? {
        val (imgKey, subKey) = keys.keys()
        if (imgKey.isBlank()) return null
        return withRetry("wbi", 2, page) { pn ->
            val p = fingerprintParams(mid, ps, pn)
            val signed = WbiSigner.sign(p, imgKey, subKey)
            // 注意：参与签名的 dm 参数必须原样随请求发出，少发一个就是 -403 签名拒绝
            var resp = service.authorVideos(
                referer = spaceReferer(mid),
                mid = mid, ps = ps, pn = pn,
                dmImgList = signed.getValue("dm_img_list"),
                dmImgStr = signed.getValue("dm_img_str"),
                dmCoverImgStr = signed.getValue("dm_cover_img_str"),
                dmImgInter = signed.getValue("dm_img_inter"),
                wRid = signed.getValue("w_rid"), wts = signed.getValue("wts")
            )
            // -352 = gaia 风控点名：用 v_voucher 换 token（无验证码时直接发 token）再试一次
            if (resp.code == -352) {
                val voucher = resp.data?.v_voucher
                if (!voucher.isNullOrBlank()) {
                    val token = gaiaToken(voucher)
                    if (!token.isNullOrBlank()) {
                        resp = service.authorVideos(
                            referer = spaceReferer(mid),
                            mid = mid, ps = ps, pn = pn,
                            dmImgList = signed.getValue("dm_img_list"),
                            dmImgStr = signed.getValue("dm_img_str"),
                            dmCoverImgStr = signed.getValue("dm_cover_img_str"),
                            dmImgInter = signed.getValue("dm_img_inter"),
                            wRid = signed.getValue("w_rid"), wts = signed.getValue("wts"),
                            gaiaToken = token
                        )
                    }
                }
            }
            val d = resp.data
            Log.d(TAG, "[3.wbi] code=${resp.code} count=${d?.page?.count} vlist=${d?.list?.vlist?.size}")
            slots[2] = "${resp.code}:${d?.list?.vlist?.size ?: -1}"
            if (d != null && d.list.vlist.isNotEmpty()) d.list.vlist to d.page.count else null
        }
    }

    /**
     * wbi 层的 dm 指纹参数：B站用这组「浏览器环境指纹」区分脚本和真人，
     * 缺了会被 -352 点名（社区逆向结论，2024 起对 space 接口生效）。
     * 值不要求真实，但要随请求签名（WbiSigner 会排序+编码后参与 w_rid）。
     */
    private fun fingerprintParams(mid: Long, ps: Int, pn: Int): Map<String, String> = mapOf(
        "mid" to mid.toString(),
        "ps" to ps.toString(),
        "pn" to pn.toString(),
        "dm_img_list" to "[]",
        "dm_img_str" to b64(randomStr(6)),
        "dm_cover_img_str" to b64(randomStr(12)),
        "dm_img_inter" to "{\"ds\":[],\"wh\":[3387,1554,103],\"of\":[320,675,320]}"
    )

    private fun randomStr(n: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return buildString { repeat(n) { append(chars[ThreadLocalRandom.current().nextInt(chars.length)]) } }
    }

    private fun b64(s: String): String =
        java.util.Base64.getEncoder().encodeToString(s.toByteArray())

    /** v_voucher 换 token；challenge 非空意味着要人过验证码，放弃（null）走后续兜底 */
    private suspend fun gaiaToken(voucher: String): String? = runCatching {
        val resp = service.gaiaRegister(GaiaRegisterReq(voucher))
        val d = resp.data
        Log.d(TAG, "[gaia] code=${resp.code} token=${d?.token != null} challenge=${d?.challenge?.size ?: 0}")
        d?.token?.takeIf { it.isNotBlank() && d.challenge.isEmpty() }
    }.onFailure { Log.w(TAG, "[gaia] 注册失败 ${it.message}") }.getOrNull()

    private suspend fun tryApp(mid: Long, page: Int, ps: Int): Pair<List<AuthorVideo>, Int>? {
        return try {
            val resp: BiliResponse<AppArchiveData> =
                service.authorAppArchive(mid = mid, pn = page, ps = ps)
            val d = resp.data
            Log.d(TAG, "[4.app] code=${resp.code} count=${d?.page?.count} vlist=${d?.vlist?.size}")
            slots[3] = "${resp.code}:${d?.vlist?.size ?: -1}"
            if (d != null && d.vlist.isNotEmpty()) d.vlist to d.page.count else null
        } catch (e: Exception) {
            Log.w(TAG, "[4.app] 失败 ${e.message}")
            null
        }
    }

    /** 对同一页尝试 N 次：B 站软限流时 code=0 但间歇性返回空，短间隔重试可明显提成功率 */
    private suspend fun withRetry(
        tag: String,
        slot: Int,
        pn: Int,
        attempt: suspend (Int) -> Pair<List<AuthorVideo>, Int>?
    ): Pair<List<AuthorVideo>, Int>? {
        var res: Pair<List<AuthorVideo>, Int>? = null
        repeat(RETRY) { i ->
            try {
                res = attempt(pn)
                if (res != null) return@repeat
                if (i < RETRY - 1) delay((i + 1) * 400L)
            } catch (e: Exception) {
                Log.w(TAG, "[$tag] 第${i + 1}次异常 ${e.message}")
                slots[slot] = "异常"
                if (i < RETRY - 1) delay((i + 1) * 400L)
            }
        }
        return res
    }

    /**
     * space 系接口必须带 UP 主主页 Referer（2026-09-28 实测）：全局的
     * `https://www.bilibili.com/` 会让四层接口分别吃到 412 / -799 / -352 风控拦截；
     * 换成 `https://space.bilibili.com/{mid}/` 后连无签名的层1 都直接放行。
     */
    private fun spaceReferer(mid: Long) = "https://space.bilibili.com/$mid/"

    private companion object {
        const val TAG = "AuthorApi"
        const val RETRY = 2
    }
}