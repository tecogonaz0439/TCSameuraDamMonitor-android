// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import net.tecogonaz.tcsameuradammonitor.domain.model.AppTheme
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.ui.navigation.AppNavHost
import net.tecogonaz.tcsameuradammonitor.ui.navigation.AppRoutes
import net.tecogonaz.tcsameuradammonitor.ui.main.MainViewModel
import net.tecogonaz.tcsameuradammonitor.ui.settings.SettingsViewModel
import net.tecogonaz.tcsameuradammonitor.ui.theme.TCSameuraDamMonitorTheme
import net.tecogonaz.tcsameuradammonitor.worker.DamWorkManagerGateway
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import android.graphics.Color as AndroidColor
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var damWorkManagerGateway: DamWorkManagerGateway

    @Inject
    lateinit var appNotificationManager: net.tecogonaz.tcsameuradammonitor.util.AppNotificationManager

    
    private val navigationChannel = Channel<String>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchedFromAppPreferences = intent.action == Intent.ACTION_APPLICATION_PREFERENCES

        lifecycleScope.launch {
            val appSettings = viewModel.appSettings.filterNotNull().first()
            if (appSettings.autoUpdateEnabled) {
                val now = System.currentTimeMillis()
                val nextRunMillis = appSettings.calculateNextRunTime(now)
                damWorkManagerGateway.scheduleWorkAtTime(appSettings, nextRunMillis)
                settingsRepository.updateSettings { it.copy(nextScheduledUpdateMillis = nextRunMillis) }
            }
        }

        appNotificationManager.cancelBootNotification()

        setContent {
            val appSettings by viewModel.appSettings.collectAsState()
            val themeOption = appSettings?.theme ?: AppTheme.SYSTEM
            val useDarkTheme = when (themeOption) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            enableEdgeToEdge(
                statusBarStyle = if (useDarkTheme) {
                    SystemBarStyle.dark(scrim = AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(scrim = AndroidColor.TRANSPARENT, darkScrim = AndroidColor.TRANSPARENT)
                },
                navigationBarStyle = if (useDarkTheme) {
                    SystemBarStyle.dark(scrim = AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(scrim = AndroidColor.TRANSPARENT, darkScrim = AndroidColor.TRANSPARENT)
                }
            )

            val navController = rememberNavController()

            
            DisposableEffect(navController) {
                val job = lifecycleScope.launch {
                    navigationChannel.receiveAsFlow().collect { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    }
                }
                onDispose { job.cancel() }
            }

            TCSameuraDamMonitorTheme(darkTheme = useDarkTheme) {
                AppNavHost(
                    navController = navController,
                    mainViewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    startDestination = if (launchedFromAppPreferences) {
                        AppRoutes.Settings.route
                    } else {
                        AppRoutes.Main.route
                    },
                    finishExternalSettings = if (launchedFromAppPreferences) {
                        { finish() }
                    } else {
                        null
                    }
                )
            }
        }
    }

    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) {
            navigationChannel.trySend(AppRoutes.Settings.route)
        }
    }
}
