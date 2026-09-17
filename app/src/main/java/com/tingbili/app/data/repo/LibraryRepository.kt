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

    suspend fun all(): List<BookRecord> = dao.all()

    suspend fun replaceAll(records: List<BookRecord>) = dao.replaceAll(records)

    /**
     * 累计收听时长统计：所有记录（不论是否收藏）累加 progressMs。
     */
    suspend fun totalPlayedMs(): Long = dao.all().sumOf { it.progressMs.coerceAtLeast(0L) }

    /** 本月新增（任意 lastPlayedAt 落在当月日历内） */
    suspend fun monthlyPlayedCount(): Int {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        return dao.all().count { it.lastPlayedAt >= start }
    }

    interface Dao {
        suspend fun getById(id: String): BookRecord?
        suspend fun upsert(record: BookRecord)
        suspend fun setFavorite(id: String, fav: Boolean, ts: Long)
        suspend fun all(): List<BookRecord>
        suspend fun replaceAll(records: List<BookRecord>)
    }
}
