package com.tingbili.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "book_records")
@Serializable
data class BookRecord(
    @PrimaryKey val id: String,
    val title: String,
    val owner: String,
    val type: String,
    val cover: String = "",
    val totalParts: Int = 0,
    val currentPart: Int = 1,
    val currentCid: Long? = null,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val isFavorite: Boolean = false,
    val favoriteAt: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val bvid: String? = null,
    val auid: Long? = null,
    val ownerMid: Long = 0L,
    val ownerAvatar: String = ""
)