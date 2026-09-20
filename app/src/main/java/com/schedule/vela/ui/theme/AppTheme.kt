package com.schedule.vela.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController

enum class ThemeMode {
    System,
    Light,
    Dark,
}

private const val DEFAULT_SEED_COLOR = 0xFF4A672D.toInt()

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.System,
    customColor: Boolean = false,
    dynamicColor: Boolean = false,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    seedColor: Int = DEFAULT_SEED_COLOR,
    content: @Composable () -> Unit,
) {
    val isDark =
        when (themeMode) {
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
            ThemeMode.System -> isSystemInDarkTheme()
        }

    val colorSchemeMode =
        when {
            customColor && isDark -> ColorSchemeMode.MonetDark
            customColor && !isDark -> ColorSchemeMode.MonetLight
            isDark -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.Light
        }

    val dynamicKeyColor = if (customColor && dynamicColor) systemDynamicColorKey() else null
    val keyColor =
        when {
            !customColor -> null
            dynamicColor -> dynamicKeyColor
            else -> Color(seedColor.toLong())
        }

    val controller =
        remember(colorSchemeMode, keyColor, paletteStyle, customColor, isDark) {
            if (customColor) {
                ThemeController(
                    colorSchemeMode = colorSchemeMode,
                    keyColor = keyColor,
                    paletteStyle = paletteStyle.toMiuixPaletteStyle(),
                    colorSpec = ThemeColorSpec.Spec2025,
                    isDark = isDark,
                )
            } else {
                ThemeController(colorSchemeMode)
            }
        }

    MiuixTheme(controller = controller) {
        content()
    }
}

@Composable
private fun systemDynamicColorKey(): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return colorResource(android.R.color.system_accent1_500)
}
