package com.tingbili.app.data.repo

import com.tingbili.app.data.local.BookRecord

class LibraryRepository(private val dao: LibraryRepository.Dao) {

    suspend fun recordPlayed(record: BookRecord) =
        dao.upsert(record.copy(lastPlayedAt = System.currentTimeMillis()))

    suspend fun get(id: String): BookRecord? = dao.getById(id)

    suspend fun toggleFavorite(id: String, fav: Boolean) {
        val ts = System.currentTimeMillis()
        if (fav) {
            val existing = dao.getById(id)
            if (existing != null) dao.setFavorite(id, true, ts)
            else dao.upsert(
                BookRecord(
                    id = id, title = "", owner = "", type = "video",
                    isFavorite = true, favoriteAt = ts, lastPlayedAt = ts
                )
            )
        } else {
            dao.setFavorite(id, false, 0L)
        }
    }

    // 可替换接口：生产环境由 BookRecordDao 实现（见 data.local），单测用内存 Fake
    interface Dao {
        suspend fun getById(id: String): BookRecord?
        suspend fun upsert(record: BookRecord)
        suspend fun setFavorite(id: String, fav: Boolean, ts: Long)
    }
}
