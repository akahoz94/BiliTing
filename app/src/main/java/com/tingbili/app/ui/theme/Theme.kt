package com.tingbili.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tingbili.app.data.local.Defaults

/**
 * 完整 M3 调色板：每个主题色 13 个色位（primary/secondary/tertiary × 主色 + onXxx + container 系列）
 *
 * 设计原则：
 *   1. 主题色只决定 primary 色系（按主色派生整套），secondary/tertiary 取同色相但低饱和
 *   2. light/dark 双套，dark 套把背景压暗、on 颜色提亮
 *   3. 不再调用 darkColorScheme(primary=) 的方式 —— 那只覆盖 primary，其他仍是默认紫
 *      → 必须传完整 ColorScheme 才能保证"换肤彻底"
 */
object ThemePalettes {

    // ============ Light 调色板（6 色 × 13 位） ============

    private val PurpleLight = lightColorScheme(
        primary             = Color(0xFF6750A4),
        onPrimary           = Color(0xFFFFFFFF),
        primaryContainer    = Color(0xFFEADDFF),
        onPrimaryContainer  = Color(0xFF21005D),

        secondary             = Color(0xFF625B71),
        onSecondary           = Color(0xFFFFFFFF),
        secondaryContainer    = Color(0xFFE8DEF8),
        onSecondaryContainer  = Color(0xFF1D192B),

        tertiary             = Color(0xFF7D5260),
        onTertiary           = Color(0xFFFFFFFF),
        tertiaryContainer    = Color(0xFFFFD8E4),
        onTertiaryContainer  = Color(0xFF31111D),

        background   = Color(0xFFFFFBFE),
        onBackground = Color(0xFF1C1B1F),
        surface      = Color(0xFFFFFBFE),
        onSurface    = Color(0xFF1C1B1F),
        surfaceVariant     = Color(0xFFE7E0EC),
        onSurfaceVariant   = Color(0xFF49454F),
        outline            = Color(0xFF79747E),
        outlineVariant     = Color(0xFFCAC4D0),

        error             = Color(0xFFB3261E),
        onError           = Color(0xFFFFFFFF),
        errorContainer    = Color(0xFFF9DEDC),
        onErrorContainer  = Color(0xFF410E0B),

        inverseSurface    = Color(0xFF313033),
        inverseOnSurface  = Color(0xFFF4EFF4),
        inversePrimary    = Color(0xFFD0BCFF),

        scrim             = Color(0xFF000000)
    )

    private val BlueLight = lightColorScheme(
        primary             = Color(0xFF1976D2),
        onPrimary           = Color(0xFFFFFFFF),
        primaryContainer    = Color(0xFFD1E4FF),
        onPrimaryContainer  = Color(0xFF001D36),

        secondary             = Color(0xFF1976D2),
        onSecondary           = Color(0xFFFFFFFF),
        secondaryContainer    = Color(0xFFD1E4FF),
        onSecondaryContainer  = Color(0xFF001D36),

        tertiary             = Color(0xFF0288D1),
        onTertiary           = Color(0xFFFFFFFF),
        tertiaryContainer    = Color(0xFFCDE5FF),
        onTertiaryContainer  = Color(0xFF001E2E),

        background   = Color(0xFFFCFCFF),
        onBackground = Color(0xFF1A1C1E),
        surface      = Color(0xFFFCFCFF),
        onSurface    = Color(0xFF1A1C1E),
        surfaceVariant     = Color(0xFFDFE2EB),
        onSurfaceVariant   = Color(0xFF43474E),
        outline            = Color(0xFF73777F),
        outlineVariant     = Color(0xFFC3C7CF),

        error             = Color(0xFFBA1A1A),
        onError           = Color(0xFFFFFFFF),
        errorContainer    = Color(0xFFFFDAD6),
        onErrorContainer  = Color(0xFF410002),

        inverseSurface    = Color(0xFF2F3033),
        inverseOnSurface  = Color(0xFFF1F0F4),
        inversePrimary    = Color(0xFF9ECAFF),

        scrim             = Color(0xFF000000)
    )

    private val GreenLight = lightColorScheme(
        primary             = Color(0xFF388E3C),
        onPrimary           = Color(0xFFFFFFFF),
        primaryContainer    = Color(0xFFB7F0BB),
        onPrimaryContainer  = Color(0xFF002106),

        secondary             = Color(0xFF52634F),
        onSecondary           = Color(0xFFFFFFFF),
        secondaryContainer    = Color(0xFFD5E8CF),
        onSecondaryContainer  = Color(0xFF101F10),

        tertiary             = Color(0xFF386569),
        onTertiary           = Color(0xFFFFFFFF),
        tertiaryContainer    = Color(0xFFBCEBEF),
        onTertiaryContainer  = Color(0xFF002023),

        background   = Color(0xFFFCFDF6),
        onBackground = Color(0xFF1A1C19),
        surface      = Color(0xFFFCFDF6),
        onSurface    = Color(0xFF1A1C19),
        surfaceVariant     = Color(0xFFDEE5D9),
        onSurfaceVariant   = Color(0xFF424940),
        outline            = Color(0xFF72796F),
        outlineVariant     = Color(0xFFC2C9BD),

        error             = Color(0xFFBA1A1A),
        onError           = Color(0xFFFFFFFF),
        errorContainer    = Color(0xFFFFDAD6),
        onErrorContainer  = Color(0xFF410002),

        inverseSurface    = Color(0xFF2E312D),
        inverseOnSurface  = Color(0xFFEEF1E9),
        inversePrimary    = Color(0xFF9BD49F),

        scrim             = Color(0xFF000000)
    )

    private val OrangeLight = lightColorScheme(
        primary             = Color(0xFFE65100),
        onPrimary           = Color(0xFFFFFFFF),
        primaryContainer    = Color(0xFFFFDBC9),
        onPrimaryContainer  = Color(0xFF381E00),

        secondary             = Color(0xFF77574E),
        onSecondary           = Color(0xFFFFFFFF),
        secondaryContainer    = Color(0xFFFFDBC9),
        onSecondaryContainer  = Color(0xFF2C150F),

        tertiary             = Color(0xFF6750A4),
        onTertiary           = Color(0xFFFFFFFF),
        tertiaryContainer    = Color(0xFFEADDFF),
        onTertiaryContainer  = Color(0xFF21005D),

        background   = Color(0xFFFFFBF8),
        onBackground = Color(0xFF201A17),
        surface      = Color(0xFFFFFBF8),
        onSurface    = Color(0xFF201A17),
        surfaceVariant     = Color(0xFFF4DED4),
        onSurfaceVariant   = Color(0xFF52443D),
        outline            = Color(0xFF85736C),
        outlineVariant     = Color(0xFFD7C2B8),

        error             = Color(0xFFBA1A1A),
        onError           = Color(0xFFFFFFFF),
        errorContainer    = Color(0xFFFFDAD6),
        onErrorContainer  = Color(0xFF410002),

        inverseSurface    = Color(0xFF362F2C),
        inverseOnSurface  = Color(0xFFFBEEE7),
        inversePrimary    = Color(0xFFFFB68F),

        scrim             = Color(0xFF000000)
    )

    private val PinkLight = lightColorScheme(
        primary             = Color(0xFFAD1457),
        onPrimary           = Color(0xFFFFFFFF),
        primaryContainer    = Color(0xFFFFD9E2),
        onPrimaryContainer  = Color(0xFF3F0017),

        secondary             = Color(0xFF74565F),
        onSecondary           = Color(0xFFFFFFFF),
        secondaryContainer    = Color(0xFFFFD9E2),
        onSecondaryContainer  = Color(0xFF2B151C),

        tertiary             = Color(0xFF7C5535),
        onTertiary           = Color(0xFFFFFFFF),
        tertiaryContainer    = Color(0xFFFFDBC2),
        onTertiaryContainer  = Color(0xFF2E1500),

        background   = Color(0xFFFFFBF8),
        onBackground = Color(0xFF201A1B),
        surface      = Color(0xFFFFFBF8),
        onSurface    = Color(0xFF201A1B),
        surfaceVariant     = Color(0xFFF2DDE2),
        onSurfaceVariant   = Color(0xFF514347),
        outline            = Color(0xFF837377),
        outlineVariant     = Color(0xFFD5C2C7),

        error             = Color(0xFFBA1A1A),
        onError           = Color(0xFFFFFFFF),
        errorContainer    = Color(0xFFFFDAD6),
        onErrorContainer  = Color(0xFF410002),

        inverseSurface    = Color(0xFF362F30),
        inverseOnSurface  = Color(0xFFFBEEEF),
        inversePrimary    = Color(0xFFFFB1C5),

        scrim             = Color(0xFF000000)
    )

    private val RedLight = lightColorScheme(
        primary             = Color(0xFFD32F2F),
        onPrimary           = Color(0xFFFFFFFF),
        primaryContainer    = Color(0xFFFFDAD5),
        onPrimaryContainer  = Color(0xFF410001),

        secondary             = Color(0xFF775652),
        onSecondary           = Color(0xFFFFFFFF),
        secondaryContainer    = Color(0xFFFFDAD5),
        onSecondaryContainer  = Color(0xFF2C1512),

        tertiary             = Color(0xFF6750A4),
        onTertiary           = Color(0xFFFFFFFF),
        tertiaryContainer    = Color(0xFFEADDFF),
        onTertiaryContainer  = Color(0xFF21005D),

        background   = Color(0xFFFFFBF8),
        onBackground = Color(0xFF201A19),
        surface      = Color(0xFFFFFBF8),
        onSurface    = Color(0xFF201A19),
        surfaceVariant     = Color(0xFFF5DDDA),
        onSurfaceVariant   = Color(0xFF534341),
        outline            = Color(0xFF857370),
        outlineVariant     = Color(0xFFD8C2BF),

        error             = Color(0xFFBA1A1A),
        onError           = Color(0xFFFFFFFF),
        errorContainer    = Color(0xFFFFDAD6),
        onErrorContainer  = Color(0xFF410002),

        inverseSurface    = Color(0xFF362928),
        inverseOnSurface  = Color(0xFFFBEDEA),
        inversePrimary    = Color(0xFFFFB4A8),

        scrim             = Color(0xFF000000)
    )

    // ============ Dark 调色板（6 色 × 13 位） ============

    private val PurpleDark = darkColorScheme(
        primary             = Color(0xFFD0BCFF),
        onPrimary           = Color(0xFF381E72),
        primaryContainer    = Color(0xFF4F378B),
        onPrimaryContainer  = Color(0xFFEADDFF),

        secondary             = Color(0xFFCCC2DC),
        onSecondary           = Color(0xFF332D41),
        secondaryContainer    = Color(0xFF4A4458),
        onSecondaryContainer  = Color(0xFFE8DEF8),

        tertiary             = Color(0xFFEFB8C8),
        onTertiary           = Color(0xFF492532),
        tertiaryContainer    = Color(0xFF633B48),
        onTertiaryContainer  = Color(0xFFFFD8E4),

        background   = Color(0xFF1C1B1F),
        onBackground = Color(0xFFE6E1E5),
        surface      = Color(0xFF1C1B1F),
        onSurface    = Color(0xFFE6E1E5),
        surfaceVariant     = Color(0xFF49454F),
        onSurfaceVariant   = Color(0xFFCAC4D0),
        outline            = Color(0xFF938F99),
        outlineVariant     = Color(0xFF49454F),

        error             = Color(0xFFF2B8B5),
        onError           = Color(0xFF601410),
        errorContainer    = Color(0xFF8C1D18),
        onErrorContainer  = Color(0xFFF9DEDC),

        inverseSurface    = Color(0xFFE6E1E5),
        inverseOnSurface  = Color(0xFF313033),
        inversePrimary    = Color(0xFF6750A4),

        scrim             = Color(0xFF000000)
    )

    private val BlueDark = darkColorScheme(
        primary             = Color(0xFF9ECAFF),
        onPrimary           = Color(0xFF003258),
        primaryContainer    = Color(0xFF00497D),
        onPrimaryContainer  = Color(0xFFD1E4FF),

        secondary             = Color(0xFF9ECAFF),
        onSecondary           = Color(0xFF003258),
        secondaryContainer    = Color(0xFF00497D),
        onSecondaryContainer  = Color(0xFFD1E4FF),

        tertiary             = Color(0xFF7FCFFF),
        onTertiary           = Color(0xFF00344A),
        tertiaryContainer    = Color(0xFF004C6A),
        onTertiaryContainer  = Color(0xFFCDE5FF),

        background   = Color(0xFF1A1C1E),
        onBackground = Color(0xFFE2E2E5),
        surface      = Color(0xFF1A1C1E),
        onSurface    = Color(0xFFE2E2E5),
        surfaceVariant     = Color(0xFF43474E),
        onSurfaceVariant   = Color(0xFFC3C7CF),
        outline            = Color(0xFF8D9199),
        outlineVariant     = Color(0xFF43474E),

        error             = Color(0xFFFFB4AB),
        onError           = Color(0xFF690005),
        errorContainer    = Color(0xFF93000A),
        onErrorContainer  = Color(0xFFFFDAD6),

        inverseSurface    = Color(0xFFE2E2E5),
        inverseOnSurface  = Color(0xFF2F3033),
        inversePrimary    = Color(0xFF1976D2),

        scrim             = Color(0xFF000000)
    )

    private val GreenDark = darkColorScheme(
        primary             = Color(0xFF9BD49F),
        onPrimary           = Color(0xFF003910),
        primaryContainer    = Color(0xFF1F5624),
        onPrimaryContainer  = Color(0xFFB7F0BB),

        secondary             = Color(0xFFB9CCB4),
        onSecondary           = Color(0xFF253423),
        secondaryContainer    = Color(0xFF3B4B38),
        onSecondaryContainer  = Color(0xFFD5E8CF),

        tertiary             = Color(0xFFA0CFD3),
        onTertiary           = Color(0xFF00373B),
        tertiaryContainer    = Color(0xFF1F4E52),
        onTertiaryContainer  = Color(0xFFBCEBEF),

        background   = Color(0xFF1A1C19),
        onBackground = Color(0xFFE2E3DD),
        surface      = Color(0xFF1A1C19),
        onSurface    = Color(0xFFE2E3DD),
        surfaceVariant     = Color(0xFF424940),
        onSurfaceVariant   = Color(0xFFC2C9BD),
        outline            = Color(0xFF8C9388),
        outlineVariant     = Color(0xFF424940),

        error             = Color(0xFFFFB4AB),
        onError           = Color(0xFF690005),
        errorContainer    = Color(0xFF93000A),
        onErrorContainer  = Color(0xFFFFDAD6),

        inverseSurface    = Color(0xFFE2E3DD),
        inverseOnSurface  = Color(0xFF2E312D),
        inversePrimary    = Color(0xFF388E3C),

        scrim             = Color(0xFF000000)
    )

    private val OrangeDark = darkColorScheme(
        primary             = Color(0xFFFFB68F),
        onPrimary           = Color(0xFF552100),
        primaryContainer    = Color(0xFF7A3700),
        onPrimaryContainer  = Color(0xFFFFDBC9),

        secondary             = Color(0xFFE7BFAF),
        onSecondary           = Color(0xFF442921),
        secondaryContainer    = Color(0xFF5D4037),
        onSecondaryContainer  = Color(0xFFFFDBC9),

        tertiary             = Color(0xFFD0BCFF),
        onTertiary           = Color(0xFF381E72),
        tertiaryContainer    = Color(0xFF4F378B),
        onTertiaryContainer  = Color(0xFFEADDFF),

        background   = Color(0xFF201A17),
        onBackground = Color(0xFFEDE0DA),
        surface      = Color(0xFF201A17),
        onSurface    = Color(0xFFEDE0DA),
        surfaceVariant     = Color(0xFF52443D),
        onSurfaceVariant   = Color(0xFFD7C2B8),
        outline            = Color(0xFFA08D84),
        outlineVariant     = Color(0xFF52443D),

        error             = Color(0xFFFFB4AB),
        onError           = Color(0xFF690005),
        errorContainer    = Color(0xFF93000A),
        onErrorContainer  = Color(0xFFFFDAD6),

        inverseSurface    = Color(0xFFEDE0DA),
        inverseOnSurface  = Color(0xFF362F2C),
        inversePrimary    = Color(0xFFE65100),

        scrim             = Color(0xFF000000)
    )

    private val PinkDark = darkColorScheme(
        primary             = Color(0xFFFFB1C5),
        onPrimary           = Color(0xFF650030),
        primaryContainer    = Color(0xFF88104B),
        onPrimaryContainer  = Color(0xFFFFD9E2),

        secondary             = Color(0xFFE3BDC6),
        onSecondary           = Color(0xFF422931),
        secondaryContainer    = Color(0xFF5A3F47),
        onSecondaryContainer  = Color(0xFFFFD9E2),

        tertiary             = Color(0xFFEFBA94),
        onTertiary           = Color(0xFF48250C),
        tertiaryContainer    = Color(0xFF623B21),
        onTertiaryContainer  = Color(0xFFFFDBC2),

        background   = Color(0xFF201A1B),
        onBackground = Color(0xFFEDE0E2),
        surface      = Color(0xFF201A1B),
        onSurface    = Color(0xFFEDE0E2),
        surfaceVariant     = Color(0xFF514347),
        onSurfaceVariant   = Color(0xFFD5C2C7),
        outline            = Color(0xFF9E8C92),
        outlineVariant     = Color(0xFF514347),

        error             = Color(0xFFFFB4AB),
        onError           = Color(0xFF690005),
        errorContainer    = Color(0xFF93000A),
        onErrorContainer  = Color(0xFFFFDAD6),

        inverseSurface    = Color(0xFFEDE0E2),
        inverseOnSurface  = Color(0xFF362F30),
        inversePrimary    = Color(0xFFAD1457),

        scrim             = Color(0xFF000000)
    )

    private val RedDark = darkColorScheme(
        primary             = Color(0xFFFFB4A8),
        onPrimary           = Color(0xFF690100),
        primaryContainer    = Color(0xFF8B0807),
        onPrimaryContainer  = Color(0xFFFFDAD5),

        secondary             = Color(0xFFE7BDB5),
        onSecondary           = Color(0xFF442925),
        secondaryContainer    = Color(0xFF5D3F3B),
        onSecondaryContainer  = Color(0xFFFFDAD5),

        tertiary             = Color(0xFFD0BCFF),
        onTertiary           = Color(0xFF381E72),
        tertiaryContainer    = Color(0xFF4F378B),
        onTertiaryContainer  = Color(0xFFEADDFF),

        background   = Color(0xFF201A19),
        onBackground = Color(0xFFEDE0DD),
        surface      = Color(0xFF201A19),
        onSurface    = Color(0xFFEDE0DD),
        surfaceVariant     = Color(0xFF534341),
        onSurfaceVariant   = Color(0xFFD8C2BF),
        outline            = Color(0xFFA08C89),
        outlineVariant     = Color(0xFF534341),

        error             = Color(0xFFFFB4AB),
        onError           = Color(0xFF690005),
        errorContainer    = Color(0xFF93000A),
        onErrorContainer  = Color(0xFFFFDAD6),

        inverseSurface    = Color(0xFFEDE0DD),
        inverseOnSurface  = Color(0xFF362928),
        inversePrimary    = Color(0xFFD32F2F),

        scrim             = Color(0xFF000000)
    )

    /**
     * 取完整 ColorScheme。
     * @param color 主题色 id（Defaults.COLOR_*）
     * @param dark  true=dark, false=light
     */
    fun schemeOf(color: Int, dark: Boolean): ColorScheme = when (color) {
        Defaults.COLOR_BLUE   -> if (dark) BlueDark   else BlueLight
        Defaults.COLOR_GREEN  -> if (dark) GreenDark  else GreenLight
        Defaults.COLOR_ORANGE -> if (dark) OrangeDark else OrangeLight
        Defaults.COLOR_PINK   -> if (dark) PinkDark   else PinkLight
        Defaults.COLOR_RED    -> if (dark) RedDark    else RedLight
        else /* PURPLE */     -> if (dark) PurpleDark else PurpleLight
    }
}

@Composable
fun BiliTingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: Int = Defaults.COLOR_PURPLE,
    content: @Composable () -> Unit
) {
    val colorScheme = ThemePalettes.schemeOf(themeColor, darkTheme)
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