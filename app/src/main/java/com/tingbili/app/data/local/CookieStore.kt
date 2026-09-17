package com.tingbili.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cookieStore by preferencesDataStore(name = "cookies")

class CookieStore(private val context: Context) {
    private val dataStore = context.cookieStore
    private val keyBuvid3 = stringPreferencesKey("buvid3")
    private val keySessdata = stringPreferencesKey("sessdata")
    private val keyCookie = stringPreferencesKey("cookie")

    // DataStore 读到坏文件/磁盘异常会抛，这里一律兜底为空，绝不让崩溃冒泡到启动主线程；
    // Preferences DataStore 自带的 corruption handler 会在首次失败后重置文件，下次读取即正常。
    suspend fun buvid3(): String = runCatching { dataStore.data.map { it[keyBuvid3] ?: "" }.first() }.getOrDefault("")
    suspend fun cookieHeader(): String = runCatching { dataStore.data.map { it[keyCookie] ?: "" }.first() }.getOrDefault("")
    suspend fun save(buvid3: String, cookie: String) =
        runCatching { dataStore.edit { it[keyBuvid3] = buvid3; it[keyCookie] = cookie } }
}
