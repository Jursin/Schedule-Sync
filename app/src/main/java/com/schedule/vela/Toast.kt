package com.schedule.vela

import android.widget.Toast

private var currentToast: Toast? = null

fun showToast(message: String) {
    val context = ApplicationContext.instance
    currentToast?.cancel()
    currentToast = Toast.makeText(context, message, Toast.LENGTH_SHORT).apply { show() }
}

fun dismissToast() {
    currentToast?.cancel()
    currentToast = null
}
