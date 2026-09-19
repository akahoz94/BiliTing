package com.tingbili.app.data.backup

import android.content.Context
import android.util.Log
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.data.local.SettingsStore
import com.tingbili.app.data.local.encodeTags
import com.tingbili.app.data.local.tagSet
import com.tingbili.app.data.repo.LibraryRepository
import kotlinx.coroutines.flow.first

/**
 * 启动时自动同步：**自举凭证 → 拉云端 → 与本地按 id 合并 → 回写本地 → 回传云端**。
 *
 * 两块设计要点：
 *  1. 凭证自举：先读公共目录里的 [PublicConfigStore] 配置补进本地设置，
 *     这样重装后不需要用户重输 WebDAV 密码就能直接连上。
 *  2. 合并而非覆盖：同一条内容比较 [BookRecord.lastPlayedAt] 取进度新的那份；
 *     收藏状态按 [BookRecord.favoriteAt] 取最近一次操作；排序/标签这类本地组织性字段保留本地。
 *     任何一边为空时直接采用另一边，因此「新设备恢复」和「本地有数据」都不会互相清空。
 */
class SyncManager(
    private val context: Context,
    private val store: SettingsStore,
    private val library: LibraryRepository
) {

    sealed class Result {
        /**
         * fetched = 云端拉回多少条；total = 合并后本地总条数；
         * legacyBlocked = 云端那份是旧密码加密的、这次没能读进来（已降级为只上传本地）
         */
        data class Ok(val total: Int, val fetched: Int, val legacyBlocked: Boolean = false) : Result()
        data class Failed(val message: String) : Result()
        /** 没开自动同步、或 WebDAV 还没配好 —— 不是错误 */
        object Skipped : Result()
    }

    companion object {
        private const val TAG = "SyncManager"

        /** 自动同步专用文件名（复用备份层的常量，避免两处写歪） */
        val AUTO_FILE = WebDavBackup.AUTO_FILE
    }

    /** 启动时调用；manual=true 表示用户在设置页手动点「立即同步」 */
    suspend fun sync(manual: Boolean = false): Result {
        // ---------- 1) 凭证自举（重装后靠这一步把密码找回来） ----------
        runCatching {
            PublicConfigStore.load(context)
                ?.takeIf { PublicConfigStore.isConfigured(it) }
                ?.let {
                    store.applyExternalConfig(it.url, it.user, it.pass)
                    Log.i(TAG, "外置配置已就位：url=${it.url} user=${it.user}")
                }
        }.onFailure { Log.w(TAG, "读取外置配置失败", it) }

        if (!manual && !store.autoSyncEnabled.first()) return Result.Skipped

        val url = store.webdavUrl.first().trim()
        val user = store.webdavUser.first().trim()
        val pass = store.webdavPass.first().trim()
        if (url.isBlank() || user.isBlank() || pass.isBlank()) {
            Log.i(TAG, "WebDAV 未配置完整，跳过同步")
            return Result.Skipped
        }

        val client = WebDavBackup(url, user, pass, store.cloudDir.first(), store.legacyBackupPass.first())

        // ---------- 2) 拉云端 ----------
        // 旧备份解不开**不算致命**：降级成"忽略云端、把本地传上去"。
        // 用户本地的听单/收藏一条不会丢，只是没法把升级前那份合并回来；
        // 想合并就去设置里填「历史备份密码」再同步一次。
        var legacyBlocked = false
        val remote = client.restore(preferAuto = !manual).getOrElse { e ->
            if (e is BackupDecryptException) {
                Log.w(TAG, "云端备份解不开（旧密码加密），降级为仅上传本地", e)
                legacyBlocked = true
                WebDavBackup.BackupPayload(records = emptyList())
            } else {
                Log.w(TAG, "拉取云端失败", e)
                return Result.Failed(e.message ?: "拉取云端备份失败")
            }
        }

        // ---------- 3) 合并 + 回写本地 ----------
        val local = library.all()
        val firstSync = store.lastSyncAt.first() == 0L
        val merged = mergeRecords(local, remote.records)
        if (merged.size != local.size || merged != local) {
            library.replaceAll(merged)
            Log.i(TAG, "合并完成：本地 ${local.size} + 云端 ${remote.records.size} → ${merged.size}")
        }

        // 设置：只有「首次同步」才接受云端设置，之后以本地为准，
        // 免得每次启动都把用户刚改的设置在设备间来回冲。
        if (firstSync && remote.settings != null) {
            runCatching { store.importSnapshot(remote.settings) }
                .onFailure { Log.w(TAG, "导入云端设置失败", it) }
        }

        // ---------- 4) 回传合并结果（云端从此持有权威副本） ----------
        val snapshot = store.exportSnapshot()
        client.backup(merged, snapshot, AUTO_FILE).onFailure {
            Log.w(TAG, "回传失败", it)
            return Result.Failed("已同步到本地，但回传云端失败：${it.message ?: "网络异常"}")
        }

        // ---------- 5) 刷新外置配置 + 记录时间 ----------
        runCatching { PublicConfigStore.save(context, PublicConfigStore.SyncConfig(url, user, pass)) }
            .onFailure { Log.w(TAG, "写外置配置失败", it) }
        store.setLastSyncAt(System.currentTimeMillis())

        return Result.Ok(total = merged.size, fetched = remote.records.size, legacyBlocked = legacyBlocked)
    }

    // ------------------------------------------------------------------ 合并

    /**
     * 按 id 合并两份记录。远程独有 → 直接加入；本地独有 → 保留；
     * 两边都有 → [mergeOne] 逐字段取"更新的那个"。
     */
    private fun mergeRecords(local: List<BookRecord>, remote: List<BookRecord>): List<BookRecord> {
        if (remote.isEmpty()) return local
        if (local.isEmpty()) return remote

        val out = LinkedHashMap<String, BookRecord>(local.size + remote.size)
        local.forEach { out[it.id] = it }
        remote.forEach { r ->
            val l = out[r.id]
            out[r.id] = if (l == null) r else mergeOne(l, r)
        }
        // 保持本地的排列顺序（本地顺序代表用户当前的听单排序）
        val index = local.withIndex().associate { (i, r) -> r.id to i }
        return out.values.sortedBy { index[it.id] ?: Int.MAX_VALUE }
    }

    private fun mergeOne(l: BookRecord, r: BookRecord): BookRecord {
        // 播放进度/分P：听过的更新
        val newer = if (r.lastPlayedAt > l.lastPlayedAt) r else l
        // 收藏状态：最近一次收藏/取消收藏生效（两边的 favoriteAt 都会被 toggleFavorite 刷新）
        val fav = if (r.favoriteAt > l.favoriteAt) r else l
        return newer.copy(
            isFavorite = fav.isFavorite,
            favoriteAt = fav.favoriteAt,
            // 排序属于"本地怎么摆"，不参与云端覆盖
            sortOrder = l.sortOrder,
            // 标签取并集：字段里存的是分号分隔的多标签，两边各自挂上的标签都要留住，
            // 否则会出现"在 A 设备打的标签被 B 设备的旧数据覆盖掉"
            tag = encodeTags(l.tagSet() + r.tagSet())
        )
    }
}
