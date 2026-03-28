package com.schedule.vela

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val logTextState = mutableStateOf("")
    val isConnectedState = mutableStateOf(false)
    val connectedDeviceText = mutableStateOf("设备未连接")
    val selectedFileName = mutableStateOf("选择配置文件")
    val pendingFileUri = mutableStateOf<Uri?>(null)
    val showAboutDialog = mutableStateOf(false)

    private var nodeId: String? = null
    private var curNode: Node? = null
    private var lastAutoOpenedNodeId: String? = null
    private val nodeApi: NodeApi = Wearable.getNodeApi(application)

    fun startDeviceQuery() {
        viewModelScope.launch {
            while (nodeId == null) {
                queryConnectedDevices()
                delay(1000)
            }
            curNode?.let { connectedDeviceText.value = it.name }
        }
    }

    private fun log(message: Any) {
        logTextState.value += "$message\n"
    }

    private fun queryConnectedDevices() {
        nodeApi.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                curNode = nodes[0]
                nodeId = curNode?.id
                isConnectedState.value = true
                log("已连接${curNode?.name}")
                if (lastAutoOpenedNodeId != nodeId) {
                    nodeId?.let { id ->
                        nodeApi.isWearAppInstalled(id)
                            .addOnSuccessListener {
                                nodeApi.launchWearApp(id, "pages/index")
                                    .addOnSuccessListener {
                                        lastAutoOpenedNodeId = id
                                        log("打开手环端快应用成功")
                                    }
                                    .addOnFailureListener {
                                        log("打开手环端快应用失败")
                                    }
                            }
                            .addOnFailureListener {
                                log("手环未安装快应用")
                            }
                    }
                }
                checkAndRequestPermissions()
            } else {
                isConnectedState.value = false
            }
        }.addOnFailureListener { e ->
            isConnectedState.value = false
            log("获取已连接设备失败: ${e.message}")
        }
    }

    private fun checkAndRequestPermissions() {
        val context = getApplication<Application>()
        val authApi = Wearable.getAuthApi(context)
        nodeId?.let { did ->
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
                                }
                                log("申请权限失败: ${e.message}")
                            }
                    } else {
                        log("权限已授予")
                    }
                }.addOnFailureListener { e ->
                    val errorMessage = e.message.orEmpty()
                    if (errorMessage.contains("fingerprint verify failed", ignoreCase = true)) {
                        log("权限检查失败：指纹校验未通过，请检查手机端包名和签名是否已在手环快应用端正确配置")
                    }
                    log(errorMessage)
                }
        }
    }

    fun onFilePicked(contentResolver: ContentResolver, uri: Uri) {
        pendingFileUri.value = uri
        selectedFileName.value = getFileName(contentResolver, uri)
        log("已选择 ${selectedFileName.value}")
    }

    fun confirmImport(context: Context) {
        val nodeId = nodeId
        if (nodeId == null) {
            Toast.makeText(context, "未连接到设备", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = pendingFileUri.value
        if (uri == null) {
            Toast.makeText(context, "请先选择配置文件", Toast.LENGTH_SHORT).show()
            return
        }
        val contentResolver = context.contentResolver
        val fileName = getFileName(contentResolver, uri)
        val jsonText: String? = if (fileName.endsWith(".wakeup_schedule")) {
            log("开始转化")
            val wakeupText = readTextFromUri(contentResolver, uri)
            if (wakeupText == null) {
                Toast.makeText(context, "读取配置文件失败", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val converted = convertWakeupScheduleToJson(wakeupText)
                log("转化成功")
                converted
            } catch (e: Exception) {
                log("转化失败: ${e.message}")
                Toast.makeText(context, "转化失败: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }
        } else {
            readTextFromUri(contentResolver, uri)
        }
        if (jsonText == null) {
            Toast.makeText(context, "读取配置文件失败", Toast.LENGTH_SHORT).show()
            return
        }
        val validationError = validateScheduleConfig(jsonText)
        if (validationError != null) {
            log("导入失败，缺失${extractMissingItem(validationError)}")
            Toast.makeText(context, validationError, Toast.LENGTH_LONG).show()
            return
        }
        sendMessageToWearable(context, jsonText)
    }

    private fun sendMessageToWearable(context: Context, message: String) {
        val messageApi = Wearable.getMessageApi(context)
        val nodeId = nodeId
        if (nodeId != null) {
            messageApi.sendMessage(nodeId, message.toByteArray())
                .addOnSuccessListener {
                    log("导入成功")
                    Toast.makeText(context, "配置发送成功", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    log("导入失败")
                    Toast.makeText(context, "配置发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "未连接到设备", Toast.LENGTH_SHORT).show()
        }
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

    private fun convertWakeupScheduleToJson(wakeupText: String): String {
        val segments = wakeupText.split("\n").filter { it.trim().isNotEmpty() }
        val jsonObjects = mutableListOf<String>()
        var buffer = StringBuilder()
        for (line in segments) {
            buffer.append(line)
            if (line.trim().endsWith("}")) {
                jsonObjects.add(buffer.toString())
                buffer = StringBuilder()
            } else if (line.trim().endsWith("]")) {
                jsonObjects.add(buffer.toString())
                buffer = StringBuilder()
            } else {
                buffer.append("\n")
            }
        }
        if (buffer.isNotEmpty()) jsonObjects.add(buffer.toString())
        if (jsonObjects.size < 5) throw Exception("wakeup_schedule 文件结构异常")
        val timeSlotsArr = JSONArray(jsonObjects[1])
        val tableConfig = JSONObject(jsonObjects[2])
        val courseListArr = JSONArray(jsonObjects[3])
        val courseArr = JSONArray(jsonObjects[4])
        val timeSlots = JSONArray()
        for (i in 0 until timeSlotsArr.length()) {
            val slot = timeSlotsArr.getJSONObject(i)
            val obj = JSONObject()
            obj.put("number", slot.optInt("node"))
            obj.put("startTime", slot.optString("startTime"))
            obj.put("endTime", slot.optString("endTime"))
            timeSlots.put(obj)
        }
        val config = JSONObject()
        config.put("semesterStartDate", tableConfig.optString("startDate"))
        config.put("semesterTotalWeeks", tableConfig.optInt("maxWeek"))
        val courseIdNameMap = mutableMapOf<Int, String>()
        for (i in 0 until courseListArr.length()) {
            val c = courseListArr.getJSONObject(i)
            courseIdNameMap[c.optInt("id")] = c.optString("courseName")
        }
        val courses = JSONArray()
        for (i in 0 until courseArr.length()) {
            val c = courseArr.getJSONObject(i)
            val courseObj = JSONObject()
            val courseId = c.optInt("id")
            courseObj.put("name", courseIdNameMap[courseId] ?: "")
            courseObj.put("teacher", c.optString("teacher"))
            courseObj.put("position", c.optString("room"))
            courseObj.put("day", c.optInt("day"))
            val startWeek = c.optInt("startWeek")
            val endWeek = c.optInt("endWeek")
            val weeks = JSONArray()
            for (w in startWeek..endWeek) weeks.put(w)
            courseObj.put("weeks", weeks)
            val ownTime = c.optBoolean("ownTime", false)
            courseObj.put("isCustomTime", ownTime)
            if (ownTime) {
                courseObj.put("customStartTime", c.optString("startTime"))
                courseObj.put("customEndTime", c.optString("endTime"))
            } else {
                courseObj.put("startSection", c.optInt("startNode"))
                courseObj.put("endSection", c.optInt("startNode") + c.optInt("step") - 1)
            }
            courses.put(courseObj)
        }
        val root = JSONObject()
        root.put("courses", courses)
        root.put("timeSlots", timeSlots)
        root.put("config", config)
        return root.toString()
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