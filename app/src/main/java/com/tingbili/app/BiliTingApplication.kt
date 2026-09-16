package com.tingbili.app

import android.app.Application
import com.tingbili.app.data.api.AudioApi
import com.tingbili.app.data.api.BiliApiService
import com.tingbili.app.data.api.PlayUrlApi
import com.tingbili.app.data.api.SearchApi
import com.tingbili.app.data.api.WbiKeyStore
import com.tingbili.app.data.api.buildHttpClient
import com.tingbili.app.data.api.buildRetrofit
import com.tingbili.app.data.local.AppDatabase
import com.tingbili.app.data.local.BookRecordDao
import com.tingbili.app.data.local.CookieStore
import com.tingbili.app.data.local.SettingsStore
import com.tingbili.app.data.repo.LibraryRepository
import com.tingbili.app.data.repo.PlayRepository
import com.tingbili.app.data.repo.SearchRepository
import com.tingbili.app.player.PlayerHolder

class BiliTingApplication : Application() {
    lateinit var appDatabase: AppDatabase
    lateinit var settingsStore: SettingsStore
    lateinit var cookieStore: CookieStore
    lateinit var playerHolder: PlayerHolder
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        appDatabase = AppDatabase.get(this)
        settingsStore = SettingsStore(this)
        cookieStore = CookieStore(this)
        playerHolder = PlayerHolder(this)
        container = AppContainer(this)
    }
}

/**
 * 极简手工依赖注入容器：UI 层（ViewModel Factory）统一从这里取依赖，
 * 避免引入 DI 框架。API/仓库实例全局唯一，跨屏幕共享。
 */
class AppContainer(val app: BiliTingApplication) {
    private val service: BiliApiService =
        buildRetrofit(buildHttpClient("", "")).create(BiliApiService::class.java)
    private val wbiKeys = WbiKeyStore(service)

    val searchApi = SearchApi(service, wbiKeys, app.cookieStore)
    val searchRepo = SearchRepository(searchApi)
    val playRepo = PlayRepository(PlayUrlApi(service, wbiKeys), AudioApi(service))

    val dao: BookRecordDao = app.appDatabase.bookRecordDao()
    val libraryRepo = LibraryRepository(dao)

    // 引用 Application 已有实例，不重复持有
    val settingsStore: SettingsStore get() = app.settingsStore
    val cookieStore: CookieStore get() = app.cookieStore
    val playerHolder: PlayerHolder get() = app.playerHolder
}
