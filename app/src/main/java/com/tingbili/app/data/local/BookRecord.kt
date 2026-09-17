package com.tingbili.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_records")
data class BookRecord(
    @PrimaryKey val id: String,          // "video:bvid" 或 "audio:auid"
    val title: String,
    val owner: String,                   // UP主/作者
    val type: String,                    // "video" / "audio"
    val cover: String = "",              // 封面图 URL
    val totalParts: Int = 0,             // 总集数（0=未知）
    val currentPart: Int = 1,            // 当前第几集
    val currentCid: Long? = null,        // 当前集 cid（video）
    val progressMs: Long = 0L,           // 集内播放进度
    val durationMs: Long = 0L,           // 集总时长
    val isFavorite: Boolean = false,
    val favoriteAt: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val bvid: String? = null,
    val auid: Long? = null
)
