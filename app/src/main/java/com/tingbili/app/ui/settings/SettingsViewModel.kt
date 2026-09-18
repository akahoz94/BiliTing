package com.tingbili.app.ui.settings

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class SettingsViewModel(
    private val store: SettingsStore,
    private val cookieStore: CookieStore,
    private val app: BiliTingApplication
) : ViewModel() {
    val themeMode: StateFlow<Int> = store.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val themeColor: StateFlow<Int> = store.themeColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val audioOnly: StateFlow<Boolean> = store.audioOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val keywords: StateFlow<Set<String>> = store.keywords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val speed: StateFlow<Float> = store.playbackSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val sleepMinutes: StateFlow<Int> = store.sleepMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)
    val sleepEndOfTrack: StateFlow<Boolean> = store.sleepEndOfTrack.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val webdavUrl: StateFlow<String> = store.webdavUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val webdavUser: StateFlow<String> = store.webdavUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val webdavPass: StateFlow<String> = store.webdavPass.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val immersiveMode: StateFlow<Int> = store.immersiveMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val paletteStrength: StateFlow<Int> = store.paletteStrength.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)
    val autoNextEnabled: StateFlow<Boolean> = store.autoNextEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val rememberSpeedPerAuthor: StateFlow<Boolean> = store.rememberSpeedPerAuthor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val cookieHeader: StateFlow<String> = cookieStore.cookieFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _msg = MutableStateFlow<String?>(null)
    val msg: StateFlow<String?> = _msg.asStateFlow()

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
            val r = WebDavBackup(baseUrl, user, webdavPass.value, pass).backup(records, snapshot)
            _msg.value = r.fold({ "已备份 ${records.size} 条 + 设置" }, { "备份失败：${it.message}" })
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
            val r = WebDavBackup(baseUrl, user, webdavPass.value, pass).restore()
            r.fold({ payload ->
                app.container.libraryRepo.replaceAll(payload.records)
                payload.settings?.let { store.importSnapshot(it) }
                _msg.value = "已恢复 ${payload.records.size} 条 + 设置"
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
            val ok = WebDavBackup(baseUrl, user, webdavPass.value, "").ping()
            _msg.value = if (ok) "连接成功 ✓" else "连接失败：检查地址和用户名/密码"
            _busy.value = false
        }
    }

    fun consumeMsg() { _msg.value = null }

    // ============ 缓存管理 ============

    private val _coverCacheSize = MutableStateFlow("")
    val coverCacheSize: StateFlow<String> = _coverCacheSize.asStateFlow()
    private val _downloadCacheSize = MutableStateFlow("")
    val downloadCacheSize: StateFlow<String> = _downloadCacheSize.asStateFlow()

    fun refreshCacheSizes() {
        viewModelScope.launch(Dispatchers.IO) {
            _coverCacheSize.value = formatSize(coverCacheBytes())
            _downloadCacheSize.value = formatSize(downloadCacheBytes())
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

    private fun downloadCacheBytes(): Long {
        var total = 0L
        runCatching {
            val dir = java.io.File(app.filesDir, "downloads")
            if (dir.exists()) total += dir.walkTopDown().filter { it.isFile && it.name != "index.json" }.sumOf { it.length() }
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

    fun clearDownloadCache() {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var freed = 0L
            runCatching {
                val dir = java.io.File(app.filesDir, "downloads")
                if (dir.exists()) {
                    freed = dir.walkTopDown().filter { it.isFile && it.name != "index.json" }.sumOf { it.length() }
                    dir.walkTopDown().filter { it.isFile && it.name != "index.json" }.forEach { it.delete() }
                }
            }
            _downloadCacheSize.value = formatSize(0)
            _busy.value = false
            _msg.value = "下载音频已清除（释放 ${formatSize(freed)}）"
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