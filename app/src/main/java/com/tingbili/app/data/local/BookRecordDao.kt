package com.tingbili.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT * FROM book_records WHERE isFavorite = 1 AND isFinished = 0 AND tag = :tag ORDER BY sortOrder ASC, favoriteAt DESC")
    fun observeFavoritesByTag(tag: String): Flow<List<BookRecord>>

    @Query("SELECT DISTINCT tag FROM book_records WHERE isFavorite = 1 AND isFinished = 0 AND tag != ''")
    fun observeDistinctTags(): Flow<List<String>>

    @Query("UPDATE book_records SET tag = :tag WHERE id = :id")
    suspend fun setTag(id: String, tag: String)

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

    @Query("UPDATE book_records SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int)

    @Query("DELETE FROM book_records")
    suspend fun clearAll()
}
