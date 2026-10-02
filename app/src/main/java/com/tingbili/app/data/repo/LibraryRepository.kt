package com.tingbili.app.data.repo

import com.tingbili.app.data.local.BookRecord

class LibraryRepository(private val dao: LibraryRepository.Dao) {

    /**
     * 记录一次播放（开播 / 切集时调用）。
     *
     * ⚠️ 内部是**列级 UPDATE**（不存在才整行插入），不是 `upsert`。
     * 原因：调用方传进来的多半是播放层**开播那一刻抓的快照**，用 `@Insert(REPLACE)`
     * 会把这行的 tag / sortOrder / isFavorite / favoriteAt / isFinished 一起还原成旧值 ——
     * 用户就会看到"刚打上的标签没了""上移下移点了没反应""收藏自己掉了"。
     */
    suspend fun recordPlayed(record: BookRecord) =
        dao.savePlayMetaOrInsert(record.copy(lastPlayedAt = System.currentTimeMillis()))

    /**
     * 只落播放进度（播放中每 5 秒一次、退出播放页一次）。
     * 绝不整行覆盖 —— 只写 progressMs / durationMs / speed / lastPlayedAt 四列。
     */
    suspend fun savePlaybackProgress(id: String, pos: Long, dur: Long, speed: Float): Int =
        dao.updateProgress(id, pos, dur, speed, System.currentTimeMillis())

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

    /** 听单当前顺序（未听完的收藏，按 sortOrder 再按收藏时间）——跨书连播取"下一本"就靠它 */
    suspend fun playlist(): List<BookRecord> = dao.favoritesNow()

    suspend fun replaceAll(records: List<BookRecord>) = dao.replaceAll(records)

    /**
     * 睡前标记：把"听到这儿睡着的位置"记下来。一本书一个标记，新的覆盖旧的。
     * part 是 1-based 集数；列级 UPDATE，不整行覆盖（tag/sortOrder/isFavorite 等不动）。
     */
    suspend fun setMark(id: String, part: Int, ms: Long) {
        dao.updateMark(id, part, ms, System.currentTimeMillis())
    }

    /** 清除睡前标记（三列置 NULL）。 */
    suspend fun clearMark(id: String) {
        dao.clearMark(id)
    }

    /**
     * 跳过片头：这本书每一集开头跳过的秒数，0 = 不跳，全书各集共用一个值。
     * 列级 UPDATE —— 和标记同理，绝不能整行覆盖，否则开播快照会把 tag/进度打回去。
     */
    suspend fun setIntroSec(id: String, sec: Int) {
        dao.setIntroSec(id, sec.coerceAtLeast(0))
    }

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

        /** 见 [LibraryRepository.playlist]：听单当前顺序（未听完的收藏） */
        suspend fun favoritesNow(): List<BookRecord>
        suspend fun replaceAll(records: List<BookRecord>)

        /** 见 [LibraryRepository.recordPlayed]：列级更新，返回受影响行数（0 = 记录不存在） */
        suspend fun savePlayMetaOrInsert(record: BookRecord): Int

        /** 见 [LibraryRepository.savePlaybackProgress]：只写进度列 */
        suspend fun updateProgress(id: String, pos: Long, dur: Long, speed: Float, ts: Long): Int

        /** 睡前标记：列级写入（见 [LibraryRepository.setMark]） */
        suspend fun updateMark(id: String, part: Int, ms: Long, at: Long)

        /** 清除睡前标记：三列置 NULL */
        suspend fun clearMark(id: String)

        /** 跳过片头秒数：列级写入（见 [LibraryRepository.setIntroSec]） */
        suspend fun setIntroSec(id: String, sec: Int)
    }
}
