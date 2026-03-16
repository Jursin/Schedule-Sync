package com.shiguang.band

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.shiguang.band.ui.theme.ShiGuangTheme
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private lateinit var nodeId: String
    private lateinit var curNode:Node
    private lateinit var nodeApi:NodeApi
    // 全局状态
    private var logTextState = mutableStateOf("")
    private var isConnectedState = mutableStateOf(false)
    private var lastAutoOpenedNodeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nodeApi= Wearable.getNodeApi(this)
        enableEdgeToEdge()
        setContent {
            ShiGuangTheme {
                MainContent()
            }
        }
    }
    //发送信息
    private fun sendMessageToWearable(message: String) {
        val messageApi = Wearable.getMessageApi(this)
        if (::nodeId.isInitialized) {
            messageApi.sendMessage(nodeId, message.toByteArray())
                .addOnSuccessListener {
                    log("导入成功")
                    Toast.makeText(this, "配置发送成功", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    log("导入失败")
                    Toast.makeText(this, "配置发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "未连接到设备", Toast.LENGTH_SHORT).show()
        }
    }
    // 查询已连接的设备
    private fun queryConnectedDevices() {
        nodeApi.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                curNode = nodes[0]
                nodeId = curNode.id
                isConnectedState.value = true
                //connectedDeviceText.text =getString(R.string.connStat)+curNode.name
                log("已连接${curNode.name}")
                if (lastAutoOpenedNodeId != nodeId) {
                    nodeApi.isWearAppInstalled(nodeId)
                        .addOnSuccessListener {
                            nodeApi.launchWearApp(nodeId, "pages/index")
                                .addOnSuccessListener {
                                    lastAutoOpenedNodeId = nodeId
                                    log("打开手环端快应用成功")
                                }
                                .addOnFailureListener {
                                    log("打开手环端快应用失败")
                                }
                        }
                        .addOnFailureListener {
                            log("手环未安装快应用")
                            Toast.makeText(
                                this,
                                "手环未安装快应用！如果已经安装，请尝试重启手环",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                checkAndRequestPermissions()
            } else {
                isConnectedState.value = false
                //connectedDeviceText.text =getString(R.string.connStat)+"None"
            }
        }.addOnFailureListener { e ->
            isConnectedState.value = false
            Toast.makeText(
                this,
                "获取已连接设备失败: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 申请权限
    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            // 请求蓝牙权限
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH), 1001)
        }
        val authApi = Wearable.getAuthApi(this)
        if (::nodeId.isInitialized) {
            val did =nodeId  // 填入你的设备 ID
            authApi.checkPermission(did, Permission.DEVICE_MANAGER)
                .addOnSuccessListener { granted ->
                    if (!granted) {
                        authApi.requestPermission(did, Permission.DEVICE_MANAGER)
                            .addOnSuccessListener {
                                log("权限已授予")
                            }.addOnFailureListener { e ->
                                val errorMessage = e.message.orEmpty()
                                if (errorMessage.contains("fingerprint verify failed", ignoreCase = true)) {
                                    log("权限申请失败：指纹校验未通过，请检查手机端包名和签名是否已在手环快应用端正确配置")
                                    Toast.makeText(this, "指纹校验失败，请检查包名和签名配置", Toast.LENGTH_LONG).show()
                                }
                                Toast.makeText(this, "申请权限失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        log("权限已授予")
                    }
                }.addOnFailureListener { e ->
                    val errorMessage = e.message.orEmpty()
                    if (errorMessage.contains("fingerprint verify failed", ignoreCase = true)) {
                        log("权限检查失败：指纹校验未通过，请检查手机端包名和签名是否已在手环快应用端正确配置")
                        Toast.makeText(this, "指纹校验失败，请检查包名和签名配置", Toast.LENGTH_LONG).show()
                    }
                    e.message?.let { log(it) }
                }}
    }
    @Composable
    fun MainContent(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        var connectedDeviceText by remember { mutableStateOf("设备未连接") }
        var isConnected by remember { isConnectedState }
        var logText by remember { logTextState }
        var pendingFileUri by remember { mutableStateOf<Uri?>(null) }
        var selectedFileName by remember { mutableStateOf("选择配置文件") }
        val importButtonShape = RoundedCornerShape(12.dp)
        val logCardBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
        val deviceCardContainerColor = if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        }
        // 启动文件选择器
        val pickFileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                pendingFileUri = it
                selectedFileName = getFileName(context.contentResolver, it)
                log("已选择 $selectedFileName")
             }
         }
        fun startPickFile(){
            if(::nodeId.isInitialized) {
                pickFileLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*"))
            }else{
                Toast.makeText(this, "未连接到设备", Toast.LENGTH_SHORT).show()
            }
        }
        fun confirmImport(){
             if(::nodeId.isInitialized) {
                val targetUri = pendingFileUri
                if (targetUri == null) {
                    Toast.makeText(this, "请先选择配置文件", Toast.LENGTH_SHORT).show()
                    return
                }
                val contentResolver = context.contentResolver
                val jsonText = readTextFromUri(contentResolver, targetUri)
                if (jsonText == null) {
                    Toast.makeText(this, "读取配置文件失败", Toast.LENGTH_SHORT).show()
                    return
                }

                val validationError = validateScheduleConfig(jsonText)
                if (validationError != null) {
                    log("导入失败，缺失${extractMissingItem(validationError)}")
                    Toast.makeText(this, validationError, Toast.LENGTH_LONG).show()
                    return
                }
                sendMessageToWearable(jsonText)
              }else{
                  Toast.makeText(this, "未连接到设备", Toast.LENGTH_SHORT).show()
              }
          }

        LaunchedEffect(Unit) {
            while (!(::nodeId.isInitialized)) {
                // 更新文本
                queryConnectedDevices()
                // 延迟一段时间再更新
                delay(1000) // 每秒更新一次
            }
            connectedDeviceText = "${curNode.name}"
        }

        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize().systemBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top =4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp, bottom = 1.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "拾光课程表同步器",
                    fontSize = 26.sp,
                     fontWeight = FontWeight.SemiBold,
                     textAlign = TextAlign.Center,
                     color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 已连接设备信息卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp),
                border = CardDefaults.outlinedCardBorder(),
                colors = CardDefaults.cardColors(containerColor = deviceCardContainerColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val statusColor = MaterialTheme.colorScheme.onPrimaryContainer
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            contentDescription = if (isConnected) "已连接" else "未连接",
                            tint = statusColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = connectedDeviceText,
                            fontSize = 20.sp,
                            style = MaterialTheme.typography.bodyLarge,
                            color = statusColor
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { startPickFile() } ,
                    modifier = Modifier.weight(1f),
                    shape = importButtonShape
                ) {
                    Text(
                        text = selectedFileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = { confirmImport() },
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
            // 日志显示卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = logCardBackgroundColor) // 与确认导入按钮使用同一背景色
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "日志",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer // 动态颜色
                )
                Column(modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())) {
                    Text(
                        text = logText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        ShiGuangTheme {
            MainContent()
        }
    }
    // 方法用于添加日志
    private fun log(message:Any) {
        logTextState.value +="$message\n"
    }

    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "已选择文件"
    }

    private fun readTextFromUri(contentResolver: ContentResolver, uri: Uri): String? {
        val stringBuilder = StringBuilder()
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }
            }
        }
        return if (stringBuilder.isEmpty()) null else stringBuilder.toString()
    }

    private fun validateScheduleConfig(jsonText: String): String? {
        val root = try {
            JSONObject(jsonText)
        } catch (_: JSONException) {
            return "配置文件不是有效 JSON"
        }

        val courses = root.optJSONArray("courses") ?: return "缺少必填项 courses"
        val timeSlots = root.optJSONArray("timeSlots") ?: return "缺少必填项 timeSlots"
        val config = root.optJSONObject("config") ?: return "缺少必填项 config"

        validateCourses(courses)?.let { return it }
        validateTimeSlots(timeSlots)?.let { return it }

        if (config.optString("semesterStartDate").isBlank()) {
            return "config.semesterStartDate 必填"
        }

        return null
    }

    private fun validateCourses(courses: JSONArray): String? {
        for (i in 0 until courses.length()) {
            val course = courses.optJSONObject(i) ?: return "courses[$i] 必须是对象"
            if (course.optString("name").isBlank()) return "courses[$i].name 必填"
            if (course.optString("position").isBlank()) return "courses[$i].position 必填"
            if (!course.has("day")) return "courses[$i].day 必填"
            val weeks = course.optJSONArray("weeks") ?: return "courses[$i].weeks 必填"
            if (weeks.length() == 0) return "courses[$i].weeks 不能为空"

            val isCustomTime = course.optBoolean("isCustomTime", false)
            if (isCustomTime) {
                if (course.optString("customStartTime").isBlank()) return "courses[$i].customStartTime 必填"
                if (course.optString("customEndTime").isBlank()) return "courses[$i].customEndTime 必填"
            } else {
                if (!course.has("startSection")) return "courses[$i].startSection 必填"
                if (!course.has("endSection")) return "courses[$i].endSection 必填"
            }
        }
        return null
    }

    private fun validateTimeSlots(timeSlots: JSONArray): String? {
        for (i in 0 until timeSlots.length()) {
            val timeSlot = timeSlots.optJSONObject(i) ?: return "timeSlots[$i] 必须是对象"
            if (!timeSlot.has("number")) return "timeSlots[$i].number 必填"
            if (timeSlot.optString("startTime").isBlank()) return "timeSlots[$i].startTime 必填"
            if (timeSlot.optString("endTime").isBlank()) return "timeSlots[$i].endTime 必填"
        }
        return null
    }

    private fun extractMissingItem(validationError: String): String {
        return when {
            validationError.startsWith("缺少必填项 ") -> validationError.removePrefix("缺少必填项 ")
            validationError.endsWith(" 必填") -> validationError.removeSuffix(" 必填")
            else -> validationError
        }
    }
}
