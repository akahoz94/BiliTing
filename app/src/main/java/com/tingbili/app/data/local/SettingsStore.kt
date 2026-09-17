package com.tingbili.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
    private val dataStore = context.dataStore

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

    // 所有 Flow 统一 .catch：DataStore 文件损坏/IO 异常时 emit 空值流，绝不让 error 上冒到 Compose 触发闪退
    val playbackSpeed: Flow<Float> = dataStore.data.map { it[keyPlaybackSpeed] ?: 1.0f }.catch { emit(1.0f) }
    val sleepMinutes: Flow<Int> = dataStore.data.map { it[keySleepMinutes] ?: 30 }.catch { emit(30) }
    val sleepEndOfTrack: Flow<Boolean> = dataStore.data.map { it[keySleepEndOfTrack] ?: false }.catch { emit(false) }
    val themeMode: Flow<Int> = dataStore.data.map { it[keyTheme] ?: 0 }.catch { emit(0) }
    val keywords: Flow<Set<String>> = dataStore.data.map { it[keyKeywords] ?: Defaults.KEYWORDS }.catch { emit(Defaults.KEYWORDS) }
    val themeColor: Flow<Int> = dataStore.data.map { it[keyThemeColor] ?: Defaults.COLOR_PURPLE }.catch { emit(Defaults.COLOR_PURPLE) }
    val audioOnly: Flow<Boolean> = dataStore.data.map { it[keyAudioOnly] ?: true }.catch { emit(true) }
    val webdavUrl: Flow<String> = dataStore.data.map { it[keyWebdavUrl] ?: "" }.catch { emit("") }
    val webdavUser: Flow<String> = dataStore.data.map { it[keyWebdavUser] ?: "" }.catch { emit("") }
    val webdavPass: Flow<String> = dataStore.data.map { it[keyWebdavPass] ?: "" }.catch { emit("") }
    val searchHistory: Flow<List<String>> = dataStore.data.map {
        val raw = it[keySearchHistory] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("\n").filter { x -> x.isNotBlank() }
    }.catch { emit(emptyList()) }
    /** 沉浸式背景：0=封面取色（默认） 1=主题色沉浸 2=极简底色 */
    val immersiveMode: Flow<Int> = dataStore.data.map { it[keyImmersiveMode] ?: 0 }.catch { emit(0) }
    /** 封面取色强度（0~100），决定模糊度与饱和度，60 默认 */
    val paletteStrength: Flow<Int> = dataStore.data.map { it[keyPaletteStrength] ?: 60 }.catch { emit(60) }
    /** 播完本集自动播放下一集（默认 false） */
    val autoNextEnabled: Flow<Boolean> = dataStore.data.map { it[keyAutoNextEnabled] ?: false }.catch { emit(false) }
    /** 是否按 UP 主记忆倍速（默认 false，UP 主粒度的 speed 写入 BookRecord.speedAuthorKey） */
    val rememberSpeedPerAuthor: Flow<Boolean> = dataStore.data.map { it[keyRememberSpeedPerAuthor] ?: false }.catch { emit(false) }
    /** 听单分组模式：0=按文件夹（默认）/ 1=按 UP 主（owner） */
    val playlistGroupMode: Flow<Int> = dataStore.data.map { it[keyPlaylistGroupMode] ?: 0 }.catch { emit(0) }

    // 所有写操作走 runCatching，DataStore 写失败时静默忽略，绝不阻塞调用方/触发闪退
    suspend fun setPlaybackSpeed(v: Float) = runCatching { dataStore.edit { it[keyPlaybackSpeed] = v } }
    suspend fun setSleepMinutes(v: Int) = runCatching { dataStore.edit { it[keySleepMinutes] = v } }
    suspend fun setSleepEndOfTrack(v: Boolean) = runCatching { dataStore.edit { it[keySleepEndOfTrack] = v } }
    suspend fun setThemeMode(v: Int) = runCatching { dataStore.edit { it[keyTheme] = v } }
    suspend fun setKeywords(v: Set<String>) = runCatching { dataStore.edit { it[keyKeywords] = v } }
    suspend fun setThemeColor(v: Int) = runCatching { dataStore.edit { it[keyThemeColor] = v } }
    suspend fun setAudioOnly(v: Boolean) = runCatching { dataStore.edit { it[keyAudioOnly] = v } }
    suspend fun setWebdavUrl(v: String) = runCatching { dataStore.edit { it[keyWebdavUrl] = v } }
    suspend fun setWebdavUser(v: String) = runCatching { dataStore.edit { it[keyWebdavUser] = v } }
    suspend fun setWebdavPass(v: String) = runCatching { dataStore.edit { it[keyWebdavPass] = v } }
    suspend fun setImmersiveMode(v: Int) = runCatching { dataStore.edit { it[keyImmersiveMode] = v } }
    suspend fun setPaletteStrength(v: Int) = runCatching { dataStore.edit { it[keyPaletteStrength] = v } }
    suspend fun setAutoNextEnabled(v: Boolean) = runCatching { dataStore.edit { it[keyAutoNextEnabled] = v } }
    suspend fun setRememberSpeedPerAuthor(v: Boolean) = runCatching { dataStore.edit { it[keyRememberSpeedPerAuthor] = v } }
    suspend fun setPlaylistGroupMode(v: Int) = runCatching { dataStore.edit { it[keyPlaylistGroupMode] = v } }

    /** 每日收听时长（epochDay -> ms）。空值流，用于日历统计 hot-reload */
    val listeningMs: Flow<Map<Int, Long>> = dataStore.data.map { prefs ->
        decodeListeningMs(prefs[keyListeningMs])
    }.catch { emit(emptyMap()) }

    /** 累加今日收听时长（D4 日历统计的数据源） */
    suspend fun addListeningMs(ms: Long) {
        if (ms <= 0L) return
        val day = java.time.LocalDate.now().toEpochDay().toInt()
        runCatching {
            dataStore.edit { prefs ->
                val map = decodeListeningMs(prefs[keyListeningMs]).toMutableMap()
                map[day] = (map[day] ?: 0L) + ms
                prefs[keyListeningMs] = map.entries.joinToString(";") { "${it.key}:${it.value}" }
            }
        }
    }

    suspend fun pushSearchHistory(keyword: String, max: Int = 10) {
        val k = keyword.trim()
        if (k.isBlank()) return
        runCatching {
            dataStore.edit { prefs ->
                val cur = (prefs[keySearchHistory] ?: "").split("\n").filter { it.isNotBlank() }
                val updated = (listOf(k) + cur.filter { it != k }).take(max)
                prefs[keySearchHistory] = updated.joinToString("\n")
            }
        }
    }

    suspend fun clearSearchHistory() = runCatching { dataStore.edit { it[keySearchHistory] = "" } }

    /**
     * 按 UP 主 mid 记忆倍速。encode: "mid:speed;mid:speed"。
     * 返回 0f 表示未命中。
     */
    fun authorSpeedFlow(mid: String): Flow<Float> = dataStore.data.map { prefs ->
        val raw = prefs[keyAuthorSpeedMap] ?: return@map 0f
        if (mid.isBlank()) return@map 0f
        raw.split(";").firstOrNull { it.startsWith("$mid:") }
            ?.substringAfter(':')?.toFloatOrNull() ?: 0f
    }.catch { emit(0f) }

    suspend fun setAuthorSpeed(mid: String, speed: Float) {
        if (mid.isBlank()) return
        runCatching {
            dataStore.edit { prefs ->
                val raw = prefs[keyAuthorSpeedMap] ?: ""
                val pairs = raw.split(";").filter { it.isNotBlank() }
                    .filterNot { it.startsWith("$mid:") }
                    .toMutableList()
                pairs.add("$mid:${speed}")
                prefs[keyAuthorSpeedMap] = pairs.joinToString(";")
            }
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
    }.catch { emit(emptyMap()) }

    suspend fun setShelfFolder(id: String, folder: String) {
        if (id.isBlank()) return
        runCatching {
            dataStore.edit { prefs ->
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
    }
}