package com.tingbili.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookRecordDao {
    @Query("SELECT * FROM book_records ORDER BY lastPlayedAt DESC")
    fun observeHistory(): Flow<List<BookRecord>>

    @Query("SELECT * FROM book_records WHERE isFavorite = 1 ORDER BY favoriteAt DESC")
    fun observeFavorites(): Flow<List<BookRecord>>

    @Query("SELECT * FROM book_records WHERE id = :id")
    suspend fun getById(id: String): BookRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: BookRecord)

    @Query("UPDATE book_records SET isFavorite = :fav, favoriteAt = :ts WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean, ts: Long)

    @Query("DELETE FROM book_records WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM book_records")
    suspend fun clearAll()
}
