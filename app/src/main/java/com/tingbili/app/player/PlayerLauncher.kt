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
        // 正在播的就是这一条：别重新装载——会断一下，进度还会被库里的旧值拉回去
        if (holder.hasMedia() && holder.record.value?.id == existing.id) {
            if (!holder.isPlaying()) holder.togglePlay()
            return
        }
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
            markPlayed(r, existing)
        } else {
            val bvid = existing.bvid!!
            // currentCid 可能为 null（新收藏没播过），先 resolveVideo 拿第一集
            var cid = existing.currentCid
            var queueFromVideo: List<PartItem>? = null
            if (cid == null || cid <= 0L) {
                val resolved = playRepo.resolveVideo(bvid)
                queueFromVideo = resolved?.second
                cid = queueFromVideo?.firstOrNull()?.cid
                if (cid == null || cid <= 0L) return
            }
            val url = playRepo.resolveAudioUrl(bvid, cid, null)
            if (url == null) {
                com.tingbili.app.util.ErrorBus.post(message = "解析播放地址失败，请检查网络后重试")
                return
            }
            val initialQueue = queueFromVideo ?: listOf(PartItem(bvid, cid, "", 0L))
            val base = existing.copy(
                progressMs = 0L,
                currentCid = cid,
                currentPart = existing.currentPart,
                totalParts = existing.totalParts,
                speed = initialSpeed
            )
            val r = if (base.ownerMid <= 0L) enrichAuthor(base) else base
            holder.play(r, url, initialQueue, positionMs, initialSpeed, startIndex = if (queueFromVideo != null) queueFromVideo.indexOfFirst { it.cid == cid }.coerceAtLeast(0) else 0)
            markPlayed(r, existing)
            // 如果初始队列就是单集（currentCid 有值，没 resolveVideo），后台补全分P
            if (queueFromVideo == null) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    val fullQueue = playRepo.resolveVideo(bvid)?.second
                    if (fullQueue != null && fullQueue.size > 1) {
                        holder.updateQueue(fullQueue, fullQueue.indexOfFirst { it.cid == cid }.coerceAtLeast(0))
                    }
                }
            }
        }
    }

    /** 落盘播放记录，但不要把已存的进度/时长抹成 0 */
    private suspend fun markPlayed(r: BookRecord, keep: BookRecord) =
        library.recordPlayed(r.copy(progressMs = keep.progressMs, durationMs = keep.durationMs))

    /** 冷启动后播放器是空的：拿当前 record 真正装载音频并续播上次进度 */
    suspend fun resumeCurrent() {
        playRecord(holder.record.value ?: return)
    }

    /**
     * 重新解析「当前这一集」的地址并重装，停在原处（队列 / 倍速 / 播放页状态都不变）。
     *
     * 存在的唯一理由：B 站音频地址带有效期。暂停久了或长时间退后台，地址会过期；换集时源报错、
     * 或者播放器跨进占位项没被救回来，也会让播放器停在一个**永远拉不动**的地址上。
     * 这时如果只 `prepare()`，它拿的是同一个废地址，必然再失败一次 —— 点多少次播放都没反应，
     * 用户只能杀进程重开（冷启动走 playRecord/resumeCurrent，那里会重新解析地址）。
     * 这条路径就是替掉那种"只能重开"的处境。
     */
    suspend fun reloadCurrent(atMs: Long) {
        val cur = holder.record.value ?: return
        val queue = holder.currentQueue()
        val idx = holder.currentQueueIndex().coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        val item = queue.getOrNull(idx)
        // 队列里那一项才是"当前集"的权威来源（bvid+cid）；队列为空（音频稿件/本地文件）时退回 record
        val url = if (item != null) {
            playRepo.resolveAudioUrl(item.bvid, item.cid, null)
        } else {
            playRepo.resolveAudioUrl(cur.bvid, cur.currentCid, cur.auid)
        }
        if (url.isNullOrBlank()) {
            com.tingbili.app.util.ErrorBus.post(message = "音频地址解析失败，请检查网络后重试")
            return
        }
        val speed = cur.speed.takeIf { it > 0.05f && it < 4f } ?: 1.0f
        val pos = atMs.coerceAtLeast(0L)
        val r = if (item != null) {
            cur.copy(currentCid = item.cid, currentPart = idx + 1, speed = speed)
        } else {
            cur.copy(speed = speed)
        }
        Log.i("PlayerLauncher", "reloadCurrent id=${r.id} idx=$idx pos=$pos speed=$speed")
        holder.play(r, url, queue, pos, speed, startIndex = idx)
        library.savePlaybackProgress(r.id, pos, r.durationMs, speed)
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
        Log.i("PlayerLauncher", "nextPart cur=${holder.currentQueueIndex()} next=$next size=${queue.size}")
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
        Log.i("PlayerLauncher", "playQueueItem index=$index part=${item.part} cid=${item.cid}")
        val url = playRepo.resolveAudioUrl(item.bvid, item.cid, null) ?: return
        val cur = holder.record.value ?: return
        // 切集要沿用当前倍速：写死 1.0f 会让"1.5 倍速听了一集，下一集变回原速"
        val speed = cur.speed.takeIf { it > 0.05f && it < 4f } ?: 1.0f
        val r = cur.copy(currentCid = item.cid, currentPart = index + 1, progressMs = 0L, speed = speed)
        holder.play(r, url, queue, 0L, speed, startIndex = index)
        library.recordPlayed(r)
    }
}
