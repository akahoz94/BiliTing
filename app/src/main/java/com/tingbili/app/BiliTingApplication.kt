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
import com.tingbili.app.player.PlayerLauncher
import com.tingbili.app.util.AppImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        Log.i(TAG_BANNER, "=== BiliTing v0.16.0-debug (unified UI tokens + shared components) ===")
        // 初始化 Coil 全局 ImageLoader（带 UA/Referer，CoverUtil 失效兜底）
        runCatching { AppImageLoader.get(this) }
            .onFailure { Log.e(TAG_BANNER, "AppImageLoader 初始化失败", it) }
        try {
            appDatabase = AppDatabase.get(this)
            Log.i(TAG_BANNER, "AppDatabase open ok: version=${appDatabase.openHelper.readableDatabase.version}")
        } catch (t: Throwable) {
            Log.e(TAG_BANNER, "AppDatabase init failed, falling back to destructive rebuild", t)
            // schema 与 migration 双重失败 → 强制删 db 后重建，保留用户可打开。
            // 重建这一路若再抛（文件系统损坏/IO 错误），也用同级别兜底，绝不逃出 onCreate。
            runCatching { deleteDatabase("bili_ting.db") }
            try {
                appDatabase = AppDatabase.get(this)
                Log.w(TAG_BANNER, "AppDatabase rebuilt (local data wiped)")
            } catch (t2: Throwable) {
                Log.e(TAG_BANNER, "AppDatabase 重建也失败，运行时访问会继续报错，但不阻断启动", t2)
            }
        }
        // 后续所有构造都做"绝不阻断启动"兜底：任何一个环节抛异常，应用至少能进入首页
        try { settingsStore = SettingsStore(this) } catch (t: Throwable) { Log.e(TAG_BANNER, "SettingsStore 初始化失败", t) }
        try { cookieStore = CookieStore(this) } catch (t: Throwable) { Log.e(TAG_BANNER, "CookieStore 初始化失败", t) }
        try { playerHolder = PlayerHolder(this, settingsStore) } catch (t: Throwable) { Log.e(TAG_BANNER, "PlayerHolder 初始化失败（构造已懒化，理论上不会抛）", t) }
        try { container = AppContainer(this) } catch (t: Throwable) { Log.e(TAG_BANNER, "AppContainer 初始化失败", t) }
        try {
            playerLauncher = PlayerLauncher(playerHolder, container.playRepo, container.libraryRepo, container.biliService, settingsStore)
            // 让 PlaybackService（系统后台进程）能拿到 launcher 翻下一集
            container.playerLauncher = playerLauncher
            // ExoPlayer 内置 sentinel MediaItem 触发后调用 launcher.nextPart()/prevPart()
            playerHolder.onSeekToNextRequested = {
                applicationScope.launch(Dispatchers.Main) { playerLauncher.nextPart() }
            }
            playerHolder.onSeekToPreviousRequested = {
                applicationScope.launch(Dispatchers.Main) { playerLauncher.prevPart() }
            }
        } catch (t: Throwable) {
            Log.e(TAG_BANNER, "PlayerLauncher 初始化失败", t)
        }
    }

    /**
     * 全局未捕获异常落盘到 filesDir/crash.log，附加设备信息，便于无 USB 调试时回看。
     * 即便设备无法 logcat，至少本机文件可被用户手机自带文件管理器取出。
     */
    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val body = buildString {
                    appendLine("=== BiliTing crash @ ${System.currentTimeMillis()} ===")
                    appendLine("thread=${thread.name} build=v0.10.3-debug")
                    appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}")
                    appendLine(sw.toString())
                }
                runCatching { File(filesDir, "crash.log").writeText(body) }
                Log.e(TAG_CRASH, body)
                // 把前 30 行关键栈也走 Logcat Info 通道，方便用户在系统日志/Logcat app 里直接看到
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

/**
 * 极简手工依赖注入容器：UI 层（ViewModel Factory）统一从这里取依赖，
 * 避免引入 DI 框架。API/仓库实例全局唯一，跨屏幕共享。
 */
class AppContainer(val app: BiliTingApplication) {
    // B站风控要求匿名请求携带 buvid3：启动时读一次，没有则生成并持久化。
    // runBlocking 被压在主线程 onCreate 内，DataStore 读写在文件损坏/异常时会抛异常，
    // 若不兜底会直接拖垮整个启动（进 App 即闪退）。任何失败都退回临时值，绝不阻断启动。
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
        Log.w("AppContainer", "buvid3 初始化失败，改用手头临时值继续启动", e)
        WbiSigner.randomBuvid3()
    }
    // 启动时默认 cookie（仅含 buvid3 系列），用户后续在设置页粘贴的完整 cookie 由
    // biliService / downloadManager 在请求时通过全局拦截器按需补齐。
    val cookie: String = "buvid3=$buvid3; buvid4=${WbiSigner.randomBuvid4()}; b_nut=${System.currentTimeMillis() / 1000}"

    val biliService: BiliApiService = buildRetrofit(buildHttpClient(cookie)).create(BiliApiService::class.java)
    private val wbiKeys = WbiKeyStore(biliService)

    val searchApi = SearchApi(biliService, wbiKeys)
    val searchRepo = SearchRepository(searchApi)
    val authorApi = AuthorApi(biliService, wbiKeys)
    val playRepo = PlayRepository(PlayUrlApi(biliService, wbiKeys), AudioApi(biliService), biliService)
    val downloadManager: com.tingbili.app.download.DownloadManager = runCatching {
        // 复用 biliService 用的 OkHttpClient（已带 UA/Referer/Cookie 全局拦截器）
        val client = buildHttpClient(cookie)
        com.tingbili.app.download.DownloadManager(app, playRepo, app.cookieStore, client)
    }.getOrElse { e ->
        Log.e("AppContainer", "DownloadManager 初始化失败，使用空壳兜底", e)
        com.tingbili.app.download.DownloadManager.empty(app)
    }

    val dao: BookRecordDao = app.appDatabase.bookRecordDao()
    val libraryRepo = LibraryRepository(dao)

    // 引用 Application 已有实例，不重复持有
    val settingsStore: SettingsStore get() = app.settingsStore
    val cookieStore: CookieStore get() = app.cookieStore
    val playerHolder: PlayerHolder get() = app.playerHolder

    /** PlayerLauncher 由 Application 注入，PlaybackService（锁屏 ⏭）也需要它 */
    var playerLauncher: PlayerLauncher = PlayerLauncher(
        app.playerHolder, playRepo, libraryRepo, biliService, app.settingsStore
    )
}
