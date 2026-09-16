package com.tingbili.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.local.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {
    val themeMode: StateFlow<Int> = store.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val keywords: StateFlow<Set<String>> = store.keywords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val speed: StateFlow<Float> = store.playbackSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    fun setTheme(v: Int) = viewModelScope.launch { store.setThemeMode(v) }
    fun addKeyword(k: String) = viewModelScope.launch {
        val cur = keywords.value.toMutableSet(); cur.add(k); store.setKeywords(cur)
    }
    fun removeKeyword(k: String) = viewModelScope.launch {
        val cur = keywords.value.toMutableSet(); cur.remove(k); store.setKeywords(cur)
    }
    fun setSpeed(v: Float) = viewModelScope.launch { store.setPlaybackSpeed(v) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                SettingsViewModel(app.container.settingsStore)
            }
        }
    }
}
