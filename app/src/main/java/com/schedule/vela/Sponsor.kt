package com.schedule.vela

import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

// 赞助支持链接（设置页同名入口）
const val SPONSOR_URL = "https://afdian.com/@Jursin"

// 打开赞助页面，返回是否成功发起跳转（用于区分“确实跳出了外部应用”与“弹窗被取消”）
fun openSponsorPage(): Boolean =
    try {
        val intent =
            Intent(Intent.ACTION_VIEW, SPONSOR_URL.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ApplicationContext.instance.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w("ScheduleSync", "openSponsorPage: ${e.message}")
        false
    }
