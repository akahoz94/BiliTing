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
    val ownerAvatar: String = "",
    val isFinished: Boolean = false,
    val sortOrder: Int = 0,
    /**
     * 标签：**分号分隔的多标签**（如 "武侠;睡前"）。
     *
     * 字段名沿用历史的单值 `tag`，刻意**不**改名 `tags`、也**不**新建字段，原因：
     *  1. Room schema 不变 —— 不需要写迁移，也不会撞上"表里有多余列导致表结构校验失败"；
     *  2. 旧版单标签数据天然就是"只有一个标签"，零迁移成本、零数据丢失；
     *  3. WebDAV 备份用的是默认 `Json`（`ignoreUnknownKeys = false`、`encodeDefaults = false`），
     *     改字段名会让旧备份直接反序列化报错，新增字段又因为等于默认值而**不会被写进 JSON**
     *     —— 沿用旧名字是唯一能同时保住"旧备份可读"和"新标签能同步"的选择。
     *
     * 读取一律用 [tagSet]，写入一律用 [encodeTags]，不要自己 split/join。
     */
    val tag: String = ""
)

/** 把 "武侠;睡前" 拆成去空、去重后的标签列表 */
fun BookRecord.tagSet(): List<String> =
    tag.split(';').map { it.trim() }.filter { it.isNotBlank() }.distinct()

/** 把标签列表编码成存储格式（去空、去重；标签内部的分号会被替换掉，避免破坏分隔符） */
fun encodeTags(tags: List<String>): String =
    tags.map { it.replace(';', ' ').trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(";")
