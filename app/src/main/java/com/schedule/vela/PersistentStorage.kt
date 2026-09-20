package com.schedule.vela

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PersistentStorage(
    context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("schedule_sync_prefs", Context.MODE_PRIVATE)

    fun saveString(
        key: String,
        value: String,
    ) {
        prefs.edit(commit = true) { putString(key, value) }
    }

    fun getString(key: String): String? = prefs.getString(key, null)

    fun saveBoolean(
        key: String,
        value: Boolean,
    ) {
        prefs.edit(commit = true) { putBoolean(key, value) }
    }

    fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = prefs.getBoolean(key, defaultValue)

    fun saveInt(
        key: String,
        value: Int,
    ) {
        prefs.edit(commit = true) { putInt(key, value) }
    }

    fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = prefs.getInt(key, defaultValue)
}

object StorageKeys {
    const val DYNAMIC_COLOR = "setting_dynamic_color"
    const val CUSTOM_COLOR = "setting_custom_color"
    const val PALETTE_STYLE = "setting_palette_style"
    const val SEED_COLOR = "setting_seed_color"
    const val FLOATING_NAV = "setting_floating_nav"
    const val APP_BLUR = "setting_app_blur"
    const val PREDICTIVE_BACK = "setting_predictive_back"
    const val THEME_MODE = "setting_theme_mode"
    const val GITHUB_PROXY = "setting_github_proxy"
    const val CHECK_UPDATE_ON_START = "setting_check_update_on_start"
}

object AppStorage {
    lateinit var instance: PersistentStorage
}
