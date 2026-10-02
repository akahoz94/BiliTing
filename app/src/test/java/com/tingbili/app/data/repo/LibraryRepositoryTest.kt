package com.tingbili.app.data.repo

import com.tingbili.app.data.local.BookRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryTest {
    private val repo = LibraryRepository(FakeDao())

    @Test
    fun `记录播放会更新进度与时间戳`() = runBlocking {
        repo.recordPlayed(BookRecord(id = "video:1", title = "T", owner = "O", type = "video"))
        val rec = repo.get("video:1")!!
        assertEquals(1, rec.currentPart)
        assertTrue(rec.lastPlayedAt > 0)
    }

    @Test
    fun `收藏切换`() = runBlocking {
        repo.recordPlayed(BookRecord(id = "video:2", title = "T", owner = "O", type = "video"))
        repo.toggleFavorite("video:2", true)
        assertTrue(repo.get("video:2")!!.isFavorite)
        repo.toggleFavorite("video:2", false)
        assertTrue(!repo.get("video:2")!!.isFavorite)
    }

    @Test
    fun `跳过片头设置不会被进度回写抹掉`() = runBlocking {
        repo.recordPlayed(BookRecord(id = "video:3", title = "T", owner = "O", type = "video"))
        repo.setIntroSec("video:3", 25)
        repo.savePlaybackProgress("video:3", 300_000L, 600_000L, 1.5f)
        repo.recordPlayed(BookRecord(id = "video:3", title = "T2", owner = "O", type = "video"))
        assertEquals(25, repo.get("video:3")!!.introSec)
    }

    class FakeDao : LibraryRepository.Dao {
        private val map = mutableMapOf<String, BookRecord>()
        override suspend fun getById(id: String) = map[id]
        override suspend fun upsert(record: BookRecord) { map[record.id] = record }
        override suspend fun setFavorite(id: String, fav: Boolean, ts: Long) {
            map[id]?.let { map[id] = it.copy(isFavorite = fav, favoriteAt = ts) }
        }
        override suspend fun all(): List<BookRecord> = map.values.toList()

        // 对齐生产查询：未听完的收藏，按 sortOrder 再按收藏时间
        override suspend fun favoritesNow(): List<BookRecord> = map.values
            .filter { it.isFavorite && !it.isFinished }
            .sortedWith(compareBy({ it.sortOrder }, { -it.favoriteAt }))
        override suspend fun replaceAll(records: List<BookRecord>) {
            map.clear()
            records.forEach { map[it.id] = it }
        }

        // 对齐生产 BookRecordDao.savePlayMetaOrInsert：存在则只更新播放元字段（保留
        // tag/sortOrder/isFavorite/favoriteAt/isFinished），不存在则整行插入。
        override suspend fun savePlayMetaOrInsert(record: BookRecord): Int {
            val existing = map[record.id]
            map[record.id] = if (existing != null) {
                existing.copy(
                    title = record.title,
                    owner = record.owner,
                    cover = record.cover,
                    totalParts = record.totalParts,
                    currentCid = record.currentCid,
                    currentPart = record.currentPart,
                    bvid = record.bvid,
                    auid = record.auid,
                    ownerMid = record.ownerMid,
                    ownerAvatar = record.ownerAvatar,
                    progressMs = record.progressMs,
                    durationMs = record.durationMs,
                    speed = record.speed,
                    lastPlayedAt = record.lastPlayedAt
                )
            } else record
            return 1
        }

        override suspend fun updateProgress(id: String, pos: Long, dur: Long, speed: Float, ts: Long): Int {
            val existing = map[id] ?: return 0
            map[id] = existing.copy(
                progressMs = pos,
                durationMs = dur,
                speed = speed,
                lastPlayedAt = ts
            )
            return 1
        }

        override suspend fun updateMark(id: String, part: Int, ms: Long, at: Long) {
            map[id]?.let { map[id] = it.copy(markPart = part, markMs = ms, markAt = at) }
        }

        override suspend fun clearMark(id: String) {
            map[id]?.let { map[id] = it.copy(markPart = null, markMs = null, markAt = null) }
        }

        override suspend fun setIntroSec(id: String, sec: Int) {
            map[id]?.let { map[id] = it.copy(introSec = sec) }
        }
    }
}
