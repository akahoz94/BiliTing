package com.tingbili.app.download

import android.content.Context
import android.util.Log
import com.tingbili.app.data.local.CookieStore
import com.tingbili.app.data.repo.PlayRepository
import com.tingbili.app.util.ErrorBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * 离线下载管理：分集音频下载到 filesDir/downloads（无需存储权限），
 * 用 JSON 索引文件记录已下载分集（避免引入 Room 迁移）。进度经 tasks 暴露给 UI。
 *
 * 关键设计：
 *   - 复用 AppContainer 注入的 OkHttpClient（已带 UA/Referer/Cookie 全局拦截器）
 *   - 多 CDN 候选 URL（upcdn → upgcxcode）顺序重试
 *   - auid 路径解析失败时自动 fallback 到 bvid+cid DASH
 *   - 请求带 Range: bytes=0- 让 CDN 返回 206，便于流式下载与失败即时重试
 */
class DownloadManager(
    private val context: Context,
    private val playRepo: PlayRepository?,
    private val cookieStore: CookieStore? = null,
    private val httpClient: OkHttpClient? = null
) {
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    private val client: OkHttpClient by lazy {
        httpClient ?: OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /** 从 CookieStore 读 cookie；失败时退回临时 buvid3。OkHttp client 的拦截器会补 UA/Referer。 */
    private fun cookieHeader(): String {
        val live = runCatching {
            runBlocking { cookieStore?.cookieHeader() ?: "" }
        }.getOrDefault("")
        return live.ifBlank {
            "buvid3=${com.tingbili.app.data.api.WbiSigner.randomBuvid3()}; " +
                "buvid4=${com.tingbili.app.data.api.WbiSigner.randomBuvid4()}; " +
                "b_nut=${System.currentTimeMillis() / 1000}"
        }
    }

    @Serializable
    data class Item(
        val key: String,
        val bookId: String,
        val bookTitle: String,
        val cover: String,
        val bvid: String?,
        val cid: Long?,
        val auid: Long?,
        val partTitle: String,
        val fileName: String,
        val sizeBytes: Long,
        val downloadedAt: Long
    )

    sealed interface TaskState {
        data object Idle : TaskState
        data class Running(val bytes: Long, val total: Long?) : TaskState
        data class Failed(val msg: String) : TaskState
    }

    private val _tasks = MutableStateFlow<Map<String, TaskState>>(emptyMap())
    val tasks: StateFlow<Map<String, TaskState>> = _tasks

    private val _index = MutableStateFlow<List<Item>>(loadIndex())
    val index: StateFlow<List<Item>> = _index

    fun keyOf(recordId: String, bvid: String?, cid: Long?, auid: Long?): String =
        if (!auid.isNullOrZero()) "audio:$auid" else "video:$recordId:$cid"

    fun itemFor(key: String): Item? = _index.value.firstOrNull { it.key == key }

    fun isDownloaded(key: String): Boolean = _index.value.any { it.key == key }

    fun localFile(item: Item): File = File(dir, item.fileName)

    /**
     * 下载一集音频。
     *
     * 流程：
     *   1) playRepo.resolveAudioUrl 解析首个候选 URL
     *   2) 失败时尝试 playRepo.candidates() 拉多 CDN 候选，逐个 GET 重试
     *   3) auid 单独失败时通过 service.pagelist 反查 bvid+cid 再走 DASH（按入参 bookId 推断 bvid）
     *   4) 任一 URL 成功即写入本地，索引 + 进度
     */
    suspend fun download(
        recordId: String, bookTitle: String, cover: String,
        bvid: String?, cid: Long?, auid: Long?, partTitle: String
    ): Boolean {
        val key = keyOf(recordId, bvid, cid, auid)
        if (isDownloaded(key)) return true
        if (_tasks.value[key] is TaskState.Running) return true
        val repo = playRepo ?: run {
            val msg = "下载服务未就绪"
            _tasks.value = _tasks.value + (key to TaskState.Failed(msg))
            ErrorBus.post(message = "下载失败：$msg")
            return false
        }

        _tasks.value = _tasks.value + (key to TaskState.Running(0, null))
        val fileName = buildFileName(bookTitle, partTitle, key)

        // 解析候选 URL：先按入参解析，再尝试播放库反查 bvid+cid
        val candidates = resolveCandidates(repo, bvid, cid, auid, recordId)
        if (candidates.isEmpty()) {
            val msg = "未拿到可用音频 URL（bvid=${bvid ?: "空"} cid=${cid ?: "空"} auid=${auid ?: "空"}；常见原因：未登录 → 设置里粘 SESSDATA 后重试）"
            Log.w(TAG, "[DL] $msg key=$key")
            _tasks.value = _tasks.value + (key to TaskState.Failed(msg))
            ErrorBus.post(message = "下载失败：$msg")
            return false
        }
        Log.i(TAG, "[DL] 解析到 ${candidates.size} 个候选 URL key=$key")

        for ((idx, url) in candidates.withIndex()) {
            Log.i(TAG, "[DL] 尝试 URL#${idx + 1}/${candidates.size} key=$key url=${url.take(80)}...")
            val ok = tryDownloadOne(key, url, fileName, bookTitle, cover, recordId, bvid, cid, auid, partTitle)
            if (ok) return true
        }

        val msg = "全部 ${candidates.size} 个 URL 均失败"
        _tasks.value = _tasks.value + (key to TaskState.Failed(msg))
        ErrorBus.post(message = "下载失败：$msg")
        return false
    }

    /**
     * 解析下载候选 URL 列表。
     * - 已有 bvid+cid → candidates(bvid,cid) 多 CDN
     * - 仅有 auid     → audioApi.url(auid)（如有）
     * - 若 auid 单独失败：通过记录 id 推断 bvid 重新 pagelist 拿 cid，再走 DASH
     */
    private suspend fun resolveCandidates(
        repo: PlayRepository,
        bvid: String?, cid: Long?, auid: Long?,
        recordId: String
    ): List<String> {
        // 只调一次 playUrl：先 resolveAudioUrl 再 candidates 会二次请求同视频，
        // B 站短时间内第二次 playUrl 触发风控返回空 → 一直"未拿到可用 URL"
        val multi = repo.candidates(bvid, cid).toMutableList()

        // DASH 没拿到且有 auid → 走音频区直链
        if (multi.isEmpty() && auid != null && auid > 0L) {
            val au = repo.resolveAudioUrl(null, null, auid)
            if (!au.isNullOrBlank()) multi.add(au)
        }

        // 兜底：recordId="video:BVxxx" 时从 ID 推断 bvid 再 pagelist
        if (multi.isEmpty() && recordId.startsWith("video:") && bvid.isNullOrBlank()) {
            val guessedBvid = recordId.removePrefix("video:")
            runCatching {
                val pages = repo.resolveVideo(guessedBvid)?.second.orEmpty()
                val firstCid = pages.firstOrNull()?.cid ?: 0L
                if (firstCid > 0L) {
                    multi.addAll(repo.candidates(guessedBvid, firstCid))
                }
            }.onFailure {
                Log.w(TAG, "[DL] pagelist 回退失败 bvid=$guessedBvid: ${it.message}")
            }
        }
        return multi.distinct()
    }

    private suspend fun tryDownloadOne(
        key: String, url: String, fileName: String,
        bookTitle: String, cover: String,
        recordId: String, bvid: String?, cid: Long?, auid: Long?, partTitle: String
    ): Boolean {
        return try {
            // 不手动设置 Referer/UA/Cookie：client 已带全局拦截器统一加登录态，
            // 手动覆盖会把 SESSDATA 冲成临时 buvid3 导致 CDN 403。只补 Range。
            val req = Request.Builder().url(url)
                .header("Range", "bytes=0-")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val hint = when (resp.code) {
                        403 -> "（需要 SESSDATA 登录态；先在设置里粘登录后的 cookie）"
                        412 -> "（B 站风控拦截，cookie 不全；请粘贴登录态 cookie）"
                        404 -> "（视频已失效或被删除）"
                        416 -> "（Range 越界，CDN 不支持分段）"
                        else -> ""
                    }
                    Log.w(TAG, "[DL] HTTP 失败 key=$key code=${resp.code} url=${url.take(80)}")
                    _tasks.value = _tasks.value + (key to TaskState.Failed("HTTP ${resp.code} $hint"))
                    if (resp.code == 403 || resp.code == 412) {
                        ErrorBus.post(message = "下载失败：HTTP ${resp.code} $hint")
                    }
                    return false
                }
                val body = resp.body ?: run {
                    _tasks.value = _tasks.value + (key to TaskState.Failed("空响应体"))
                    return false
                }
                val total = body.contentLength().takeIf { it > 0 }
                val out = File(dir, fileName)
                body.byteStream().use { ins ->
                    out.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            done += n
                            _tasks.value = _tasks.value + (key to TaskState.Running(done, total))
                        }
                    }
                }
                if (out.length() <= 0L) {
                    runCatching { out.delete() }
                    _tasks.value = _tasks.value + (key to TaskState.Failed("响应体为空"))
                    return false
                }
                val item = Item(
                    key = key, bookId = recordId, bookTitle = bookTitle, cover = cover,
                    bvid = bvid, cid = cid, auid = auid,
                    partTitle = partTitle, fileName = fileName,
                    sizeBytes = out.length(), downloadedAt = System.currentTimeMillis()
                )
                _index.value = _index.value.filter { it.key != key } + item
                persist()
                _tasks.value = _tasks.value + (key to TaskState.Idle)
                Log.i(TAG, "[DL] 下载完成 key=$key -> ${out.absolutePath} ${out.length()}B")
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "[DL] 下载失败 key=$key url=${url.take(80)}: ${e.message}", e)
            _tasks.value = _tasks.value + (key to TaskState.Failed(e.message ?: "网络错误"))
            false
        }
    }

    /** 删除一个已下载分集（文件 + 索引） */
    fun delete(key: String) {
        val item = _index.value.firstOrNull { it.key == key } ?: return
        runCatching { localFile(item).delete() }
        _index.value = _index.value.filter { it.key != key }
        persist()
    }

    /** 清空所有已下载分集（用于"全部删除"按钮），返回被删除的字节数 */
    fun deleteAll(): Long {
        val total = _index.value.sumOf { it.sizeBytes }
        _index.value.forEach { runCatching { localFile(it).delete() } }
        _index.value = emptyList()
        persist()
        return total
    }

    /** 清理失败/中断的残留任务状态 */
    fun clearTask(key: String) {
        _tasks.value = _tasks.value - key
    }

    fun totalSizeBytes(): Long = _index.value.sumOf { it.sizeBytes }

    private fun buildFileName(bookTitle: String, partTitle: String, key: String): String {
        val base = (if (partTitle.isNotBlank()) partTitle else bookTitle.ifBlank { key })
        val safe = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(48)
        return "${if (safe.isBlank()) "part" else safe}-${key.hashCode().and(0xffff)}.m4a"
    }

    private fun persist() {
        runCatching { indexFile.writeText(json.encodeToString(_index.value)) }
    }

    private fun loadIndex(): List<Item> = runCatching {
        if (indexFile.exists()) json.decodeFromString<List<Item>>(indexFile.readText()) else emptyList()
    }.getOrElse { emptyList() }

    private fun Long?.isNullOrZero(): Boolean = this == null || this == 0L

    companion object {
        const val TAG = "DownloadManager"
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        val json = Json { ignoreUnknownKeys = true }

        /** 空壳下载管理器：playRepo 为 null，所有下载请求会立刻失败但不抛异常。用于 AppContainer 兜底 */
        fun empty(context: Context): DownloadManager = DownloadManager(context, null)
    }
}