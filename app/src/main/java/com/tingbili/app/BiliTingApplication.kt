package com.tingbili.app

import android.app.Application
import android.util.Log
import com.tingbili.app.data.api.AudioApi
import com.tingbili.app.data.api.AuthorApi
import com.tingbili.app.data.api.BiliApiService
import com.tingbili.app.data.api.PlayUrlApi
import com.tingbili.app.data.api.SearchApi
import com.tingbili.app.data.api.WbiKeyStore
import com.tingbili.app.data.api.WbiSigner
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
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class BiliTingApplication : Application() {
    lateinit var appDatabase: AppDatabase
    lateinit var settingsStore: SettingsStore
    lateinit var cookieStore: CookieStore
    lateinit var playerHolder: PlayerHolder
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        Log.i(TAG_BANNER, "=== BiliTing v0.5-debug (crash handler installed) ===")
        appDatabase = AppDatabase.get(this)
        settingsStore = SettingsStore(this)
        cookieStore = CookieStore(this)
        playerHolder = PlayerHolder(this)
        container = AppContainer(this)
    }

    /**
     * 全局未捕获异常落盘到 filesDir/crash.log，附加设备信息，便于无 USB 调试时回看。
     * 即便设备无法 logcat，至少本机文件可被用户手机自带文件管理器取出。
     */
    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val body = buildString {
                appendLine("=== BiliTing crash @ ${System.currentTimeMillis()} ===")
                appendLine("thread=${thread.name} build=v0.5-debug")
                appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}")
                appendLine(sw.toString())
            }
            try {
                File(filesDir, "crash.log").writeText(body)
            } catch (_: Throwable) { /* 写不动就 logcat */ }
            Log.e(TAG_CRASH, body)
            prev?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG_BANNER = "BiliTing"
        private const val TAG_CRASH = "BiliTingCrash"
    }
}

/**
 * 极简手工依赖注入容器：UI 层（ViewModel Factory）统一从这里取依赖，
 * 避免引入 DI 框架。API/仓库实例全局唯一，跨屏幕共享。
 */
class AppContainer(val app: BiliTingApplication) {
    private val service: BiliApiService = run {
        // B站风控要求匿名请求携带 buvid3：启动时读一次，没有则生成并持久化
        val buvid3 = runBlocking {
            var v = app.cookieStore.buvid3()
            if (v.isBlank()) {
                v = WbiSigner.randomBuvid3()
                app.cookieStore.save(v, "")
            }
            v
        }
        val cookie = "buvid3=$buvid3; buvid4=${WbiSigner.randomBuvid4()}; b_nut=${System.currentTimeMillis() / 1000}"
        buildRetrofit(buildHttpClient(cookie)).create(BiliApiService::class.java)
    }
    private val wbiKeys = WbiKeyStore(service)

    val searchApi = SearchApi(service, wbiKeys)
    val searchRepo = SearchRepository(searchApi)
    val authorApi = AuthorApi(service, wbiKeys)
    val playRepo = PlayRepository(PlayUrlApi(service, wbiKeys), AudioApi(service), service)

    val dao: BookRecordDao = app.appDatabase.bookRecordDao()
    val libraryRepo = LibraryRepository(dao)

    // 引用 Application 已有实例，不重复持有
    val settingsStore: SettingsStore get() = app.settingsStore
    val cookieStore: CookieStore get() = app.cookieStore
    val playerHolder: PlayerHolder get() = app.playerHolder
}
