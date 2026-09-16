package com.tingbili.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tingbili.app.ui.nav.BiliNavHost
import com.tingbili.app.ui.theme.BiliTingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BiliTingTheme { BiliNavHost() } }
    }
}
