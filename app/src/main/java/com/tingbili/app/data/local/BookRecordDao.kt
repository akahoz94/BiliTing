package com.tingbili.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tingbili.app.data.repo.LibraryRepository
import kotlinx.coroutines.flow.Flow

@Dao
interface BookRecordDao : LibraryRepository.Dao {
    @Query("SELECT * FROM book_records ORDER BY lastPlayedAt DESC")
    fun observeHistory(): Flow<List<BookRecord>>

    @Query("SELECT * FROM book_records WHERE isFavorite = 1 ORDER BY favoriteAt DESC")
    fun observeFavorites(): Flow<List<BookRecord>>

    // LibraryRepository.Dao 继承的方法需在此带 Room 注解重新声明（override），
    // Room 要求 DAO 层级中每个抽象方法都必须有 @Query/@Insert 等注解
    @Query("SELECT * FROM book_records WHERE id = :id")
    override suspend fun getById(id: String): BookRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsert(record: BookRecord)

    @Query("UPDATE book_records SET isFavorite = :fav, favoriteAt = :ts WHERE id = :id")
    override suspend fun setFavorite(id: String, fav: Boolean, ts: Long)

    @Query("DELETE FROM book_records WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM book_records")
    suspend fun clearAll()
}
