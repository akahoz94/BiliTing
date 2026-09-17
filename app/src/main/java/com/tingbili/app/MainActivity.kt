package com.tingbili.app

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tingbili.app.ui.nav.BiliNavHost
import com.tingbili.app.ui.theme.BiliTingTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = (application as BiliTingApplication).settingsStore
        // 读一次已持久化的主题，让系统栏图标配色与主题保持一致
        val savedTheme = runBlocking { settings.themeMode.first() }
        val dark = when (savedTheme) {
            1 -> false
            2 -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        // 显式开启沉浸式/边到边：状态栏、导航栏全透明，图标颜色随主题切换，
        // 由 Compose 层用 windowInsets 做内容避让。
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                             else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                                 else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = savedTheme)
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            BiliTingTheme(darkTheme = darkTheme) { BiliNavHost() }
        }
    }
}