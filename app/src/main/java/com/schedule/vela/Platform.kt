package com.schedule.vela

import android.os.Build

// 是否支持预测性返回动画（Android 13+）
val supportsPredictiveBack: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// 是否支持基于系统壁纸的动态取色（Android 12+）
val supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
