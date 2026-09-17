package com.tingbili.app.player

import android.util.Log
import com.tingbili.app.data.api.BiliApiService
import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.data.repo.LibraryRepository
import com.tingbili.app.data.repo.PlayRepository
import com.tingbili.app.data.repo.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * 播放拉起器：把任意入口（搜索/听单/历史）转成 ExoPlayer 播放，并写本地记录。
 * 队列元素使用 PartItem（含 part 名称与时长），音频记录队列为空。
 */
class PlayerLauncher(
    private val holder: PlayerHolder,
    private val playRepo: PlayRepository,
    private val library: LibraryRepository,
    private val biliService: BiliApiService,
    private val settings: com.tingbili.app.data.local.SettingsStore? = null
) {
    /** 搜索结果点击播放：video 走 pagelist→resolveVideo→第一P；audio 走音频区 */
    suspend fun playSearchItem(item: SearchItem) {
        // 封面：video 用 pic，audio 用 cover
        val cover = if (item.cover.isNotBlank()) item.cover else item.pic
        if (item.type == "audio" || item.bvid.isBlank()) {
            val auid = item.aid
            if (auid <= 0L) return
            val url = playRepo.resolveAudioUrl(null, null, auid) ?: return
            val record = BookRecord(
                id = "audio:$auid",
                title = SearchRepository.stripHtml(item.title),
                owner = item.author,
                type = "audio",
                cover = cover,
                currentPart = 1,
                auid = auid,
                ownerMid = item.uid,
                ownerAvatar = item.upic
            )
            holder.play(record, url, emptyList(), 0L, 1.0f)
            library.recordPlayed(record)
        } else {
            val (record, queue) = playRepo.resolveVideo(item.bvid) ?: return
            val r = record.copy(
                title = record.title.ifBlank { SearchRepository.stripHtml(item.title) },
                owner = record.owner.ifBlank { item.author },
                cover = cover.ifBlank { record.cover },
                ownerMid = item.uid,
                ownerAvatar = item.upic.ifBlank { record.ownerAvatar }
            )
            // 兜底：搜索结果没带 mid 时（如 websearch 端），用 view 接口反查
            val rWithAuthor = if (r.ownerMid <= 0L) enrichAuthor(r) else r
            val url = playRepo.resolveAudioUrl(rWithAuthor.bvid, rWithAuthor.currentCid, null) ?: return
            holder.play(rWithAuthor, url, queue, 0L, 1.0f)
            library.recordPlayed(rWithAuthor)
        }
    }

    /** 听单/历史续播：用 Room 已有进度续播，重建分P 队列支持上下集 */
    suspend fun playRecord(record: BookRecord) {
        val existing = library.get(record.id) ?: record
        val positionMs = existing.progressMs.coerceAtLeast(0L)
        // 解析速度：BookRecord.speed > 按 UP 主记忆 > 全局默认
        val initialSpeed = when {
            existing.speed > 0.05f && existing.speed < 4f -> existing.speed
            else -> resolvePerAuthorSpeed(existing.ownerMid) ?: 1.0f
        }
        if (existing.type == "audio" || existing.bvid.isNullOrBlank()) {
            val auid = existing.auid
            if (auid == null || auid <= 0L) return
            val url = playRepo.resolveAudioUrl(null, null, auid) ?: return
            val r = existing.copy(progressMs = 0L, speed = initialSpeed)
            holder.play(r, url, emptyList(), positionMs, initialSpeed)
            library.recordPlayed(r)
        } else {
            val bvid = existing.bvid!!
            // 优先重建完整分P 队列；pagelist 失败则退回单P
            val queue = playRepo.resolveVideo(bvid)?.second
                ?: listOf(PartItem(bvid, existing.currentCid ?: 0L, "", 0L))
                    .filter { it.cid > 0L }
            val cid = existing.currentCid ?: queue.firstOrNull()?.cid ?: return
            val url = playRepo.resolveAudioUrl(bvid, cid, null) ?: return
            val idx = queue.indexOfFirst { it.cid == cid }.coerceAtLeast(0)
            // 兜底：早期记录可能没存 ownerMid，调 view 接口补全后再播放
            val base = existing.copy(
                progressMs = 0L,
                currentCid = cid,
                currentPart = idx + 1,
                totalParts = queue.size,
                speed = initialSpeed
            )
            val r = if (base.ownerMid <= 0L) enrichAuthor(base) else base
            holder.play(r, url, queue, positionMs, initialSpeed)
            holder.moveQueueTo(idx)
            library.recordPlayed(r)
        }
    }

    /** 按 UP 主 mid 解析倍速（如开启）。返回 null 表示未命中 */
    private suspend fun resolvePerAuthorSpeed(mid: Long): Float? {
        val s = settings ?: return null
        if (mid <= 0L) return null
        val remember = s.rememberSpeedPerAuthor.firstOrNull() ?: false
        if (!remember) return null
        val sp = s.authorSpeedFlow(mid.toString()).firstOrNull()
        return sp?.takeIf { it in 0.5f..3.0f }
    }

    /**
     * 用 bvid 调 /x/web-interface/view 反查 up 主 mid/upic/name；
     * 失败时原样返回。view 接口比 view 用 wbi 端风控宽松，多数情况可用。
     */
    private suspend fun enrichAuthor(r: BookRecord): BookRecord = withContext(Dispatchers.IO) {
        val bvid = r.bvid ?: return@withContext r
        try {
            val resp = biliService.view(bvid)
            val o = resp.data?.owner
            if (o != null && o.mid > 0L) {
                Log.i("PlayerLauncher", "view 兜底反查 UP：${o.name} mid=${o.mid}")
                r.copy(
                    owner = o.name.ifBlank { r.owner },
                    ownerMid = o.mid,
                    ownerAvatar = o.face.ifBlank { r.ownerAvatar }
                )
            } else r
        } catch (t: Throwable) {
            Log.w("PlayerLauncher", "view 兜底失败：${t.message}")
            r
        }
    }

    /** 播放本地已下载分集（离线），不联网 */
    suspend fun playLocalFile(item: com.tingbili.app.download.DownloadManager.Item, localFile: java.io.File) {
        val record = BookRecord(
            id = item.key,
            title = item.bookTitle.ifBlank { item.partTitle },
            owner = "",
            type = if (item.auid != null) "audio" else "video",
            cover = item.cover,
            bvid = item.bvid,
            auid = item.auid,
            currentCid = item.cid,
            currentPart = 1,
            totalParts = 0,
            progressMs = 0L
        )
        val uri = android.net.Uri.fromFile(localFile).toString()
        holder.play(record, uri, emptyList(), 0L, 1.0f)
        library.recordPlayed(record)
    }

    /** 下一集：队列下标 +1 后解析播放（从头），写记录 */
    suspend fun nextPart() {
        val queue = holder.currentQueue()
        if (queue.isEmpty()) return
        val next = holder.currentQueueIndex() + 1
        if (next >= queue.size) return
        playQueueItem(queue, next)
    }

    /** 上一集：队列下标 -1 */
    suspend fun prevPart() {
        val queue = holder.currentQueue()
        val prev = holder.currentQueueIndex() - 1
        if (prev < 0) return
        playQueueItem(queue, prev)
    }

    /** 跳转到指定队列下标 */
    suspend fun jumpToPart(index: Int) {
        val queue = holder.currentQueue()
        if (index !in queue.indices) return
        playQueueItem(queue, index)
    }

    private suspend fun playQueueItem(queue: List<PartItem>, index: Int) {
        val item = queue[index]
        val url = playRepo.resolveAudioUrl(item.bvid, item.cid, null) ?: return
        val cur = holder.record.value ?: return
        val r = cur.copy(currentCid = item.cid, currentPart = index + 1, progressMs = 0L)
        holder.play(r, url, queue, 0L, 1.0f)
        holder.moveQueueTo(index)
        library.recordPlayed(r)
    }
}
