package com.tingbili.app.player

import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.data.repo.LibraryRepository
import com.tingbili.app.data.repo.PlayRepository
import com.tingbili.app.data.repo.SearchRepository

/**
 * 播放拉起器：把任意入口（搜索/书架/历史）转成 ExoPlayer 播放，并写本地记录。
 * 队列元素使用 PartItem（含 part 名称与时长），音频记录队列为空。
 */
class PlayerLauncher(
    private val holder: PlayerHolder,
    private val playRepo: PlayRepository,
    private val library: LibraryRepository
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
            val url = playRepo.resolveAudioUrl(r.bvid, r.currentCid, null) ?: return
            holder.play(r, url, queue, 0L, 1.0f)
            library.recordPlayed(r)
        }
    }

    /** 书架/历史续播：用 Room 已有进度续播，重建分P 队列支持上下集 */
    suspend fun playRecord(record: BookRecord) {
        val existing = library.get(record.id) ?: record
        val positionMs = existing.progressMs.coerceAtLeast(0L)
        if (existing.type == "audio" || existing.bvid.isNullOrBlank()) {
            val auid = existing.auid
            if (auid == null || auid <= 0L) return
            val url = playRepo.resolveAudioUrl(null, null, auid) ?: return
            val r = existing.copy(progressMs = 0L)
            holder.play(r, url, emptyList(), positionMs, 1.0f)
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
            val r = existing.copy(
                progressMs = 0L,
                currentCid = cid,
                currentPart = idx + 1,
                totalParts = queue.size
            )
            holder.play(r, url, queue, positionMs, existing.speed.coerceAtLeast(0.5f))
            holder.moveQueueTo(idx)
            library.recordPlayed(r)
        }
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
