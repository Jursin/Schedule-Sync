package com.schedule.vela

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.schedule.vela.ui.navigation.MainScaffold
import com.schedule.vela.ui.navigation.Page
import com.schedule.vela.ui.page.HomePage
import com.schedule.vela.ui.page.SettingsPage
import com.schedule.vela.ui.theme.AppTheme
import com.schedule.vela.update.onNotificationPermissionResult

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ApplicationContext.instance = applicationContext
        ActivityHolder.current = this
        AppStorage.instance = PersistentStorage(applicationContext)

        handleIntent(intent)

        setContent {
            AppTheme(
                themeMode = viewModel.themeMode,
                customColor = viewModel.customColor,
                dynamicColor = viewModel.dynamicColor,
                paletteStyle = viewModel.paletteStyle,
                seedColor = viewModel.seedColor,
            ) {
                MainScaffold(
                    floatingNav = viewModel.floatingNav,
                    appBlur = viewModel.appBlur,
                    predictiveBackEnabled = viewModel.predictiveBackEnabled,
                ) { page, bottomPadding, navigate ->
                    when (page) {
                        0 -> {
                            HomePage(
                                viewModel = viewModel,
                                appBlur = viewModel.appBlur,
                                bottomPadding = bottomPadding,
                            )
                        }

                        else -> {
                            SettingsPage(
                                viewModel = viewModel,
                                onLicenseClick = { navigate(Page.License) },
                                bottomPadding = bottomPadding,
                            )
                        }
                    }
                }
            }
        }
        viewModel.startDeviceQuery()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    override fun onDestroy() {
        if (ActivityHolder.current === this) {
            ActivityHolder.current = null
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onNotificationPermissionResult(requestCode)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            IntentActions.ACTION_SHOW_UPDATE -> viewModel.reopenUpdateProgressDialog()
        }
    }
}
