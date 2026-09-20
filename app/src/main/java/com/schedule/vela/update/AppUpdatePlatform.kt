package com.schedule.vela.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.schedule.vela.ActivityHolder
import com.schedule.vela.ApplicationContext
import com.schedule.vela.IntentActions
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.resume

private const val TAG = "ScheduleSyncUpdate"

internal const val UPDATE_CHANNEL_ID = "app_update"
internal const val UPDATE_NOTIFICATION_ID = 1001

internal fun ensureUpdateChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(UPDATE_CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL_ID,
                "应用更新",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "下载更新期间显示下载进度"
                setShowBadge(false)
            },
        )
    }
}

// 本机 ABI 列表，按优先级排序。
internal fun currentAbis(): List<String> = Build.SUPPORTED_ABIS.toList()

// 是否已授权本应用安装未知来源应用。
internal fun canInstallPackages(): Boolean = ApplicationContext.instance.packageManager.canRequestPackageInstalls()

// 跳转到系统的“安装未知应用”授权页面。
internal fun openInstallPermissionSettings() {
    val context = ApplicationContext.instance
    val packageUri = "package:${context.packageName}".toUri()
    val intents =
        listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            Log.w(TAG, "openInstallPermissionSettings: ${e.message}")
        }
    }
}

// 调用系统安装器安装指定路径的安装包。返回是否成功发起安装。
internal fun installApk(filePath: String): Boolean =
    try {
        val context = ApplicationContext.instance
        val file = File(filePath)
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "installApk: ${e.message}")
        false
    }

internal fun apkTargetFile(): File {
    val context = ApplicationContext.instance
    val dir = context.getExternalFilesDir(null) ?: context.cacheDir
    return File(dir, "schedule-sync-update.apk")
}

// 下载安装包到本地临时文件，通过 [onProgress] 回调 0f..1f 的进度，返回文件路径。
internal suspend fun downloadApkToFile(
    url: String,
    onProgress: (Float) -> Unit,
): String =
    withContext(Dispatchers.IO) {
        val target = apkTargetFile()
        if (target.exists()) target.delete()
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            val total = connection.contentLengthLong
            val input = connection.inputStream
            val buffer = ByteArray(64 * 1024)
            var downloaded = 0L
            var lastPercent = -1
            target.outputStream().use { out ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            input.close()
        } finally {
            connection.disconnect()
        }
        if (!target.exists() || target.length() == 0L) {
            error("下载失败")
        }
        onProgress(1f)
        target.absolutePath
    }

// 删除已下载的安装包文件。
internal fun deleteDownloadedApk() {
    try {
        apkTargetFile().takeIf { it.exists() }?.delete()
    } catch (e: Exception) {
        Log.w(TAG, "deleteDownloadedApk: ${e.message}")
    }
}

// 若已下载的安装包与期望一致（优先比对 sha256，其次比对文件大小）则返回其路径，否则返回 null。
internal fun downloadedApkPathIfValid(
    sha256: String?,
    size: Long,
): String? {
    val file = apkTargetFile()
    if (!file.exists() || file.length() == 0L) return null
    if (!sha256.isNullOrBlank()) {
        val actual = file.sha256Hex()
        return if (actual != null && actual.equals(sha256, ignoreCase = true)) file.absolutePath else null
    }
    if (size > 0L) {
        return if (file.length() == size) file.absolutePath else null
    }
    return null
}

private fun File.sha256Hex(): String? =
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.w(TAG, "sha256Hex: ${e.message}")
        null
    }

private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 9001

// 等待权限弹窗结果（无论是否授权）以恢复挂起的调用。
@Volatile
private var notificationPermissionContinuation: CancellableContinuation<Unit>? = null

// 请求通知权限（Android 13+ 需要），挂起直到权限弹窗关闭（无论是否授权）。
internal suspend fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val activity = ActivityHolder.current ?: return
    if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    suspendCancellableCoroutine { continuation ->
        notificationPermissionContinuation?.let { if (it.isActive) it.resume(Unit) }
        notificationPermissionContinuation = continuation
        continuation.invokeOnCancellation { notificationPermissionContinuation = null }
        try {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE,
            )
        } catch (e: Exception) {
            Log.w(TAG, "requestNotificationPermission: ${e.message}")
            notificationPermissionContinuation = null
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
}

// 由 MainActivity.onRequestPermissionsResult 调用，唤醒等待中的请求。
fun onNotificationPermissionResult(requestCode: Int) {
    if (requestCode != NOTIFICATION_PERMISSION_REQUEST_CODE) return
    val continuation = notificationPermissionContinuation ?: return
    notificationPermissionContinuation = null
    if (continuation.isActive) continuation.resume(Unit)
}

// 在通知栏显示下载进度。
internal fun showUpdateProgressNotification(progress: Float) {
    val context = ApplicationContext.instance
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    ensureUpdateChannel(context)
    val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
    val contentIntent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
            launchIntent.action = IntentActions.ACTION_SHOW_UPDATE
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    val notification =
        NotificationCompat
            .Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 · $percent%")
            .setContentIntent(contentIntent)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    try {
        NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
    } catch (e: Exception) {
        Log.w(TAG, "showUpdateProgressNotification: ${e.message}")
    }
}

// 取消下载进度通知。
internal fun cancelUpdateProgressNotification() {
    try {
        NotificationManagerCompat
            .from(ApplicationContext.instance)
            .cancel(UPDATE_NOTIFICATION_ID)
    } catch (e: Exception) {
        Log.w(TAG, "cancelUpdateProgressNotification: ${e.message}")
    }
}
