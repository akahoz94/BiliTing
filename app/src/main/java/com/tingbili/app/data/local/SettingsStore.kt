package com.tingbili.app.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        const val MIN_GAIN = 0.5f
        const val MAX_GAIN = 3.0f

        /** 云端备份默认目录（历史版本的硬编码值，保持兼容） */
        const val DEFAULT_CLOUD_DIR = "BiliTing"
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
    /** 音量增益（1.0 = 原始音量，最高 3.0）。ExoPlayer.volume 上限是 1，想更响只能增益 PCM */
    private val keyAudioGain = floatPreferencesKey("audio_gain")
    private val keyWebdavUrl = stringPreferencesKey("webdav_url")
    private val keyWebdavUser = stringPreferencesKey("webdav_user")
    private val keyWebdavPass = stringPreferencesKey("webdav_pass")
    private val keyWebdavPassEnc = stringPreferencesKey("webdav_pass_enc")
    private val keyImmersiveMode = intPreferencesKey("immersive_mode")
    /**
     * ⚠️ 历史遗留：听单「分组模式」（0 = 按文件夹 / 1 = 按 UP 主）。
     *
     * 来历：v0.14.0 上线过完整 UI（听单页顶部两个 FilterChip 切换分组方式，列表按分组折叠）；
     * v0.17.0 听单页重做时把分组视图整个摘掉，从此只剩存储层、零消费者。
     * 听单的"归类"需求现在由多标签承担（见 [BookRecord.tag]）。
     *
     * 保留 key 和快照字段**只是为了让旧备份还能反序列化**（WebDavBackup 用的默认 Json，
     * `ignoreUnknownKeys = false`，快照里少一个字段就会让整份旧备份恢复失败）。
     * 不要为它新增读取方，也不要删。
     */
    private val keyPlaylistGroupMode = intPreferencesKey("playlist_group_mode")
    private val keyPaletteStrength = intPreferencesKey("palette_strength")
    private val keyAutoNextEnabled = booleanPreferencesKey("auto_next_enabled")
    private val keyAutoNextBookEnabled = booleanPreferencesKey("auto_next_book_enabled")
    private val keyRememberSpeedPerAuthor = booleanPreferencesKey("remember_speed_per_author")
    /** 最近搜索词（最多 10 条；每条一行） */
    private val keySearchHistory = stringPreferencesKey("search_history")
    /** UP 主粒度倍速映射（"mid:speed;mid:speed"），仅 rememberSpeedPerAuthor=true 时使用 */
    private val keyAuthorSpeedMap = stringPreferencesKey("author_speed_map")
    /**
     * ⚠️ 历史遗留：听单分组的 id→folder 映射（"id:folder;id:folder"）。
     * 和 [keyPlaylistGroupMode] 同期登场、同期在 v0.17.0 被摘掉 UI，现在零消费者。
     * 保留只为旧备份能反序列化。不要新增读取方，也不要删。
     * （key 名沿用 "shelf_folders" 是因为它最早来自已删除的"书架页"。）
     */
    private val keyShelfFolders = stringPreferencesKey("shelf_folders")
    /** 每日收听时长：encode "epochDay:ms;epochDay:ms"（D4 日历统计数据源） */
    private val keyListeningMs = stringPreferencesKey("listening_ms")
    /** 启动时自动同步听单/收藏/进度/设置 */
    private val keyAutoSyncEnabled = booleanPreferencesKey("auto_sync_enabled")
    /** 摇一摇延长睡眠定时：定时激活时摇一下手机延长 15 分钟 */
    private val keyShakeExtendEnabled = booleanPreferencesKey("shake_extend_enabled")
    /** 上次成功同步的时间戳（0 = 从未同步过） */
    private val keyLastSyncAt = longPreferencesKey("last_sync_at")
    /** 云端备份目录（WebDAV 下的子路径，默认 BiliTing；支持 "a/b" 多级） */
    private val keyCloudDir = stringPreferencesKey("webdav_cloud_dir")
    /**
     * 历史「备份加密密码」：升级前那套独立加密密码。
     * 新版不再需要它，但老云端备份是它加密的 —— 留个字段让用户填一次就能把旧备份打开。
     */
    private val keyLegacyBackupPass = stringPreferencesKey("legacy_backup_pass")

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
    /**
     * 设置项「播完本集自动停止」：**常开**语义（不像睡眠面板里的「听完本集退出」是一次性）。
     * 打开后每次播完本集都就地停住、不自动续播。与 [autoNextEnabled] 互斥（开它会关掉自动下一集）。
     * 运行时由 PlayerHolder 统一裁决（停止优先于下一集）。
     */
    val sleepEndOfTrack: Flow<Boolean> = dataStore.data.map { it[keySleepEndOfTrack] ?: false }.failSafe("sleep_end_of_track", false)
    val themeMode: Flow<Int> = dataStore.data.map { it[keyTheme] ?: 0 }.failSafe("theme_mode", 0)
    val keywords: Flow<Set<String>> = dataStore.data.map { it[keyKeywords] ?: Defaults.KEYWORDS }.failSafe("keywords", Defaults.KEYWORDS)
    val themeColor: Flow<Int> = dataStore.data.map { it[keyThemeColor] ?: Defaults.COLOR_PURPLE }.failSafe("theme_color", Defaults.COLOR_PURPLE)
    val audioOnly: Flow<Boolean> = dataStore.data.map { it[keyAudioOnly] ?: true }.failSafe("audio_only", true)
    val audioGain: Flow<Float> = dataStore.data.map { (it[keyAudioGain] ?: 1.0f).coerceIn(MIN_GAIN, MAX_GAIN) }
        .failSafe("audio_gain", 1.0f)
    val webdavUrl: Flow<String> = dataStore.data.map { it[keyWebdavUrl] ?: "" }.failSafe("webdav_url", "")
    val webdavUser: Flow<String> = dataStore.data.map { it[keyWebdavUser] ?: "" }.failSafe("webdav_user", "")
    val immersiveMode: Flow<Int> = dataStore.data.map { it[keyImmersiveMode] ?: 0 }.failSafe("immersive_mode", 0)
    val paletteStrength: Flow<Int> = dataStore.data.map { it[keyPaletteStrength] ?: 60 }.failSafe("palette_strength", 60)
    /**
     * 设置项「播完本集自动下一集」，默认开（= v0.23.13 之前的实际行为，默认值不能反过来，
     * 否则老用户一升级就不再自动续播）。关掉等价于「播完停下来」。
     */
    val autoNextEnabled: Flow<Boolean> = dataStore.data.map { it[keyAutoNextEnabled] ?: true }.failSafe("auto_next_enabled", true)
    /**
     * 设置项「播完本书自动播放听单下一本」，默认开 —— 听书是连载，睡前放着不管，
     * 一本书听完就该接着下一本，否则整晚就停在最后一集的末尾干等。
     * 只在"本书最后一集播完"时生效，且受「播完本集自动停止」和睡眠定时压制。
     */
    val autoNextBookEnabled: Flow<Boolean> = dataStore.data.map { it[keyAutoNextBookEnabled] ?: true }.failSafe("auto_next_book_enabled", true)
    val rememberSpeedPerAuthor: Flow<Boolean> = dataStore.data.map { it[keyRememberSpeedPerAuthor] ?: false }.failSafe("remember_speed_per_author", false)
    val autoSyncEnabled: Flow<Boolean> = dataStore.data.map { it[keyAutoSyncEnabled] ?: true }.failSafe("auto_sync_enabled", true)
    /** 摇一摇延长睡眠定时，默认开 */
    val shakeExtendEnabled: Flow<Boolean> = dataStore.data.map { it[keyShakeExtendEnabled] ?: true }.failSafe("shake_extend_enabled", true)
    val lastSyncAt: Flow<Long> = dataStore.data.map { it[keyLastSyncAt] ?: 0L }.failSafe("last_sync_at", 0L)

    /** 云端备份目录：空白/含非法字符时回落默认值，避免拼出坏 URL */
    val cloudDir: Flow<String> = dataStore.data.map {
        sanitizeCloudDir(it[keyCloudDir] ?: "")
    }.failSafe("webdav_cloud_dir", DEFAULT_CLOUD_DIR)

    /** 历史备份密码（仅用于打开升级前的旧备份），默认空 */
    val legacyBackupPass: Flow<String> = dataStore.data.map { it[keyLegacyBackupPass] ?: "" }
        .failSafe("legacy_backup_pass", "")

    /** 去掉首尾斜杠与空白；非法（空、含 ..）时用默认目录 */
    private fun sanitizeCloudDir(raw: String): String {
        val trimmed = raw.trim().trim('/').trim()
        if (trimmed.isBlank()) return DEFAULT_CLOUD_DIR
        if (trimmed.split('/').any { it == ".." || it.isBlank() }) return DEFAULT_CLOUD_DIR
        return trimmed
    }

    /**
     * WebDAV 密码读取：明文优先，再回退历史 KeyStore 密文副本。
     *
     * 为什么放弃 KeyStore 加密：密钥存在 AndroidKeyStore，卸载 App 时被系统一并销毁，
     * 密文即使跟着外置配置活下来也解不开 —— 对「重装不用重输」这个目标是负资产。
     * 现存密文由 [migrateSecretsToPlaintext] 在启动时一次性转成明文。
     */
    val webdavPass: Flow<String> = dataStore.data.map { prefs ->
        val plain = prefs[keyWebdavPass]
        if (!plain.isNullOrBlank()) plain
        else prefs[keyWebdavPassEnc]?.let { SecretVault.decrypt(it) } ?: ""
    }.failSafe("webdav_pass", "")

    val searchHistory: Flow<List<String>> = dataStore.data.map {
        val raw = it[keySearchHistory] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("\n").filter { x -> x.isNotBlank() }
    }.failSafe("search_history", emptyList())

    suspend fun setPlaybackSpeed(v: Float) = write("playback_speed") { it[keyPlaybackSpeed] = v }
    suspend fun setSleepMinutes(v: Int) = write("sleep_minutes") { it[keySleepMinutes] = v }
    suspend fun setSleepEndOfTrack(v: Boolean) = write("sleep_end_of_track") { p ->
        p[keySleepEndOfTrack] = v
        if (v) p[keyAutoNextEnabled] = false
    }
    suspend fun setThemeMode(v: Int) = write("theme_mode") { it[keyTheme] = v }
    suspend fun setKeywords(v: Set<String>) = write("keywords") { it[keyKeywords] = v }
    suspend fun setThemeColor(v: Int) = write("theme_color") { it[keyThemeColor] = v }
    suspend fun setAudioOnly(v: Boolean) = write("audio_only") { it[keyAudioOnly] = v }
    suspend fun setAudioGain(v: Float) = write("audio_gain") { it[keyAudioGain] = v.coerceIn(MIN_GAIN, MAX_GAIN) }
    suspend fun setImmersiveMode(v: Int) = write("immersive_mode") { it[keyImmersiveMode] = v }
    suspend fun setPaletteStrength(v: Int) = write("palette_strength") { it[keyPaletteStrength] = v }
    /**
     * 两个「播完本集」开关互斥：开了自动下一集就关掉自动停止，反之亦然。
     * 放在 store 层做（而不是各自 UI 里），是为了让播放页抽屉、设置页、备份恢复
     * 三条入口拿到的是同一套状态，不会出现「两个都开着」的互相打架组合。
     */
    suspend fun setAutoNextEnabled(v: Boolean) = write("auto_next_enabled") { p ->
        p[keyAutoNextEnabled] = v
        if (v) p[keySleepEndOfTrack] = false
    }
    suspend fun setRememberSpeedPerAuthor(v: Boolean) = write("remember_speed_per_author") { it[keyRememberSpeedPerAuthor] = v }
    suspend fun setAutoNextBookEnabled(v: Boolean) = write("auto_next_book_enabled") { it[keyAutoNextBookEnabled] = v }
    suspend fun setAutoSyncEnabled(v: Boolean) = write("auto_sync_enabled") { it[keyAutoSyncEnabled] = v }
    suspend fun setShakeExtendEnabled(v: Boolean) = write("shake_extend_enabled") { it[keyShakeExtendEnabled] = v }
    suspend fun setLastSyncAt(v: Long) = write("last_sync_at") { it[keyLastSyncAt] = v }
    suspend fun setCloudDir(v: String) = write("webdav_cloud_dir") { it[keyCloudDir] = v.trim() }
    suspend fun setLegacyBackupPass(v: String) = write("legacy_backup_pass") { it[keyLegacyBackupPass] = v }

    /** 一次性把外置同步配置写进本地（重装后自举用）；已有本地值时不覆盖 */
    suspend fun applyExternalConfig(url: String, user: String, pass: String) {
        write("apply_external_config") { prefs ->
            if (prefs[keyWebdavUrl].isNullOrBlank()) prefs[keyWebdavUrl] = url
            if (prefs[keyWebdavUser].isNullOrBlank()) prefs[keyWebdavUser] = user
            if (prefs[keyWebdavPass].isNullOrBlank()) {
                prefs[keyWebdavPass] = pass
                prefs.remove(keyWebdavPassEnc)
            }
        }
    }
    suspend fun setWebdavUrl(v: String) = write("webdav_url") { it[keyWebdavUrl] = v }
    suspend fun setWebdavUser(v: String) = write("webdav_user") { it[keyWebdavUser] = v }

    /**
     * WebDAV 密码写入：直接存明文（理由见 [webdavPass]），并顺手清掉历史密文副本。
     * 空值表示清除。
     */
    suspend fun setWebdavPass(v: String) = write("webdav_pass") { prefs ->
        if (v.isBlank()) {
            prefs.remove(keyWebdavPass)
            prefs.remove(keyWebdavPassEnc)
            return@write
        }
        prefs[keyWebdavPass] = v
        prefs.remove(keyWebdavPassEnc)
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
     * 把历史遗留的 KeyStore 密文密码转回明文（幂等，启动时调一次）。
     * 解不开（换了设备/清过 KeyStore）就原样保留，靠 [webdavPass] 的回退读取兜底。
     */
    suspend fun migrateSecretsToPlaintext() {
        runCatching {
            val prefs = dataStore.data.first()
            val enc = prefs[keyWebdavPassEnc]
            if (!enc.isNullOrBlank() && prefs[keyWebdavPass].isNullOrBlank()) {
                val plain = SecretVault.decrypt(enc)
                if (!plain.isNullOrBlank()) {
                    dataStore.edit { it[keyWebdavPass] = plain; it.remove(keyWebdavPassEnc) }
                    Log.i(TAG, "webdav password migrated to plaintext")
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
            autoNextEnabled = prefs[keyAutoNextEnabled] ?: true,
            autoNextBookEnabled = prefs[keyAutoNextBookEnabled] ?: true,
            rememberSpeedPerAuthor = prefs[keyRememberSpeedPerAuthor] ?: false,
            playlistGroupMode = prefs[keyPlaylistGroupMode] ?: 0,
            keywords = (prefs[keyKeywords] ?: Defaults.KEYWORDS).toList(),
            shelfFolders = prefs[keyShelfFolders] ?: "",
            authorSpeedMap = prefs[keyAuthorSpeedMap] ?: "",
            listeningMs = prefs[keyListeningMs] ?: "",
            shakeExtendEnabled = prefs[keyShakeExtendEnabled] ?: true
        )
    }

    /** 从快照恢复设置（用于 WebDAV 恢复，不覆盖 WebDAV 凭据） */
    suspend fun importSnapshot(s: SettingsSnapshot) {
        // 两个「播完本集」开关在旧版本里都是死开关，快照里的 false 很可能只是当时的默认值，
        // 直接照搬会把新版本的默认行为（自动下一集）关掉。归一化：只有"明确只开了自动停止"
        // 才认为自动下一集是关的；互相矛盾的组合按自动下一集优先。
        val autoNext = if (s.autoNextEnabled || s.sleepEndOfTrack) s.autoNextEnabled else true
        write("import_snapshot") { prefs ->
            prefs[keyPlaybackSpeed] = s.playbackSpeed
            prefs[keySleepMinutes] = s.sleepMinutes
            prefs[keySleepEndOfTrack] = s.sleepEndOfTrack && !autoNext
            prefs[keyTheme] = s.themeMode
            prefs[keyThemeColor] = s.themeColor
            prefs[keyAudioOnly] = s.audioOnly
            prefs[keyImmersiveMode] = s.immersiveMode
            prefs[keyPaletteStrength] = s.paletteStrength
            prefs[keyAutoNextEnabled] = autoNext
            prefs[keyAutoNextBookEnabled] = s.autoNextBookEnabled
            prefs[keyRememberSpeedPerAuthor] = s.rememberSpeedPerAuthor
            prefs[keyPlaylistGroupMode] = s.playlistGroupMode
            prefs[keyKeywords] = s.keywords.toSet()
            prefs[keyShelfFolders] = s.shelfFolders
            prefs[keyAuthorSpeedMap] = s.authorSpeedMap
            prefs[keyListeningMs] = s.listeningMs
            prefs[keyShakeExtendEnabled] = s.shakeExtendEnabled
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
    val autoNextEnabled: Boolean = true,
    val autoNextBookEnabled: Boolean = true,
    val rememberSpeedPerAuthor: Boolean = false,
    val playlistGroupMode: Int = 0,
    val keywords: List<String> = emptyList(),
    val shelfFolders: String = "",
    val authorSpeedMap: String = "",
    val listeningMs: String = "",
    val shakeExtendEnabled: Boolean = true
)
