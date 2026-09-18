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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class PlayerLauncher(
    private val holder: PlayerHolder,
    private val playRepo: PlayRepository,
    private val library: LibraryRepository,
    private val biliService: BiliApiService,
    private val settings: com.tingbili.app.data.local.SettingsStore? = null
) {
    suspend fun playSearchItem(item: SearchItem) {
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
            val rWithAuthor = if (r.ownerMid <= 0L) enrichAuthor(r) else r
            val url = playRepo.resolveAudioUrl(rWithAuthor.bvid, rWithAuthor.currentCid, null) ?: return
            val startIdx = queue.indexOfFirst { it.cid == rWithAuthor.currentCid }.coerceAtLeast(0)
            holder.play(rWithAuthor, url, queue, 0L, 1.0f, startIndex = startIdx)
            library.recordPlayed(rWithAuthor)
        }
    }

    suspend fun playRecord(record: BookRecord) {
        val existing = library.get(record.id) ?: record
        val positionMs = existing.progressMs.coerceAtLeast(0L)
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
            val cid = existing.currentCid ?: return
            // 1. 先拿音频URL立刻播（关键路径，只发一个请求）
            val url = playRepo.resolveAudioUrl(bvid, cid, null)
            if (url == null) {
                com.tingbili.app.util.ErrorBus.post(message = "解析播放地址失败，请检查网络后重试")
                return
            }
            // 2. 先用单集队列立刻开播
            val singleQueue = listOf(PartItem(bvid, cid, "", 0L))
            val base = existing.copy(
                progressMs = 0L,
                currentCid = cid,
                currentPart = existing.currentPart,
                totalParts = existing.totalParts,
                speed = initialSpeed
            )
            val r = if (base.ownerMid <= 0L) enrichAuthor(base) else base
            holder.play(r, url, singleQueue, positionMs, initialSpeed, startIndex = 0)
            library.recordPlayed(r)
            // 3. 后台补全分P列表，补完后更新队列
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val fullQueue = playRepo.resolveVideo(bvid)?.second
                if (fullQueue != null && fullQueue.size > 1) {
                    val idx = fullQueue.indexOfFirst { it.cid == cid }.coerceAtLeast(0)
                    // 重新设置完整队列，保留当前播放位置
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        holder.play(r, url, fullQueue, positionMs, initialSpeed, startIndex = idx)
                    }
                }
            }
        }
    }

    private suspend fun resolvePerAuthorSpeed(mid: Long): Float? {
        val s = settings ?: return null
        if (mid <= 0L) return null
        val remember = s.rememberSpeedPerAuthor.firstOrNull() ?: false
        if (!remember) return null
        val sp = s.authorSpeedFlow(mid.toString()).firstOrNull()
        return sp?.takeIf { it in 0.5f..3.0f }
    }

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

    suspend fun nextPart() {
        val queue = holder.currentQueue()
        if (queue.isEmpty()) return
        val next = holder.currentQueueIndex() + 1
        if (next >= queue.size) return
        playQueueItem(queue, next)
    }

    suspend fun prevPart() {
        val queue = holder.currentQueue()
        val prev = holder.currentQueueIndex() - 1
        if (prev < 0) return
        playQueueItem(queue, prev)
    }

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
        holder.play(r, url, queue, 0L, 1.0f, startIndex = index)
        library.recordPlayed(r)
    }
}
