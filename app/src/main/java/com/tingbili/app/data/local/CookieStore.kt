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

    suspend fun buvid3(): String = dataStore.data.map { it[keyBuvid3] ?: "" }.first()
    suspend fun cookieHeader(): String = dataStore.data.map { it[keyCookie] ?: "" }.first()
    suspend fun save(buvid3: String, cookie: String) =
        dataStore.edit { it[keyBuvid3] = buvid3; it[keyCookie] = cookie }
}
