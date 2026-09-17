package com.tingbili.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.backup.WebDavBackup
import com.tingbili.app.data.local.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore,
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
            val r = WebDavBackup(baseUrl, user, "", pass).backup(records)
            _msg.value = r.fold({ "已备份 ${records.size} 条" }, { "备份失败：${it.message}" })
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
            val r = WebDavBackup(baseUrl, user, "", pass).restore()
            r.fold({ list ->
                app.container.libraryRepo.replaceAll(list)
                _msg.value = "已恢复 ${list.size} 条"
            }, { _msg.value = "恢复失败：${it.message}" })
            _busy.value = false
        }
    }

    fun consumeMsg() { _msg.value = null }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                SettingsViewModel(app.container.settingsStore, app)
            }
        }
    }
}