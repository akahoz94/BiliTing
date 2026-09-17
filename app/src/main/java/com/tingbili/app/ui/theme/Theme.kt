package com.tingbili.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tingbili.app.data.local.Defaults

object ThemePalettes {
    val Purple = Color(0xFF6750A4)
    val Blue   = Color(0xFF1976D2)
    val Green  = Color(0xFF388E3C)
    val Orange = Color(0xFFE65100)
    val Pink   = Color(0xFFAD1457)
    val Red    = Color(0xFFD32F2F)

    fun primaryFor(color: Int): Color = when (color) {
        Defaults.COLOR_BLUE   -> Blue
        Defaults.COLOR_GREEN  -> Green
        Defaults.COLOR_ORANGE -> Orange
        Defaults.COLOR_PINK   -> Pink
        Defaults.COLOR_RED    -> Red
        else                  -> Purple
    }
}

@Composable
fun BiliTingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themeColor: Int = Defaults.COLOR_PURPLE,
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> {
            val primary = ThemePalettes.primaryFor(themeColor)
            darkColorScheme(primary = primary)
        }
        else -> {
            val primary = ThemePalettes.primaryFor(themeColor)
            lightColorScheme(primary = primary)
        }
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}