package com.schedule.vela

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.schedule.vela.ui.theme.PaletteStyle
import com.schedule.vela.ui.theme.ThemeMode
import com.schedule.vela.update.UpdateController
import com.schedule.vela.update.UpdateDialogState
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
import kotlin.time.Duration.Companion.milliseconds

private const val DEFAULT_SEED_COLOR = 0xFF6750A4.toInt()

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    val logTextState = mutableStateOf("")
    val isConnectedState = mutableStateOf(false)
    val connectedDeviceText = mutableStateOf("设备未连接")
    val selectedFileName = mutableStateOf("选择配置文件")
    val pendingFileUri = mutableStateOf<Uri?>(null)
    val awaitingTimetableState = mutableStateOf(false)
    private var pendingStarlinkJson: String? = null

    // 外观设置
    private var themeModeState by mutableStateOf(ThemeMode.System)
    val themeMode: ThemeMode get() = themeModeState
    private var customColorState by mutableStateOf(false)
    val customColor: Boolean get() = customColorState
    private var dynamicColorState by mutableStateOf(true)
    val dynamicColor: Boolean get() = dynamicColorState
    private var paletteStyleState by mutableStateOf(PaletteStyle.TonalSpot)
    val paletteStyle: PaletteStyle get() = paletteStyleState
    private var seedColorState by mutableIntStateOf(DEFAULT_SEED_COLOR)
    val seedColor: Int get() = seedColorState
    private var floatingNavState by mutableStateOf(false)
    val floatingNav: Boolean get() = floatingNavState
    private var appBlurState by mutableStateOf(true)
    val appBlur: Boolean get() = appBlurState
    private var predictiveBackEnabledState by mutableStateOf(true)
    val predictiveBackEnabled: Boolean get() = predictiveBackEnabledState

    // 应用更新（检查/下载/安装）
    private val update =
        UpdateController(
            scope = viewModelScope,
            onToast = { showToast(it) },
            onDismissToast = { dismissToast() },
            onLogError = { scope, e -> Log.w("ScheduleSyncUpdate", "$scope: ${e.message}") },
        )

    init {
        loadSettings()
        update.loadSettings()
        viewModelScope.launch {
            delay(1500L.milliseconds)
            if (update.checkUpdateOnStart) update.checkForUpdate(silent = true)
        }
    }

    private var nodeId: String? = null
    private var curNode: Node? = null
    private var lastAutoOpenedNodeId: String? = null
    private val nodeApi: NodeApi = Wearable.getNodeApi(application)

    private var hasLoggedNoDevice = false

    fun startDeviceQuery() {
        viewModelScope.launch {
            while (nodeId == null) {
                queryConnectedDevices()
                delay(1000L.milliseconds)
            }
            curNode?.let { connectedDeviceText.value = it.name }
        }
    }

    private fun log(message: Any) {
        logTextState.value += "$message\n"
    }

    private fun queryConnectedDevices() {
        nodeApi.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isNotEmpty()) {
                    curNode = nodes[0]
                    nodeId = curNode?.id
                    isConnectedState.value = true
                    hasLoggedNoDevice = false
                    log("已连接到设备：${curNode?.name ?: "未知"}")
                    if (lastAutoOpenedNodeId != nodeId) {
                        nodeId?.let { id ->
                            nodeApi
                                .isWearAppInstalled(id)
                                .addOnSuccessListener {
                                    nodeApi
                                        .launchWearApp(id, "pages/index")
                                        .addOnSuccessListener {
                                            lastAutoOpenedNodeId = id
                                            log("手环端快应用启动成功")
                                        }.addOnFailureListener {
                                            log("手环端快应用启动失败")
                                        }
                                }.addOnFailureListener {
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
            authApi
                .checkPermission(did, Permission.DEVICE_MANAGER)
                .addOnSuccessListener { granted ->
                    if (!granted) {
                        authApi
                            .requestPermission(did, Permission.DEVICE_MANAGER)
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

    fun onFilePicked(
        contentResolver: ContentResolver,
        uri: Uri,
    ) {
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
        val jsonText: String? =
            when {
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
                    val rawText = readTextFromUri(contentResolver, uri)
                    if (rawText != null && isStarlinkJson(rawText)) {
                        log("检测到星链课表文件，开始转换为标准 JSON")
                        val converted =
                            try {
                                convertStarlinkToJson(rawText)
                            } catch (e: Exception) {
                                log("星链课表转换失败：${e.message}")
                                Toast.makeText(context, "转换失败: ${e.message}", Toast.LENGTH_LONG).show()
                                return
                            }
                        val timeSlots = JSONObject(converted).optJSONArray("timeSlots")
                        if (timeSlots == null || timeSlots.length() == 0) {
                            pendingStarlinkJson = converted
                            awaitingTimetableState.value = true
                            selectedFileName.value = "选择时间表文件"
                            log("星链课程表已解析，请选择对应的时间表文件")
                            Toast.makeText(context, "请选择对应的时间表文件", Toast.LENGTH_LONG).show()
                            return
                        }
                        log("星链课表转换成功")
                        converted
                    } else {
                        rawText
                    }
                }
            }
        if (jsonText == null) {
            log("读取配置文件失败：文件内容为空")
            Toast.makeText(context, "读取配置文件失败", Toast.LENGTH_SHORT).show()
            return
        }
        finishImport(context, jsonText)
    }

    fun onTimetablePicked(
        context: Context,
        uri: Uri,
    ) {
        val text = readTextFromUri(context.contentResolver, uri)
        if (text == null) {
            log("读取时间表失败：文件内容为空")
            Toast.makeText(context, "读取时间表失败", Toast.LENGTH_SHORT).show()
            return
        }
        val timeSlots =
            try {
                convertTimetableToTimeSlots(text)
            } catch (e: Exception) {
                log("时间表解析失败：${e.message}")
                Toast.makeText(context, "时间表解析失败: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }
        if (timeSlots.length() == 0) {
            log("时间表解析失败：没有可用的节次")
            Toast.makeText(context, "时间表没有可用的节次", Toast.LENGTH_LONG).show()
            return
        }
        val base = pendingStarlinkJson
        if (base == null) {
            log("导入失败：没有待合并的课程表")
            return
        }
        val root = JSONObject(base)
        root.put("timeSlots", timeSlots)
        pendingStarlinkJson = null
        awaitingTimetableState.value = false
        selectedFileName.value = "选择配置文件"
        log("时间表已合并（${timeSlots.length()} 个节次）")
        finishImport(context, root.toString())
    }

    private fun finishImport(
        context: Context,
        jsonText: String,
    ) {
        val root =
            try {
                JSONObject(jsonText)
            } catch (_: JSONException) {
                log("导入失败：配置文件不是有效 JSON")
                Toast.makeText(context, "配置文件不是有效 JSON", Toast.LENGTH_LONG).show()
                return
            }
        val skipped = dropInvalidEntries(root)
        val structuralError = validateScheduleStructure(root)
        if (structuralError != null) {
            log("导入失败：$structuralError")
            Toast.makeText(context, structuralError, Toast.LENGTH_LONG).show()
            return
        }
        if (skipped.isNotEmpty()) {
            val shown = skipped.take(5)
            val more = if (skipped.size > shown.size) " 等" else ""
            val summary = "已跳过 ${skipped.size} 条无效数据：${shown.joinToString("；")}$more"
            log(summary)
            Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
        }
        sendMessageToWearable(context, sanitizeSchedulePayload(root.toString()))
    }

    private fun sendMessageToWearable(
        context: Context,
        message: String,
    ) {
        val messageApi = Wearable.getMessageApi(context)
        val nodeId = nodeId
        if (nodeId != null) {
            messageApi
                .sendMessage(nodeId, message.toByteArray())
                .addOnSuccessListener {
                    log("配置已发送到手环")
                    Toast.makeText(context, "配置发送成功", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener { e ->
                    log("配置发送失败：${e.message ?: "未知错误"}")
                    Toast.makeText(context, "配置发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            log("配置发送失败：未连接到设备")
            Toast.makeText(context, "未连接到设备", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(
        contentResolver: ContentResolver,
        uri: Uri,
    ): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "已选择文件"
    }

    private fun readTextFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
    ): String? {
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
                out.put("position", course.optString("position").trimStart('@'))
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
        val data =
            try {
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

        val schedules =
            data["schedules"] as? List<*>
                ?: throw Exception("缺少必填项 schedules")

        val allUniqueTimes = mutableListOf<String>()
        schedules.forEach { schedule ->
            if (schedule is Map<*, *>) {
                val classes = schedule["classes"] as? List<*>
                classes?.forEach { cls ->
                    if (cls is Map<*, *>) {
                        val start =
                            normalizeScheduleTime(cls["start_time"])
                                ?: throw Exception("课程 ${cls["subject"]?.toString().orEmpty()} 的 start_time 格式不合法")
                        val end =
                            normalizeScheduleTime(cls["end_time"])
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
                        val start =
                            normalizeScheduleTime(cls["start_time"])
                                ?: throw Exception("课程 $subject 的 start_time 格式不合法")
                        val end =
                            normalizeScheduleTime(cls["end_time"])
                                ?: throw Exception("课程 $subject 的 end_time 格式不合法")

                        val startNumber =
                            timeNumberMap[start]
                                ?: throw Exception("课程 $subject 时间 [$start - $end] 在时间轴中未找到")
                        val endNumber =
                            timeNumberMap[end]
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
                val customStart =
                    normalizeScheduleTime(c.opt("startTime"))
                        ?: throw Exception("课程 ${courseObj.optString("name")} 的 startTime 格式不合法")
                val customEnd =
                    normalizeScheduleTime(c.opt("endTime"))
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

    // 星链课表：根有 courses 数组、课程用 weekday 字段、根无 timeSlots
    private fun isStarlinkJson(jsonText: String): Boolean =
        try {
            val root = JSONObject(jsonText)
            !root.has("timeSlots") &&
                root.optJSONArray("courses")?.optJSONObject(0)?.has("weekday") == true
        } catch (_: JSONException) {
            false
        }

    private fun convertStarlinkToJson(jsonText: String): String {
        val root = JSONObject(jsonText)
        val courses = root.optJSONArray("courses") ?: throw Exception("缺少 courses 数组")
        val convertedCourses = JSONArray()
        for (i in 0 until courses.length()) {
            val course = courses.optJSONObject(i) ?: continue
            val name = course.optString("name")
            val weeks = course.optJSONArray("weeks")
            if (name.isBlank() || weeks == null || weeks.length() == 0) continue
            val out = JSONObject()
            out.put("name", name)
            out.put("teacher", course.optString("teacher"))
            out.put("position", course.optString("location"))
            out.put("day", course.optInt("weekday"))
            out.put("startSection", course.optInt("startSection"))
            out.put("endSection", course.optInt("endSection"))
            out.put("weeks", weeks)
            convertedCourses.put(out)
        }
        val result = JSONObject()
        result.put("courses", convertedCourses)
        result.put("timeSlots", convertStarlinkTimeSlots(root))
        extractStarlinkConfig(root)?.let { result.put("config", it) }
        return result.toString()
    }

    private fun convertStarlinkTimeSlots(root: JSONObject): JSONArray {
        root.optJSONObject("sectionMinutes")?.let { sectionMinutes ->
            val slots = JSONArray()
            val keys = sectionMinutes.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val number = key.toIntOrNull() ?: continue
                val range = sectionMinutes.optJSONArray(key) ?: continue
                if (range.length() < 2) continue
                val out = JSONObject()
                out.put("number", number)
                out.put("startTime", minutesToTimeString(range.optInt(0)))
                out.put("endTime", minutesToTimeString(range.optInt(1)))
                slots.put(out)
            }
            if (slots.length() > 0) return sortTimeSlots(slots)
        }
        val items = root.optJSONObject("timetable")?.optJSONArray("items") ?: return JSONArray()
        return timetableItemsToTimeSlots(items)
    }

    private fun extractStarlinkConfig(root: JSONObject): JSONObject? {
        val startDate = root.optString("startDate").take(10).takeIf { it.isNotBlank() } ?: return null
        val totalWeeks = root.optInt("totalWeeks")
        if (totalWeeks <= 0) return null
        val config = JSONObject()
        config.put("semesterStartDate", startDate)
        config.put("semesterTotalWeeks", totalWeeks)
        return config
    }

    private fun convertTimetableToTimeSlots(jsonText: String): JSONArray {
        val root = JSONObject(jsonText)
        if (root.optString("type") != "timetable") throw Exception("不是有效的时间表文件")
        val items = root.optJSONObject("data")?.optJSONArray("items") ?: throw Exception("时间表缺少 data.items")
        return timetableItemsToTimeSlots(items)
    }

    private fun timetableItemsToTimeSlots(items: JSONArray): JSONArray {
        val slots = JSONArray()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val out = JSONObject()
            out.put("number", item.optInt("section"))
            out.put("startTime", "%02d:%02d".format(item.optInt("startHour"), item.optInt("startMinute")))
            out.put("endTime", "%02d:%02d".format(item.optInt("endHour"), item.optInt("endMinute")))
            slots.put(out)
        }
        return sortTimeSlots(slots)
    }

    private fun sortTimeSlots(slots: JSONArray): JSONArray {
        val sorted = (0 until slots.length()).mapNotNull { slots.optJSONObject(it) }.sortedBy { it.optInt("number") }
        val result = JSONArray()
        sorted.forEach { result.put(it) }
        return result
    }

    private fun minutesToTimeString(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    private fun validateScheduleStructure(root: JSONObject): String? {
        val courses = root.optJSONArray("courses") ?: return "缺少必填项 courses"
        if (root.optJSONArray("timeSlots") == null) return "缺少必填项 timeSlots"
        if (courses.length() == 0) return "没有可用的课程数据"
        return null
    }

    // 剔除单条非法的课程与节次并返回跳过原因，结构性问题仍由 validateScheduleStructure 处理
    private fun dropInvalidEntries(root: JSONObject): List<String> {
        val skipped = mutableListOf<String>()
        root.optJSONArray("courses")?.let { courses ->
            val kept = JSONArray()
            for (i in 0 until courses.length()) {
                val course = courses.optJSONObject(i)
                val reason = courseSkipReason(i, course)
                if (reason != null) skipped.add(reason) else kept.put(course)
            }
            root.put("courses", kept)
        }
        root.optJSONArray("timeSlots")?.let { slots ->
            val kept = JSONArray()
            for (i in 0 until slots.length()) {
                val slot = slots.optJSONObject(i)
                val reason = slotSkipReason(i, slot)
                if (reason != null) skipped.add(reason) else kept.put(slot)
            }
            root.put("timeSlots", kept)
        }
        return skipped
    }

    private fun courseSkipReason(
        index: Int,
        course: JSONObject?,
    ): String? {
        val label = "第 ${index + 1} 门课程"
        if (course == null) return "$label 格式错误"
        val name = course.optString("name")
        if (name.isBlank()) return "$label 缺少名称"
        val named = "$label「$name」"

        if (!course.has("day")) return "$named 缺少星期"
        if (!course.has("weeks") && !course.has("weekType")) return "$named 缺少周数"
        if (course.has("weeks")) {
            val weeks = course.optJSONArray("weeks")
            if (weeks == null || weeks.length() == 0) return "$named 的周数为空"
        }
        if (course.has("weekType") && course.optString("weekType").isBlank()) return "$named 的周数类型为空"

        if (course.optBoolean("isCustomTime", false)) {
            if (course.optString("customStartTime").isBlank()) return "$named 缺少开始时间"
            if (course.optString("customEndTime").isBlank()) return "$named 缺少结束时间"
        } else if (course.optInt("startSection") <= 0 || course.optInt("endSection") <= 0) {
            return "$named 的节次无效"
        }
        return null
    }

    private fun slotSkipReason(
        index: Int,
        slot: JSONObject?,
    ): String? {
        val label = "第 ${index + 1} 个节次"
        if (slot == null) return "$label 格式错误"
        if (!slot.has("number")) return "$label 缺少节次号"
        if (slot.optString("startTime").isBlank() || slot.optString("endTime").isBlank()) return "$label 的时间为空"
        return null
    }

    private fun loadSettings() {
        try {
            val storage = AppStorage.instance
            themeModeState =
                ThemeMode.entries.firstOrNull { it.name == storage.getString(StorageKeys.THEME_MODE) }
                    ?: ThemeMode.System
            customColorState = storage.getBoolean(StorageKeys.CUSTOM_COLOR, false)
            // 平台不支持的设置项强制为默认值（动态取色需 Android 12+，预测性返回需 Android 13+）
            dynamicColorState = storage.getBoolean(StorageKeys.DYNAMIC_COLOR, true) && supportsDynamicColor
            paletteStyleState =
                PaletteStyle.entries.firstOrNull { it.name == storage.getString(StorageKeys.PALETTE_STYLE) }
                    ?: PaletteStyle.TonalSpot
            seedColorState = storage.getInt(StorageKeys.SEED_COLOR, DEFAULT_SEED_COLOR)
            floatingNavState = storage.getBoolean(StorageKeys.FLOATING_NAV, false)
            appBlurState = storage.getBoolean(StorageKeys.APP_BLUR, true)
            predictiveBackEnabledState = storage.getBoolean(StorageKeys.PREDICTIVE_BACK, true) && supportsPredictiveBack
        } catch (e: Exception) {
            log("loadSettings: ${e.message}")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        themeModeState = mode
        AppStorage.instance.saveString(StorageKeys.THEME_MODE, mode.name)
    }

    fun setCustomColor(enabled: Boolean) {
        customColorState = enabled
        AppStorage.instance.saveBoolean(StorageKeys.CUSTOM_COLOR, enabled)
    }

    fun setDynamicColor(enabled: Boolean) {
        dynamicColorState = enabled
        AppStorage.instance.saveBoolean(StorageKeys.DYNAMIC_COLOR, enabled)
    }

    fun setPaletteStyle(style: PaletteStyle) {
        paletteStyleState = style
        AppStorage.instance.saveString(StorageKeys.PALETTE_STYLE, style.name)
    }

    fun setSeedColor(color: Int) {
        seedColorState = color
        AppStorage.instance.saveInt(StorageKeys.SEED_COLOR, color)
    }

    fun setFloatingNav(enabled: Boolean) {
        floatingNavState = enabled
        AppStorage.instance.saveBoolean(StorageKeys.FLOATING_NAV, enabled)
    }

    fun setAppBlur(enabled: Boolean) {
        appBlurState = enabled
        AppStorage.instance.saveBoolean(StorageKeys.APP_BLUR, enabled)
    }

    fun setPredictiveBackEnabled(enabled: Boolean) {
        predictiveBackEnabledState = enabled
        AppStorage.instance.saveBoolean(StorageKeys.PREDICTIVE_BACK, enabled)
    }

    // 更新设置与流程代理（逻辑见 UpdateController）
    val updateDialog: UpdateDialogState get() = update.dialog

    val githubProxyUrl: String get() = update.githubProxyUrl

    val checkUpdateOnStart: Boolean get() = update.checkUpdateOnStart

    fun checkForUpdate(silent: Boolean = false) = update.checkForUpdate(silent)

    fun startUpdate() = update.startUpdate()

    fun hideUpdateProgressDialog() = update.hideUpdateProgressDialog()

    fun reopenUpdateProgressDialog() = update.reopenUpdateProgressDialog()

    fun stopUpdate() = update.stopUpdate()

    fun openInstallSettings() = update.openInstallSettings()

    fun dismissUpdateDialog() = update.dismissDialog()

    fun setGithubProxy(url: String) = update.setGithubProxy(url)

    fun setCheckUpdateOnStartEnabled(enabled: Boolean) = update.setCheckUpdateOnStartEnabled(enabled)

    fun onAppResumed() = update.onAppResumed()
}
