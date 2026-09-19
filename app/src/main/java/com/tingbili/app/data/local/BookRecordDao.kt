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

    @Query("SELECT * FROM book_records ORDER BY lastPlayedAt DESC")
    fun observeHistory(): Flow<List<BookRecord>>

    @Query("SELECT * FROM book_records WHERE isFavorite = 1 AND isFinished = 0 ORDER BY sortOrder ASC, favoriteAt DESC")
    fun observeFavorites(): Flow<List<BookRecord>>

    /**
     * 听单全量快照（suspend 版）。
     * 上移/下移要拿它当基准：用户可能正在某个标签筛选视图里操作，只重排"看得见的那几条"
     * 会把它们的 sortOrder 和别的标签搅在一起，所以要按全量顺序整体重编号。
     */
    @Query("SELECT * FROM book_records WHERE isFavorite = 1 AND isFinished = 0 ORDER BY sortOrder ASC, favoriteAt DESC")
    suspend fun favoritesNow(): List<BookRecord>

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

    @Query("UPDATE book_records SET isFinished = :finished WHERE id = :id")
    suspend fun setFinished(id: String, finished: Boolean)

    @Query("DELETE FROM book_records")
    suspend fun clearAll()
}
