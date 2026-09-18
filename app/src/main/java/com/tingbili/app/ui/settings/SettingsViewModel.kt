package com.tingbili.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.backup.WebDavBackup
import com.tingbili.app.data.local.CookieStore
import com.tingbili.app.data.local.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class SettingsViewModel(
    private val store: SettingsStore,
    private val cookieStore: CookieStore,
    private val app: BiliTingApplication
) : ViewModel() {
    /**
     * ⚠️ 这些偏好一律用 Eagerly，不要用 WhileSubscribed：
     * 曾经 webdavPass 用 WhileSubscribed 但 UI 从不订阅它，导致 `.value` 永远是空串，
     * 表现为"密码框有字、测试连接永远 401"。偏好数据量极小，常驻订阅没有成本，
     * 这样任何地方 `.value` 读到的一定是真值。DB/列表类大流才适合 WhileSubscribed。
     */
    val themeMode: StateFlow<Int> = store.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val themeColor: StateFlow<Int> = store.themeColor.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val audioOnly: StateFlow<Boolean> = store.audioOnly.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val keywords: StateFlow<Set<String>> = store.keywords.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val speed: StateFlow<Float> = store.playbackSpeed.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val sleepMinutes: StateFlow<Int> = store.sleepMinutes.stateIn(viewModelScope, SharingStarted.Eagerly, 30)
    val sleepEndOfTrack: StateFlow<Boolean> = store.sleepEndOfTrack.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val webdavUrl: StateFlow<String> = store.webdavUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val webdavUser: StateFlow<String> = store.webdavUser.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val webdavPass: StateFlow<String> = store.webdavPass.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    /** 是否已经保存过 WebDAV 密码（只对外暴露"有没有"，不让 UI 直接拿到密码） */
    val hasSavedWebdavPass: StateFlow<Boolean> = store.webdavPass
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val immersiveMode: StateFlow<Int> = store.immersiveMode.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val paletteStrength: StateFlow<Int> = store.paletteStrength.stateIn(viewModelScope, SharingStarted.Eagerly, 60)
    val autoNextEnabled: StateFlow<Boolean> = store.autoNextEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val rememberSpeedPerAuthor: StateFlow<Boolean> = store.rememberSpeedPerAuthor.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val playlistGroupMode: StateFlow<Int> = store.playlistGroupMode.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val cookieHeader: StateFlow<String> = cookieStore.cookieFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** 当前使用的匿名 cookie（未登录时自动生成） */
    val anonymousCookie: String get() = app.container.baseCookie
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _msg = MutableStateFlow<String?>(null)
    val msg: StateFlow<String?> = _msg.asStateFlow()

    // ============ 缓存管理（声明必须在 init 之前，防止 IO 协程先于属性初始化执行） ============
    private val _coverCacheSize = MutableStateFlow("")
    val coverCacheSize: StateFlow<String> = _coverCacheSize.asStateFlow()
    private val _playCacheSize = MutableStateFlow("")
    val playCacheSize: StateFlow<String> = _playCacheSize.asStateFlow()

    init {
        // 注意：init 必须放在缓存大小等属性声明之后（Kotlin 按声明顺序初始化），
        // 否则 IO 协程会在属性初始化完成前执行，触发 NPE。
        // 进页面就把各项缓存大小算出来，避免副标题显示空值（以前 playCacheSize 永远为空）
        refreshCacheSizes()
        viewModelScope.launch(Dispatchers.IO) { store.migrateWebdavPassToEncrypted() }
        // DataStore 读写异常以前被静默吞掉，这里统一转成用户可见提示
        viewModelScope.launch {
            store.errors.collect { _msg.value = it }
        }
    }

    fun setTheme(v: Int) = viewModelScope.launch { store.setThemeMode(v) }
    fun setThemeColor(v: Int) = viewModelScope.launch { store.setThemeColor(v) }
    fun setAudioOnly(v: Boolean) {
        viewModelScope.launch { store.setAudioOnly(v) }
        app.container.playerHolder.setAudioOnly(v)
    }
    fun addKeyword(k: String) = viewModelScope.launch {
        val cur = keywords.value.toMutableSet(); cur.add(k); store.setKeywords(cur)
    }
    fun removeKeyword(k: String) = viewModelScope.launch {
        val cur = keywords.value.toMutableSet(); cur.remove(k); store.setKeywords(cur)
    }
    fun setSpeed(v: Float) = viewModelScope.launch { store.setPlaybackSpeed(v) }
    fun setSleepMinutes(v: Int) = viewModelScope.launch { store.setSleepMinutes(v) }
    fun setSleepEndOfTrack(v: Boolean) = viewModelScope.launch { store.setSleepEndOfTrack(v) }

    fun setWebdavUrl(v: String) = viewModelScope.launch { store.setWebdavUrl(v) }
    fun setWebdavUser(v: String) = viewModelScope.launch { store.setWebdavUser(v) }
    fun setWebdavPass(v: String) = viewModelScope.launch { store.setWebdavPass(v) }
    fun setImmersiveMode(v: Int) = viewModelScope.launch { store.setImmersiveMode(v) }
    fun setPaletteStrength(v: Int) = viewModelScope.launch { store.setPaletteStrength(v) }
    fun setAutoNextEnabled(v: Boolean) = viewModelScope.launch { store.setAutoNextEnabled(v) }
    fun setRememberSpeedPerAuthor(v: Boolean) = viewModelScope.launch { store.setRememberSpeedPerAuthor(v) }

    /** 保存 B 站登录 cookie（用户从浏览器复制粘贴的整段 cookie 字符串） */
    fun setCookie(raw: String) {
        val cleaned = raw.trim()
        if (cleaned.isBlank()) {
            _msg.value = "cookie 不能为空"; return
        }
        _busy.value = true
        viewModelScope.launch {
            val buvid3 = runCatching { cookieStore.buvid3() }.getOrDefault("")
            val r = runCatching { cookieStore.save(buvid3, cleaned) }
            _busy.value = false
            _msg.value = if (r.isSuccess) "已保存，下次启动生效" else "保存失败：${r.exceptionOrNull()?.message}"
        }
    }

    /** 清空已保存的 cookie（退回匿名访问） */
    fun clearCookie() {
        _busy.value = true
        viewModelScope.launch {
            val buvid3 = runCatching { cookieStore.buvid3() }.getOrDefault("")
            runCatching { cookieStore.save(buvid3, "") }
            _busy.value = false
            _msg.value = "已清空 cookie"
        }
    }

    /** WebDAV 备份到云端；返回结果会推到 msg 中 */
    fun webdavBackup(pass: String) {
        val baseUrl = webdavUrl.value.trim()
        val user = webdavUser.value.trim()
        if (baseUrl.isBlank() || user.isBlank() || pass.isBlank()) {
            _msg.value = "请先填写 WebDAV 地址 / 用户名 / 备份密码"; return
        }
        _busy.value = true
        viewModelScope.launch {
            val records = app.container.libraryRepo.all()
            val snapshot = store.exportSnapshot()
            val r = WebDavBackup(baseUrl, user, store.webdavPass.first(), pass).backup(records, snapshot)
            _msg.value = r.fold(
                { "已备份 ${records.size} 条 → 云端 $it" },
                { "备份失败：${it.message}" }
            )
            _busy.value = false
        }
    }

    fun webdavRestore(pass: String) {
        val baseUrl = webdavUrl.value.trim()
        val user = webdavUser.value.trim()
        if (baseUrl.isBlank() || user.isBlank() || pass.isBlank()) {
            _msg.value = "请先填写 WebDAV 地址 / 用户名 / 备份密码"; return
        }
        _busy.value = true
        viewModelScope.launch {
            val client = WebDavBackup(baseUrl, user, store.webdavPass.first(), pass)
            // 覆盖式恢复有风险：先把当前数据另存一份到云端，出错还能捞回来
            runCatching {
                val cur = app.container.libraryRepo.all()
                if (cur.isNotEmpty()) {
                    client.backup(cur, store.exportSnapshot(), "restore_backup_${System.currentTimeMillis()}.bin")
                }
            }.onFailure { Log.w("SettingsViewModel", "pre-restore snapshot failed", it) }
            val r = client.restore()
            r.fold({ payload ->
                app.container.libraryRepo.replaceAll(payload.records)
                payload.settings?.let { store.importSnapshot(it) }
                _msg.value = "已恢复 ${payload.records.size} 条 + 设置（旧数据已另存为云端快照）"
            }, { _msg.value = "恢复失败：${it.message}" })
            _busy.value = false
        }
    }

    /** 仅测试连接，不传输数据；用来排查"用户名密码/URL 不对"的问题 */
    fun webdavPing() {
        val baseUrl = webdavUrl.value.trim()
        val user = webdavUser.value.trim()
        if (baseUrl.isBlank() || user.isBlank()) {
            _msg.value = "请先填写 WebDAV 地址和用户名"; return
        }
        _busy.value = true
        viewModelScope.launch {
            val (ok, detail) = WebDavBackup(baseUrl, user, store.webdavPass.first(), "").ping()
            _msg.value = if (ok) "连接成功 ✓" else "连接失败：$detail"
            _busy.value = false
        }
    }

    fun clearPlayCache() {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var freed = 0L
            runCatching {
                app.cacheDir.listFiles()?.forEach { f ->
                    if (f.isFile) { freed += f.length(); f.delete() }
                    else if (f.isDirectory && f.name !in listOf("coverCache", "img_cache")) {
                        freed += f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        f.deleteRecursively(); f.mkdirs()
                    }
                }
            }
            _playCacheSize.value = formatSize(0)
            _busy.value = false
            _msg.value = "在线播放缓存已清除（释放 ${formatSize(freed)}）"
        }
    }

    fun consumeMsg() { _msg.value = null }

    // ============ 缓存管理 ============

    fun refreshCacheSizes() {
        viewModelScope.launch(Dispatchers.IO) {
            _coverCacheSize.value = formatSize(coverCacheBytes())
            _playCacheSize.value = formatSize(playCacheBytes())
        }
    }

    private fun coverCacheBytes(): Long {
        var total = 0L
        runCatching {
            val dir = java.io.File(app.cacheDir, "coverCache")
            if (dir.exists()) total += dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val imgDir = java.io.File(app.cacheDir, "img_cache")
            if (imgDir.exists()) total += imgDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        return total
    }

    private fun playCacheBytes(): Long {
        var total = 0L
        runCatching {
            // ExoPlayer在线缓存 + 系统HTTP缓存
            val dir = java.io.File(app.cacheDir, "exo_cache")
            if (dir.exists()) total += dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            // 整个cacheDir除了coverCache和img_cache
            app.cacheDir.listFiles()?.forEach { f ->
                if (f.isFile) total += f.length()
                else if (f.isDirectory && f.name !in listOf("coverCache", "img_cache", "exo_cache")) {
                    total += f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                }
            }
        }
        return total
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 100) "${"%.0f".format(mb)} MB" else "${"%.1f".format(mb)} MB"
    }

    fun clearCoverCache() {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var freed = 0L
            runCatching {
                listOf("coverCache", "img_cache").forEach { name ->
                    val dir = java.io.File(app.cacheDir, name)
                    if (dir.exists()) {
                        freed += dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        dir.deleteRecursively(); dir.mkdirs()
                    }
                }
            }
            _coverCacheSize.value = formatSize(0)
            _busy.value = false
            _msg.value = "封面缓存已清除（释放 ${formatSize(freed)}）"
        }
    }


    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                SettingsViewModel(app.container.settingsStore, app.container.cookieStore, app)
            }
        }
    }
}
