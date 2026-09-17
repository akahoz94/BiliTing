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
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object Defaults {
    val KEYWORDS = setOf("有声书", "小说", "广播剧", "有声剧", "评书", "朗读", "播客", "故事", "听书", "有声", "名著")
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
    /** 最近搜索词（最多 10 条；每条一行） */
    private val keySearchHistory = stringPreferencesKey("search_history")

    val playbackSpeed: Flow<Float> = dataStore.data.map { it[keyPlaybackSpeed] ?: 1.0f }
    val sleepMinutes: Flow<Int> = dataStore.data.map { it[keySleepMinutes] ?: 30 }
    val sleepEndOfTrack: Flow<Boolean> = dataStore.data.map { it[keySleepEndOfTrack] ?: false }
    val themeMode: Flow<Int> = dataStore.data.map { it[keyTheme] ?: 0 }
    val keywords: Flow<Set<String>> = dataStore.data.map { it[keyKeywords] ?: Defaults.KEYWORDS }
    val themeColor: Flow<Int> = dataStore.data.map { it[keyThemeColor] ?: Defaults.COLOR_PURPLE }
    val audioOnly: Flow<Boolean> = dataStore.data.map { it[keyAudioOnly] ?: true }
    val webdavUrl: Flow<String> = dataStore.data.map { it[keyWebdavUrl] ?: "" }
    val webdavUser: Flow<String> = dataStore.data.map { it[keyWebdavUser] ?: "" }
    val webdavPass: Flow<String> = dataStore.data.map { it[keyWebdavPass] ?: "" }
    val searchHistory: Flow<List<String>> = dataStore.data.map {
        val raw = it[keySearchHistory] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("\n").filter { x -> x.isNotBlank() }
    }
    /** 沉浸式背景：0=主题色沉浸（默认） 1=极简底色 */
    val immersiveMode: Flow<Int> = dataStore.data.map { it[keyImmersiveMode] ?: 0 }

    suspend fun setPlaybackSpeed(v: Float) = dataStore.edit { it[keyPlaybackSpeed] = v }
    suspend fun setSleepMinutes(v: Int) = dataStore.edit { it[keySleepMinutes] = v }
    suspend fun setSleepEndOfTrack(v: Boolean) = dataStore.edit { it[keySleepEndOfTrack] = v }
    suspend fun setThemeMode(v: Int) = dataStore.edit { it[keyTheme] = v }
    suspend fun setKeywords(v: Set<String>) = dataStore.edit { it[keyKeywords] = v }
    suspend fun setThemeColor(v: Int) = dataStore.edit { it[keyThemeColor] = v }
    suspend fun setAudioOnly(v: Boolean) = dataStore.edit { it[keyAudioOnly] = v }
    suspend fun setWebdavUrl(v: String) = dataStore.edit { it[keyWebdavUrl] = v }
    suspend fun setWebdavUser(v: String) = dataStore.edit { it[keyWebdavUser] = v }
    suspend fun setWebdavPass(v: String) = dataStore.edit { it[keyWebdavPass] = v }
    suspend fun setImmersiveMode(v: Int) = dataStore.edit { it[keyImmersiveMode] = v }

    suspend fun pushSearchHistory(keyword: String, max: Int = 10) {
        val k = keyword.trim()
        if (k.isBlank()) return
        dataStore.edit { prefs ->
            val cur = (prefs[keySearchHistory] ?: "").split("\n").filter { it.isNotBlank() }
            val updated = (listOf(k) + cur.filter { it != k }).take(max)
            prefs[keySearchHistory] = updated.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() = dataStore.edit { it[keySearchHistory] = "" }
}