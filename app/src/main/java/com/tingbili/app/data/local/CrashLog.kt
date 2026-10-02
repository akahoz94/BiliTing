package com.tingbili.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 全局未捕获异常兜底日志。进程崩溃时由 BiliTingApplication 的 UncaughtExceptionHandler
 * 在"进程死前"同步写入（见 [com.tingbili.app.BiliTingApplication]）。
 * 只落本地，不做任何网络操作。
 */
@Entity(tableName = "crash_logs")
data class CrashLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val threadName: String,
    val exceptionType: String,
    val message: String?,
    val stackTrace: String,
    val deviceModel: String,
    val androidVersion: String
)

@Dao
interface CrashLogDao {
    /**
     * 非 suspend：崩溃 handler 在 runBlocking 里同步调它，此时进程正要死，阻塞无所谓。
     */
    @Insert
    fun insert(log: CrashLog)

    /** 最近 N 条，新的在前。 */
    @Query("SELECT * FROM crash_logs ORDER BY id DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<CrashLog>

    @Query("DELETE FROM crash_logs")
    suspend fun clearAll()

    /**
     * 只保留最新 [keep] 条，删更老的。非 suspend，崩溃 handler 里跟着 insert 一起同步调。
     */
    @Query("DELETE FROM crash_logs WHERE id NOT IN (SELECT id FROM crash_logs ORDER BY id DESC LIMIT :keep)")
    fun trimExcess(keep: Int = 50)
}
