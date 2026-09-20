package com.schedule.vela.ui.page

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.schedule.vela.MainViewModel
import com.schedule.vela.getAppVersion
import com.schedule.vela.getAppVersionCode
import com.schedule.vela.ui.component.BlurredTopAppBar
import com.schedule.vela.ui.component.PageScrollColumn
import com.schedule.vela.ui.component.SwitchPreference
import com.schedule.vela.ui.theme.ColorSwatchPreview
import com.schedule.vela.ui.theme.PaletteStyle
import com.schedule.vela.ui.theme.PresetColors
import com.schedule.vela.ui.theme.ThemeMode
import com.schedule.vela.ui.theme.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

@Composable
fun SettingsPage(
    viewModel: MainViewModel,
    onLicenseClick: () -> Unit,
    bottomPadding: Dp = 16.dp,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = MiuixScrollBehavior()
    val appBlur = viewModel.appBlur
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val themeOptions = listOf("跟随系统", "浅色", "深色")
    var themeSelectedIndex by remember {
        mutableIntStateOf(
            when (viewModel.themeMode) {
                ThemeMode.System -> 0
                ThemeMode.Light -> 1
                ThemeMode.Dark -> 2
            },
        )
    }
    var showGithubProxyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { BlurredTopAppBar("设置", blurBackdrop, scrollBehavior) },
    ) { paddingValues ->
        PageScrollColumn(
            blurBackdrop = blurBackdrop,
            scrollBehavior = scrollBehavior,
            contentPadding = paddingValues,
            topPadding = 4.dp,
            bottomPadding = bottomPadding,
        ) {
            SmallTitle(text = "外观设置", insideMargin = PaddingValues(12.dp, 8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    OverlayDropdownPreference(
                        title = "主题模式",
                        items = themeOptions,
                        selectedIndex = themeSelectedIndex,
                        onSelectedIndexChange = { index ->
                            themeSelectedIndex = index
                            val mode =
                                when (index) {
                                    0 -> ThemeMode.System
                                    1 -> ThemeMode.Light
                                    else -> ThemeMode.Dark
                                }
                            viewModel.setThemeMode(mode)
                        },
                    )
                    if (isRuntimeShaderSupported()) {
                        SwitchPreference(
                            title = "模糊效果",
                            summary = "为顶栏、底栏、对话框添加模糊效果",
                            checked = appBlur,
                            onCheckedChange = { viewModel.setAppBlur(it) },
                        )
                    }
                    SwitchPreference(
                        title = "预测性返回动画",
                        summary = "返回滑动前提前预览即将跳转至的界面",
                        checked = viewModel.predictiveBackEnabled,
                        onCheckedChange = { viewModel.setPredictiveBackEnabled(it) },
                    )
                    SwitchPreference(
                        title = "自定义颜色",
                        summary = "自定义应用主题配色方案",
                        checked = viewModel.customColor,
                        onCheckedChange = { viewModel.setCustomColor(it) },
                    )
                    AnimatedVisibility(
                        visible = viewModel.customColor,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        SwitchPreference(
                            title = "动态取色",
                            summary = "基于系统壁纸颜色生成配色方案",
                            checked = viewModel.dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                        )
                    }
                    AnimatedVisibility(
                        visible = viewModel.customColor,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        OverlayDropdownPreference(
                            title = "调色板风格",
                            items = PaletteStyle.entries.map { it.displayName },
                            selectedIndex = PaletteStyle.entries.indexOf(viewModel.paletteStyle).coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                viewModel.setPaletteStyle(PaletteStyle.entries[index])
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = viewModel.customColor && !viewModel.dynamicColor,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        PresetColorGrid(
                            paletteStyle = viewModel.paletteStyle,
                            selectedSeed = viewModel.seedColor,
                            onSelect = { viewModel.setSeedColor(it) },
                        )
                    }
                    SwitchPreference(
                        title = "悬浮底栏",
                        summary = "切换悬浮式底部导航栏",
                        checked = viewModel.floatingNav,
                        onCheckedChange = { viewModel.setFloatingNav(it) },
                    )
                }
            }

            SmallTitle(text = "更新设置", insideMargin = PaddingValues(12.dp, 8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(
                        title = "检查更新",
                        summary = "从 GitHub 检查最新版本",
                        onClick = { viewModel.checkForUpdate() },
                    )
                    SwitchPreference(
                        title = "启动时检查更新",
                        summary = "打开应用时自动检查新版本",
                        checked = viewModel.checkUpdateOnStart,
                        onCheckedChange = { viewModel.setCheckUpdateOnStartEnabled(it) },
                    )
                    ArrowPreference(
                        title = "加速地址",
                        summary = viewModel.githubProxyUrl.ifBlank { "设置 GitHub 加速地址" },
                        onClick = { showGithubProxyDialog = true },
                    )
                }
            }

            SmallTitle(text = "关于", insideMargin = PaddingValues(12.dp, 8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    BasicComponent(
                        title = "版本",
                        summary = "${getAppVersion()}(${getAppVersionCode()})",
                    )
                    ArrowPreference(
                        title = "查看源代码",
                        summary = "在 GitHub 上查看源代码",
                        onClick = { uriHandler.openUri("https://github.com/Jursin/Schedule-Sync") },
                    )
                    ArrowPreference(
                        title = "开放源代码许可",
                        summary = "查看应用所使用的第三方开源库及其许可证信息",
                        onClick = onLicenseClick,
                    )
                    ArrowPreference(
                        title = "帮助",
                        summary = "查看博客文档",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, "https://blog.jursin.top/blog/tutorials/schedule-vela.html".toUri())
                            context.startActivity(intent)
                        },
                    )
                    ArrowPreference(
                        title = "赞助支持",
                        summary = "在爱发电赞助我",
                        onClick = { uriHandler.openUri("https://afdian.com/@Jursin") },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    UpdateDialogs(
        viewModel = viewModel,
        showGithubProxyDialog = showGithubProxyDialog,
        onDismissGithubProxyDialog = { showGithubProxyDialog = false },
    )
}

@Composable
private fun PresetColorGrid(
    paletteStyle: PaletteStyle,
    selectedSeed: Int,
    onSelect: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        val columns = (maxWidth / 80.dp).toInt().coerceAtLeast(1)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PresetColors.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    rowItems.forEach { preset ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            ColorSwatchPreview(
                                preset = preset,
                                paletteStyle = paletteStyle,
                                selected = selectedSeed == preset.color.toArgb(),
                                onClick = { onSelect(preset.color.toArgb()) },
                            )
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
