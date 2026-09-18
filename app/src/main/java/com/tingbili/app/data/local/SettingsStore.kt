package com.tingbili.app.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 解析 "epochDay:ms;epochDay:ms" → map */
private fun decodeListeningMs(raw: String?): Map<Int, Long> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(";").mapNotNull { seg ->
        val i = seg.indexOf(':')
        if (i <= 0) null
        else seg.substring(0, i).toIntOrNull()?.let { it to (seg.substring(i + 1).toLongOrNull() ?: 0L) }
    }.toMap()

}

object Defaults {
    val KEYWORDS = setOf("有声小说", "广播剧", "有声剧", "有声书")
    const val COLOR_PURPLE = 0
    const val COLOR_BLUE = 1
    const val COLOR_GREEN = 2
    const val COLOR_ORANGE = 3
    const val COLOR_PINK = 4
    const val COLOR_RED = 5
    val COLORS = listOf(COLOR_PURPLE, COLOR_BLUE, COLOR_GREEN, COLOR_ORANGE, COLOR_PINK, COLOR_RED)
}

class SettingsStore(private val context: Context) {

    companion object {
        private const val TAG = "SettingsStore"
    }

    private val dataStore = context.dataStore

    /**
     * 读写异常通道：以前 runCatching / catch 全部静默吞掉异常，
     * 导致 DataStore 损坏时"UI 有字、ViewModel 全空"这类问题完全无法察觉。
     * 现在所有失败都会 Log + 抛到这里，由 ViewModel 转成用户可见提示。
     */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val keyPlaybackSpeed = floatPreferencesKey("playback_speed")
    private val keySleepMinutes = intPreferencesKey("sleep_minutes")
    private val keySleepEndOfTrack = booleanPreferencesKey("sleep_end_of_track")
    private val keyTheme = intPreferencesKey("theme_mode")
    private val keyKeywords = stringSetPreferencesKey("keywords")
    private val keyThemeColor = intPreferencesKey("theme_color")
    private val keyAudioOnly = booleanPreferencesKey("audio_only")
    private val keyWebdavUrl = stringPreferencesKey("webdav_url")
    private val keyWebdavUser = stringPreferencesKey("webdav_user")
    private val keyWebdavPass = stringPreferencesKey("webdav_pass")
    private val keyWebdavPassEnc = stringPreferencesKey("webdav_pass_enc")
    private val keyImmersiveMode = intPreferencesKey("immersive_mode")
    private val keyPlaylistGroupMode = intPreferencesKey("playlist_group_mode")
    private val keyPaletteStrength = intPreferencesKey("palette_strength")
    private val keyAutoNextEnabled = booleanPreferencesKey("auto_next_enabled")
    private val keyRememberSpeedPerAuthor = booleanPreferencesKey("remember_speed_per_author")
    /** 最近搜索词（最多 10 条；每条一行） */
    private val keySearchHistory = stringPreferencesKey("search_history")
    /** UP 主粒度倍速映射（"mid:speed;mid:speed"），仅 rememberSpeedPerAuthor=true 时使用 */
    private val keyAuthorSpeedMap = stringPreferencesKey("author_speed_map")
    /** 听单分组映射（"id:folder;id:folder"），folder 为空字符串表示"未分组"。
     *  DataStore key 名沿用 shelf_folders 以兼容旧版本用户已分组的听单数据。 */
    private val keyShelfFolders = stringPreferencesKey("shelf_folders")
    /** 每日收听时长：encode "epochDay:ms;epochDay:ms"（D4 日历统计数据源） */
    private val keyListeningMs = stringPreferencesKey("listening_ms")

    // ---------- 统一容错：读失败 Log + 上报 + 回落默认值；不再静默吞掉 ----------
    private fun <T> Flow<T>.failSafe(name: String, fallback: T): Flow<T> = catch {
        Log.e(TAG, "read $name failed, fallback=$fallback", it)
        _errors.tryEmit("读取设置失败（$name）：${it.message ?: "未知错误"}")
        emit(fallback)
    }

    private suspend fun write(name: String, block: (MutablePreferences) -> Unit) {
        runCatching { dataStore.edit { block(it) } }
            .onFailure {
                Log.e(TAG, "write $name failed", it)
                _errors.tryEmit("保存设置失败（$name）：${it.message ?: "未知错误"}")
            }
    }

    val playbackSpeed: Flow<Float> = dataStore.data.map { it[keyPlaybackSpeed] ?: 1.0f }.failSafe("playback_speed", 1.0f)
    val sleepMinutes: Flow<Int> = dataStore.data.map { it[keySleepMinutes] ?: 30 }.failSafe("sleep_minutes", 30)
    val sleepEndOfTrack: Flow<Boolean> = dataStore.data.map { it[keySleepEndOfTrack] ?: false }.failSafe("sleep_end_of_track", false)
    val themeMode: Flow<Int> = dataStore.data.map { it[keyTheme] ?: 0 }.failSafe("theme_mode", 0)
    val keywords: Flow<Set<String>> = dataStore.data.map { it[keyKeywords] ?: Defaults.KEYWORDS }.failSafe("keywords", Defaults.KEYWORDS)
    val themeColor: Flow<Int> = dataStore.data.map { it[keyThemeColor] ?: Defaults.COLOR_PURPLE }.failSafe("theme_color", Defaults.COLOR_PURPLE)
    val audioOnly: Flow<Boolean> = dataStore.data.map { it[keyAudioOnly] ?: true }.failSafe("audio_only", true)
    val webdavUrl: Flow<String> = dataStore.data.map { it[keyWebdavUrl] ?: "" }.failSafe("webdav_url", "")
    val webdavUser: Flow<String> = dataStore.data.map { it[keyWebdavUser] ?: "" }.failSafe("webdav_user", "")
    val immersiveMode: Flow<Int> = dataStore.data.map { it[keyImmersiveMode] ?: 0 }.failSafe("immersive_mode", 0)
    val paletteStrength: Flow<Int> = dataStore.data.map { it[keyPaletteStrength] ?: 60 }.failSafe("palette_strength", 60)
    val autoNextEnabled: Flow<Boolean> = dataStore.data.map { it[keyAutoNextEnabled] ?: false }.failSafe("auto_next_enabled", false)
    val rememberSpeedPerAuthor: Flow<Boolean> = dataStore.data.map { it[keyRememberSpeedPerAuthor] ?: false }.failSafe("remember_speed_per_author", false)
    val playlistGroupMode: Flow<Int> = dataStore.data.map { it[keyPlaylistGroupMode] ?: 0 }.failSafe("playlist_group_mode", 0)

    /**
     * WebDAV 密码读取：优先读加密副本（webdav_pass_enc），
     * 读不到再回退到旧版本遗留的明文副本（webdav_pass）——保证老用户升级后不丢密码。
     */
    val webdavPass: Flow<String> = dataStore.data.map { prefs ->
        val enc = prefs[keyWebdavPassEnc]
        if (!enc.isNullOrBlank()) SecretVault.decrypt(enc) ?: ""
        else prefs[keyWebdavPass] ?: ""
    }.failSafe("webdav_pass", "")

    val searchHistory: Flow<List<String>> = dataStore.data.map {
        val raw = it[keySearchHistory] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("\n").filter { x -> x.isNotBlank() }
    }.failSafe("search_history", emptyList())

    suspend fun setPlaybackSpeed(v: Float) = write("playback_speed") { it[keyPlaybackSpeed] = v }
    suspend fun setSleepMinutes(v: Int) = write("sleep_minutes") { it[keySleepMinutes] = v }
    suspend fun setSleepEndOfTrack(v: Boolean) = write("sleep_end_of_track") { it[keySleepEndOfTrack] = v }
    suspend fun setThemeMode(v: Int) = write("theme_mode") { it[keyTheme] = v }
    suspend fun setKeywords(v: Set<String>) = write("keywords") { it[keyKeywords] = v }
    suspend fun setThemeColor(v: Int) = write("theme_color") { it[keyThemeColor] = v }
    suspend fun setAudioOnly(v: Boolean) = write("audio_only") { it[keyAudioOnly] = v }
    suspend fun setImmersiveMode(v: Int) = write("immersive_mode") { it[keyImmersiveMode] = v }
    suspend fun setPaletteStrength(v: Int) = write("palette_strength") { it[keyPaletteStrength] = v }
    suspend fun setAutoNextEnabled(v: Boolean) = write("auto_next_enabled") { it[keyAutoNextEnabled] = v }
    suspend fun setRememberSpeedPerAuthor(v: Boolean) = write("remember_speed_per_author") { it[keyRememberSpeedPerAuthor] = v }
    suspend fun setPlaylistGroupMode(v: Int) = write("playlist_group_mode") { it[keyPlaylistGroupMode] = v }
    suspend fun setWebdavUrl(v: String) = write("webdav_url") { it[keyWebdavUrl] = v }
    suspend fun setWebdavUser(v: String) = write("webdav_user") { it[keyWebdavUser] = v }

    /**
     * WebDAV 密码写入：优先 KeyStore 加密存储，并顺手清掉旧明文副本；
     * 加密不可用时（极少）降级为明文，但会 Log 警示。空值表示清除。
     */
    suspend fun setWebdavPass(v: String) = write("webdav_pass") { prefs ->
        if (v.isBlank()) {
            prefs.remove(keyWebdavPass)
            prefs.remove(keyWebdavPassEnc)
            return@write
        }
        val enc = SecretVault.encrypt(v)
        if (enc != null) {
            prefs[keyWebdavPassEnc] = enc
            prefs.remove(keyWebdavPass)
        } else {
            Log.w(TAG, "keystore unavailable, fallback to plaintext webdav pass")
            prefs[keyWebdavPass] = v
            prefs.remove(keyWebdavPassEnc)
        }
    }

    /** 每日收听时长（epochDay -> ms）。空值流，用于日历统计 hot-reload */
    val listeningMs: Flow<Map<Int, Long>> = dataStore.data.map { prefs ->
        decodeListeningMs(prefs[keyListeningMs])
    }.failSafe("listening_ms", emptyMap())

    /** 累加今日收听时长（D4 日历统计的数据源） */
    suspend fun addListeningMs(ms: Long) {
        if (ms <= 0L) return
        val day = java.time.LocalDate.now().toEpochDay().toInt()
        write("listening_ms") { prefs ->
            val map = decodeListeningMs(prefs[keyListeningMs]).toMutableMap()
            map[day] = (map[day] ?: 0L) + ms
            prefs[keyListeningMs] = map.entries.joinToString(";") { "${it.key}:${it.value}" }
        }
    }

    suspend fun pushSearchHistory(keyword: String, max: Int = 10) {
        val k = keyword.trim()
        if (k.isBlank()) return
        write("search_history") { prefs ->
            val cur = (prefs[keySearchHistory] ?: "").split("\n").filter { it.isNotBlank() }
            val updated = (listOf(k) + cur.filter { it != k }).take(max)
            prefs[keySearchHistory] = updated.joinToString("\n")
        }
    }

    /**
     * 老版本遗留的明文密码自动升级为加密存储。
     * 在 App 启动时调用一次即可，幂等；失败不影响使用（仍按明文回落读取）。
     */
    suspend fun migrateWebdavPassToEncrypted() {
        runCatching {
            val prefs = dataStore.data.first()
            val plain = prefs[keyWebdavPass]
            if (!plain.isNullOrBlank() && prefs[keyWebdavPassEnc].isNullOrBlank()) {
                val enc = SecretVault.encrypt(plain)
                if (enc != null) {
                    dataStore.edit { it[keyWebdavPassEnc] = enc; it.remove(keyWebdavPass) }
                    Log.i(TAG, "webdav password migrated to encrypted storage")
                }
            }
        }.onFailure { Log.w(TAG, "password migration failed", it) }
    }

    suspend fun clearSearchHistory() = write("search_history") { it[keySearchHistory] = "" }

    /**
     * 按 UP 主 mid 记忆倍速。encode: "mid:speed;mid:speed"。
     * 返回 0f 表示未命中。
     */
    fun authorSpeedFlow(mid: String): Flow<Float> = dataStore.data.map { prefs ->
        val raw = prefs[keyAuthorSpeedMap] ?: return@map 0f
        if (mid.isBlank()) return@map 0f
        raw.split(";").firstOrNull { it.startsWith("$mid:") }
            ?.substringAfter(':')?.toFloatOrNull() ?: 0f
    }.failSafe("author_speed_map", 0f)

    suspend fun setAuthorSpeed(mid: String, speed: Float) {
        if (mid.isBlank()) return
        write("author_speed_map") { prefs ->
            val raw = prefs[keyAuthorSpeedMap] ?: ""
            val pairs = raw.split(";").filter { it.isNotBlank() }
                .filterNot { it.startsWith("$mid:") }
                .toMutableList()
            pairs.add("$mid:${speed}")
            prefs[keyAuthorSpeedMap] = pairs.joinToString(";")
        }
    }

    /** 听单分组：id -> folder 映射 */
    fun shelfFoldersFlow(): Flow<Map<String, String>> = dataStore.data.map { prefs ->
        val raw = prefs[keyShelfFolders] ?: return@map emptyMap()
        raw.split(";").filter { it.isNotBlank() }
            .mapNotNull {
                val idx = it.indexOf(':')
                if (idx <= 0 || idx >= it.length - 1) null
                else it.substring(0, idx) to it.substring(idx + 1)
            }.toMap()
    }.failSafe("shelf_folders", emptyMap())

    suspend fun setShelfFolder(id: String, folder: String) {
        if (id.isBlank()) return
        write("shelf_folders") { prefs ->
            val raw = prefs[keyShelfFolders] ?: ""
            val pairs = raw.split(";").filter { it.isNotBlank() }
                .filterNot { it.startsWith("$id:") }
                .toMutableList()
            if (folder.isNotBlank()) {
                pairs.add("$id:$folder")
            }
            prefs[keyShelfFolders] = pairs.joinToString(";")
        }
    }

    /** 导出设置快照（用于 WebDAV 备份，不含 WebDAV 自身凭据） */
    suspend fun exportSnapshot(): SettingsSnapshot {
        val prefs = dataStore.data.first()
        return SettingsSnapshot(
            playbackSpeed = prefs[keyPlaybackSpeed] ?: 1.0f,
            sleepMinutes = prefs[keySleepMinutes] ?: 30,
            sleepEndOfTrack = prefs[keySleepEndOfTrack] ?: false,
            themeMode = prefs[keyTheme] ?: 0,
            themeColor = prefs[keyThemeColor] ?: Defaults.COLOR_PURPLE,
            audioOnly = prefs[keyAudioOnly] ?: true,
            immersiveMode = prefs[keyImmersiveMode] ?: 0,
            paletteStrength = prefs[keyPaletteStrength] ?: 60,
            autoNextEnabled = prefs[keyAutoNextEnabled] ?: false,
            rememberSpeedPerAuthor = prefs[keyRememberSpeedPerAuthor] ?: false,
            playlistGroupMode = prefs[keyPlaylistGroupMode] ?: 0,
            keywords = (prefs[keyKeywords] ?: Defaults.KEYWORDS).toList(),
            shelfFolders = prefs[keyShelfFolders] ?: "",
            authorSpeedMap = prefs[keyAuthorSpeedMap] ?: "",
            listeningMs = prefs[keyListeningMs] ?: ""
        )
    }

    /** 从快照恢复设置（用于 WebDAV 恢复，不覆盖 WebDAV 凭据） */
    suspend fun importSnapshot(s: SettingsSnapshot) {
        write("import_snapshot") { prefs ->
            prefs[keyPlaybackSpeed] = s.playbackSpeed
            prefs[keySleepMinutes] = s.sleepMinutes
            prefs[keySleepEndOfTrack] = s.sleepEndOfTrack
            prefs[keyTheme] = s.themeMode
            prefs[keyThemeColor] = s.themeColor
            prefs[keyAudioOnly] = s.audioOnly
            prefs[keyImmersiveMode] = s.immersiveMode
            prefs[keyPaletteStrength] = s.paletteStrength
            prefs[keyAutoNextEnabled] = s.autoNextEnabled
            prefs[keyRememberSpeedPerAuthor] = s.rememberSpeedPerAuthor
            prefs[keyPlaylistGroupMode] = s.playlistGroupMode
            prefs[keyKeywords] = s.keywords.toSet()
            prefs[keyShelfFolders] = s.shelfFolders
            prefs[keyAuthorSpeedMap] = s.authorSpeedMap
            prefs[keyListeningMs] = s.listeningMs
        }
    }
}

@kotlinx.serialization.Serializable
data class SettingsSnapshot(
    val playbackSpeed: Float = 1.0f,
    val sleepMinutes: Int = 30,
    val sleepEndOfTrack: Boolean = false,
    val themeMode: Int = 0,
    val themeColor: Int = 0,
    val audioOnly: Boolean = true,
    val immersiveMode: Int = 0,
    val paletteStrength: Int = 60,
    val autoNextEnabled: Boolean = false,
    val rememberSpeedPerAuthor: Boolean = false,
    val playlistGroupMode: Int = 0,
    val keywords: List<String> = emptyList(),
    val shelfFolders: String = "",
    val authorSpeedMap: String = "",
    val listeningMs: String = ""
)
