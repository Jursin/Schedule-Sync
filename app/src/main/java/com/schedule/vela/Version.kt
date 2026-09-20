package com.schedule.vela

import android.os.Build

fun getAppVersion(): String =
    try {
        val packageInfo =
            ApplicationContext.instance.packageManager.getPackageInfo(ApplicationContext.instance.packageName, 0)
        packageInfo.versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

fun getAppVersionCode(): String =
    try {
        val packageInfo =
            ApplicationContext.instance.packageManager.getPackageInfo(ApplicationContext.instance.packageName, 0)
        val code =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        code.toString()
    } catch (_: Exception) {
        "unknown"
    }
