package com.tingbili.app.download

import android.content.Context
import android.util.Log
import com.tingbili.app.data.local.CookieStore
import com.tingbili.app.data.repo.PlayRepository
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
 */
class DownloadManager(
    private val context: Context,
    private val playRepo: PlayRepository?,
    cookieStore: CookieStore? = null
) {
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    // B 站音频直链 412/403 频发 —— 必须带 buvid3/buvid4/b_nut 才能下载成功。
    // 没有 CookieStore 时降级为空 cookie header，避免阻塞 AppContainer 兜底路径。
    private val cookieHeader: String = runCatching {
        runBlocking { cookieStore?.cookieHeader() ?: "" }
    }.getOrDefault("").ifBlank {
        // 兜底：用 WbiSigner 现场生成一组，让请求至少带上 buvid3
        "buvid3=${com.tingbili.app.data.api.WbiSigner.randomBuvid3()}; " +
            "buvid4=${com.tingbili.app.data.api.WbiSigner.randomBuvid4()}; " +
            "b_nut=${System.currentTimeMillis() / 1000}"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

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

    /** 下载一集音频到本地。已在本地则直接返回 true。 */
    suspend fun download(
        recordId: String, bookTitle: String, cover: String,
        bvid: String?, cid: Long?, auid: Long?, partTitle: String
    ): Boolean {
        val key = keyOf(recordId, bvid, cid, auid)
        if (isDownloaded(key)) return true
        if (_tasks.value[key] is TaskState.Running) return true
        val repo = playRepo ?: run {
            _tasks.value = _tasks.value + (key to TaskState.Failed("下载服务未就绪"))
            return false
        }
        val url = repo.resolveAudioUrl(bvid, cid, auid) ?: run {
            Log.w(TAG, "download: 解析音频 URL 失败 recordId=$recordId")
            _tasks.value = _tasks.value + (key to TaskState.Failed("解析 URL 失败"))
            return false
        }

        _tasks.value = _tasks.value + (key to TaskState.Running(0, null))
        val fileName = buildFileName(bookTitle, partTitle, key)
        return try {
            val req = Request.Builder().url(url)
                .header("Referer", "https://www.bilibili.com/")
                .header("User-Agent", UA)
                .header("Cookie", cookieHeader)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    _tasks.value = _tasks.value + (key to TaskState.Failed("HTTP ${resp.code}"))
                    return false
                }
                val body = resp.body ?: run {
                    _tasks.value = _tasks.value + (key to TaskState.Failed("空响应体"))
                    return false
                }
                val total = body.contentLength()
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
                val item = Item(
                    key = key, bookId = recordId, bookTitle = bookTitle, cover = cover,
                    bvid = bvid, cid = cid, auid = auid,
                    partTitle = partTitle, fileName = fileName,
                    sizeBytes = out.length(), downloadedAt = System.currentTimeMillis()
                )
                _index.value = _index.value.filter { it.key != key } + item
                persist()
                _tasks.value = _tasks.value + (key to TaskState.Idle)
                Log.i(TAG, "下载完成 $key -> ${out.absolutePath} ${out.length()}B")
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "下载失败 $key: ${e.message}")
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