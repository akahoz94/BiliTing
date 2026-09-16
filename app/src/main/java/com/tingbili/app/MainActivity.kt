package com.tingbili.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tingbili.app.ui.nav.BiliNavHost
import com.tingbili.app.ui.theme.BiliTingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings = (application as BiliTingApplication).settingsStore
            val themeMode by settings.themeMode.collectAsState(initial = 0)
            val dark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            BiliTingTheme(darkTheme = dark) { BiliNavHost() }
        }
    }
}
