package com.tingbili.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tingbili.app.data.repo.LibraryRepository
import kotlinx.coroutines.flow.Flow

@Dao
interface BookRecordDao : LibraryRepository.Dao {
    @Query("SELECT * FROM book_records ORDER BY lastPlayedAt DESC LIMIT 1")
    suspend fun getMostRecent(): BookRecord?

    @Query("SELECT * FROM book_records WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC")
    fun observeHistory(): Flow<List<BookRecord>>

    @Query("SELECT * FROM book_records WHERE isFavorite = 1 AND isFinished = 0 ORDER BY sortOrder ASC, favoriteAt DESC")
    fun observeFavorites(): Flow<List<BookRecord>>

    /**
     * 听单全量快照（suspend 版）。
     * 上移/下移要拿它当基准：用户可能正在某个标签筛选视图里操作，只重排"看得见的那几条"
     * 会把它们的 sortOrder 和别的标签搅在一起，所以要按全量顺序整体重编号。
     */
    @Query("SELECT * FROM book_records WHERE isFavorite = 1 AND isFinished = 0 ORDER BY sortOrder ASC, favoriteAt DESC")
    override suspend fun favoritesNow(): List<BookRecord>

    /**
     * 标签写入。字段存的是**分号分隔的多标签串**（见 [BookRecord.tag] 的说明），
     * 所以没法用 SQL 按单个标签筛选，标签筛选在 ViewModel 里做内存过滤。
     */
    @Query("UPDATE book_records SET tag = :tags WHERE id = :id")
    suspend fun setTags(id: String, tags: String)

    @Query("UPDATE book_records SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int)

    /**
     * 按给定顺序整体重写 sortOrder（0..n-1）。
     *
     * 必须整体重编号，不能只"交换这两条的 sortOrder"：历史数据里所有记录的 sortOrder
     * 都还是建表时的默认值 0，交换 0 和 0 等于什么都没干 —— 这正是"上移/下移点了没反应"
     * 的根因。整体重编号对"全是 0"和"部分有序"两种历史状态都能一次修正。
     */
    @Transaction
    suspend fun reorder(ids: List<String>) {
        ids.forEachIndexed { i, id -> setSortOrder(id, i) }
    }

    @Query("SELECT * FROM book_records WHERE id = :id")
    override suspend fun getById(id: String): BookRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsert(record: BookRecord)

    @Query("UPDATE book_records SET isFavorite = :fav, favoriteAt = :ts WHERE id = :id")
    override suspend fun setFavorite(id: String, fav: Boolean, ts: Long)

    @Query("SELECT * FROM book_records")
    override suspend fun all(): List<BookRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun replaceAll(records: List<BookRecord>)

    @Query("DELETE FROM book_records WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM book_records WHERE isFavorite = 1 AND isFinished = 1 ORDER BY favoriteAt DESC")
    fun observeArchivedFavorites(): Flow<List<BookRecord>>

    /**
     * 只更新「播放进度」三件套 + 最近播放时间，**绝不碰其它列**。
     *
     * 为什么必须是列级 UPDATE 而不是 upsert：
     * 播放层持有的是一条**开始播放那一刻抓的快照**（`PlayerHolder._record`），
     * 而 `upsert` 是 `@Insert(REPLACE)` —— 整行覆盖。只要用户在播放期间改了标签、
     * 拖了听单顺序、切了收藏，下一次进度回写（播放中每 5 秒一次、退出播放页一次）
     * 就会把这条记录**整个还原成开播时的样子**。
     * 表现就是"标签只能打一个/刚打的标签没了""上移下移点了没反应"。
     *
     * 所以：**任何来自播放层的写入都只能走列级 UPDATE**，见 [updatePlayMeta]。
     */
    @Query(
        "UPDATE book_records SET progressMs = :pos, durationMs = :dur, speed = :speed, " +
            "lastPlayedAt = :ts WHERE id = :id"
    )
    override suspend fun updateProgress(id: String, pos: Long, dur: Long, speed: Float, ts: Long): Int

    /**
     * 更新「播放元数据 + 进度」，同样不碰 tag / sortOrder / isFavorite / favoriteAt / isFinished。
     * 用于开播时落盘（封面、UP 主、分P 等可能被补全）。
     * 返回受影响行数：0 表示这条记录还不存在，调用方应改用完整插入。
     */
    @Query(
        "UPDATE book_records SET title = :title, owner = :owner, cover = :cover, " +
            "totalParts = :totalParts, currentCid = :currentCid, currentPart = :currentPart, " +
            "bvid = :bvid, auid = :auid, ownerMid = :ownerMid, ownerAvatar = :ownerAvatar, " +
            "progressMs = :progressMs, durationMs = :durationMs, speed = :speed, " +
            "lastPlayedAt = :lastPlayedAt WHERE id = :id"
    )
    suspend fun updatePlayMeta(
        id: String,
        title: String,
        owner: String,
        cover: String,
        totalParts: Int,
        currentCid: Long?,
        currentPart: Int,
        bvid: String?,
        auid: Long?,
        ownerMid: Long,
        ownerAvatar: String,
        progressMs: Long,
        durationMs: Long,
        speed: Float,
        lastPlayedAt: Long
    ): Int

    /**
     * 存在就只更新播放相关列，不存在才整行插入（新收藏首次播放）。
     *
     * 已知极小 TOCTOU 窗口：先 UPDATE 判行数、再据 0 行 INSERT，两步之间若另一协程
     * 恰好插入了同一 id，INSERT 会撞主键。当前播放写入都是单路（开播/进度回写），
     * 并发概率可忽略；真要修应改 Room 的 @Upsert 或 INSERT ... ON CONFLICT 列级更新。
     */
    @Transaction
    override suspend fun savePlayMetaOrInsert(record: BookRecord): Int {
        val n = updatePlayMeta(
            id = record.id,
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
        if (n == 0) upsert(record)
        return n
    }

    @Query("UPDATE book_records SET isFinished = :finished WHERE id = :id")
    suspend fun setFinished(id: String, finished: Boolean)

    /** 睡前标记：列级 UPDATE（不整行覆盖，避免抹掉 tag/sortOrder/isFavorite）。新标记覆盖旧的。 */
    @Query("UPDATE book_records SET markPart = :part, markMs = :ms, markAt = :at WHERE id = :id")
    override suspend fun updateMark(id: String, part: Int, ms: Long, at: Long)

    /** 清除睡前标记：三列置 NULL。 */
    @Query("UPDATE book_records SET markPart = NULL, markMs = NULL, markAt = NULL WHERE id = :id")
    override suspend fun clearMark(id: String)

    /** 跳过片头秒数：列级 UPDATE，不整行覆盖（见 [LibraryRepository.setIntroSec]）。 */
    @Query("UPDATE book_records SET introSec = :sec WHERE id = :id")
    override suspend fun setIntroSec(id: String, sec: Int)

    /**
     * 清空「收听历史」= 把 lastPlayedAt 归零，让记录从历史列表消失。
     * 绝不能写成 DELETE FROM book_records：历史、听单、收藏是**同一张表**，
     * 删表等于把用户的收藏和标签一起清光（历史上就这么错过一次）。
     */
    @Query("UPDATE book_records SET lastPlayedAt = 0")
    suspend fun clearHistory()
}
