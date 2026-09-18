package com.tingbili.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 把 WebDAV 同步配置写到「卸载不会被清掉」的公共目录，解决重装后必须重输密码的问题。
 *
 * 落盘位置：`Download/BiliTing/biliting-sync.json`（用户能在文件管理器里看到）
 *  - Android 10+ 走 MediaStore（无需任何权限，重装后用同包名可读回自己创建的文件）
 *  - Android 9 及以下退化为直接文件写入（需要 WRITE_EXTERNAL_STORAGE，maxSdkVersion=28）
 *
 * 为什么不用 AndroidKeyStore 加密这份配置：KeyStore 里的密钥在卸载时被系统销毁，
 * 密文留下也解不开 —— 那层加密在「跨重装」场景下等于把钥匙和锁一起扔掉。
 */
object PublicConfigStore {

    private const val TAG = "PublicConfigStore"
    private const val DIR_NAME = "BiliTing"
    private const val FILE_NAME = "biliting-sync.json"
    private val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/" + DIR_NAME + "/"

    /** 同步配置：只保留自举所需的三个字段（备份加密直接用 pass 派生，不再单独设密码） */
    @Serializable
    data class SyncConfig(
        val url: String = "",
        val user: String = "",
        val pass: String = ""
    )

    fun isConfigured(c: SyncConfig?): Boolean =
        c != null && c.url.isNotBlank() && c.user.isNotBlank() && c.pass.isNotBlank()

    // ------------------------------------------------------------------ 写

    suspend fun save(context: Context, cfg: SyncConfig): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured(cfg)) return@withContext false
        val json = Json.encodeToString(cfg)
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, json)
        } else {
            saveViaFile(json)
        }
        Log.i(TAG, "save ok=$ok path=$RELATIVE_PATH$FILE_NAME")
        ok
    }

    private fun saveViaMediaStore(context: Context, json: String): Boolean = runCatching {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        // 已有同名文件就直接覆盖，否则插入新条目（IS_PENDING 期间不对外可见）
        val existing = findUriViaMediaStore(context)
        val uri: Uri = existing ?: run {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            resolver.insert(collection, values) ?: error("insert 返回 null")
        }

        resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: error("openOutputStream 返回 null")

        if (existing == null) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        true
    }.onFailure { Log.e(TAG, "saveViaMediaStore failed", it) }.getOrDefault(false)

    private fun saveViaFile(json: String): Boolean = runCatching {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        File(dir, FILE_NAME).writeText(json, Charsets.UTF_8)
        true
    }.onFailure { Log.e(TAG, "saveViaFile failed", it) }.getOrDefault(false)

    // ------------------------------------------------------------------ 读

    /** 读不到（首次安装/用户删了文件）返回 null，绝不抛异常 */
    suspend fun load(context: Context): SyncConfig? = withContext(Dispatchers.IO) {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            readViaMediaStore(context)
        } else {
            readViaFile()
        }
        if (raw.isNullOrBlank()) return@withContext null
        runCatching { Json.decodeFromString(SyncConfig.serializer(), raw) }
            .onFailure { Log.e(TAG, "config 解析失败", it) }
            .getOrNull()
            ?.takeIf { isConfigured(it) }
    }

    private fun readViaMediaStore(context: Context): String? = runCatching {
        val uri = findUriViaMediaStore(context) ?: return@runCatching null
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.onFailure { Log.e(TAG, "readViaMediaStore failed", it) }.getOrNull()

    private fun readViaFile(): String? = runCatching {
        val f = File(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME),
            FILE_NAME
        )
        if (f.exists()) f.readText(Charsets.UTF_8) else null
    }.onFailure { Log.e(TAG, "readViaFile failed", it) }.getOrNull()

    private fun findUriViaMediaStore(context: Context): Uri? = runCatching {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(FILE_NAME, RELATIVE_PATH),
            null
        )?.use { c -> if (c.moveToFirst()) {
            Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0).toString())
        } else null }
    }.onFailure { Log.w(TAG, "query failed（可能无读取权限）", it) }.getOrNull()

    /** 用户改配置时顺手删掉外置文件（做"取消外置"用） */
    suspend fun clear(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                findUriViaMediaStore(context)?.let { context.contentResolver.delete(it, null, null) }
            } else {
                File(
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME),
                    FILE_NAME
                ).delete()
            }
            true
        }.onFailure { Log.e(TAG, "clear failed", it) }.getOrDefault(false)
    }
}
