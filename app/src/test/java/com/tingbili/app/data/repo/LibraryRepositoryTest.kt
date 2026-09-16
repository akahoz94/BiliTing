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

    class FakeDao : LibraryRepository.Dao {
        private val map = mutableMapOf<String, BookRecord>()
        override suspend fun getById(id: String) = map[id]
        override suspend fun upsert(record: BookRecord) { map[record.id] = record }
        override suspend fun setFavorite(id: String, fav: Boolean, ts: Long) {
            map[id]?.let { map[id] = it.copy(isFavorite = fav, favoriteAt = ts) }
        }
    }
}
