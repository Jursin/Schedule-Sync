package com.schedule.vela.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberAppBlurBackdrop(enabled: Boolean): LayerBackdrop? {
    if (!enabled || !isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun blurAppBarColor(backdrop: LayerBackdrop?): Color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

fun Modifier.captureForBlur(backdrop: LayerBackdrop?): Modifier = if (backdrop != null) this.layerBackdrop(backdrop) else this

@Composable
fun Modifier.appBarBlur(
    backdrop: LayerBackdrop?,
    blurRadius: Float = 25f,
): Modifier {
    if (backdrop == null) return this
    return this.textureBlur(
        backdrop = backdrop,
        shape = RectangleShape,
        blurRadius = blurRadius,
        colors =
            BlurColors(
                blendColors =
                    listOf(
                        BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)),
                    ),
            ),
    )
}
