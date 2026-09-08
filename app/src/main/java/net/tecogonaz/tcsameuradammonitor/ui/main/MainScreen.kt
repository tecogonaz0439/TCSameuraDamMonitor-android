// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterListOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionKey
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import net.tecogonaz.tcsameuradammonitor.ui.theme.trendAccentColor
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import net.tecogonaz.tcsameuradammonitor.widget.WidgetDisplayFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.tecogonaz.tcsameuradammonitor.domain.model.isAllObservationDataInvalid
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.ui.about.AboutScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoLicenseScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoOssLicenseScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoPrivacyPolicyJaScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoPrivacyPolicyScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoTermsOfUseJaScreen
import net.tecogonaz.tcsameuradammonitor.ui.about.AppInfoTermsOfUseScreen
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_PERCENTAGE_TEXT
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_TEXT
import net.tecogonaz.tcsameuradammonitor.ui.common.mouseDragScroll
import net.tecogonaz.tcsameuradammonitor.ui.debuglog.DebugLogScreen
import net.tecogonaz.tcsameuradammonitor.ui.debuglog.DebugLogViewModel
import net.tecogonaz.tcsameuradammonitor.ui.historicalmanage.HistoricalManageScreen
import net.tecogonaz.tcsameuradammonitor.ui.settings.DebugScreen
import net.tecogonaz.tcsameuradammonitor.ui.settings.SettingsScreen
import net.tecogonaz.tcsameuradammonitor.ui.settings.SettingsViewModel
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils


internal enum class NavigationLayout { DRAWER, PERMANENT }


internal enum class ContentPage {
    MAIN,
    SETTINGS,
    DEBUG,
    DEBUG_LOG,
    HISTORICAL_MANAGE,
    OBSERVATION_HISTORY_LIST,
    APP_INFO,
    APP_INFO_LICENSE,
    APP_INFO_TERMS_OF_USE,
    APP_INFO_TERMS_OF_USE_JA,
    APP_INFO_PRIVACY_POLICY,
    APP_INFO_PRIVACY_POLICY_JA,
    APP_INFO_OSS_LICENSE
}

/**
 * リアルタイムデータ、履歴データ、水位・放流量等の推移グラフ、出典リンク、手動更新操作などの
 * アプリの主要コンテンツをグリッド/カード形式で一覧表示する、本アプリケーションの中心となる画面のComposable関数です。
 *
 * デバイスの画面幅に応じて `ModalNavigationDrawer`（スマートフォン幅）または折りたたみ可能な
 * `PermanentDrawerSheet` サイドバー（タブレット/PC幅、840dp以上）を動的に切り替えるレスポンシブデザインを提供します。
 * 広い画面のサイドバーは開閉でき、開くとアプリタイトル右に閉じるアイコン、閉じると画面タイトル左に開くアイコンが表示されます。
 * タッチ操作、マウスホイール、マウス左ボタンドラッグによる慣性スクロールに対応しています。
 * また、端末のタイムゾーン変更を動的レシーバーで検知して日時表示を動的に更新します。
 *
 * @param viewModel メイン画面の状態とデータ処理を管理する [MainViewModel]。
 * @param settingsViewModel 設定変更処理を管理する [SettingsViewModel]（ドロワー設定の同期に使用します）。
 * @param onMenuSettingsClick ドロワーの「設定」をクリックしたときの画面遷移コールバック。
 * @param onDebugClick ドロワーの「デバッグ」をクリックしたときの画面遷移コールバック（デバッグ表示が有効な場合のみ表示）。
 * @param onManageHistoricalClick ドロワーの「過去データ管理」をクリックしたときの画面遷移コールバック。
 * @param onAppInfoClick ドロワーの「このアプリについて」をクリックしたときの画面遷移コールバック。
 * @param onShowObservationHistoryList 「観測データ一覧（テーブル）」を全画面表示する画面へ遷移するためのコールバック。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel? = null,
    onMenuSettingsClick: () -> Unit,
    onDebugClick: () -> Unit = {},
    onManageHistoricalClick: () -> Unit,
    onAppInfoClick: () -> Unit,
    onShowObservationHistoryList: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val mainCardExpansionState by viewModel.mainCardExpansionState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val appLocale = LocaleUtils.effectiveLocale(context)
    val appLocaleTag = appLocale.toLanguageTag()
    val isJapaneseLocale = appLocale.language == Locale.JAPANESE.language
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    
    var localTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    
    
    
    val cardResetKey = "${uiState.isHistoricalMode}_${uiState.isSudmonitorHistoryMode}_${uiState.historicalDamMeta?.id}_${uiState.damConfig?.id}_" +
            "${uiState.isHistoricalDisplayRangeFiltered}_${uiState.historicalDisplayStartDate}_${uiState.historicalDisplayEndDate}_" +
            "${uiState.isSudmonitorHistoryRangeFiltered}_${uiState.sudmonitorHistoryDisplayStartDate}_${uiState.sudmonitorHistoryDisplayEndDate}"
    var showHistoricalSearchDialog by remember { mutableStateOf(false) }
    
    var showHistoricalRangeDialog by remember { mutableStateOf(false) }
    
    var showSudmonitorHistoryRangeDialog by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(10000) 
                localTimeMillis = System.currentTimeMillis()
            }
        }
    }

    
    var currentTimeZoneId by remember { mutableStateOf(TimeZone.getDefault().id) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    currentTimeZoneId = TimeZone.getDefault().id
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    var showWebMenu by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val navigationLayout = rememberNavigationLayout()
    
    var contentPage by remember { mutableStateOf(ContentPage.MAIN) }
    
    var sidebarExpanded by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(navigationLayout) {
        if (navigationLayout != NavigationLayout.PERMANENT) {
            contentPage = ContentPage.MAIN
        }
    }
    
    val lastFetchDateStr = remember(uiState.damData?.updatedAt, appSettings?.debugModeEnabled, viewModel.lastFetchTimeMillis) {
        val debugDataMillis = if (appSettings?.debugModeEnabled == true) {
            uiState.damData?.updatedAt?.let {
                TimeUtils.parseJstMillisAllow24Hour(it, "yyyy/MM/dd HH:mm")
            }
        } else {
            null
        }
        val millis = debugDataMillis ?: viewModel.lastFetchTimeMillis
        if (millis > 0L) {
            val cal = TimeUtils.calendarInJst(millis)
            String.format(
                Locale.US, "%04d/%02d/%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        } else ""
    }

    
    val autoUpdateEnabled = appSettings?.autoUpdateEnabled == true
    val isAutoStyleRunning = uiState.isAutoUpdateRunning || uiState.isInitialLoadRunning || uiState.isBootUpdateRunning
    val infiniteTransitionAuto = rememberInfiniteTransition(label = "autorenew")
    val autoRotation by infiniteTransitionAuto.animateFloat(
        initialValue = 0f,
        targetValue = if (isAutoStyleRunning) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "autorenewRotation"
    )

    
    val isOneTimeRunning = uiState.isManualUpdateRunning
    val canRefreshForTint = viewModel.canRefresh(localTimeMillis)
    val infiniteTransitionRefresh = rememberInfiniteTransition(label = "refresh")
    val refreshRotation by infiniteTransitionRefresh.animateFloat(
        initialValue = 0f,
        targetValue = if (isOneTimeRunning) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refreshRotation"
    )

    val autoUpdateInProgressMsg = stringResource(R.string.main_autorenew_in_progress)
    val manualUpdateInProgressMsg = stringResource(R.string.main_manual_update_in_progress)
    val refreshTooEarlyMsgTemplate = stringResource(R.string.main_refresh_too_early)

    
    val isSudmonitorHistoryUpdateRunning = uiState.isSudmonitorHistoryUpdateRunning
    val infiniteTransitionSudmonitorHistory = rememberInfiniteTransition(label = "sudmonitorHistory")
    val sudmonitorHistoryRotation by infiniteTransitionSudmonitorHistory.animateFloat(
        initialValue = 0f,
        targetValue = if (uiState.isSudmonitorHistoryManualRunning) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sudmonitorHistoryRotation"
    )

    // 過去データ(日次)の自動更新アイコンは、定期自動更新ワーク（DamAutoUpdateWork）と
    // 日次の初回読込（初回起動 INITIAL / ダム変更 TARGET_CHANGE）の実行中のみ回転させる
    // （リアルタイム側の isAutoStyleRunning = 自動更新 + 初回読込 + ブート と同等の扱い）。
    // 手動更新（MANUAL）は手動更新アイコンの回転（isSudmonitorHistoryManualRunning）へ分離する
    val isSudmonitorHistoryAutoStyleRunning =
        uiState.isAutoUpdateRunning || uiState.isSudmonitorHistoryInitialRunning
    val infiniteTransitionDailyAuto = rememberInfiniteTransition(label = "dailyAuto")
    val dailyAutoRotation by infiniteTransitionDailyAuto.animateFloat(
        initialValue = 0f,
        targetValue = if (isSudmonitorHistoryAutoStyleRunning) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dailyAutoRotation"
    )

    
    var sudmonitorHistoryCanRefresh by remember { mutableStateOf(true) }
    LaunchedEffect(
        uiState.sudmonitorHistory?.nextUpdateAtEpochMs,
        uiState.sudmonitorHistory?.damId,
        isSudmonitorHistoryUpdateRunning,
        localTimeMillis
    ) {
        val damId = uiState.sudmonitorHistory?.damId
        if (damId == null) {
            sudmonitorHistoryCanRefresh = true
            return@LaunchedEffect
        }
        val settings = viewModel.appSettings.value
        if (settings?.debugModeEnabled == true) {
            sudmonitorHistoryCanRefresh = true
            return@LaunchedEffect
        }
        val availableAt = viewModel.manualRefreshAvailableAt(damId)
        sudmonitorHistoryCanRefresh = availableAt == null || localTimeMillis >= availableAt
    }
    val sudmonitorHistoryRefreshTint = when {
        isSudmonitorHistoryUpdateRunning -> LocalContentColor.current.copy(alpha = 0.38f)
        !sudmonitorHistoryCanRefresh -> LocalContentColor.current.copy(alpha = 0.38f)
        else -> LocalContentColor.current
    }
    val sudmonitorHistoryAutoTint = if (autoUpdateEnabled) {
        LocalContentColor.current
    } else {
        LocalContentColor.current.copy(alpha = 0.38f)
    }

    
    val onSudmonitorHistoryAutoUpdateIconClick: () -> Unit = {
        coroutineScope.launch {
            viewModel.showSudmonitorHistoryAutoUpdateStatus()
        }
    }

    
    suspend fun isSudmonitorHistoryRefreshAvailable(): Boolean {
        val damId = uiState.sudmonitorHistory?.damId ?: return true
        val settings = viewModel.appSettings.value
        if (settings?.debugModeEnabled == true) return true
        val availableAt = viewModel.manualRefreshAvailableAt(damId) ?: return true
        return System.currentTimeMillis() >= availableAt
    }

    
    val onSudmonitorHistoryManualUpdateIconClick: () -> Unit = {
        coroutineScope.launch {
            val history = uiState.sudmonitorHistory
            when {
                isSudmonitorHistoryAutoStyleRunning -> {
                    snackbarHostState.showSnackbar(autoUpdateInProgressMsg)
                }
                uiState.isSudmonitorHistoryManualRunning -> {
                    snackbarHostState.showSnackbar(manualUpdateInProgressMsg)
                }
                !isSudmonitorHistoryRefreshAvailable() -> {
                    val nextUpdateAt = history?.nextUpdateAtEpochMs
                    if (nextUpdateAt != null) {
                        val timeDisplay = TimeUtils.appendJstSuffix(
                            TimeUtils.formatToJst(nextUpdateAt, "yyyy/MM/dd HH:mm")
                        )
                        snackbarHostState.showSnackbar(
                            String.format(refreshTooEarlyMsgTemplate, timeDisplay)
                        )
                    }
                }
                else -> {
                    viewModel.fetchSudmonitorHistoryData(isManual = true)
                }
            }
        }
    }

    
    val onAutoUpdateIconClick: () -> Unit = {
        coroutineScope.launch {
            if (uiState.isAutoUpdateRunning) {
                snackbarHostState.showSnackbar(autoUpdateInProgressMsg)
            } else {
                viewModel.showAutoUpdateStatus()
            }
        }
    }

    
    val onManualUpdateIconClick: () -> Unit = {
        coroutineScope.launch {
            when {
                isAutoStyleRunning -> {
                    snackbarHostState.showSnackbar(autoUpdateInProgressMsg)
                }
                uiState.isManualUpdateRunning -> {
                    snackbarHostState.showSnackbar(manualUpdateInProgressMsg)
                }
                !viewModel.canRefresh() -> {
                    val availableAt = viewModel.refreshAvailableAtMillis
                    val timeDisplay = if (availableAt != null) {
                        TimeUtils.appendJstSuffix(
                            TimeUtils.formatToJst(availableAt, "yyyy/MM/dd HH:mm")
                        )
                    } else {
                        val lastFetchTime = viewModel.lastFetchTimeMillis
                        val calendar = TimeUtils.calendarInJst(lastFetchTime).apply {
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        calendar.add(Calendar.MINUTE, MainViewModel.REFRESH_COOLDOWN_MINUTES)
                        TimeUtils.appendJstSuffix(
                            TimeUtils.formatToJst(calendar.timeInMillis, "yyyy/MM/dd HH:mm")
                        )
                    }
                    snackbarHostState.showSnackbar(
                        String.format(refreshTooEarlyMsgTemplate, timeDisplay)
                    )
                }
                else -> {
                    viewModel.fetchData(isManual = true)
                }
            }
        }
    }

    val onHistoricalSearchRequest: () -> Unit = {
        coroutineScope.launch {
            if (viewModel.canOpenHistoricalSearchDialog()) {
                showHistoricalSearchDialog = true
            } else {
                val maxCount = MainViewModel.HISTORICAL_SEARCH_MAX_STORED_COUNT
                snackbarHostState.showSnackbar(
                    context.getString(R.string.historical_search_max_count_snackbar, maxCount)
                )
            }
        }
    }
    val closeDrawerThen: (() -> Unit) -> Unit = { action ->
        coroutineScope.launch {
            if (!drawerState.isClosed) {
                drawerState.close()
            }
            action()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.updateShowNotification(isGranted)
        viewModel.markInitialNotificationPermissionRequested()
    }

    fun requestNotificationPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.markInitialNotificationPermissionRequested()
        }
    }

    
    LaunchedEffect(uiState.showInitialNotificationPermissionRequest) {
        if (uiState.showInitialNotificationPermissionRequest) {
            requestNotificationPermissionIfNeeded()
        }
    }

    InitialAutoUpdateDialog(
        show = uiState.showInitialAutoUpdateDialog,
        onDismiss = { viewModel.markInitialAutoUpdateDialogShown() },
        onConfirm = {
            viewModel.enableAutoUpdateFromInitialDialog()
            viewModel.markInitialAutoUpdateDialogShown()
        }
    )

    
    val searchDamConfig = uiState.damConfig
    if (showHistoricalSearchDialog && searchDamConfig != null) {
        HistoricalDataSearchDialog(
            initialDamConfig = searchDamConfig,
            isLoading = uiState.isHistoricalSearchLoading,
            errorMessage = uiState.historicalErrorMessage,
            onSearch = { selectedDam, startDate, endDate ->
                viewModel.fetchHistoricalData(selectedDam, startDate, endDate)
                if (navigationLayout == NavigationLayout.PERMANENT) {
                    contentPage = ContentPage.MAIN
                }
            },
            onDismiss = {
                if (!uiState.isHistoricalSearchLoading) {
                    showHistoricalSearchDialog = false
                }
            },
            onCheckDuplicate = { damId, start, end ->
                viewModel.checkDuplicate(damId, start, end)
            }
        )
    }

    val rangeDamConfig = uiState.historicalDamConfig
    val rangeMeta = uiState.historicalDamMeta
    if (showHistoricalRangeDialog && uiState.isHistoricalMode && rangeDamConfig != null && rangeMeta != null) {
        HistoricalDataRangeDialog(
            damConfig = rangeDamConfig,
            meta = rangeMeta,
            initialStartDate = uiState.historicalDisplayStartDate ?: rangeMeta.searchBgnDate,
            initialEndDate = uiState.historicalDisplayEndDate ?: rangeMeta.searchEndDate,
            onDisplay = { startDate, endDate ->
                viewModel.applyHistoricalDisplayRange(startDate, endDate)
                showHistoricalRangeDialog = false
            },
            onDismiss = { showHistoricalRangeDialog = false }
        )
    }

    
    val dailyRangeConfig = uiState.sudmonitorHistoryDamConfig
    val dailyHistory = uiState.sudmonitorHistory
    if (showSudmonitorHistoryRangeDialog && uiState.isSudmonitorHistoryMode &&
        dailyRangeConfig != null && dailyHistory != null
    ) {
        val periodStart = dailyHistory.periodStartEpochMs ?: 0L
        val periodEndExclusive = (dailyHistory.periodEndEpochMs ?: 0L) + MILLIS_PER_DAY
        if (periodStart > 0L && periodEndExclusive > periodStart) {
            SudmonitorHistoryRangeDialog(
                damConfig = dailyRangeConfig,
                initialStartDate = uiState.sudmonitorHistoryDisplayStartDate
                    ?: TimeUtils.formatToJst(periodStart, "yyyy/MM/dd"),
                initialEndDate = uiState.sudmonitorHistoryDisplayEndDate
                    ?: TimeUtils.formatToJst(periodEndExclusive - MILLIS_PER_DAY, "yyyy/MM/dd"),
                periodStartMillis = periodStart,
                periodEndMillis = periodEndExclusive,
                onDisplay = { fromMillis, toMillis ->
                    viewModel.applySudmonitorHistoryDisplayRange(fromMillis, toMillis)
                    showSudmonitorHistoryRangeDialog = false
                },
                onDismiss = { showSudmonitorHistoryRangeDialog = false }
            )
        }
    }

    LaunchedEffect(uiState.isHistoricalMode, uiState.historicalDamMeta?.id) {
        if (!uiState.isHistoricalMode) {
            showHistoricalRangeDialog = false
        }
    }

    LaunchedEffect(uiState.isSudmonitorHistoryMode) {
        if (!uiState.isSudmonitorHistoryMode) {
            showSudmonitorHistoryRangeDialog = false
        }
    }

    
    
    
    LaunchedEffect(uiState.isHistoricalMode, uiState.isHistoricalSearchLoading) {
        if (uiState.isHistoricalMode && !uiState.isHistoricalSearchLoading && uiState.historicalErrorMessage == null) {
            showHistoricalSearchDialog = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val view = LocalView.current
    val updateCompleteMsg = stringResource(R.string.desc_update_complete)

    LaunchedEffect(uiState.damLoadStatus) {
        when (uiState.damLoadStatus) {
            DamLoadStatus.SUCCESS -> {
                view.announceForAccessibility(updateCompleteMsg)
            }
            else -> {  }
        }
    }

    
    val mainTitleRes = when {
        uiState.isSudmonitorHistoryMode -> R.string.main_title_daily_history_data
        uiState.isHistoricalMode -> R.string.main_title_historical_data
        else -> R.string.main_title_realtime_data
    }

    
    val isPastDataMode = uiState.isHistoricalMode || uiState.isSudmonitorHistoryMode

    
    
    val scaffoldComposable: @Composable () -> Unit = {
        Scaffold(
            snackbarHost = {
                if (navigationLayout != NavigationLayout.PERMANENT && navigationLayout != NavigationLayout.DRAWER) {
                    SnackbarHost(snackbarHostState)
                }
            },
            topBar = {
                if (navigationLayout == NavigationLayout.PERMANENT) {
                    TopAppBar(
                        title = {
                            AutoResizingSingleLineText(
                                text = stringResource(mainTitleRes),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            if (!sidebarExpanded) {
                                IconButton(
                                    onClick = { sidebarExpanded = true },
                                    modifier = Modifier.testTag(TestTags.SIDEBAR_EXPAND_BUTTON)
                                ) {
                                    Icon(
                                        Icons.Outlined.Menu,
                                        contentDescription = stringResource(R.string.desc_expand_sidebar)
                                    )
                                }
                            }
                        },
                        actions = {
                            if (uiState.isSudmonitorHistoryMode) {
                                HistoricalRangeActionIcons(
                                    isFiltered = uiState.isSudmonitorHistoryRangeFiltered,
                                    onResetRange = { viewModel.resetSudmonitorHistoryDisplayRange() },
                                    onOpenRangeDialog = { showSudmonitorHistoryRangeDialog = true }
                                )
                                IconButton(
                                    onClick = onSudmonitorHistoryAutoUpdateIconClick,
                                    modifier = Modifier.testTag(TestTags.AUTO_UPDATE_BUTTON)
                                ) {
                                    Icon(
                                        Icons.Outlined.Autorenew,
                                        contentDescription = stringResource(R.string.nav_auto_update),
                                        tint = sudmonitorHistoryAutoTint,
                                        modifier = if (isSudmonitorHistoryAutoStyleRunning) {
                                            Modifier.rotate(dailyAutoRotation)
                                        } else {
                                            Modifier
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = onSudmonitorHistoryManualUpdateIconClick,
                                    modifier = Modifier.testTag(TestTags.MANUAL_UPDATE_BUTTON)
                                ) {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        contentDescription = stringResource(id = R.string.desc_reload),
                                        tint = sudmonitorHistoryRefreshTint,
                                        modifier = if (uiState.isSudmonitorHistoryManualRunning) {
                                            Modifier.rotate(sudmonitorHistoryRotation)
                                        } else {
                                            Modifier
                                        }
                                    )
                                }
                            } else if (uiState.isHistoricalMode) {
                                HistoricalRangeActionIcons(
                                    isFiltered = uiState.isHistoricalDisplayRangeFiltered,
                                    onResetRange = { viewModel.resetHistoricalDisplayRange() },
                                    onOpenRangeDialog = { showHistoricalRangeDialog = true }
                                )
                            } else {
                                val autoUpdateDesc = if (autoUpdateEnabled) {
                                    stringResource(R.string.desc_auto_update_enabled)
                                } else {
                                    stringResource(R.string.desc_auto_update_disabled)
                                }
                                IconButton(
                                    onClick = onAutoUpdateIconClick,
                                    modifier = Modifier
                                        .testTag(TestTags.AUTO_UPDATE_BUTTON)
                                        .semantics {
                                            stateDescription = autoUpdateDesc
                                        }
                                ) {
                                    Icon(
                                        Icons.Outlined.Autorenew,
                                        contentDescription = stringResource(R.string.nav_auto_update),
                                        tint = if (autoUpdateEnabled) LocalContentColor.current
                                        else LocalContentColor.current.copy(alpha = 0.38f),
                                        modifier = if (isAutoStyleRunning) Modifier.rotate(autoRotation) else Modifier
                                    )
                                }
                                val refreshTint = when {
                                    isAutoStyleRunning -> LocalContentColor.current.copy(alpha = 0.38f)
                                    !canRefreshForTint && !isOneTimeRunning -> LocalContentColor.current.copy(alpha = 0.38f)
                                    else -> LocalContentColor.current
                                }
                                IconButton(
                                    onClick = onManualUpdateIconClick,
                                    modifier = Modifier.testTag(TestTags.MANUAL_UPDATE_BUTTON)
                                ) {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        contentDescription = stringResource(id = R.string.desc_reload),
                                        tint = refreshTint,
                                        modifier = if (isOneTimeRunning) Modifier.rotate(refreshRotation) else Modifier
                                    )
                                }
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            AutoResizingSingleLineText(
                                text = stringResource(mainTitleRes),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (drawerState.isClosed) {
                                            drawerState.open()
                                        }
                                    }
                                },
                                enabled = drawerState.isClosed
                            ) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = stringResource(id = R.string.desc_menu)
                                )
                            }
                        },
                        actions = {
                            if (uiState.isSudmonitorHistoryMode) {
                                HistoricalRangeActionIcons(
                                    isFiltered = uiState.isSudmonitorHistoryRangeFiltered,
                                    onResetRange = { viewModel.resetSudmonitorHistoryDisplayRange() },
                                    onOpenRangeDialog = { showSudmonitorHistoryRangeDialog = true }
                                )
                                IconButton(
                                    onClick = onSudmonitorHistoryAutoUpdateIconClick,
                                    modifier = Modifier.testTag(TestTags.AUTO_UPDATE_BUTTON)
                                ) {
                                    Icon(
                                        Icons.Outlined.Autorenew,
                                        contentDescription = stringResource(R.string.nav_auto_update),
                                        tint = sudmonitorHistoryAutoTint,
                                        modifier = if (isSudmonitorHistoryAutoStyleRunning) {
                                            Modifier.rotate(dailyAutoRotation)
                                        } else {
                                            Modifier
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = onSudmonitorHistoryManualUpdateIconClick,
                                    modifier = Modifier.testTag(TestTags.MANUAL_UPDATE_BUTTON)
                                ) {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        contentDescription = stringResource(id = R.string.desc_reload),
                                        tint = sudmonitorHistoryRefreshTint,
                                        modifier = if (uiState.isSudmonitorHistoryManualRunning) {
                                            Modifier.rotate(sudmonitorHistoryRotation)
                                        } else {
                                            Modifier
                                        }
                                    )
                                }
                            } else if (uiState.isHistoricalMode) {
                                HistoricalRangeActionIcons(
                                    isFiltered = uiState.isHistoricalDisplayRangeFiltered,
                                    onResetRange = { viewModel.resetHistoricalDisplayRange() },
                                    onOpenRangeDialog = { showHistoricalRangeDialog = true }
                                )
                            } else {
                                
                                val autoUpdateDesc = if (autoUpdateEnabled) {
                                    stringResource(R.string.desc_auto_update_enabled)
                                } else {
                                    stringResource(R.string.desc_auto_update_disabled)
                                }
                                IconButton(
                                    onClick = onAutoUpdateIconClick,
                                    modifier = Modifier
                                        .testTag(TestTags.AUTO_UPDATE_BUTTON)
                                        .semantics {
                                            stateDescription = autoUpdateDesc
                                        }
                                ) {
                                    Icon(
                                        Icons.Outlined.Autorenew,
                                        contentDescription = stringResource(R.string.nav_auto_update),
                                        tint = if (autoUpdateEnabled) LocalContentColor.current
                                        else LocalContentColor.current.copy(alpha = 0.38f),
                                        modifier = if (isAutoStyleRunning) Modifier.rotate(autoRotation) else Modifier
                                    )
                                }
                                
                                val refreshTint = when {
                                    isAutoStyleRunning -> LocalContentColor.current.copy(alpha = 0.38f)
                                    !canRefreshForTint && !isOneTimeRunning -> LocalContentColor.current.copy(alpha = 0.38f)
                                    else -> LocalContentColor.current
                                }
                                IconButton(
                                    onClick = onManualUpdateIconClick,
                                    modifier = Modifier.testTag(TestTags.MANUAL_UPDATE_BUTTON)
                                ) {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        contentDescription = stringResource(id = R.string.desc_reload),
                                        tint = refreshTint,
                                        modifier = if (isOneTimeRunning) Modifier.rotate(refreshRotation) else Modifier
                                    )
                                }
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TestTags.MAIN_ROOT)
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                if (appSettings != null && mainCardExpansionState != null) {
                    val settings = appSettings!!
                    val cardExpansionState = mainCardExpansionState!!
                    val data = uiState.damData
                    val percentage = data?.storagePercentage
                    
                    val isJa = isJapaneseLocale
                    
                    val historicalConfig = uiState.historicalDamConfig
                    val historicalMeta = uiState.historicalDamMeta
                    val dailyConfigForObservation = uiState.sudmonitorHistoryDamConfig
                    val observationCardKey = if (isPastDataMode) {
                        MainCardExpansionKey.HISTORICAL_OBSERVATION
                    } else {
                        MainCardExpansionKey.REALTIME_OBSERVATION
                    }
                    val historyCardKey = if (isPastDataMode) {
                        MainCardExpansionKey.HISTORICAL_HISTORY
                    } else {
                        MainCardExpansionKey.REALTIME_HISTORY
                    }
                    val graphCardKey = if (isPastDataMode) {
                        MainCardExpansionKey.HISTORICAL_GRAPH
                    } else {
                        MainCardExpansionKey.REALTIME_GRAPH
                    }
                    val linksCardKey = if (isPastDataMode) {
                        MainCardExpansionKey.HISTORICAL_LINKS
                    } else {
                        MainCardExpansionKey.REALTIME_LINKS
                    }
                    val isObsExpanded = cardExpansionState[observationCardKey]
                    val isLatestExpanded =
                        cardExpansionState[MainCardExpansionKey.REALTIME_LATEST]
                    val isHistoryExpanded = cardExpansionState[historyCardKey]
                    val isGraphExpanded = cardExpansionState[graphCardKey]
                    val isLinksExpanded = cardExpansionState[linksCardKey]

                    
                    val scrollState = remember(cardResetKey) { ScrollState(0) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .mouseDragScroll(scrollState, Orientation.Vertical)
                            .verticalScroll(scrollState)
                            .testTag(TestTags.MAIN_SCROLL_CONTAINER)
                    ) {
                        
                        if (!isPastDataMode && navigationLayout != NavigationLayout.PERMANENT) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(TestTags.SUMMARY_CARD)
                                    .padding(bottom = 16.dp)
                                    .semantics { isTraversalGroup = true },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    val damName = uiState.damConfig?.let {
                                        LocaleUtils.normalDamName(appLocaleTag, it)
                                    } ?: ""

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = damName,
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.semantics { heading() }
                                        )
                                    }

                                    if (data != null) {
                                        val targetTimeStr = data.storagePercentageTime ?: data.updatedAt
                                        val summaryDateStr = TimeUtils.parseAndFormatToJst(
                                            targetTimeStr, "yyyy/MM/dd H:m", "yyyy/MM/dd"
                                        ) ?: ""
                                        val summaryTimeStr = TimeUtils.parseAndFormatToJstWithSuffix(
                                            targetTimeStr, "yyyy/MM/dd H:m", "HH:mm"
                                        ) ?: ""
                                        val summaryPercentage = data.storagePercentage
                                        val summaryTrend = data.storagePercentageTrend
                                        val summaryTrendColor = trendAccentColor(summaryTrend)
                                            ?: MaterialTheme.colorScheme.onSurfaceVariant
                                        val dateTimeStr = WidgetDisplayFormatter.buildDateTimePrefix(
                                            summaryDateStr,
                                            summaryTimeStr
                                        )
                                        val percentageAndTrendText =
                                            WidgetDisplayFormatter.percentageAndTrendText(
                                                percentage = summaryPercentage,
                                                trend = summaryTrend,
                                                locale = appLocale,
                                                missingPercentageText = MISSING_PERCENTAGE_TEXT
                                            )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (dateTimeStr.isNotEmpty()) {
                                                Text(
                                                    text = dateTimeStr,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            val trendStateDesc = when (summaryTrend) {
                                                Trend.UP -> stringResource(R.string.desc_trend_up)
                                                Trend.DOWN -> stringResource(R.string.desc_trend_down)
                                                else -> stringResource(R.string.desc_trend_stable)
                                            }
                                            Text(
                                                text = percentageAndTrendText,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (summaryPercentage != null) {
                                                    summaryTrendColor
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.semantics {
                                                    stateDescription = trendStateDesc
                                                }
                                            )
                                        }

                                        val dayChange = data.storagePercentageDayChange
                                        val dayChangeTrend = data.storagePercentageDayChangeTrend
                                        val dayChangeText = WidgetDisplayFormatter.dayChangeText(
                                            label = stringResource(
                                                R.string.summary_storage_percentage_day_change
                                            ),
                                            dayChange = dayChange,
                                            trend = dayChangeTrend,
                                            locale = appLocale
                                        )
                                        val dayChangeTrendColor = trendAccentColor(dayChangeTrend)
                                            ?: MaterialTheme.colorScheme.onSurfaceVariant
                                        val dayChangeTrendStateDesc = when (dayChangeTrend) {
                                            Trend.UP -> stringResource(R.string.desc_trend_up)
                                            Trend.DOWN -> stringResource(R.string.desc_trend_down)
                                            else -> stringResource(R.string.desc_trend_stable)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = dayChangeText,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (dayChange != null) {
                                                    dayChangeTrendColor
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.semantics {
                                                    stateDescription = dayChangeTrendStateDesc
                                                }
                                            )
                                        }

                                        val summaryStateText = settings.getStateText(
                                            summaryPercentage,
                                            isJapaneseLocale,
                                            data.isAllObservationDataInvalid(),
                                            settings.targetDamId == AppSettings.DEFAULT_DAM_ID,
                                            data.storageVolumeForMessage
                                        )
                                        if (summaryStateText.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Start,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = summaryStateText,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    modifier = Modifier
                                                        .testTag(TestTags.SUMMARY_STATUS_TEXT)
                                                        .semantics {
                                                            liveRegion = LiveRegionMode.Polite
                                                        }
                                                )
                                            }
                                        }
                                    } else {
                                        val summaryStateText = when (uiState.damLoadStatus) {
                                            DamLoadStatus.NETWORK_UNAVAILABLE ->
                                                settings.getNetworkUnavailableText(isJapaneseLocale)
                                            DamLoadStatus.LOADING_FAILURE ->
                                                settings.getLoadingErrorText(isJapaneseLocale)
                                            DamLoadStatus.INITIAL,
                                            DamLoadStatus.SUCCESS ->
                                                ""
                                        }
                                        if (summaryStateText.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Start,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = summaryStateText,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    modifier = Modifier
                                                        .testTag(TestTags.SUMMARY_STATUS_TEXT)
                                                        .semantics {
                                                            liveRegion = LiveRegionMode.Polite
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            } 
                        } 

                        
                        if (uiState.isHistoricalMode && navigationLayout != NavigationLayout.PERMANENT && historicalMeta != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(TestTags.HISTORICAL_SUMMARY_CARD)
                                    .padding(bottom = 16.dp)
                                    .semantics { isTraversalGroup = true },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // 1行目: ダム名（リアルタイムサマリーCard 1行目と同じフォント・表示位置）
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val histDamName = historicalConfig?.let {
                                            LocaleUtils.normalDamName(appLocaleTag, it)
                                        } ?: historicalMeta.damConfigId

                                        Text(
                                            text = histDamName,
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.semantics { heading() }
                                        )
                                    }

                                    
                                    // 2行目: 期間文字列（リアルタイムサマリーCard 2行目と同じフォント・表示位置）
                                    val histLine2 = "${historicalMeta.searchBgnDate.toYmdSlash()} 01:00 - " +
                                            TimeUtils.appendJstSuffix("${historicalMeta.searchEndDate.toNextDayYmdSlash()} 00:00")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = histLine2,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // 3行目: 貯水率範囲（リアルタイムサマリーCard 2行目と同じフォント・表示位置）
                                    val histLine3 = run {
                                        val startPct = historicalMeta.dataStartStoragePct
                                        val endPct = historicalMeta.dataEndStoragePct
                                        val minPct = historicalMeta.dataMinStoragePct
                                        val maxPct = historicalMeta.dataMaxStoragePct
                                        if (startPct != null && endPct != null) {
                                            val rangeStr = if (minPct != null && maxPct != null) {
                                                " (${
                                                    String.format(appLocale, "%.2f", minPct)
                                                }% ~ ${
                                                    String.format(appLocale, "%.2f", maxPct)
                                                }%)"
                                            } else ""
                                            "${
                                                String.format(appLocale, "%.2f", startPct)
                                            }% → ${
                                                String.format(appLocale, "%.2f", endPct)
                                            }%$rangeStr"
                                        } else MISSING_PERCENTAGE_TEXT
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = histLine3,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (historicalMeta.dataStartStoragePct != null)
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        
                        // 過去データ(日次)モードでは保存行が未保存の間もサマリーCardを表示する
                        // （ダム名のみ。期間行・貯水率範囲行は行保存後に表示。リアルタイム表示の
                        // データ未取得時の「ダム名のみ」表示と同様の最小表示）
                        if (uiState.isSudmonitorHistoryMode && navigationLayout != NavigationLayout.PERMANENT) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(TestTags.HISTORICAL_SUMMARY_CARD)
                                    .padding(bottom = 16.dp)
                                    .semantics { isTraversalGroup = true },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // 1行目: ダム名（履歴サマリーCard 1行目と同じフォント・表示位置）。
                                    // 行未保存でも表示対象ダム設定が保持されているためダム名を表示できる
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val dailyDamName = uiState.sudmonitorHistoryDamConfig?.let {
                                            LocaleUtils.normalDamName(appLocaleTag, it)
                                        } ?: dailyHistory?.damId ?: ""
                                        Text(
                                            text = dailyDamName,
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.semantics { heading() }
                                        )
                                    }

                                    // 2行目: 期間文字列（読込済み期間の yyyy/MM/dd 01:00 - yyyy/MM/dd 00:00 表記。
                                    // periodEndEpochMs は最終被覆日の JST 00:00（日単位規約）のため、
                                    // 終端は最終行の 24:00 = 翌日 00:00 として +1日で表示する。
                                    // 通常の過去データと同じく、端末TZがJST以外の場合は終端にのみ
                                    // (JST) を付与する（開始は無し）。行未保存時は空=行非表示）
                                    val dailyLine2 = dailyHistory?.let { history ->
                                        buildString {
                                            val startStr = history.periodStartEpochMs
                                                ?.let { "${TimeUtils.formatToJst(it, "yyyy/MM/dd")} 01:00" }
                                                ?: ""
                                            val endStr = history.periodEndEpochMs
                                                ?.let {
                                                    TimeUtils.appendJstSuffix(
                                                        "${TimeUtils.formatToJst(it + MILLIS_PER_DAY, "yyyy/MM/dd")} 00:00"
                                                    )
                                                }
                                                ?: ""
                                            if (startStr.isNotEmpty()) append(startStr)
                                            if (startStr.isNotEmpty() && endStr.isNotEmpty()) append(" - ")
                                            if (endStr.isNotEmpty()) append(endStr)
                                        }
                                    } ?: ""
                                    if (dailyLine2.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = dailyLine2,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // 3行目: 貯水率範囲（履歴サマリーCard 3行目と同じ書式。
                                    // 行未保存時は行自体を表示しない=ダム名のみ）
                                    val dailyLine3 = dailyHistory?.let { history ->
                                        run {
                                            val startPct = history.firstStorageRatePct
                                            val endPct = history.lastStorageRatePct
                                            val minPct = history.minStorageRatePct
                                            val maxPct = history.maxStorageRatePct
                                            if (startPct != null && endPct != null) {
                                                val rangeStr = if (minPct != null && maxPct != null) {
                                                    " (${
                                                        String.format(appLocale, "%.2f", minPct)
                                                    }% ~ ${
                                                        String.format(appLocale, "%.2f", maxPct)
                                                    }%)"
                                                } else ""
                                                "${
                                                    String.format(appLocale, "%.2f", startPct)
                                                }% → ${
                                                    String.format(appLocale, "%.2f", endPct)
                                                }%$rangeStr"
                                            } else MISSING_PERCENTAGE_TEXT
                                        }
                                    } ?: ""
                                    if (dailyLine3.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = dailyLine3,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestTags.OBSERVATION_CARD)
                                .padding(bottom = 16.dp)
                                .semantics { isTraversalGroup = true },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.main_observation_data),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .semantics { heading() }
                                    )
                                    IconButton(
                                        onClick = {
                                            viewModel.toggleMainCardExpansion(observationCardKey)
                                        },
                                        modifier = Modifier.testTag(TestTags.OBSERVATION_EXPAND_BUTTON)
                                    ) {
                                        Icon(
                                            imageVector = if (isObsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                            contentDescription = if (isObsExpanded) stringResource(R.string.desc_collapse) else stringResource(R.string.desc_expand),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (isObsExpanded) {
                                    if (uiState.isSudmonitorHistoryMode && dailyConfigForObservation != null) {
                                        
                                        InfoRow(
                                            stringResource(id = R.string.main_station_id),
                                            dailyHistory?.damId ?: MISSING_TEXT
                                        )
                                        InfoRow(
                                            stringResource(id = R.string.main_station_name),
                                            LocaleUtils.normalDamName(
                                                appLocaleTag,
                                                dailyConfigForObservation
                                            )
                                        )
                                        InfoRow(
                                            stringResource(id = R.string.main_river_system),
                                            LocaleUtils.normalWaterSystemName(
                                                appLocaleTag,
                                                dailyConfigForObservation
                                            )
                                                .ifEmpty { MISSING_TEXT }
                                        )
                                        InfoRow(
                                            stringResource(id = R.string.main_river_name),
                                            LocaleUtils.normalRiverName(
                                                appLocaleTag,
                                                dailyConfigForObservation
                                            )
                                                .ifEmpty { MISSING_TEXT }
                                        )
                                        if (dailyConfigForObservation.geoUrl.isNotEmpty()) {
                                            GeoUriInfoRow(
                                                label = stringResource(id = R.string.settings_dam_geo),
                                                geoUri = dailyConfigForObservation.geoUrl,
                                                availableApps = uiState.availableApps,
                                                onOpenUrl = { url ->
                                                    openUrl(
                                                        context,
                                                        url
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onShareText = { text ->
                                                    shareText(
                                                        context,
                                                        text
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onCopied = { viewModel.emitSnackbarMessage(it) }
                                            )
                                        }
                                    } else if (uiState.isHistoricalMode && historicalConfig != null) {
                                        
                                        InfoRow(
                                            stringResource(id = R.string.main_station_id),
                                            historicalMeta?.observationStationId ?: MISSING_TEXT
                                        )
                                        InfoRow(
                                            stringResource(id = R.string.main_station_name),
                                                LocaleUtils.normalDamName(
                                                    appLocaleTag,
                                                    historicalConfig
                                                )
                                        )
                                        InfoRow(
                                            stringResource(id = R.string.main_river_system),
                                                LocaleUtils.normalWaterSystemName(
                                                    appLocaleTag,
                                                    historicalConfig
                                                )
                                                .ifEmpty { MISSING_TEXT }
                                        )
                                        InfoRow(
                                            stringResource(id = R.string.main_river_name),
                                                LocaleUtils.normalRiverName(
                                                    appLocaleTag,
                                                    historicalConfig
                                                )
                                                .ifEmpty { MISSING_TEXT }
                                        )
                                        if (historicalConfig.geoUrl.isNotEmpty()) {
                                            GeoUriInfoRow(
                                                label = stringResource(id = R.string.settings_dam_geo),
                                                geoUri = historicalConfig.geoUrl,
                                                availableApps = uiState.availableApps,
                                                onOpenUrl = { url ->
                                                    openUrl(
                                                        context,
                                                        url
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onShareText = { text ->
                                                    shareText(
                                                        context,
                                                        text
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onCopied = { viewModel.emitSnackbarMessage(it) }
                                            )
                                        }
                                    } else {
                                        
                                        
                                        InfoRow(
                                            stringResource(id = R.string.main_station_id),
                                            data?.observationStationId ?: MISSING_TEXT
                                        )
                                        
                                        val damCfg = uiState.damConfig
                                        InfoRow(
                                            stringResource(id = R.string.main_station_name),
                                            damCfg?.let {
                                                LocaleUtils.normalDamName(
                                                    appLocaleTag,
                                                    it
                                                )
                                            } ?: MISSING_TEXT
                                        )
                                        InfoRow(
                                            stringResource(id = R.string.main_river_system),
                                            damCfg?.let {
                                                LocaleUtils.normalWaterSystemName(
                                                    appLocaleTag, it
                                                )
                                            }
                                                ?.ifEmpty { MISSING_TEXT } ?: MISSING_TEXT
                                        )
                                        InfoRow(
                                            stringResource(id = R.string.main_river_name),
                                            damCfg?.let {
                                                LocaleUtils.normalRiverName(
                                                    appLocaleTag, it
                                                )
                                            }?.ifEmpty { MISSING_TEXT }
                                                ?: MISSING_TEXT
                                        )
                                        
                                        if (damCfg != null && damCfg.geoUrl.isNotEmpty()) {
                                            GeoUriInfoRow(
                                                label = stringResource(id = R.string.settings_dam_geo),
                                                geoUri = damCfg.geoUrl,
                                                availableApps = uiState.availableApps,
                                                onOpenUrl = { url ->
                                                    openUrl(
                                                        context,
                                                        url
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onShareText = { text ->
                                                    shareText(
                                                        context,
                                                        text
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onCopied = { viewModel.emitSnackbarMessage(it) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }

                        
                        if (!isPastDataMode) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(TestTags.LATEST_CARD)
                                    .padding(bottom = 16.dp)
                                    .semantics { isTraversalGroup = true },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.main_latest_data),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .weight(1f)
                                                .semantics { heading() }
                                        )
                                        IconButton(
                                            onClick = {
                                                viewModel.toggleMainCardExpansion(
                                                    MainCardExpansionKey.REALTIME_LATEST
                                                )
                                            },
                                            modifier = Modifier.testTag(TestTags.LATEST_EXPAND_BUTTON)
                                        ) {
                                            Icon(
                                                imageVector = if (isLatestExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                                contentDescription = if (isLatestExpanded) stringResource(R.string.desc_collapse) else stringResource(R.string.desc_expand),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (isLatestExpanded) {
                                        
                                        
                                        
                                        @Suppress("UNUSED_EXPRESSION")
                                        currentTimeZoneId
                                        val showJstSuffix = !TimeUtils.isJst()
                                        val updateDateStr = if (data != null) {
                                            val parsed = TimeUtils.parseAndFormatToJst(
                                                data.updatedAt,
                                                "yyyy/MM/dd HH:mm",
                                                "yyyy/MM/dd HH:mm"
                                            )
                                                ?: data.updatedAt
                                            if (showJstSuffix) "$parsed (JST)" else parsed
                                        } else MISSING_TEXT
                                        val fetchDateStr = when {
                                            appSettings?.debugModeEnabled == true && data != null -> updateDateStr
                                            viewModel.lastFetchTimeMillis > 0 -> {
                                                val base = TimeUtils.formatToJst(viewModel.lastFetchTimeMillis, "yyyy/MM/dd HH:mm")
                                                if (showJstSuffix) "$base (JST)" else base
                                            }
                                            else -> MISSING_TEXT
                                        }

                                        if (data != null) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 16.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(
                                                        id = R.string.main_data_fetch_time,
                                                        fetchDateStr
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    text = stringResource(
                                                        id = R.string.main_data_update_time,
                                                        updateDateStr
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                val storagePercentageTime = data.storagePercentageTime
                                                if (storagePercentageTime != null) {
                                                    val timeStr = TimeUtils.parseAndFormatToJst(
                                                        storagePercentageTime,
                                                        "yyyy/MM/dd H:m",
                                                        "yyyy/MM/dd HH:mm"
                                                    )?.let { if (showJstSuffix) "$it (JST)" else it }
                                                        ?: storagePercentageTime
                                                    Text(
                                                        text = stringResource(
                                                            id = R.string.main_percentage_time,
                                                            timeStr
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }

                                        DataRow(
                                            label = stringResource(id = R.string.main_rainfall),
                                            value = when {
                                                data == null -> MISSING_TEXT
                                                data.catchmentAverageRainfall == null -> MISSING_TEXT
                                                else -> formatRainfall(data.catchmentAverageRainfall, appLocale)
                                            },
                                            trend = Trend.UNKNOWN,
                                            isMissing = data == null || data.catchmentAverageRainfall == null
                                        )
                                        DataRow(
                                            label = stringResource(id = R.string.main_storage_volume),
                                            value = when {
                                                data == null -> MISSING_TEXT
                                                data.storageVolume == null -> MISSING_TEXT
                                                else -> formatValue(data.storageVolume, decimals = 0, locale = appLocale)
                                            },
                                            trend = if (data != null) data.storageVolumeTrend else Trend.UNKNOWN,
                                            isMissing = data == null || data.storageVolume == null
                                        )
                                        DataRow(
                                            label = stringResource(id = R.string.main_inflow),
                                            value = when {
                                                data == null -> MISSING_TEXT
                                                data.inflow == null -> MISSING_TEXT
                                                else -> formatValue(data.inflow, decimals = 2, locale = appLocale)
                                            },
                                            trend = Trend.UNKNOWN,
                                            isMissing = data == null || data.inflow == null
                                        )
                                        DataRow(
                                            label = stringResource(id = R.string.main_outflow),
                                            value = when {
                                                data == null -> MISSING_TEXT
                                                data.outflow == null -> MISSING_TEXT
                                                else -> formatValue(data.outflow, decimals = 2, locale = appLocale)
                                            },
                                            trend = Trend.UNKNOWN,
                                            isMissing = data == null || data.outflow == null
                                        )
                                        DataRow(
                                            label = stringResource(id = R.string.main_storage_percentage),
                                            value = when {
                                                data == null -> MISSING_TEXT
                                                data.storagePercentage == null -> MISSING_TEXT
                                                else -> formatValue(data.storagePercentage, decimals = 2, locale = appLocale)
                                            },
                                            trend = if (data != null) data.storagePercentageTrend else Trend.UNKNOWN,
                                            isMissing = data == null || data.storagePercentage == null
                                        )
                                        DataRow(
                                            label = stringResource(id = R.string.main_storage_percentage_day_change),
                                            value = if (data == null || data.storagePercentageDayChange == null) MISSING_TEXT else formatChangeValue(
                                                data.storagePercentageDayChange,
                                                appLocale
                                            ),
                                            trend = if (data != null) data.storagePercentageDayChangeTrend else Trend.UNKNOWN,
                                            isMissing = data == null || data.storagePercentage == null || data.storagePercentageDayChange == null
                                        )
                                        DataRow(
                                            label = stringResource(id = R.string.main_storage_percentage_week_change),
                                            value = if (data == null || data.storagePercentageWeekChange == null) MISSING_TEXT else formatChangeValue(
                                                data.storagePercentageWeekChange,
                                                appLocale
                                            ),
                                            trend = if (data != null) data.storagePercentageWeekChangeTrend else Trend.UNKNOWN,
                                            isMissing = data == null || data.storagePercentage == null || data.storagePercentageWeekChange == null
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    } 
                                }
                            }
                        } 

                        
                        val rainfallUnitLabel = if (isPastDataMode) {
                            stringResource(id = R.string.graph_unit_rainfall_per_hour)
                        } else {
                            stringResource(id = R.string.graph_unit_rainfall)
                        }
                        val historicalDisplayStartDate =
                            uiState.historicalDisplayStartDate ?: uiState.historicalDamMeta?.searchBgnDate
                        val historicalDisplayEndDate =
                            uiState.historicalDisplayEndDate ?: uiState.historicalDamMeta?.searchEndDate
                        
                        val historicalSearchStartMillis = remember(historicalDisplayStartDate) {
                            val bgnDate = historicalDisplayStartDate
                                ?: return@remember 0L
                            TimeUtils.parseJstMillis(bgnDate, "yyyyMMdd") ?: 0L
                        }
                        
                        val historicalSearchEndMillis = remember(historicalDisplayEndDate) {
                            val endDate = historicalDisplayEndDate
                                ?: return@remember 0L
                            try {
                                val cal = Calendar.getInstance(TimeUtils.JST_TIMEZONE)
                                cal.timeInMillis = TimeUtils.parseJstMillis(endDate, "yyyyMMdd") ?: return@remember 0L
                                cal.add(Calendar.DAY_OF_MONTH, 1)
                                cal.timeInMillis
                            } catch (_: Exception) {
                                0L
                            }
                        }
                        // 過去データ(日次)モードでは保存行（読込済みデータ）がある場合のみ観測データ(一覧)Cardを表示する。
                        // 行未保存（初回取得失敗・未取得）の間はリアルタイム表示のデータ未取得時と同様に非表示
                        // （リアルタイム分岐へフォールスルーしないよう、日次モード中は行なしなら何も表示しない）
                        if (uiState.isSudmonitorHistoryMode) {
                            if (uiState.sudmonitorHistory != null) {
                                ObservationHistoryCard(
                                    historicalData = uiState.sudmonitorHistoryDisplayData,
                                    isExpanded = isHistoryExpanded,
                                    onExpandToggle = {
                                        viewModel.toggleMainCardExpansion(historyCardKey)
                                    },
                                    isHistorical = true,
                                    rainfallUnit = rainfallUnitLabel,
                                    locale = appLocale,
                                    onShowAllPeriod = {
                                        if (navigationLayout == NavigationLayout.PERMANENT) {
                                            contentPage = ContentPage.OBSERVATION_HISTORY_LIST
                                        } else {
                                            onShowObservationHistoryList()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(TestTags.HISTORY_CARD)
                                        .padding(bottom = 16.dp)
                                )
                            }
                        } else if (uiState.isHistoricalMode) {
                            ObservationHistoryCard(
                                historicalData = uiState.historicalDisplayData,
                                isExpanded = isHistoryExpanded,
                                onExpandToggle = {
                                    viewModel.toggleMainCardExpansion(historyCardKey)
                                },
                                isHistorical = true,
                                rainfallUnit = rainfallUnitLabel,
                                locale = appLocale,
                                onShowAllPeriod = {
                                    if (navigationLayout == NavigationLayout.PERMANENT) {
                                        contentPage = ContentPage.OBSERVATION_HISTORY_LIST
                                    } else {
                                        onShowObservationHistoryList()
                                    }
                                },
                                onCollapsed = { viewModel.resetHistoricalDisplayToInitial() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(TestTags.HISTORY_CARD)
                                    .padding(bottom = 16.dp)
                            )
                        } else if (data?.historicalData?.isNotEmpty() == true) {
                            ObservationHistoryCard(
                                historicalData = data.historicalData,
                                isExpanded = isHistoryExpanded,
                                onExpandToggle = {
                                    viewModel.toggleMainCardExpansion(historyCardKey)
                                },
                                rainfallUnit = rainfallUnitLabel,
                                locale = appLocale,
                                onShowAllPeriod = {
                                    if (navigationLayout == NavigationLayout.PERMANENT) {
                                        contentPage = ContentPage.OBSERVATION_HISTORY_LIST
                                    } else {
                                        onShowObservationHistoryList()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(TestTags.HISTORY_CARD)
                                    .padding(bottom = 16.dp)
                            )
                        }

                        
                        
                        val graphData = when {
                            uiState.isSudmonitorHistoryMode -> uiState.sudmonitorHistoryVisibleData
                            uiState.isHistoricalMode -> uiState.historicalVisibleData
                            else -> data?.historicalData ?: emptyList()
                        }
                        if (graphData.isNotEmpty()) {
                            val sudmonitorHistorySearchStartMillis = if (uiState.isSudmonitorHistoryMode) {
                                uiState.sudmonitorHistoryVisibleFromMillis.takeIf { it > 0L }
                                    ?: uiState.sudmonitorHistory?.periodStartEpochMs
                            } else {
                                null
                            }
                            val sudmonitorHistorySearchEndMillis = if (uiState.isSudmonitorHistoryMode) {
                                uiState.sudmonitorHistoryVisibleToMillis.takeIf { it > 0L }
                                    ?: uiState.sudmonitorHistory?.periodEndEpochMs
                            } else {
                                null
                            }
                            key(cardResetKey) {
                                ObservationGraphCard(
                                    historicalData = graphData,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                    isExpanded = isGraphExpanded,
                                    onExpandToggle = {
                                        viewModel.toggleMainCardExpansion(graphCardKey)
                                    },
                                    showJstSuffix = !TimeUtils.isJst().also { currentTimeZoneId },
                                    isHistorical = uiState.isHistoricalMode,
                                    isDailyHistory = uiState.isSudmonitorHistoryMode,
                                    rainfallUnitLabel = rainfallUnitLabel,
                                    historicalSearchStartMillis = when {
                                        uiState.isHistoricalMode -> historicalSearchStartMillis.takeIf { it != 0L }
                                        uiState.isSudmonitorHistoryMode -> sudmonitorHistorySearchStartMillis
                                        else -> null
                                    },
                                    historicalSearchEndMillis = when {
                                        uiState.isHistoricalMode -> historicalSearchEndMillis.takeIf { it != 0L }
                                        uiState.isSudmonitorHistoryMode -> sudmonitorHistorySearchEndMillis
                                        else -> null
                                    },
                                    damId = uiState.displayedDamConfig?.id,
                                    comparisonStates = uiState.historicalComparisonStates,
                                    onHistoricalMetricSelected = { metric, startMillis, endMillis, mainYear, isRealtimeWindow ->
                                        viewModel.ensureHistoricalComparison(
                                            metric,
                                            startMillis,
                                            endMillis,
                                            mainYear,
                                            isRealtimeWindow
                                        )
                                    }
                                )
                            }
                        }

                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestTags.LINKS_CARD)
                                .padding(bottom = 16.dp)
                                .semantics { isTraversalGroup = true },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.main_url_card_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .semantics { heading() }
                                    )
                                    IconButton(
                                        onClick = {
                                            viewModel.toggleMainCardExpansion(linksCardKey)
                                        },
                                        modifier = Modifier.testTag(TestTags.LINKS_EXPAND_BUTTON)
                                    ) {
                                        Icon(
                                            imageVector = if (isLinksExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                            contentDescription = if (isLinksExpanded) stringResource(R.string.desc_collapse) else stringResource(R.string.desc_expand),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (isLinksExpanded) {
                                    val displayedConfig = uiState.displayedDamConfig
                                    displayedConfig?.let { config ->
                                        
                                        if (config.disasterInfoUrl.isNotEmpty()) {
                                            UrlItemRow(
                                                title = stringResource(id = R.string.settings_dam_url_disaster),
                                                url = config.disasterInfoUrl,
                                                availableApps = uiState.availableApps,
                                                onOpenUrl = { url ->
                                                    openUrl(
                                                        context,
                                                        url
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onOpenUrlWith = { url, app ->
                                                    openUrlWith(
                                                        context,
                                                        url,
                                                        app
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onShareText = { text ->
                                                    shareText(
                                                        context,
                                                        text
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onCopied = { viewModel.emitSnackbarMessage(it) },
                                                onQueryApps = { viewModel.queryAvailableApps(it) },
                                                onClearApps = { viewModel.clearAvailableApps() }
                                            )
                                            Spacer(modifier = Modifier.padding(vertical = 4.dp))
                                        }

                                        
                                        if (config.siteInfoUrl.isNotEmpty()) {
                                            UrlItemRow(
                                                title = stringResource(id = R.string.settings_dam_url_site_info),
                                                url = config.siteInfoUrl,
                                                availableApps = uiState.availableApps,
                                                onOpenUrl = { url ->
                                                    openUrl(
                                                        context,
                                                        url
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onOpenUrlWith = { url, app ->
                                                    openUrlWith(
                                                        context,
                                                        url,
                                                        app
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onShareText = { text ->
                                                    shareText(
                                                        context,
                                                        text
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onCopied = { viewModel.emitSnackbarMessage(it) },
                                                onQueryApps = { viewModel.queryAvailableApps(it) },
                                                onClearApps = { viewModel.clearAvailableApps() }
                                            )
                                            Spacer(modifier = Modifier.padding(vertical = 4.dp))
                                        }

                                        
                                        if (config.dataUrl.isNotEmpty()) {
                                            UrlItemRow(
                                                title = stringResource(id = R.string.settings_dam_url_data),
                                                url = config.dataUrl,
                                                availableApps = uiState.availableApps,
                                                onOpenUrl = { url ->
                                                    openUrl(
                                                        context,
                                                        url
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onOpenUrlWith = { url, app ->
                                                    openUrlWith(
                                                        context,
                                                        url,
                                                        app
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onShareText = { text ->
                                                    shareText(
                                                        context,
                                                        text
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onCopied = { viewModel.emitSnackbarMessage(it) },
                                                onQueryApps = { viewModel.queryAvailableApps(it) },
                                                onClearApps = { viewModel.clearAvailableApps() }
                                            )
                                            Spacer(modifier = Modifier.padding(vertical = 4.dp))
                                        }

                                        
                                        if (config.searchUrl.isNotEmpty()) {
                                            UrlItemRow(
                                                title = stringResource(id = R.string.settings_dam_url_search),
                                                url = config.searchUrl,
                                                availableApps = uiState.availableApps,
                                                onOpenUrl = { url ->
                                                    openUrl(
                                                        context,
                                                        url
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onOpenUrlWith = { url, app ->
                                                    openUrlWith(
                                                        context,
                                                        url,
                                                        app
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onShareText = { text ->
                                                    shareText(
                                                        context,
                                                        text
                                                    ) { viewModel.emitSnackbarMessage(it) }
                                                },
                                                onCopied = { viewModel.emitSnackbarMessage(it) },
                                                onQueryApps = { viewModel.queryAvailableApps(it) },
                                                onClearApps = { viewModel.clearAvailableApps() }
                                            )
                                            Spacer(modifier = Modifier.padding(vertical = 4.dp))
                                        }

                                        
                                        config.otherUrls.forEach { (label, url) ->
                                            if (url.isNotEmpty()) {
                                                UrlItemRow(
                                                    title = label,
                                                    url = url,
                                                    availableApps = uiState.availableApps,
                                                    onOpenUrl = { u ->
                                                        openUrl(
                                                            context,
                                                            u
                                                        ) { viewModel.emitSnackbarMessage(it) }
                                                    },
                                                    onOpenUrlWith = { u, app ->
                                                        openUrlWith(
                                                            context,
                                                            u,
                                                            app
                                                        ) { viewModel.emitSnackbarMessage(it) }
                                                    },
                                                    onShareText = { text ->
                                                        shareText(
                                                            context,
                                                            text
                                                        ) { viewModel.emitSnackbarMessage(it) }
                                                    },
                                                    onCopied = { viewModel.emitSnackbarMessage(it) },
                                                    onQueryApps = { viewModel.queryAvailableApps(it) },
                                                    onClearApps = { viewModel.clearAvailableApps() }
                                                )
                                                Spacer(modifier = Modifier.padding(vertical = 4.dp))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                } 
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    } 
                }
            }
        }
    }

    
    when (navigationLayout) {
        NavigationLayout.DRAWER -> Box(modifier = Modifier.fillMaxSize().testTag(TestTags.DRAWER_ROOT)) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        DrawerContent(
                            isPermanent = false,
                            contentPage = ContentPage.MAIN,
                            realtimeDamData = uiState.damData,
                            realtimeDamConfig = uiState.damConfig,
                            realtimeLoadStatus = uiState.damLoadStatus,
                            appSettings = appSettings,
                            isHistoricalMode = uiState.isHistoricalMode,
                            onRealtimeClick = { closeDrawerThen { viewModel.switchToRealtimeMode() } },
                            onHistoricalSearchClick = { closeDrawerThen(onHistoricalSearchRequest) },
                            onManageHistoricalClick = {
                                closeDrawerThen(onManageHistoricalClick)
                            },
                            onShowMain = {},
                            historicalMetaList = uiState.historicalMetaList,
                            currentHistoricalMetaId = uiState.historicalDamMeta?.id ?: 0L,
                            getDamConfig = { damId -> net.tecogonaz.tcsameuradammonitor.domain.model.DamListData.allDams.find { it.id == damId } },
                            onSwitchToHistoricalMode = { metaId ->
                                closeDrawerThen { viewModel.switchToHistoricalMode(metaId) }
                            },
                            isDailyHistoryEnabled =
                                appSettings?.historicalDataSource == RealtimeDataSource.SUDMONITOR,
                            sudmonitorHistory = if (appSettings?.historicalDataSource == RealtimeDataSource.SUDMONITOR) {
                                uiState.sudmonitorHistory
                            } else {
                                null
                            },
                            isSudmonitorHistoryMode = uiState.isSudmonitorHistoryMode,
                            onSwitchToSudmonitorHistoryMode = {
                                closeDrawerThen { viewModel.switchToSudmonitorHistoryMode() }
                            },
                            lastFetchDateStr = lastFetchDateStr,
                            availableApps = uiState.availableApps,
                            onDebugClick = {
                                closeDrawerThen(onDebugClick)
                            },
                            onSettingsClick = {
                                closeDrawerThen(onMenuSettingsClick)
                            },
                            onAppInfoClick = {
                                closeDrawerThen(onAppInfoClick)
                            },
                            onOpenUrl = { url -> openUrl(context, url) { viewModel.emitSnackbarMessage(it) } },
                            onOpenUrlWith = { url, app ->
                                openUrlWith(context, url, app) { viewModel.emitSnackbarMessage(it) }
                            },
                            onShareText = { text -> shareText(context, text) { viewModel.emitSnackbarMessage(it) } },
                            onCopied = { viewModel.emitSnackbarMessage(it) },
                            onQueryApps = { viewModel.queryAvailableApps(it) },
                            onClearApps = { viewModel.clearAvailableApps() }
                        )
                    }
                }
            ) { scaffoldComposable() }
            
            
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .testTag(TestTags.SNACKBAR_HOST)
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            )
        }

        NavigationLayout.PERMANENT -> Box(modifier = Modifier.fillMaxSize().testTag(TestTags.PERMANENT_ROOT)) {
            val descNavPane = stringResource(R.string.desc_navigation_pane)
            val descMainPane = stringResource(R.string.desc_main_content_pane)
            Row(modifier = Modifier.fillMaxSize()) {
                if (sidebarExpanded) {
                    PermanentDrawerSheet(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                            .semantics { paneTitle = descNavPane }
                    ) {
                        DrawerContent(
                            isPermanent = true,
                            contentPage = contentPage,
                            realtimeDamData = uiState.damData,
                            realtimeDamConfig = uiState.damConfig,
                            realtimeLoadStatus = uiState.damLoadStatus,
                            appSettings = appSettings,
                            isHistoricalMode = uiState.isHistoricalMode,
                            onRealtimeClick = {
                                viewModel.switchToRealtimeMode()
                                contentPage = ContentPage.MAIN
                            },
                            onHistoricalSearchClick = onHistoricalSearchRequest,
                            onManageHistoricalClick = { contentPage = ContentPage.HISTORICAL_MANAGE },
                            onShowMain = { contentPage = ContentPage.MAIN },
                            historicalMetaList = uiState.historicalMetaList,
                            currentHistoricalMetaId = uiState.historicalDamMeta?.id ?: 0L,
                            getDamConfig = { damId -> net.tecogonaz.tcsameuradammonitor.domain.model.DamListData.allDams.find { it.id == damId } },
                            onSwitchToHistoricalMode = { metaId ->
                                viewModel.switchToHistoricalMode(metaId)
                                contentPage = ContentPage.MAIN
                            },
                            isDailyHistoryEnabled =
                                appSettings?.historicalDataSource == RealtimeDataSource.SUDMONITOR,
                            sudmonitorHistory = if (appSettings?.historicalDataSource == RealtimeDataSource.SUDMONITOR) {
                                uiState.sudmonitorHistory
                            } else {
                                null
                            },
                            isSudmonitorHistoryMode = uiState.isSudmonitorHistoryMode,
                            onSwitchToSudmonitorHistoryMode = {
                                viewModel.switchToSudmonitorHistoryMode()
                                contentPage = ContentPage.MAIN
                            },
                            lastFetchDateStr = lastFetchDateStr,
                            availableApps = uiState.availableApps,
                            onDebugClick = { contentPage = ContentPage.DEBUG },
                            onSettingsClick = { contentPage = ContentPage.SETTINGS },
                            onAppInfoClick = { contentPage = ContentPage.APP_INFO },
                            onOpenUrl = { url -> openUrl(context, url) { viewModel.emitSnackbarMessage(it) } },
                            onOpenUrlWith = { url, app ->
                                openUrlWith(context, url, app) { viewModel.emitSnackbarMessage(it) }
                            },
                            onShareText = { text -> shareText(context, text) { viewModel.emitSnackbarMessage(it) } },
                            onCopied = { viewModel.emitSnackbarMessage(it) },
                            onQueryApps = { viewModel.queryAvailableApps(it) },
                            onClearApps = { viewModel.clearAvailableApps() },
                            onCollapseSidebarClick = { sidebarExpanded = false }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { paneTitle = descMainPane }
                        .consumeWindowInsets(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
                ) {
                    when (contentPage) {
                        ContentPage.MAIN -> scaffoldComposable()
                        ContentPage.OBSERVATION_HISTORY_LIST -> {
                            ObservationHistoryListScreen(
                                viewModel = viewModel,
                                onBackClick = { contentPage = ContentPage.MAIN }
                            )
                        }

                        ContentPage.SETTINGS -> {
                            settingsViewModel?.let { svm ->
                                SettingsScreen(
                                    viewModel = svm,
                                    onBackClick = { contentPage = ContentPage.MAIN }
                                )
                            }
                        }

                        ContentPage.DEBUG -> {
                            settingsViewModel?.let { svm ->
                                DebugScreen(
                                    viewModel = svm,
                                    onBackClick = { contentPage = ContentPage.MAIN },
                                    onDebugLogClick = { contentPage = ContentPage.DEBUG_LOG }
                                )
                            }
                        }

                        ContentPage.DEBUG_LOG -> {
                            val debugLogViewModel: DebugLogViewModel = hiltViewModel()
                            DebugLogScreen(
                                viewModel = debugLogViewModel,
                                onBackClick = { contentPage = ContentPage.DEBUG }
                            )
                        }

                        ContentPage.HISTORICAL_MANAGE -> {
                            HistoricalManageScreen(
                                viewModel = viewModel,
                                onBackClick = { contentPage = ContentPage.MAIN },
                                onShowHistoricalData = { metaId ->
                                    viewModel.switchToHistoricalMode(metaId)
                                    contentPage = ContentPage.MAIN
                                }
                            )
                        }

                        ContentPage.APP_INFO -> {
                            settingsViewModel?.let { svm ->
                                val settings by svm.appSettings.collectAsStateWithLifecycle()
                                AboutScreen(
                                    availableApps = uiState.availableApps,
                                    debugSettingsVisible = settings?.debugSettingsVisible ?: false,
                                    onOpenUrl = { url -> openUrl(context, url) { viewModel.emitSnackbarMessage(it) } },
                                    onOpenUrlWith = { url, app ->
                                        openUrlWith(
                                            context,
                                            url,
                                            app
                                        ) { viewModel.emitSnackbarMessage(it) }
                                    },
                                    onShareText = { text ->
                                        shareText(
                                            context,
                                            text
                                        ) { viewModel.emitSnackbarMessage(it) }
                                    },
                                    onCopied = { viewModel.emitSnackbarMessage(it) },
                                    onQueryApps = { viewModel.queryAvailableApps(it) },
                                    onClearApps = { viewModel.clearAvailableApps() },
                                    onBackClick = { contentPage = ContentPage.MAIN },
                                    onTermsOfUseClick = { contentPage = ContentPage.APP_INFO_TERMS_OF_USE },
                                    onTermsOfUseJaClick = { contentPage = ContentPage.APP_INFO_TERMS_OF_USE_JA },
                                    onLicenseClick = { contentPage = ContentPage.APP_INFO_LICENSE },
                                    onPrivacyPolicyClick = { contentPage = ContentPage.APP_INFO_PRIVACY_POLICY },
                                    onPrivacyPolicyJaClick = { contentPage = ContentPage.APP_INFO_PRIVACY_POLICY_JA },
                                    onOssLicenseClick = { contentPage = ContentPage.APP_INFO_OSS_LICENSE },
                                    onToggleDebugSettingsVisibility = { svm.toggleDebugSettingsVisibility() },
                                    onDebugSettingsEnabledMessage = { viewModel.emitSnackbarMessage(it) }
                                )
                            }
                        }

                        ContentPage.APP_INFO_LICENSE -> {
                            AppInfoLicenseScreen(
                                availableApps = uiState.availableApps,
                                onOpenUrl = { url -> openUrl(context, url) { viewModel.emitSnackbarMessage(it) } },
                                onOpenUrlWith = { url, app ->
                                    openUrlWith(
                                        context,
                                        url,
                                        app
                                    ) { viewModel.emitSnackbarMessage(it) }
                                },
                                onShareText = { text ->
                                    shareText(
                                        context,
                                        text
                                    ) { viewModel.emitSnackbarMessage(it) }
                                },
                                onCopied = { viewModel.emitSnackbarMessage(it) },
                                onQueryApps = { viewModel.queryAvailableApps(it) },
                                onClearApps = { viewModel.clearAvailableApps() },
                                onBackClick = { contentPage = ContentPage.APP_INFO }
                            )
                        }

                        ContentPage.APP_INFO_TERMS_OF_USE -> {
                            AppInfoTermsOfUseScreen(
                                onOpenUrl = { url -> openUrl(context, url) { viewModel.emitSnackbarMessage(it) } },
                                onBackClick = { contentPage = ContentPage.APP_INFO }
                            )
                        }

                        ContentPage.APP_INFO_TERMS_OF_USE_JA -> {
                            AppInfoTermsOfUseJaScreen(
                                onOpenUrl = { url -> openUrl(context, url) { viewModel.emitSnackbarMessage(it) } },
                                onBackClick = { contentPage = ContentPage.APP_INFO }
                            )
                        }

                        ContentPage.APP_INFO_PRIVACY_POLICY -> {
                            AppInfoPrivacyPolicyScreen(
                                onOpenUrl = { url -> openUrl(context, url) { viewModel.emitSnackbarMessage(it) } },
                                onBackClick = { contentPage = ContentPage.APP_INFO }
                            )
                        }

                        ContentPage.APP_INFO_PRIVACY_POLICY_JA -> {
                            AppInfoPrivacyPolicyJaScreen(
                                onOpenUrl = { url -> openUrl(context, url) { viewModel.emitSnackbarMessage(it) } },
                                onBackClick = { contentPage = ContentPage.APP_INFO }
                            )
                        }

                        ContentPage.APP_INFO_OSS_LICENSE -> {
                            AppInfoOssLicenseScreen(
                                availableApps = uiState.availableApps,
                                onOpenUrl = { url -> openUrl(context, url) { viewModel.emitSnackbarMessage(it) } },
                                onOpenUrlWith = { url, app ->
                                    openUrlWith(
                                        context,
                                        url,
                                        app
                                    ) { viewModel.emitSnackbarMessage(it) }
                                },
                                onShareText = { text ->
                                    shareText(
                                        context,
                                        text
                                    ) { viewModel.emitSnackbarMessage(it) }
                                },
                                onCopied = { viewModel.emitSnackbarMessage(it) },
                                onQueryApps = { viewModel.queryAvailableApps(it) },
                                onClearApps = { viewModel.clearAvailableApps() },
                                onBackClick = { contentPage = ContentPage.APP_INFO }
                            )
                        }
                    }
                }
            }
            
            
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .testTag(TestTags.SNACKBAR_HOST)
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            )
        }   
    }   
}

private val ymdSlashFormatterForMainScreen: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd", java.util.Locale.US)

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

private fun String.toYmdSlash(): String =
    if (length == 8) "${substring(0, 4)}/${substring(4, 6)}/${substring(6, 8)}" else this

private fun String.toNextDayYmdSlash(): String =
    runCatching {
        LocalDate.parse(this, DateTimeFormatter.BASIC_ISO_DATE)
            .plusDays(1)
            .format(ymdSlashFormatterForMainScreen)
    }.getOrElse { toYmdSlash() }
