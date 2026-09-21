package com.schedule.vela.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.vela.ApplicationContext
import com.schedule.vela.MainViewModel
import com.schedule.vela.showToast
import com.schedule.vela.ui.component.BlurredTopAppBar
import com.schedule.vela.ui.component.PageScrollColumn
import com.schedule.vela.ui.theme.ThemeMode
import com.schedule.vela.ui.theme.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val StatusCardColorActivated = Color(0xFF36D167)
private val StatusCardColorDeactivated = Color(0xFFD13636)

private fun buildStatusIcon(
    name: String,
    pathData: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()

private val StatusCheckCircleOutline: ImageVector =
    buildStatusIcon(
        "StatusCheckCircleOutline",
        "M16.59 7.58L10 14.17l-3.59-3.58L5 12l5 5 8-8zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 " +
            "10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z",
    )

private val StatusErrorOutline: ImageVector =
    buildStatusIcon(
        "StatusErrorOutline",
        "M11 15h2v2h-2zm0-8h2v6h-2zm.99-5C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 " +
            "22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z",
    )

@Composable
fun HomePage(
    viewModel: MainViewModel,
    appBlur: Boolean = false,
    bottomPadding: Dp = 16.dp,
) {
    val context = LocalContext.current
    val isConnected by viewModel.isConnectedState
    val connectedDeviceText by viewModel.connectedDeviceText
    val logText by viewModel.logTextState
    val selectedFileName by viewModel.selectedFileName
    val awaitingTimetable by viewModel.awaitingTimetableState

    val pickFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                if (awaitingTimetable) {
                    viewModel.onTimetablePicked(context, it)
                } else {
                    viewModel.onFilePicked(context.contentResolver, it)
                }
            }
        }

    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val isDark =
        when (viewModel.themeMode) {
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
            ThemeMode.System -> isSystemInDarkTheme()
        }

    Scaffold(
        topBar = { BlurredTopAppBar("首页", blurBackdrop, scrollBehavior) },
    ) { paddingValues ->
        PageScrollColumn(
            blurBackdrop = blurBackdrop,
            scrollBehavior = scrollBehavior,
            contentPadding = paddingValues,
            bottomPadding = bottomPadding,
        ) {
            ConnectionStatusCard(
                isConnected = isConnected,
                connectedDeviceText = connectedDeviceText,
                dynamicColorActive = viewModel.customColor && viewModel.dynamicColor,
                isDark = isDark,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "导入课程表",
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { pickFileLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = selectedFileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            onClick = { viewModel.confirmImport(context) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("确认导入")
                        }
                    }
                }
            }

            val logScrollState = rememberScrollState()
            var lastLogText by remember { mutableStateOf("") }
            var lastMaxValue by remember { mutableIntStateOf(0) }

            LaunchedEffect(logText) {
                val isContentGrew = logScrollState.maxValue > lastMaxValue
                if (isContentGrew || lastLogText.isEmpty()) {
                    logScrollState.animateScrollTo(logScrollState.maxValue)
                }
                lastLogText = logText
                lastMaxValue = logScrollState.maxValue
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "日志",
                            style = MiuixTheme.textStyles.title2,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { copyLogToClipboard(logText) }) {
                            Icon(
                                imageVector = MiuixIcons.Copy,
                                contentDescription = "复制日志",
                            )
                        }
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .verticalScroll(logScrollState),
                    ) {
                        SelectionContainer {
                            Text(
                                text = logText,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    isConnected: Boolean,
    connectedDeviceText: String,
    dynamicColorActive: Boolean,
    isDark: Boolean,
) {
    val containerColor =
        if (isConnected) {
            when {
                dynamicColorActive -> MiuixTheme.colorScheme.secondaryContainer
                isDark -> Color(0xFF1A3825)
                else -> Color(0xFFDFFAE4)
            }
        } else {
            when {
                dynamicColorActive -> MiuixTheme.colorScheme.errorContainer
                isDark -> Color(0xFF381A1A)
                else -> Color(0xFFFAEEEE)
            }
        }
    val textContentColor =
        if (isConnected) {
            if (dynamicColorActive) MiuixTheme.colorScheme.onSecondaryContainer else MiuixTheme.colorScheme.onSurface
        } else {
            if (dynamicColorActive) MiuixTheme.colorScheme.onErrorContainer else MiuixTheme.colorScheme.onSurface
        }
    val descTextColor = textContentColor.copy(alpha = 0.8f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = containerColor),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 背景大图标：保持彩色以维持视觉张力，独立于文字颜色
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .offset(50.dp, 38.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(170.dp),
                    imageVector = if (isConnected) StatusCheckCircleOutline else StatusErrorOutline,
                    tint =
                        if (isConnected) {
                            if (dynamicColorActive) {
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
                            } else {
                                StatusCardColorActivated
                            }
                        } else {
                            if (dynamicColorActive) {
                                MiuixTheme.colorScheme.error.copy(alpha = 0.8f)
                            } else {
                                StatusCardColorDeactivated
                            }
                        },
                    contentDescription = null,
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp),
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (isConnected) "已连接" else "未连接",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textContentColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (isConnected) connectedDeviceText else "请连接穿戴设备",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = descTextColor,
                )
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

// 写入系统剪贴板并提示结果。
private fun copyLogToClipboard(text: String) {
    val copied =
        try {
            val manager = ApplicationContext.instance.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("Schedule-Sync", text))
            true
        } catch (_: Exception) {
            false
        }
    showToast(if (copied) "复制成功" else "复制失败")
}
