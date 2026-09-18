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
import com.tingbili.app.data.backup.SyncManager
import com.tingbili.app.data.local.AppDatabase
import com.tingbili.app.data.local.BookRecordDao
import com.tingbili.app.data.local.CookieStore
import com.tingbili.app.data.local.SettingsStore
import com.tingbili.app.data.repo.LibraryRepository
import com.tingbili.app.data.repo.PlayRepository
import com.tingbili.app.data.repo.SearchRepository
import com.tingbili.app.player.PlayerHolder
import com.tingbili.app.player.PlayerLauncher
import com.tingbili.app.util.AppImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    lateinit var playerLauncher: PlayerLauncher
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        _instance = this
        installCrashHandler()
        Log.i(TAG_BANNER, "=== BiliTing v0.19.5 ===")
        runCatching { AppImageLoader.get(this) }
            .onFailure { Log.e(TAG_BANNER, "AppImageLoader 初始化失败", it) }
        try {
            appDatabase = AppDatabase.get(this)
            Log.i(TAG_BANNER, "AppDatabase open ok: version=${appDatabase.openHelper.readableDatabase.version}")
        } catch (t: Throwable) {
            Log.e(TAG_BANNER, "AppDatabase init failed, falling back to destructive rebuild", t)
            runCatching { deleteDatabase("bili_ting.db") }
            try {
                appDatabase = AppDatabase.get(this)
                Log.w(TAG_BANNER, "AppDatabase rebuilt (local data wiped)")
            } catch (t2: Throwable) {
                Log.e(TAG_BANNER, "AppDatabase 重建也失败，运行时访问会继续报错，但不阻断启动", t2)
            }
        }
        try { settingsStore = SettingsStore(this) } catch (t: Throwable) { Log.e(TAG_BANNER, "SettingsStore 初始化失败", t) }
        try { cookieStore = CookieStore(this) } catch (t: Throwable) { Log.e(TAG_BANNER, "CookieStore 初始化失败", t) }
        try { playerHolder = PlayerHolder(this, settingsStore) } catch (t: Throwable) { Log.e(TAG_BANNER, "PlayerHolder 初始化失败", t) }
        try { container = AppContainer(this) } catch (t: Throwable) { Log.e(TAG_BANNER, "AppContainer 初始化失败", t) }
        try {
            playerLauncher = PlayerLauncher(playerHolder, container.playRepo, container.libraryRepo, container.biliService, settingsStore)
            container.playerLauncher = playerLauncher
            // 播放器自动跨进了占位项：占位项自己的下标就是"该播哪一集"（别再加一，那是跳集）
            playerHolder.onSeekToPartRequested = { index ->
                applicationScope.launch(Dispatchers.Main) {
                    runCatching { playerLauncher.jumpToPart(index) }
                        .onFailure {
                            Log.w(TAG_BANNER, "切集失败（$index）：${it.message}")
                            com.tingbili.app.util.ErrorBus.post(message = "切集失败：${it.message ?: "网络异常"}")
                        }
                }
            }
            // 冷启动迷你条处于"待播"态（只有 record，播放器是空的），点播放时补装载
            playerHolder.onEmptyPlayRequested = {
                applicationScope.launch(Dispatchers.Main) {
                    runCatching { playerLauncher.resumeCurrent() }
                        .onFailure {
                            Log.w(TAG_BANNER, "续播失败：${it.message}")
                            com.tingbili.app.util.ErrorBus.post(message = "续播失败：${it.message ?: "网络异常"}")
                        }
                }
            }
            // 进度落盘下沉到播放层：从迷你条/通知栏听的时候听单进度条才不会是空的
            playerHolder.onProgressPersist = { r: com.tingbili.app.data.local.BookRecord ->
                container.libraryRepo.recordPlayed(r)
            }
        } catch (t: Throwable) {
            Log.e(TAG_BANNER, "PlayerLauncher 初始化失败", t)
        }
        // 预建 ExoPlayer：必须在主线程（media3 的 player 绑定主线程，后台线程创建会在
        // 之后任何一次访问时报 "Player is accessed on the wrong thread"）
        applicationScope.launch(Dispatchers.Main) {
            runCatching {
                playerHolder.ensurePlayer()
            }.onFailure {
                Log.w(TAG_BANNER, "启动预初始化 Player 失败：${it.message}")
            }
        }
        applicationScope.launch(Dispatchers.IO) {
            // 启动时恢复最近播放记录，让迷你播放条常驻显示
            runCatching {
                val recent = appDatabase.bookRecordDao().getMostRecent()
                if (recent != null) {
                    playerHolder.updateRecord(recent)
                    Log.i(TAG_BANNER, "恢复最近播放记录：${recent.title}")
                }
            }.onFailure {
                Log.w(TAG_BANNER, "恢复最近播放记录失败：${it.message}")
            }
            // 把历史 KeyStore 密文密码转回明文（那层加密卸载后会变成死锁，详见 SettingsStore）
            runCatching { settingsStore.migrateSecretsToPlaintext() }
                .onFailure { Log.w(TAG_BANNER, "WebDAV 密码迁移失败", it) }
        }

        // 启动自动同步：先自举外置配置（重装后靠它把 WebDAV 凭证找回来），再与云端合并一次。
        // 没配 WebDAV 或用户关掉开关时静默跳过，绝不影响启动。
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                val result = SyncManager(this@BiliTingApplication, settingsStore, container.libraryRepo).sync()
                when (result) {
                    is SyncManager.Result.Ok ->
                        Log.i(TAG_BANNER, "启动同步完成：云端 ${result.fetched} 条 → 本地共 ${result.total} 条")
                    is SyncManager.Result.Failed ->
                        Log.w(TAG_BANNER, "启动同步失败：${result.message}")
                    SyncManager.Result.Skipped ->
                        Log.i(TAG_BANNER, "启动同步跳过（未配置 WebDAV 或已关闭自动同步）")
                }
            }.onFailure { Log.w(TAG_BANNER, "启动同步异常", it) }
        }
    }

    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val body = buildString {
                    appendLine("=== BiliTing crash @ ${System.currentTimeMillis()} ===")
                    appendLine("thread=${thread.name} build=v0.19.5")
                    appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}")
                    appendLine(sw.toString())
                }
                runCatching { File(filesDir, "crash.log").writeText(body) }
                Log.e(TAG_CRASH, body)
                Log.i(TAG_CRASH, "===== SHORT-CRASH (给开发者) =====")
                sw.toString().lineSequence().take(30).forEach { Log.i(TAG_CRASH, it) }
                Log.i(TAG_CRASH, "===== /SHORT-CRASH =====")
            }.onFailure {
                Log.e(TAG_CRASH, "写 crash.log 失败", it)
            }
            runCatching { prev?.uncaughtException(thread, throwable) }
        }
    }

    companion object {
        private const val TAG_BANNER = "BiliTing"
        private const val TAG_CRASH = "BiliTingCrash"

        @Volatile private var _instance: BiliTingApplication? = null
        fun get(): BiliTingApplication? = _instance
    }
}

class AppContainer(val app: BiliTingApplication) {
    private val buvid3: String = runCatching {
        runBlocking {
            var v = app.cookieStore.buvid3()
            if (v.isBlank()) {
                v = WbiSigner.randomBuvid3()
                app.cookieStore.save(v, "")
            }
            v
        }
    }.getOrElse { e ->
        Log.w("AppContainer", "buvid3 初始化失败，改用临时值", e)
        WbiSigner.randomBuvid3()
    }
    val baseCookie: String get() = "buvid3=$buvid3; buvid4=${WbiSigner.randomBuvid4()}; b_nut=${System.currentTimeMillis() / 1000}"
    val cookieProvider: () -> String = {
        val userCookie = runCatching { kotlinx.coroutines.runBlocking { app.cookieStore.cookieHeader() } }.getOrDefault("")
        if (userCookie.isNotBlank()) "$baseCookie; $userCookie" else baseCookie
    }

    val biliService: BiliApiService = buildRetrofit(buildHttpClient(cookieProvider)).create(BiliApiService::class.java)
    private val wbiKeys = WbiKeyStore(biliService)

    val searchApi = SearchApi(biliService, wbiKeys)
    val searchRepo = SearchRepository(searchApi)
    val authorApi = AuthorApi(biliService, wbiKeys)
    val playRepo = PlayRepository(PlayUrlApi(biliService, wbiKeys), AudioApi(biliService), biliService, app)
    val downloadManager: com.tingbili.app.download.DownloadManager = runCatching {
        val client = buildHttpClient(cookieProvider)
        com.tingbili.app.download.DownloadManager(app, playRepo, app.cookieStore, client)
    }.getOrElse { e ->
        Log.e("AppContainer", "DownloadManager 初始化失败", e)
        com.tingbili.app.download.DownloadManager.empty(app)
    }

    val dao: BookRecordDao = app.appDatabase.bookRecordDao()
    val libraryRepo = LibraryRepository(dao)

    val settingsStore: SettingsStore get() = app.settingsStore
    val cookieStore: CookieStore get() = app.cookieStore
    val playerHolder: PlayerHolder get() = app.playerHolder

    var playerLauncher: PlayerLauncher = PlayerLauncher(
        app.playerHolder, playRepo, libraryRepo, biliService, app.settingsStore
    )
}