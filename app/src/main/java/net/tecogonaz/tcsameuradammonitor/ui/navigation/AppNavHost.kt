// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoLicenseScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoOssLicenseScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoPrivacyPolicyJaScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoPrivacyPolicyScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoTermsOfUseJaScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoTermsOfUseScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AboutScreen
import net.tecogonaz.tcsameuradammonitor.ui.historicalmanage.HistoricalManageScreen
import net.tecogonaz.tcsameuradammonitor.ui.debuglog.DebugLogScreen
import net.tecogonaz.tcsameuradammonitor.ui.debuglog.DebugLogViewModel
import net.tecogonaz.tcsameuradammonitor.ui.main.AppLinkInfo
import net.tecogonaz.tcsameuradammonitor.ui.main.openUrl
import net.tecogonaz.tcsameuradammonitor.ui.main.openUrlWith
import net.tecogonaz.tcsameuradammonitor.ui.main.MainScreen
import net.tecogonaz.tcsameuradammonitor.ui.main.MainViewModel
import net.tecogonaz.tcsameuradammonitor.ui.main.ObservationHistoryListScreen
import net.tecogonaz.tcsameuradammonitor.ui.settings.DebugScreen
import net.tecogonaz.tcsameuradammonitor.ui.settings.SettingsScreen
import net.tecogonaz.tcsameuradammonitor.ui.settings.SettingsViewModel


/**
 * アプリケーション内の遷移先画面（ルート）を定義する密封（sealed）クラスです。
 *
 * 各オブジェクトは Navigation コンポーネントで使用される一意のルート文字列を保持します。
 *
 * @property route 画面遷移のルートを識別するための文字列。
 */
sealed class AppRoutes(val route: String) {
    
    data object Main : AppRoutes("main")

    
    data object Settings : AppRoutes("settings")

    
    data object Debug : AppRoutes("debug")

    data object HistoricalManage : AppRoutes("historical_manage")

    
    data object ObservationHistoryList : AppRoutes("observation_history_list")

    
    data object DebugLog : AppRoutes("debug_log")

    
    data object AppInfo : AppRoutes("app_info")

    
    data object AppInfoLicense : AppRoutes("app_info_license")

    
    data object AppInfoTermsOfUse : AppRoutes("app_info_terms_of_use")

    
    data object AppInfoTermsOfUseJa : AppRoutes("app_info_terms_of_use_ja")

    
    data object AppInfoPrivacyPolicy : AppRoutes("app_info_privacy_policy")

    
    data object AppInfoPrivacyPolicyJa : AppRoutes("app_info_privacy_policy_ja")

    
    data object AppInfoOssLicense : AppRoutes("app_info_oss_license")
}


/**
 * アプリケーション全体の画面遷移（ナビゲーション）を定義・制御するメインの NavHost Composable関数です。
 *
 * メイン画面（[MainScreen]）、設定画面（[SettingsScreen]）、過去データ管理（[HistoricalManageScreen]）、
 * デバッグ設定（[DebugScreen]）、ログ画面、および「このアプリについて」などの詳細情報画面間の遷移を管理します。
 *
 * 画面の二重遷移防止や、戻るボタン押下時の冪等なポップバック処理（現在の目的地が戻り先と同じである場合は無視する設計）を含みます。
 *
 * @param navController 遷移制御を行う [NavHostController]。
 * @param mainViewModel メイン画面用の [MainViewModel]。
 * @param settingsViewModel 設定画面用の [SettingsViewModel]。
 * @param modifier このコンポーネントに適用する [Modifier]。
 * @param startDestination 開始画面のルート文字列。デフォルトは [AppRoutes.Main.route]。
 * @param finishExternalSettings 外部（システム設定など）から直接設定画面が開かれた場合に、処理を終了して戻るためのコールバック。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    startDestination: String = AppRoutes.Main.route,
    finishExternalSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.appSettings.collectAsStateWithLifecycle()
    val shareTextLabel = stringResource(R.string.action_share_text)
    val onOpenUrl: (String) -> Unit = { url ->
        openUrl(context, url) { mainViewModel.emitSnackbarMessage(it) }
    }
    val onOpenUrlWith: (String, AppLinkInfo) -> Unit = { url, appInfo ->
        openUrlWith(context, url, appInfo) { mainViewModel.emitSnackbarMessage(it) }
    }
    val onShareText: (String) -> Unit = { text ->
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, shareTextLabel))
    }
    val onCopied: (String) -> Unit = { mainViewModel.emitSnackbarMessage(it) }
    val onQueryApps: (String) -> Unit = { mainViewModel.queryAvailableApps(it) }
    val onClearApps: () -> Unit = { mainViewModel.clearAvailableApps() }
    val popBackTo: (AppRoutes) -> Unit = { route ->
        if (navController.currentDestination?.route != route.route) {
            navController.popBackStack(route.route, inclusive = false)
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(AppRoutes.Main.route) {
            MainScreen(
                viewModel = mainViewModel,
                settingsViewModel = settingsViewModel,
                onMenuSettingsClick = {
                    navController.navigate(AppRoutes.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onDebugClick = {
                    navController.navigate(AppRoutes.Debug.route) {
                        launchSingleTop = true
                    }
                },
                onManageHistoricalClick = {
                    navController.navigate(AppRoutes.HistoricalManage.route) {
                        launchSingleTop = true
                    }
                },
                onAppInfoClick = {
                    navController.navigate(AppRoutes.AppInfo.route) {
                        launchSingleTop = true
                    }
                },
                onShowObservationHistoryList = {
                    navController.navigate(AppRoutes.ObservationHistoryList.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(AppRoutes.ObservationHistoryList.route) {
            ObservationHistoryListScreen(
                viewModel = mainViewModel,
                onBackClick = { popBackTo(AppRoutes.Main) }
            )
        }

        composable(AppRoutes.HistoricalManage.route) {
            HistoricalManageScreen(
                viewModel = mainViewModel,
                onBackClick = { popBackTo(AppRoutes.Main) },
                onShowHistoricalData = { metaId ->
                    mainViewModel.switchToHistoricalMode(metaId)
                    popBackTo(AppRoutes.Main)
                }
            )
        }

        composable(AppRoutes.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = {
                    if (finishExternalSettings != null && startDestination == AppRoutes.Settings.route) {
                        finishExternalSettings()
                    } else {
                        popBackTo(AppRoutes.Main)
                    }
                }
            )
        }

        composable(AppRoutes.Debug.route) {
            DebugScreen(
                viewModel = settingsViewModel,
                onBackClick = { popBackTo(AppRoutes.Main) },
                onDebugLogClick = {
                    navController.navigate(AppRoutes.DebugLog.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(AppRoutes.DebugLog.route) {
            val debugLogViewModel: DebugLogViewModel = hiltViewModel()
            DebugLogScreen(
                viewModel = debugLogViewModel,
                onBackClick = { popBackTo(AppRoutes.Debug) }
            )
        }

        composable(AppRoutes.AppInfo.route) {
            AboutScreen(
                availableApps = uiState.availableApps,
                debugSettingsVisible = settings?.debugSettingsVisible ?: false,
                onOpenUrl = onOpenUrl,
                onOpenUrlWith = onOpenUrlWith,
                onShareText = onShareText,
                onCopied = onCopied,
                onQueryApps = onQueryApps,
                onClearApps = onClearApps,
                onBackClick = { popBackTo(AppRoutes.Main) },
                onTermsOfUseClick = {
                    navController.navigate(AppRoutes.AppInfoTermsOfUse.route) {
                        launchSingleTop = true
                    }
                },
                onTermsOfUseJaClick = {
                    navController.navigate(AppRoutes.AppInfoTermsOfUseJa.route) {
                        launchSingleTop = true
                    }
                },
                onLicenseClick = {
                    navController.navigate(AppRoutes.AppInfoLicense.route) {
                        launchSingleTop = true
                    }
                },
                onPrivacyPolicyClick = {
                    navController.navigate(AppRoutes.AppInfoPrivacyPolicy.route) {
                        launchSingleTop = true
                    }
                },
                onPrivacyPolicyJaClick = {
                    navController.navigate(AppRoutes.AppInfoPrivacyPolicyJa.route) {
                        launchSingleTop = true
                    }
                },
                onOssLicenseClick = {
                    navController.navigate(AppRoutes.AppInfoOssLicense.route) {
                        launchSingleTop = true
                    }
                },
                onToggleDebugSettingsVisibility = { settingsViewModel.toggleDebugSettingsVisibility() }
            )
        }

        composable(AppRoutes.AppInfoLicense.route) {
            AppInfoLicenseScreen(
                availableApps = uiState.availableApps,
                onOpenUrl = onOpenUrl,
                onOpenUrlWith = onOpenUrlWith,
                onShareText = onShareText,
                onCopied = onCopied,
                onQueryApps = onQueryApps,
                onClearApps = onClearApps,
                onBackClick = { popBackTo(AppRoutes.AppInfo) }
            )
        }

        composable(AppRoutes.AppInfoPrivacyPolicy.route) {
            AppInfoPrivacyPolicyScreen(
                onOpenUrl = onOpenUrl,
                onBackClick = { popBackTo(AppRoutes.AppInfo) }
            )
        }

        composable(AppRoutes.AppInfoPrivacyPolicyJa.route) {
            AppInfoPrivacyPolicyJaScreen(
                onOpenUrl = onOpenUrl,
                onBackClick = { popBackTo(AppRoutes.AppInfo) }
            )
        }

        composable(AppRoutes.AppInfoTermsOfUse.route) {
            AppInfoTermsOfUseScreen(
                onOpenUrl = onOpenUrl,
                onBackClick = { popBackTo(AppRoutes.AppInfo) }
            )
        }

        composable(AppRoutes.AppInfoTermsOfUseJa.route) {
            AppInfoTermsOfUseJaScreen(
                onOpenUrl = onOpenUrl,
                onBackClick = { popBackTo(AppRoutes.AppInfo) }
            )
        }

        composable(AppRoutes.AppInfoOssLicense.route) {
            AppInfoOssLicenseScreen(
                availableApps = uiState.availableApps,
                onOpenUrl = onOpenUrl,
                onOpenUrlWith = onOpenUrlWith,
                onShareText = onShareText,
                onCopied = onCopied,
                onQueryApps = onQueryApps,
                onClearApps = onClearApps,
                onBackClick = { popBackTo(AppRoutes.AppInfo) }
            )
        }
    }
}
