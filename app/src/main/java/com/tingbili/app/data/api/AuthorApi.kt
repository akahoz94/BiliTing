package com.tingbili.app.data.api

import android.util.Log
import com.tingbili.app.data.api.dto.AuthorVideo
import com.tingbili.app.data.api.dto.BiliResponse
import com.tingbili.app.data.api.dto.AppArchiveData
import kotlinx.coroutines.delay

class AuthorApi(
    private val service: BiliApiService,
    private val keys: WbiKeyStore
) {
    /**
     * 拉取 UP 主投稿列表（按时间倒序）。逐层兜底，任一非空即返回：
     *   层1 旧接口 /x/space/arc/search（无签名，风控宽松）＋重试
     *   层2 旧接口 + wbi 签名（部分账号强制要求签名）＋重试
     *   层3 新接口 /x/space/wbi/arc/search（强制 wbi）＋重试
     *   层4 手机端 app.bilibili.com/x/v2/space/archive（独立风控栈）
     */
    suspend fun videos(mid: Long, page: Int, pageSize: Int = 30): Pair<List<AuthorVideo>, Int> {
        if (mid <= 0L) {
            Log.w(TAG, "videos: mid=$mid 不合法，直接返回空")
            return emptyList<AuthorVideo>() to 0
        }
        // 层1：旧接口（无签名），重试 2 次绕过软限流（有时 B 站间歇性返回空）
        tryLegacy(mid, page, pageSize)?.let { return it }

        // 层2：旧接口 + wbi 签名
        tryLegacyWbi(mid, page, pageSize)?.let { return it }

        // 层3：新版强制 wbi 接口
        tryNewWbi(mid, page, pageSize)?.let { return it }

        // 层4：手机端接口
        tryApp(mid, page, pageSize)?.let { return it }

        Log.e(TAG, "videos 4 层兜底全部为空 (mid=$mid pn=$page)")
        return emptyList<AuthorVideo>() to 0
    }

    private suspend fun tryLegacy(mid: Long, page: Int, ps: Int): Pair<List<AuthorVideo>, Int>? =
        withRetry("legacy", page) { pn ->
            val resp = service.authorVideosLegacy(mid = mid, ps = ps, pn = pn)
            val d = resp.data
            Log.d(TAG, "[1.legacy] code=${resp.code} count=${d?.page?.count} vlist=${d?.list?.vlist?.size}")
            if (d != null && d.list.vlist.isNotEmpty()) d.list.vlist to d.page.count else null
        }

    private suspend fun tryLegacyWbi(mid: Long, page: Int, ps: Int): Pair<List<AuthorVideo>, Int>? {
        val (imgKey, subKey) = keys.keys()
        if (imgKey.isBlank()) return null
        return withRetry("legacy-wbi", page) { pn ->
            val p = mapOf("mid" to mid.toString(), "ps" to ps.toString(), "pn" to pn.toString())
            val signed = WbiSigner.sign(p, imgKey, subKey)
            val resp = service.authorVideosLegacyWbi(
                mid = mid, ps = ps, pn = pn,
                wRid = signed.getValue("w_rid"), wts = signed.getValue("wts")
            )
            val d = resp.data
            Log.d(TAG, "[2.legacy-wbi] code=${resp.code} count=${d?.page?.count} vlist=${d?.list?.vlist?.size}")
            if (d != null && d.list.vlist.isNotEmpty()) d.list.vlist to d.page.count else null
        }
    }

    private suspend fun tryNewWbi(mid: Long, page: Int, ps: Int): Pair<List<AuthorVideo>, Int>? {
        val (imgKey, subKey) = keys.keys()
        if (imgKey.isBlank()) return null
        return withRetry("wbi", page) { pn ->
            val p = mapOf("mid" to mid.toString(), "ps" to ps.toString(), "pn" to pn.toString())
            val signed = WbiSigner.sign(p, imgKey, subKey)
            val resp = service.authorVideos(
                mid = mid, ps = ps, pn = pn,
                wRid = signed.getValue("w_rid"), wts = signed.getValue("wts")
            )
            val d = resp.data
            Log.d(TAG, "[3.wbi] code=${resp.code} count=${d?.page?.count} vlist=${d?.list?.vlist?.size}")
            if (d != null && d.list.vlist.isNotEmpty()) d.list.vlist to d.page.count else null
        }
    }

    private suspend fun tryApp(mid: Long, page: Int, ps: Int): Pair<List<AuthorVideo>, Int>? {
        return try {
            val resp: BiliResponse<AppArchiveData> =
                service.authorAppArchive(mid = mid, pn = page, ps = ps)
            val d = resp.data
            Log.d(TAG, "[4.app] code=${resp.code} count=${d?.page?.count} vlist=${d?.vlist?.size}")
            if (d != null && d.vlist.isNotEmpty()) d.vlist to d.page.count else null
        } catch (e: Exception) {
            Log.w(TAG, "[4.app] 失败 ${e.message}")
            null
        }
    }

    /** 对同一页尝试 N 次：B 站软限流时 code=0 但间歇性返回空，短间隔重试可明显提成功率 */
    private suspend fun withRetry(
        tag: String,
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
                if (i < RETRY - 1) delay((i + 1) * 400L)
            }
        }
        return res
    }

    private companion object {
        const val TAG = "AuthorApi"
        const val RETRY = 2
    }
}