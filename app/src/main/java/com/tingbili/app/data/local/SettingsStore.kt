package com.tingbili.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object Defaults {
    val KEYWORDS = setOf("有声书", "小说", "广播剧", "有声剧", "评书", "朗读", "播客", "故事", "听书", "有声", "名著")
}

class SettingsStore(private val context: Context) {
    private val dataStore = context.dataStore
    private val keyPlaybackSpeed = floatPreferencesKey("playback_speed")
    private val keySleepMinutes = intPreferencesKey("sleep_minutes")
    private val keyTheme = intPreferencesKey("theme_mode")       // 0=system 1=light 2=dark
    private val keyKeywords = stringSetPreferencesKey("keywords")

    val playbackSpeed: Flow<Float> = dataStore.data.map { it[keyPlaybackSpeed] ?: 1.0f }
    val sleepMinutes: Flow<Int> = dataStore.data.map { it[keySleepMinutes] ?: 30 }
    val themeMode: Flow<Int> = dataStore.data.map { it[keyTheme] ?: 0 }
    val keywords: Flow<Set<String>> = dataStore.data.map { it[keyKeywords] ?: Defaults.KEYWORDS }

    suspend fun setPlaybackSpeed(v: Float) = dataStore.edit { it[keyPlaybackSpeed] = v }
    suspend fun setSleepMinutes(v: Int) = dataStore.edit { it[keySleepMinutes] = v }
    suspend fun setThemeMode(v: Int) = dataStore.edit { it[keyTheme] = v }
    suspend fun setKeywords(v: Set<String>) = dataStore.edit { it[keyKeywords] = v }
}
