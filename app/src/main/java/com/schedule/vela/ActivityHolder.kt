package com.schedule.vela

import android.app.Activity

object ActivityHolder {
    @Volatile
    var current: Activity? = null
}
