package com.schedule.vela

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.outlined.Help
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.schedule.vela.ui.theme.ShiGuangTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShiGuangTheme {
                MainScaffold(viewModel)
            }
        }
        viewModel.startDeviceQuery()
    }

    // Scaffold 布局
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScaffold(viewModel: MainViewModel) {
        val context = LocalContext.current
        val showAboutDialog by viewModel.showAboutDialog
        val isConnected by viewModel.isConnectedState
        val connectedDeviceText by viewModel.connectedDeviceText
        val logText by viewModel.logTextState
        val selectedFileName by viewModel.selectedFileName
        // 文件选择 launcher
        val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.onFilePicked(context.contentResolver, it) }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "腕上课程表同步器",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW,
                                "https://blog.jursin.top/blog/60fsmnc1/".toUri())
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Outlined.Help, contentDescription = "帮助")
                        }
                        IconButton(onClick = { viewModel.showAboutDialog.value = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = "关于")
                        }
                    }
                )
            }
        ) { innerPadding ->
            MainContent(
                modifier = Modifier.padding(innerPadding),
                isConnected = isConnected,
                connectedDeviceText = connectedDeviceText,
                logText = logText,
                selectedFileName = selectedFileName,
                onPickFile = { pickFileLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*")) },
                onConfirmImport = viewModel::confirmImport
            )
            if (showAboutDialog) {
                AboutDialog(onDismiss = { viewModel.showAboutDialog.value = false })
            }
        }
    }

    // 内容区，调用ViewModel状态
    @Composable
    fun MainContent(
        modifier: Modifier = Modifier,
        isConnected: Boolean,
        connectedDeviceText: String,
        logText: String,
        selectedFileName: String,
        onPickFile: () -> Unit = {},
        onConfirmImport: (context: android.content.Context) -> Unit = {},
    ) {
        val context = LocalContext.current
        val cardShape = RoundedCornerShape(20.dp)
        val cardElevation = 2.dp
        val cardBorder = BorderStroke(0.25.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
        val cardColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
        val statusColor = MaterialTheme.colorScheme.primary
        val logCardBackgroundColor = MaterialTheme.colorScheme.secondaryContainer
        val importButtonShape = RoundedCornerShape(20.dp)

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card(
                shape = cardShape,
                elevation = CardDefaults.cardElevation(cardElevation),
                border = cardBorder,
                colors = cardColors,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                            contentDescription = if (isConnected) "已连接" else "未连接",
                            tint = statusColor,
                            modifier = Modifier.align(Alignment.Center).size(32.dp)
                        )
                    }
                    Column {
                        Text(
                            text = connectedDeviceText,
                            fontSize = 20.sp,
                            style = MaterialTheme.typography.bodyLarge,
                            color = statusColor
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
            Card(
                shape = cardShape,
                elevation = CardDefaults.cardElevation(cardElevation),
                border = cardBorder,
                colors = cardColors,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "导入课程表",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onPickFile,
                            modifier = Modifier.weight(1f),
                            shape = importButtonShape,
                            contentPadding = ButtonDefaults.ContentPadding,
                            colors = ButtonDefaults.buttonColors(),
                        ) {
                            Text(
                                text = selectedFileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { onConfirmImport(context) },
                            modifier = Modifier.weight(1f),
                            shape = importButtonShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = logCardBackgroundColor,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("确认导入")
                        }
                    }
                }
            }
            Card(
                shape = cardShape,
                elevation = CardDefaults.cardElevation(cardElevation),
                border = cardBorder,
                colors = CardDefaults.cardColors(containerColor = logCardBackgroundColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        text = "日志",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = logText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    @Composable
    fun AboutDialog(onDismiss: () -> Unit) {
        val context = LocalContext.current
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
        } catch (_: Exception) { "-" }
        val versionCode = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toString()
        } catch (_: Exception) { "-" }
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("关闭") }
            },
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = try {
                                context.applicationInfo.loadLabel(context.packageManager).toString()
                            } catch (_: Exception) { "腕上课程表同步器" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "v$versionName ($versionCode)",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val annotatedString = buildAnnotatedString {
                        val githubUrl = "https://github.com/Jursin/Schedule-Vela"
                        append("在 ")
                        pushStringAnnotation(tag = "URL", annotation = githubUrl)
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                            append("GitHub")
                        }
                        pop()
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                            append(" 中查看源码")
                        }
                    }
                    ClickableText(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { sa ->
                                    val intent = Intent(Intent.ACTION_VIEW, sa.item.toUri())
                                    context.startActivity(intent)
                                }
                        }
                    )
                }
            }
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        ShiGuangTheme {
            MainContent(
                isConnected = false,
                connectedDeviceText = "未连接设备",
                logText = "这是日志预览内容",
                selectedFileName = "test.json"
            )
        }
    }
}
