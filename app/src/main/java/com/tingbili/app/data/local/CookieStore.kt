package com.tingbili.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cookieStore by preferencesDataStore(name = "cookies")

class CookieStore(private val context: Context) {
    private val dataStore = context.cookieStore
    private val keyBuvid3 = stringPreferencesKey("buvid3")
    private val keyBuvid4 = stringPreferencesKey("buvid4")
    private val keyBNut = longPreferencesKey("b_nut")
    private val keySessdata = stringPreferencesKey("sessdata")
    private val keyCookie = stringPreferencesKey("cookie")
    private val keyScanBackup = stringPreferencesKey("scan_backup_cookie")
    private val keyScanBackupPresent = booleanPreferencesKey("scan_backup_present")

    // DataStore 读到坏文件/磁盘异常会抛，这里一律兜底为空，绝不让崩溃冒泡到启动主线程；
    // Preferences DataStore 自带的 corruption handler 会在首次失败后重置文件，下次读取即正常。
    suspend fun buvid3(): String = runCatching { dataStore.data.map { it[keyBuvid3] ?: "" }.first() }.getOrDefault("")
    suspend fun cookieHeader(): String = runCatching { dataStore.data.map { it[keyCookie] ?: "" }.first() }.getOrDefault("")

    /** 响应式订阅当前已保存的 cookie 字符串（坏文件/IO 异常时 emit 空串） */
    fun cookieFlow(): Flow<String> = dataStore.data
        .map { it[keyCookie] ?: "" }
        .catch { emit("") }

    suspend fun save(buvid3: String, cookie: String) =
        runCatching { dataStore.edit { it[keyBuvid3] = buvid3; it[keyCookie] = cookie } }

    /**
     * 匿名指纹 buvid4 + b_nut。浏览器会长期复用同一对，每请求重掷等于自曝机器特征
     * （这正是 space/搜索吃 412 的诱因之一），所以和 buvid3 一样存一次、之后复用。
     */
    suspend fun fingerprint(): Pair<String, Long> = runCatching {
        dataStore.data.map { (it[keyBuvid4] ?: "") to (it[keyBNut] ?: 0L) }.first()
    }.getOrDefault("" to 0L)

    suspend fun saveFingerprint(buvid4: String, bNut: Long) =
        runCatching { dataStore.edit { it[keyBuvid4] = buvid4; it[keyBNut] = bNut } }

    /**
     * 扫码成功前备份用户原有 cookie（撤销扫码用）。只备份第一次：之后重复扫码
     * 也不覆盖，保证任何时候撤销都能回到**最初**扫码前的状态。
     * 空串也是合法备份（扫码前是匿名态），用 present 标志区分「没备份过」。
     */
    suspend fun backupBeforeScan(cookie: String): Boolean = runCatching {
        dataStore.edit {
            if (it[keyScanBackupPresent] != true) {
                it[keyScanBackup] = cookie
                it[keyScanBackupPresent] = true
            }
        }
    }.isSuccess

    /** 撤销扫码登录：取回扫码前的 cookie 并清掉备份；从没扫码过返回 null */
    suspend fun popScanBackup(): String? = runCatching {
        var restored: String? = null
        dataStore.edit {
            if (it[keyScanBackupPresent] == true) {
                restored = it[keyScanBackup] ?: ""
                it.remove(keyScanBackup)
                it.remove(keyScanBackupPresent)
            }
        }
        restored
    }.getOrNull()

    /** 是否存在可撤销的扫码备份（设置页据此显示「退出扫码登录」入口） */
    fun scanBackupFlow(): Flow<Boolean> = dataStore.data
        .map { it[keyScanBackupPresent] == true }
        .catch { emit(false) }
}
