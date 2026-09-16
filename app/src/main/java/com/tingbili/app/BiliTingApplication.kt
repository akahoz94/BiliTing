package com.tingbili.app

import android.app.Application
import com.tingbili.app.data.local.AppDatabase
import com.tingbili.app.data.local.CookieStore
import com.tingbili.app.data.local.SettingsStore
import com.tingbili.app.player.PlayerHolder

class BiliTingApplication : Application() {
    lateinit var appDatabase: AppDatabase
    lateinit var settingsStore: SettingsStore
    lateinit var cookieStore: CookieStore
    lateinit var playerHolder: PlayerHolder

    override fun onCreate() {
        super.onCreate()
        appDatabase = AppDatabase.get(this)
        settingsStore = SettingsStore(this)
        cookieStore = CookieStore(this)
        playerHolder = PlayerHolder(this)
    }
}
