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
import org.yaml.snakeyaml.Yaml
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

    private var hasLoggedNoDevice = false

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
                hasLoggedNoDevice = false
                log("已连接到设备：${curNode?.name ?: "未知"}")
                if (lastAutoOpenedNodeId != nodeId) {
                    nodeId?.let { id ->
                        nodeApi.isWearAppInstalled(id)
                            .addOnSuccessListener {
                                nodeApi.launchWearApp(id, "pages/index")
                                    .addOnSuccessListener {
                                        lastAutoOpenedNodeId = id
                                        log("手环端快应用启动成功")
                                    }
                                    .addOnFailureListener {
                                        log("手环端快应用启动失败")
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
                if (!hasLoggedNoDevice) {
                    log("未检测到已连接的设备")
                    hasLoggedNoDevice = true
                }
            }
        }.addOnFailureListener { e ->
            isConnectedState.value = false
            log("获取已连接设备失败：${e.message ?: "未知错误"}")
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
                                    log("权限申请失败：指纹校验未通过，请检查手机应用和手环快应用签名是否一致")
                                } else {
                                    log("权限申请失败：$errorMessage")
                                }
                            }
                    } else {
                        log("权限已授予")
                    }
                }.addOnFailureListener { e ->
                    val errorMessage = e.message.orEmpty()
                    if (errorMessage.contains("fingerprint verify failed", ignoreCase = true)) {
                        log("权限检查失败：指纹校验未通过，请检查手机应用和手环快应用签名是否一致")
                    } else {
                        log("权限检查失败：$errorMessage")
                    }
                }
        }
    }

    fun onFilePicked(contentResolver: ContentResolver, uri: Uri) {
        pendingFileUri.value = uri
        selectedFileName.value = getFileName(contentResolver, uri)
        log("已选择文件 ${selectedFileName.value}")
    }

    fun confirmImport(context: Context) {
        val nodeId = nodeId
        if (nodeId == null) {
            log("导入失败：未连接到设备")
            Toast.makeText(context, "未连接到设备", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = pendingFileUri.value
        if (uri == null) {
            log("导入失败：未选择配置文件")
            Toast.makeText(context, "请先选择配置文件", Toast.LENGTH_SHORT).show()
            return
        }
        val contentResolver = context.contentResolver
        val fileName = getFileName(contentResolver, uri)
        val jsonText: String? = when {
            fileName.endsWith(".wakeup_schedule") -> {
                log("检测到 wakeup_schedule 文件，开始转换为标准 JSON")
                val wakeupText = readTextFromUri(contentResolver, uri)
                if (wakeupText == null) {
                    log("读取配置文件失败：文件内容为空")
                    Toast.makeText(context, "读取配置文件失败", Toast.LENGTH_SHORT).show()
                    return
                }
                try {
                    val converted = convertWakeupScheduleToJson(wakeupText)
                    log("wakeup_schedule 转换成功")
                    converted
                } catch (e: Exception) {
                    log("wakeup_schedule 转换失败：${e.message}")
                    Toast.makeText(context, "转换失败: ${e.message}", Toast.LENGTH_LONG).show()
                    return
                }
            }
            fileName.endsWith(".yml") || fileName.endsWith(".yaml") -> {
                log("检测到 CSES YAML 文件，开始转换为标准 JSON")
                val yamlText = readTextFromUri(contentResolver, uri)
                if (yamlText == null) {
                    log("读取配置文件失败：文件内容为空")
                    Toast.makeText(context, "读取配置文件失败", Toast.LENGTH_SHORT).show()
                    return
                }
                try {
                    val converted = convertCsesYamlToJson(yamlText)
                    log("YAML 转换成功")
                    converted
                } catch (e: Exception) {
                    log("YAML 转换失败：${e.message}")
                    val msg = e.message ?: "未知错误"
                    if (msg.startsWith("行")) {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "转换失败: $msg", Toast.LENGTH_LONG).show()
                    }
                    return
                }
            }
            else -> {
                readTextFromUri(contentResolver, uri)
            }
        }
        if (jsonText == null) {
            log("读取配置文件失败：文件内容为空")
            Toast.makeText(context, "读取配置文件失败", Toast.LENGTH_SHORT).show()
            return
        }
        val validationError = validateScheduleConfig(jsonText)
        if (validationError != null) {
            log("导入失败，缺失字段 ${extractMissingItem(validationError)}")
            Toast.makeText(context, validationError, Toast.LENGTH_LONG).show()
            return
        }
        val sanitizedPayload = sanitizeSchedulePayload(jsonText)
        sendMessageToWearable(context, sanitizedPayload)
    }

    private fun sendMessageToWearable(context: Context, message: String) {
        val messageApi = Wearable.getMessageApi(context)
        val nodeId = nodeId
        if (nodeId != null) {
            messageApi.sendMessage(nodeId, message.toByteArray())
                .addOnSuccessListener {
                    log("配置已发送到手环")
                    Toast.makeText(context, "配置发送成功", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    log("配置发送失败：${e.message ?: "未知错误"}")
                    Toast.makeText(context, "配置发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            log("配置发送失败：未连接到设备")
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

    private fun normalizeScheduleTime(rawTime: Any?): String? {
        if (rawTime == null) return null

        if (rawTime is Number) {
            val totalSeconds = rawTime.toLong()
            if (totalSeconds in 0..86399) {
                val hour = (totalSeconds / 3600).toInt()
                val minute = ((totalSeconds % 3600) / 60).toInt()
                return "%02d:%02d".format(hour, minute)
            }
            return null
        }

        val text = rawTime.toString().trim()
        if (text.isBlank()) return null

        val parts = text.split(":")
        if (parts.size !in 2..3) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return "%02d:%02d".format(hour, minute)
    }

    private fun timeToMinutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun splitTopLevelJsonBlocks(rawText: String): List<String> {
        val blocks = mutableListOf<String>()
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false

        for (i in rawText.indices) {
            val ch = rawText[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (inString) continue

            if (ch == '{' || ch == '[') {
                if (depth == 0) start = i
                depth++
            } else if (ch == '}' || ch == ']') {
                depth--
                if (depth == 0 && start >= 0) {
                    blocks.add(rawText.substring(start, i + 1).trim())
                    start = -1
                }
            }
        }
        return blocks.filter { it.isNotBlank() }
    }

    private fun sanitizeSchedulePayload(jsonText: String): String {
        val root = JSONObject(jsonText)
        val sanitizedRoot = JSONObject()

        val courses = root.optJSONArray("courses") ?: JSONArray()
        val sanitizedCourses = JSONArray()
        for (i in 0 until courses.length()) {
            val course = courses.optJSONObject(i) ?: continue
            val out = JSONObject()
            out.put("name", course.optString("name"))
            out.put("day", course.optInt("day"))
            out.put("isCustomTime", course.optBoolean("isCustomTime", false))

            if (course.has("teacher")) {
                out.put("teacher", course.optString("teacher"))
            }
            if (course.has("position")) {
                out.put("position", course.optString("position"))
            }
            if (course.has("weeks")) {
                out.put("weeks", course.optJSONArray("weeks") ?: JSONArray())
            }
            if (course.has("weekType")) {
                out.put("weekType", course.optString("weekType"))
            }

            val isCustomTime = course.optBoolean("isCustomTime", false)
            if (isCustomTime) {
                out.put("customStartTime", course.optString("customStartTime"))
                out.put("customEndTime", course.optString("customEndTime"))
            } else {
                out.put("startSection", course.optInt("startSection"))
                out.put("endSection", course.optInt("endSection"))
            }

            sanitizedCourses.put(out)
        }
        sanitizedRoot.put("courses", sanitizedCourses)

        val timeSlots = root.optJSONArray("timeSlots") ?: JSONArray()
        val sanitizedTimeSlots = JSONArray()
        for (i in 0 until timeSlots.length()) {
            val slot = timeSlots.optJSONObject(i) ?: continue
            val out = JSONObject()
            out.put("number", slot.optInt("number"))
            out.put("startTime", slot.optString("startTime"))
            out.put("endTime", slot.optString("endTime"))
            sanitizedTimeSlots.put(out)
        }
        sanitizedRoot.put("timeSlots", sanitizedTimeSlots)

        root.optJSONObject("config")?.let { config ->
            val out = JSONObject()
            if (config.has("semesterStartDate")) {
                out.put("semesterStartDate", config.opt("semesterStartDate"))
            }
            if (config.has("semesterTotalWeeks")) {
                out.put("semesterTotalWeeks", config.opt("semesterTotalWeeks"))
            }
            sanitizedRoot.put("config", out)
        }

        return sanitizedRoot.toString()
    }

    private fun convertCsesYamlToJson(yamlText: String): String {
        val data = try {
            val yaml = Yaml()
            yaml.load<Any>(yamlText) as? Map<*, *>
        } catch (_: ExceptionInInitializerError) {
            throw Exception("YAML 解析器初始化失败（可能是发布版混淆导致的库兼容问题）")
        } catch (_: LinkageError) {
            throw Exception("YAML 解析器加载失败（类链接异常）")
        } catch (e: Exception) {
            throw Exception("YAML 解析失败: ${e.message}")
        } ?: throw Exception("无效的 YAML 格式")

        val subjects = data["subjects"] as? List<*>
        val subjectMap = mutableMapOf<String, Map<String, String>>()
        subjects?.forEach { sub ->
            if (sub is Map<*, *>) {
                val name = sub["name"]?.toString() ?: ""
                val teacher = sub["teacher"]?.toString() ?: ""
                val room = sub["room"]?.toString() ?: ""
                subjectMap[name] = mapOf("teacher" to teacher, "room" to room)
            }
        }

        val schedules = data["schedules"] as? List<*>
            ?: throw Exception("缺少必填项 schedules")

        val allUniqueTimes = mutableListOf<String>()
        schedules.forEach { schedule ->
            if (schedule is Map<*, *>) {
                val classes = schedule["classes"] as? List<*>
                classes?.forEach { cls ->
                    if (cls is Map<*, *>) {
                        val start = normalizeScheduleTime(cls["start_time"])
                            ?: throw Exception("课程 ${cls["subject"]?.toString().orEmpty()} 的 start_time 格式不合法")
                        val end = normalizeScheduleTime(cls["end_time"])
                            ?: throw Exception("课程 ${cls["subject"]?.toString().orEmpty()} 的 end_time 格式不合法")
                        if (!allUniqueTimes.contains(start)) {
                            allUniqueTimes.add(start)
                        }
                        if (!allUniqueTimes.contains(end)) {
                            allUniqueTimes.add(end)
                        }
                    }
                }
            }
        }

        allUniqueTimes.sortBy { timeToMinutes(it) }
        if (allUniqueTimes.size < 2) {
            throw Exception("时间轴数据不足，无法生成 timeSlots")
        }

        val timeNumberMap = mutableMapOf<String, Int>()
        allUniqueTimes.forEachIndexed { index, time ->
            timeNumberMap[time] = index + 1
        }

        val timeSlots = JSONArray()
        for (i in 0 until allUniqueTimes.size - 1) {
            val obj = JSONObject()
            obj.put("number", i + 1)
            obj.put("startTime", allUniqueTimes[i])
            obj.put("endTime", allUniqueTimes[i + 1])
            timeSlots.put(obj)
        }

        val courses = JSONArray()
        schedules.forEach { schedule ->
            if (schedule is Map<*, *>) {
                val day = (schedule["enable_day"] as? Number)?.toInt() ?: 1
                val weeksType = schedule["weeks"]?.toString() ?: "all"

                val classes = schedule["classes"] as? List<*>
                classes?.forEach { cls ->
                    if (cls is Map<*, *>) {
                        val subject = cls["subject"]?.toString() ?: ""
                        val start = normalizeScheduleTime(cls["start_time"])
                            ?: throw Exception("课程 $subject 的 start_time 格式不合法")
                        val end = normalizeScheduleTime(cls["end_time"])
                            ?: throw Exception("课程 $subject 的 end_time 格式不合法")

                        val startNumber = timeNumberMap[start]
                            ?: throw Exception("课程 $subject 时间 [$start - $end] 在时间轴中未找到")
                        val endNumber = timeNumberMap[end]
                            ?: throw Exception("课程 $subject 时间 [$start - $end] 在时间轴中未找到")
                        if (endNumber <= startNumber) {
                            throw Exception("课程 $subject 时间 [$start - $end] 起止顺序不合法")
                        }

                        val subInfo = subjectMap[subject]
                        val course = JSONObject()
                        course.put("name", subject)
                        course.put("teacher", subInfo?.get("teacher") ?: "")
                        course.put("position", subInfo?.get("room") ?: "")
                        course.put("day", day)
                        course.put("weekType", weeksType)
                        course.put("isCustomTime", false)
                        course.put("startSection", startNumber)
                        course.put("endSection", endNumber - 1)
                        courses.put(course)
                    }
                }
            }
        }

        val config = JSONObject()
        config.put("semesterStartDate", "")
        config.put("semesterTotalWeeks", "")

        val result = JSONObject()
        result.put("courses", courses)
        result.put("timeSlots", timeSlots)
        result.put("config", config)
        return result.toString()
    }

    private fun convertWakeupScheduleToJson(wakeupText: String): String {
        val jsonBlocks = splitTopLevelJsonBlocks(wakeupText)
        var timeSlotsArr: JSONArray? = null
        var tableConfig: JSONObject? = null
        var courseListArr: JSONArray? = null
        var courseArr: JSONArray? = null

        jsonBlocks.forEach { block ->
            val trimmed = block.trim()
            if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                if (obj.has("startDate") || obj.has("maxWeek")) {
                    tableConfig = obj
                }
            } else if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                if (arr.length() == 0) {
                    return@forEach
                }
                val first = arr.optJSONObject(0) ?: return@forEach

                when {
                    first.has("node") && first.has("startTime") && first.has("endTime") -> {
                        timeSlotsArr = arr
                    }
                    first.has("id") && first.has("courseName") -> {
                        courseListArr = arr
                    }
                    first.has("id") && first.has("day") && first.has("startWeek") && first.has("endWeek") -> {
                        courseArr = arr
                    }
                }
            }
        }

        if (timeSlotsArr == null || tableConfig == null || courseListArr == null || courseArr == null) {
            throw Exception("wakeup_schedule 文件结构异常，缺少必需数据块")
        }

        val timeSlots = JSONArray()
        for (i in 0 until timeSlotsArr.length()) {
            val slot = timeSlotsArr.getJSONObject(i)
            val startTime = normalizeScheduleTime(slot.opt("startTime")) ?: slot.optString("startTime")
            val endTime = normalizeScheduleTime(slot.opt("endTime")) ?: slot.optString("endTime")
            val obj = JSONObject()
            obj.put("number", slot.optInt("node"))
            obj.put("startTime", startTime)
            obj.put("endTime", endTime)
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
            val weekType = c.optInt("type", 0)
            val weeks = JSONArray()
            for (w in startWeek..endWeek) {
                if (weekType == 1 && w % 2 == 0) continue
                if (weekType == 2 && w % 2 != 0) continue
                weeks.put(w)
            }
            if (weeks.length() == 0) {
                throw Exception("课程 ${courseObj.optString("name")} 的周数范围无效")
            }
            courseObj.put("weeks", weeks)

            val ownTime = c.optBoolean("ownTime", false)
            courseObj.put("isCustomTime", ownTime)
            if (ownTime) {
                val customStart = normalizeScheduleTime(c.opt("startTime"))
                    ?: throw Exception("课程 ${courseObj.optString("name")} 的 startTime 格式不合法")
                val customEnd = normalizeScheduleTime(c.opt("endTime"))
                    ?: throw Exception("课程 ${courseObj.optString("name")} 的 endTime 格式不合法")
                courseObj.put("customStartTime", customStart)
                courseObj.put("customEndTime", customEnd)
            } else {
                val startNode = c.optInt("startNode")
                val step = c.optInt("step")
                if (startNode <= 0 || step <= 0) {
                    throw Exception("课程 ${courseObj.optString("name")} 的 startNode/step 不合法")
                }
                courseObj.put("startSection", startNode)
                courseObj.put("endSection", startNode + step - 1)
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
        validateCourses(courses)?.let { return it }
        validateTimeSlots(timeSlots)?.let { return it }
        return null
    }

    private fun validateCourses(courses: JSONArray): String? {
        for (i in 0 until courses.length()) {
            val course = courses.optJSONObject(i) ?: return "courses[$i] 必须是对象"
            if (course.optString("name").isBlank()) return "courses[$i].name 必填"
            if (!course.has("day")) return "courses[$i].day 必填"
            
            if (!course.has("weeks") && !course.has("weekType")) return "courses[$i].weeks 或 weekType 必填"
            if (course.has("weeks")) {
                val weeks = course.optJSONArray("weeks") ?: return "courses[$i].weeks 必须是数组"
                if (weeks.length() == 0) return "courses[$i].weeks 不能为空"
            }
            if (course.has("weekType")) {
                if (course.optString("weekType").isBlank()) return "courses[$i].weekType 不能为空"
            }

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