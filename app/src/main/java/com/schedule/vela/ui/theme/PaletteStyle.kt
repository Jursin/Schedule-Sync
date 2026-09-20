package com.schedule.vela.ui.theme

import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

enum class PaletteStyle(
    val displayName: String,
) {
    TonalSpot("TonalSpot"),
    Neutral("Neutral"),
    Vibrant("Vibrant"),
    Expressive("Expressive"),
}

fun PaletteStyle.toMiuixPaletteStyle(): ThemePaletteStyle =
    when (this) {
        PaletteStyle.TonalSpot -> ThemePaletteStyle.TonalSpot
        PaletteStyle.Neutral -> ThemePaletteStyle.Neutral
        PaletteStyle.Vibrant -> ThemePaletteStyle.Vibrant
        PaletteStyle.Expressive -> ThemePaletteStyle.Expressive
    }
