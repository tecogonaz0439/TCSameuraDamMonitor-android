// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppTheme
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DamListData
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.StorageRateMessageCategory
import net.tecogonaz.tcsameuradammonitor.domain.model.SUDMONITOR_HOST
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch
import net.tecogonaz.tcsameuradammonitor.ui.common.mouseDragScroll

internal const val TARGET_DAM_SELECTOR_TEST_TAG = "settings_target_dam_selector"

/**
 * アプリのシステム設定、更新間隔、通知、対象ダム、および「貯水率メッセージ」などの
 * カスタムメッセージ編集機能を提供する設定画面のメインComposable関数です。
 *
 * タッチ操作、マウスホイール、およびマウス左ボタンドラッグによる滑らかなスクロールに対応しています。
 *
 * @param viewModel 設定処理を担当する [SettingsViewModel]。
 * @param onBackClick 画面左上の戻るボタンが押されたときの遷移コールバック。冪等な画面遷移ルートへ戻るために使用されます。
 * @param showTopAppBar 画面上部の TopAppBar を表示するかどうかのフラグ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    showTopAppBar: Boolean = true
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val notificationDeniedMsg = stringResource(R.string.settings_notification_permission_denied)
    val damLockedMsg = stringResource(R.string.settings_data_source_dam_locked)
    var showDamSelectionDialog by remember { mutableStateOf(false) }

    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshForLocaleChange()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.updateShowNotification(true)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(notificationDeniedMsg)
            }
        }
    }

    Scaffold(
        topBar = {
            if (showTopAppBar) {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.title_activity_settings)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(id = R.string.desc_back)
                            )
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        settings?.let { currentSettings ->
            val settingsScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .mouseDragScroll(settingsScrollState, Orientation.Vertical)
                    .verticalScroll(settingsScrollState)
                    .padding(16.dp)
            ) {
                
                SettingsCategoryTitle(stringResource(id = R.string.settings_header_system))
                
                val effectiveLocale = LocalConfiguration.current.locales[0]
                
                val isAppLocaleSystemDefault by viewModel.isAppLocaleSystemDefault.collectAsStateWithLifecycle()
                val languageSubtitle = if (isAppLocaleSystemDefault) {
                    stringResource(R.string.settings_system_language_system_default, effectiveLocale.displayName)
                } else {
                    effectiveLocale.displayName
                }
                SettingItemClickable(
                    title = stringResource(id = R.string.settings_system_language),
                    subtitle = languageSubtitle,
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
                ThemeSettingItem(
                    currentTheme = currentSettings.theme,
                    onThemeSelected = { newTheme ->
                        viewModel.updateTheme(newTheme)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val dataSourceLocale = LocaleUtils.effectiveLocale(context)
                val isRealtimeSudmonitor =
                    currentSettings.realtimeDataSource == RealtimeDataSource.SUDMONITOR
                val selectedDamId = if (isRealtimeSudmonitor) {
                    AppSettings.DEFAULT_DAM_ID
                } else {
                    currentSettings.targetDamId
                }
                val selectedDam = DamListData.allDams.find { it.id == selectedDamId }
                    ?: DamListData.allDams.find { it.id == "90" }
                    ?: DamListData.allDams.first()

                SettingsCategoryTitle(stringResource(id = R.string.settings_header_data_source))
                RealtimeDataSourceSettingItem(
                    currentSource = currentSettings.realtimeDataSource,
                    onSourceSelected = { viewModel.updateRealtimeDataSource(it) }
                )
                HistoricalDataSourceSettingItem(
                    currentSource = currentSettings.historicalDataSource,
                    onSourceSelected = { viewModel.updateHistoricalDataSource(it) }
                )
                SettingItemClickable(
                    title = stringResource(id = R.string.settings_dam_dam_name),
                    subtitle = LocaleUtils.normalDamName(dataSourceLocale.toLanguageTag(), selectedDam),
                    onClick = {
                        if (isRealtimeSudmonitor) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(damLockedMsg)
                            }
                        } else {
                            showDamSelectionDialog = true
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                
                SettingsCategoryTitle(stringResource(id = R.string.settings_header_general))
                SettingItemSwitch(
                    title = stringResource(id = R.string.settings_general_show_notification),
                    description = stringResource(id = R.string.settings_general_show_notification_desc),
                    checked = currentSettings.showNotification,
                    enabled = true,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            val isGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (!isGranted) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@SettingItemSwitch
                            }
                        }
                        viewModel.updateShowNotification(isChecked)
                    }
                )

                SettingItemSwitch(
                    title = stringResource(id = R.string.settings_general_update_on_boot),
                    description = stringResource(id = R.string.settings_general_update_on_boot_desc),
                    checked = currentSettings.updateOnBoot,
                    onCheckedChange = { viewModel.updateUpdateOnBoot(it) }
                )

                SettingItemSwitch(
                    title = stringResource(id = R.string.settings_general_auto_update),
                    description = stringResource(id = R.string.settings_general_auto_update_desc),
                    checked = currentSettings.autoUpdateEnabled,
                    onCheckedChange = { isChecked ->
                        
                        viewModel.updateAutoUpdateEnabled(isChecked)
                    }
                )

                
                AnimatedVisibility(visible = currentSettings.autoUpdateEnabled) {
                    AutoUpdateIntervalSettingItem(
                        currentInterval = currentSettings.autoUpdateInterval,
                        onIntervalSelected = { viewModel.updateAutoUpdateInterval(it) }
                    )
                }

                AnimatedVisibility(visible = currentSettings.autoUpdateEnabled) {
                    Column {
                        
                        if (currentSettings.lastAutoUpdateMillis > 0L) {
                            val lastSubtitle = TimeUtils.appendJstSuffix(
                                if (currentSettings.autoUpdateInterval == AutoUpdateInterval.ONE_WEEK) {
                                TimeUtils.formatToJstWithLocale(currentSettings.lastAutoUpdateMillis, "MM/dd(EEE) HH:mm", LocaleUtils.effectiveLocale(context))
                            } else {
                                TimeUtils.formatToJst(currentSettings.lastAutoUpdateMillis, "MM/dd HH:mm")
                            }
                            )
                            SettingItemReadOnly(
                                title = stringResource(id = R.string.settings_general_auto_update_last),
                                subtitle = lastSubtitle
                            )
                        }
                        
                        AutoUpdateNextTimingSettingItem(
                            settings = currentSettings,
                            onTimingConfirmed = { millis -> viewModel.updateAutoUpdateCustomTiming(millis) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                
                SettingsCategoryTitle(stringResource(id = R.string.settings_general_messages_sameura))
                SettingItemSwitch(
                    title = stringResource(id = R.string.settings_show_storage_message),
                    description = stringResource(id = R.string.settings_show_storage_message_desc),
                    checked = currentSettings.showStorageRateMessage,
                    onCheckedChange = { viewModel.updateShowStorageRateMessage(it) }
                )
                AnimatedVisibility(visible = currentSettings.showStorageRateMessage) {
                    Column {
                        SettingsCategoryTitle(stringResource(id = R.string.settings_storage_messages_sameura_title))
                        AppSettings.STORAGE_RATE_THRESHOLD_KEYS.forEach { threshold ->
                            val level = currentSettings.getThresholdState(threshold)
                            val title = when (threshold) {
                                "80_100" -> stringResource(id = R.string.settings_storage_rate_label_80_100)
                                "60_80" -> stringResource(id = R.string.settings_storage_rate_label_60_80)
                                "40_60" -> stringResource(id = R.string.settings_storage_rate_label_40_60)
                                "20_40" -> stringResource(id = R.string.settings_storage_rate_label_20_40)
                                "0_20" -> stringResource(id = R.string.settings_storage_rate_label_0_20)
                                "0" -> stringResource(id = R.string.settings_storage_rate_label_0)
                                "all_abnormal" -> stringResource(id = R.string.settings_storage_no_data)
                                "all_data_invalid" -> stringResource(id = R.string.settings_all_data_invalid_message)
                                else -> threshold
                            }
                            val defaultState = when (threshold) {
                                "80_100" -> "😊"
                                "60_80" -> "😌"
                                "40_60" -> "😨"
                                "20_40" -> "😰"
                                "0_20" -> "😱"
                                "0" -> "😇"
                                "all_abnormal" -> "😑"
                                "all_data_invalid" -> "😴"
                                else -> ""
                            }
                            val defaultMsgJaResId = when (threshold) {
                                "80_100" -> R.string.storage_default_msg_ja_80_100
                                "60_80" -> R.string.storage_default_msg_ja_60_80
                                "40_60" -> R.string.storage_default_msg_ja_40_60
                                "20_40" -> R.string.storage_default_msg_ja_20_40
                                "0_20" -> R.string.storage_default_msg_ja_0_20
                                "0" -> R.string.storage_default_msg_ja_0
                                "all_abnormal" -> R.string.storage_default_msg_ja_all_abnormal
                                "all_data_invalid" -> R.string.storage_default_msg_ja_all_data_invalid
                                else -> 0
                            }
                            val defaultMsgNonJaResId = when (threshold) {
                                "80_100" -> R.string.storage_default_msg_non_ja_80_100
                                "60_80" -> R.string.storage_default_msg_non_ja_60_80
                                "40_60" -> R.string.storage_default_msg_non_ja_40_60
                                "20_40" -> R.string.storage_default_msg_non_ja_20_40
                                "0_20" -> R.string.storage_default_msg_non_ja_0_20
                                "0" -> R.string.storage_default_msg_non_ja_0
                                "all_abnormal" -> R.string.storage_default_msg_non_ja_all_abnormal
                                "all_data_invalid" -> R.string.storage_default_msg_non_ja_all_data_invalid
                                else -> 0
                            }
                            StorageMessageEditSettingItem(
                                title = title, threshold = threshold,
                                initialState = level.state,
                                initialMessageNonJa = level.msg,
                                initialMessageJa = level.msgJa,
                                defaultState = defaultState,
                                defaultMessageJa = stringResource(defaultMsgJaResId),
                                defaultMessageNonJa = stringResource(defaultMsgNonJaResId)
                            ) { s, mnj, mj ->
                                viewModel.updateStateAndMessage(threshold, s, mnj, mj, forOtherMessages = false)
                            }
                        }

                        SettingItemResetAllMessages(
                            showDropdown = true,
                            needsConfirmation = { mode -> viewModel.needsStorageRateResetConfirmation(mode) },
                            onReset = { mode -> viewModel.resetStorageRateMessages(mode) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        SettingsCategoryTitle(stringResource(id = R.string.settings_storage_messages_other_title))
                        AppSettings.STORAGE_RATE_THRESHOLD_KEYS.forEach { threshold ->
                            val level = currentSettings.getThresholdState(threshold, StorageRateMessageCategory.OTHER)
                            val title = when (threshold) {
                                "80_100" -> "80-100%"
                                "60_80" -> "60-80%"
                                "40_60" -> "40-60%"
                                "20_40" -> "20-40%"
                                "0_20" -> "0-20%"
                                "0" -> "0%"
                                "all_abnormal" -> stringResource(id = R.string.settings_storage_no_data)
                                "all_data_invalid" -> stringResource(id = R.string.settings_all_data_invalid_message)
                                else -> threshold
                            }
                            val defaultState = when (threshold) {
                                "80_100" -> "😊"
                                "60_80" -> "😌"
                                "40_60" -> "😨"
                                "20_40" -> "😰"
                                "0_20" -> "😱"
                                "0" -> "😇"
                                "all_abnormal" -> "😑"
                                "all_data_invalid" -> "😴"
                                else -> ""
                            }
                            val defaultMsgJaResId = when (threshold) {
                                "all_abnormal" -> R.string.storage_default_msg_ja_all_abnormal
                                "all_data_invalid" -> R.string.storage_default_msg_ja_all_data_invalid
                                "80_100" -> R.string.storage_default_msg_non_ja_80_100
                                "60_80" -> R.string.storage_default_msg_non_ja_60_80
                                "40_60" -> R.string.storage_default_msg_non_ja_40_60
                                "20_40" -> R.string.storage_default_msg_non_ja_20_40
                                "0_20" -> R.string.storage_default_msg_non_ja_0_20
                                "0" -> R.string.storage_default_msg_non_ja_0
                                else -> 0
                            }
                            val defaultMsgNonJaResId = when (threshold) {
                                "80_100" -> R.string.storage_default_msg_non_ja_80_100
                                "60_80" -> R.string.storage_default_msg_non_ja_60_80
                                "40_60" -> R.string.storage_default_msg_non_ja_40_60
                                "20_40" -> R.string.storage_default_msg_non_ja_20_40
                                "0_20" -> R.string.storage_default_msg_non_ja_0_20
                                "0" -> R.string.storage_default_msg_non_ja_0
                                "all_abnormal" -> R.string.storage_default_msg_non_ja_all_abnormal
                                "all_data_invalid" -> R.string.storage_default_msg_non_ja_all_data_invalid
                                else -> 0
                            }
                            StorageMessageEditSettingItem(
                                title = title, threshold = threshold,
                                initialState = level.state,
                                initialMessageNonJa = level.msg,
                                initialMessageJa = level.msgJa,
                                defaultState = defaultState,
                                defaultMessageJa = stringResource(defaultMsgJaResId),
                                defaultMessageNonJa = stringResource(defaultMsgNonJaResId)
                            ) { s, mnj, mj ->
                                viewModel.updateStateAndMessage(
                                    threshold, s, mnj, mj,
                                    forOtherMessages = false,
                                    category = StorageRateMessageCategory.OTHER
                                )
                            }
                        }

                        SettingItemResetAllMessages(
                            showDropdown = false,
                            needsConfirmation = { viewModel.needsOtherStorageRateResetConfirmation() },
                            onReset = { viewModel.resetOtherStorageRateMessages() }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                
                SettingsCategoryTitle(stringResource(id = R.string.settings_header_other_messages))
                OtherMessageEditSettingItem(
                    title = stringResource(id = R.string.settings_initial_message), threshold = "initial_message",
                    initialState = currentSettings.stateInitialMessage,
                    initialMessageNonJa = currentSettings.msgInitialMessage,
                    initialMessageJa = currentSettings.msgInitialMessageJa,
                    defaultState = stringResource(id = R.string.main_emoji_initial_message),
                    defaultMessageJa = stringResource(R.string.storage_default_msg_ja_initial_message),
                    defaultMessageNonJa = stringResource(R.string.storage_default_msg_non_ja_initial_message),
                    onSave = { s, mnj, mj -> viewModel.updateStateAndMessage("initial_message", s, mnj, mj, forOtherMessages = true) }
                )
                OtherMessageEditSettingItem(
                    title = stringResource(id = R.string.settings_network_unavailable_message), threshold = "network_unavailable",
                    initialState = currentSettings.stateNetworkUnavailable,
                    initialMessageNonJa = currentSettings.msgNetworkUnavailable,
                    initialMessageJa = currentSettings.msgNetworkUnavailableJa,
                    defaultState = stringResource(id = R.string.main_emoji_network_unavailable),
                    defaultMessageJa = stringResource(R.string.storage_default_msg_ja_network_unavailable),
                    defaultMessageNonJa = stringResource(R.string.storage_default_msg_non_ja_network_unavailable),
                    onSave = { s, mnj, mj -> viewModel.updateStateAndMessage("network_unavailable", s, mnj, mj, forOtherMessages = true) }
                )
                OtherMessageEditSettingItem(
                    title = stringResource(id = R.string.settings_loading_error_message), threshold = "loading_error",
                    initialState = currentSettings.stateLoadingError,
                    initialMessageNonJa = currentSettings.msgLoadingError,
                    initialMessageJa = currentSettings.msgLoadingErrorJa,
                    defaultState = stringResource(id = R.string.main_emoji_loading_error),
                    defaultMessageJa = stringResource(R.string.storage_default_msg_ja_loading_error),
                    defaultMessageNonJa = stringResource(R.string.storage_default_msg_non_ja_loading_error),
                    onSave = { s, mnj, mj -> viewModel.updateStateAndMessage("loading_error", s, mnj, mj, forOtherMessages = true) }
                )
                OtherMessageEditSettingItem(
                    title = stringResource(id = R.string.settings_data_distribution_stopped_message), threshold = "data_distribution_stopped",
                    initialState = currentSettings.stateDataDistributionStopped,
                    initialMessageNonJa = currentSettings.msgDataDistributionStopped,
                    initialMessageJa = currentSettings.msgDataDistributionStoppedJa,
                    defaultState = stringResource(id = R.string.main_emoji_data_distribution_stopped),
                    defaultMessageJa = stringResource(R.string.storage_default_msg_ja_data_distribution_stopped),
                    defaultMessageNonJa = stringResource(R.string.storage_default_msg_non_ja_data_distribution_stopped),
                    onSave = { s, mnj, mj -> viewModel.updateStateAndMessage("data_distribution_stopped", s, mnj, mj, forOtherMessages = true) }
                )
                OtherMessageEditSettingItem(
                    title = stringResource(id = R.string.settings_data_distribution_resumed_message), threshold = "data_distribution_resumed",
                    initialState = currentSettings.stateDataDistributionResumed,
                    initialMessageNonJa = currentSettings.msgDataDistributionResumed,
                    initialMessageJa = currentSettings.msgDataDistributionResumedJa,
                    defaultState = stringResource(id = R.string.main_emoji_data_distribution_resumed),
                    defaultMessageJa = stringResource(R.string.storage_default_msg_ja_data_distribution_resumed),
                    defaultMessageNonJa = stringResource(R.string.storage_default_msg_non_ja_data_distribution_resumed),
                    onSave = { s, mnj, mj -> viewModel.updateStateAndMessage("data_distribution_resumed", s, mnj, mj, forOtherMessages = true) }
                )

                var showOtherMessagesConfirmDialog by remember { mutableStateOf(false) }

                SettingItemClickable(
                    title = stringResource(id = R.string.settings_reset_all_other_messages),
                    onClick = {
                        if (viewModel.needsOtherMessagesResetConfirmation()) {
                            showOtherMessagesConfirmDialog = true
                        } else {
                            viewModel.resetOtherMessages()
                        }
                    }
                )

                if (showOtherMessagesConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showOtherMessagesConfirmDialog = false },
                        title = { Text(stringResource(R.string.settings_reset_other_messages_confirm_title)) },
                        text = { Text(stringResource(R.string.settings_reset_other_messages_confirm_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.resetOtherMessages()
                                showOtherMessagesConfirmDialog = false
                            }) {
                                Text(stringResource(R.string.dialog_ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showOtherMessagesConfirmDialog = false }) {
                                Text(stringResource(R.string.dialog_cancel))
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDamSelectionDialog) {
        val currentDam = settings?.let { currentSettings ->
            DamListData.allDams.find { it.id == currentSettings.targetDamId }
        } ?: DamListData.allDams.find { it.id == AppSettings.DEFAULT_DAM_ID }
            ?: DamListData.allDams.first()
        TargetDamSelectionDialog(
            initialDamConfig = currentDam,
            onDismiss = { showDamSelectionDialog = false },
            onConfirm = { damId ->
                showDamSelectionDialog = false
                viewModel.changeTargetDam(damId)
            }
        )
    }

    val pendingRealtimeDataSource by viewModel.pendingRealtimeDataSource.collectAsStateWithLifecycle()
    if (pendingRealtimeDataSource != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelRealtimeDataSourceChange() },
            title = { Text(stringResource(R.string.settings_data_source_switch_confirm_title)) },
            text = { Text(stringResource(R.string.settings_data_source_switch_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRealtimeDataSourceChange() }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRealtimeDataSourceChange() }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

/**
 * リアルタイムデータのデータソース設定項目（ドロップダウン選択）。
 *
 * 自動更新間隔と同じDropdownMenu表示で選択し、選択確定時に [onSourceSelected] を呼ぶ。
 * MLIT_DIRECT→SUDMONITORかつ非早明浦時の確認ダイアログはViewModel側のpendingフローで扱う。
 *
 * @param currentSource 現在選択されているデータソース
 * @param onSourceSelected 選択確定時のコールバック
 */
@Composable
private fun RealtimeDataSourceSettingItem(
    currentSource: RealtimeDataSource,
    onSourceSelected: (RealtimeDataSource) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isSudmonitor = currentSource == RealtimeDataSource.SUDMONITOR
    val selectedLabel = stringResource(
        id = if (isSudmonitor) {
            R.string.settings_data_source_realtime_sudmonitor
        } else {
            R.string.settings_data_source_realtime_mlit
        }
    )
    val helpText = if (isSudmonitor) {
        stringResource(R.string.settings_data_source_realtime_help_sudmonitor, SUDMONITOR_HOST)
    } else {
        stringResource(R.string.settings_data_source_realtime_help_mlit)
    }
    val subtitle = "$selectedLabel\n($helpText)"

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = { expanded = true },
                    role = Role.DropdownList,
                    onClickLabel = stringResource(R.string.desc_select_option)
                )
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.settings_data_source_realtime),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.settings_data_source_realtime_sudmonitor)) },
                onClick = { onSourceSelected(RealtimeDataSource.SUDMONITOR); expanded = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.settings_data_source_realtime_mlit)) },
                onClick = { onSourceSelected(RealtimeDataSource.MLIT_DIRECT); expanded = false }
            )
        }
    }
}

/**
 * 過去データのデータソース設定項目（ドロップダウン選択）。
 *
 * 表示パターンと説明文の括弧書きはリアルタイムデータ側と同じにする。
 *
 * @param currentSource 現在選択されているデータソース
 * @param onSourceSelected 選択確定時のコールバック
 */
@Composable
private fun HistoricalDataSourceSettingItem(
    currentSource: RealtimeDataSource,
    onSourceSelected: (RealtimeDataSource) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isSudmonitor = currentSource == RealtimeDataSource.SUDMONITOR
    val selectedLabel = stringResource(
        id = if (isSudmonitor) {
            R.string.settings_data_source_realtime_sudmonitor
        } else {
            R.string.settings_data_source_historical_mlit
        }
    )
    val helpText = if (isSudmonitor) {
        stringResource(R.string.settings_data_source_historical_help_sudmonitor, SUDMONITOR_HOST)
    } else {
        stringResource(R.string.settings_data_source_historical_help_mlit)
    }
    val subtitle = "$selectedLabel\n($helpText)"

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = { expanded = true },
                    role = Role.DropdownList,
                    onClickLabel = stringResource(R.string.desc_select_option)
                )
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.settings_data_source_historical),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.settings_data_source_realtime_sudmonitor)) },
                onClick = { onSourceSelected(RealtimeDataSource.SUDMONITOR); expanded = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.settings_data_source_historical_mlit)) },
                onClick = { onSourceSelected(RealtimeDataSource.MLIT_DIRECT); expanded = false }
            )
        }
    }
}

@Composable
private fun TargetDamSelectionDialog(
    initialDamConfig: net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val localeTag = LocaleUtils.effectiveLocale(LocalContext.current).toLanguageTag()
    var selectedDamConfig by remember(initialDamConfig.id) { mutableStateOf(initialDamConfig) }
    var showDamSelectDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dam_selection_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TARGET_DAM_SELECTOR_TEST_TAG)
                    .clickable(
                        onClick = { showDamSelectDialog = true },
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.desc_select_dam)
                    )
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_dam_dam_name),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = LocaleUtils.normalDamName(localeTag, selectedDamConfig),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDamConfig.id) }) {
                Text(stringResource(R.string.dam_selection_select_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )

    if (showDamSelectDialog) {
        DamSelectDialog(
            onDismiss = { showDamSelectDialog = false },
            onDamSelected = { damId ->
                DamListData.allDams.find { it.id == damId }?.let {
                    selectedDamConfig = it
                }
                showDamSelectDialog = false
            }
        )
    }
}

@Composable
fun StorageMessageEditSettingItem(
    title: String,
    threshold: String,
    initialState: String,
    initialMessageNonJa: String,
    initialMessageJa: String,
    defaultState: String,
    defaultMessageJa: String,
    defaultMessageNonJa: String,
    onSave: (String, String, String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val effectiveLocale = LocaleUtils.effectiveLocale(androidx.compose.ui.platform.LocalContext.current)
    val isJa = effectiveLocale.language == "ja"
    val displayedMessage = if (isJa) initialMessageJa else initialMessageNonJa
    val description = listOf(initialState, displayedMessage).filter { it.isNotEmpty() }.joinToString(" ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { showDialog = true },
                onClickLabel = stringResource(R.string.desc_edit_message)
            )
            .padding(vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (description.isNotBlank()) description else stringResource(id = R.string.settings_not_set),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        StateMessageEditDialog(
            title = stringResource(id = R.string.dialog_set_title_storage, title),
            initialState = initialState,
            initialMessageNonJa = initialMessageNonJa,
            initialMessageJa = initialMessageJa,
            defaultState = defaultState,
            defaultMessageJa = defaultMessageJa,
            defaultMessageNonJa = defaultMessageNonJa,
            onDismiss = { showDialog = false },
            onSave = { s, mnj, mj ->
                onSave(s, mnj, mj)
                showDialog = false
            }
        )
    }
}

@Composable
fun OtherMessageEditSettingItem(
    title: String,
    threshold: String,
    initialState: String,
    initialMessageNonJa: String,
    initialMessageJa: String,
    defaultState: String,
    defaultMessageJa: String,
    defaultMessageNonJa: String,
    onSave: (String, String, String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val isJa = LocaleUtils.isJapanese(LocalContext.current)
    val displayedMessage = if (isJa) initialMessageJa else initialMessageNonJa
    val description = listOf(initialState, displayedMessage).filter { it.isNotEmpty() }.joinToString(" ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { showDialog = true },
                onClickLabel = stringResource(R.string.desc_edit_message)
            )
            .padding(vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (description.isNotBlank()) description else stringResource(id = R.string.settings_not_set),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        StateMessageEditDialog(
            title = stringResource(id = R.string.dialog_set_title_other, title),
            initialState = initialState,
            initialMessageNonJa = initialMessageNonJa,
            initialMessageJa = initialMessageJa,
            defaultState = defaultState,
            defaultMessageJa = defaultMessageJa,
            defaultMessageNonJa = defaultMessageNonJa,
            onDismiss = { showDialog = false },
            onSave = { s, mnj, mj ->
                onSave(s, mnj, mj)
                showDialog = false
            }
        )
    }
}

@Composable
fun StateMessageEditDialog(
    title: String,
    initialState: String,
    initialMessageNonJa: String,
    initialMessageJa: String,
    defaultState: String,
    defaultMessageJa: String,
    defaultMessageNonJa: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var stateValue by remember { mutableStateOf(initialState) }
    var messageNonJaValue by remember { mutableStateOf(initialMessageNonJa) }
    var messageJaValue by remember { mutableStateOf(initialMessageJa) }

    val isStateValid = stateValue.codePointCount(0, stateValue.length) == 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = stateValue,
                    onValueChange = { stateValue = it },
                    label = { Text(stringResource(id = R.string.dialog_state_label)) },
                    singleLine = true,
                    isError = !isStateValid,
                    supportingText = {
                        if (!isStateValid) {
                            Text(stringResource(id = R.string.dialog_error_one_char_only))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = messageJaValue,
                    onValueChange = { messageJaValue = it },
                    label = { Text(stringResource(id = R.string.dialog_message_ja_label)) },
                    singleLine = false
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = messageNonJaValue,
                    onValueChange = { messageNonJaValue = it },
                    label = { Text(stringResource(id = R.string.dialog_message_non_ja_label)) },
                    singleLine = false
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(stateValue, messageNonJaValue, messageJaValue) },
                enabled = isStateValid
            ) {
                Text(stringResource(id = R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.dialog_cancel)) }
        }
    )
}


/**
 * 貯水率メッセージを一括リセットするための設定項目。
 *
 * [showDropdown] がtrueの場合はリセットモード選択のドロップダウンを表示し、
 * falseの場合はそのまま（必要に応じて確認ダイアログを挟んで）リセットを実行します。
 *
 * @param showDropdown リセットモード選択ドロップダウンを表示するかどうか
 * @param needsConfirmation リセット確認ダイアログを表示する必要があるかどうかをモードから判定するコールバック
 * @param onReset リセットを実行するモードを受け取るコールバック
 */
@Composable
fun SettingItemResetAllMessages(
    showDropdown: Boolean,
    needsConfirmation: (mode: String) -> Boolean,
    onReset: (mode: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmMode by remember { mutableStateOf<String?>(null) }

    Box {
        SettingItemClickable(
            title = stringResource(id = R.string.settings_reset_all_messages),
            onClick = {
                if (showDropdown) {
                    expanded = true
                } else {
                    val mode = "non_ja"
                    if (needsConfirmation(mode)) {
                        confirmMode = mode
                        showConfirmDialog = true
                    } else {
                        onReset(mode)
                    }
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.settings_reset_option_delete)) },
                onClick = {
                    expanded = false
                    if (needsConfirmation("delete")) {
                        confirmMode = "delete"
                        showConfirmDialog = true
                    } else {
                        onReset("delete")
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.settings_reset_option_sameura)) },
                onClick = {
                    expanded = false
                    if (needsConfirmation("ja")) {
                        confirmMode = "ja"
                        showConfirmDialog = true
                    } else {
                        onReset("ja")
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.settings_reset_option_non_ja)) },
                onClick = {
                    expanded = false
                    if (needsConfirmation("non_ja")) {
                        confirmMode = "non_ja"
                        showConfirmDialog = true
                    } else {
                        onReset("non_ja")
                    }
                }
            )
        }

        if (showConfirmDialog) {
            val mode = confirmMode ?: return@Box
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text(stringResource(R.string.settings_reset_storage_messages_confirm_title)) },
                text = { Text(stringResource(R.string.settings_reset_storage_messages_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        onReset(mode)
                        showConfirmDialog = false
                    }) {
                        Text(stringResource(R.string.dialog_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            )
        }
    }
}


@Composable
fun DamSelectDialog(
    onDismiss: () -> Unit,
    onDamSelected: (String) -> Unit
) {
    val isJa = LocaleUtils.isJapanese(LocalContext.current)
    
    val sameuraDam = DamListData.allDams.find { it.id == net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings.DEFAULT_DAM_ID }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_dam_dam_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                val damDialogListState = rememberLazyListState()
                LazyColumn(
                    state = damDialogListState,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .mouseDragScroll(damDialogListState, Orientation.Vertical)
                ) {
                    
                    if (sameuraDam != null) {
                        item {
                            DamSelectItem(
                                dam = sameuraDam,
                                isJa = isJa,
                                onDamSelected = onDamSelected
                            )
                        }
                        
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    
                    items(DamListData.allDams) { dam ->
                        DamSelectItem(
                            dam = dam,
                            isJa = isJa,
                            onDamSelected = onDamSelected
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(id = R.string.dialog_cancel))
                    }
                }
            }
        }
    }
}


@Composable
private fun DamSelectItem(
    dam: net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig,
    isJa: Boolean,
    onDamSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { onDamSelected(dam.id) },
                role = Role.Button,
                onClickLabel = stringResource(R.string.desc_select_dam)
            )
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = LocaleUtils.normalDamName(LocaleUtils.effectiveLocale(androidx.compose.ui.platform.LocalContext.current).toLanguageTag(), dam),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = if (isJa) {
                "${dam.prefecture} / ${dam.waterSystem} / ${dam.river}"
            } else {
                "${dam.prefectureEn} / ${dam.waterSystemEn} / ${dam.riverEn}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Composable
fun AutoUpdateIntervalSettingItem(
    currentInterval: AutoUpdateInterval,
    onIntervalSelected: (AutoUpdateInterval) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val intervalLabel = when (currentInterval) {
        AutoUpdateInterval.ONE_WEEK -> stringResource(id = R.string.settings_general_auto_update_interval_one_week)
        AutoUpdateInterval.ONE_DAY -> stringResource(id = R.string.settings_general_auto_update_interval_one_day)
        AutoUpdateInterval.TWELVE_HOURS -> stringResource(id = R.string.settings_general_auto_update_interval_12_hours)
        AutoUpdateInterval.ONE_HOUR -> stringResource(id = R.string.settings_general_auto_update_interval_one_hour)
    }
    val isShortInterval =
        currentInterval == AutoUpdateInterval.ONE_HOUR || currentInterval == AutoUpdateInterval.TWELVE_HOURS

    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = { expanded = true },
                        role = Role.DropdownList,
                        onClickLabel = stringResource(R.string.desc_select_option)
                    )
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_general_auto_update_interval),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isShortInterval) {
                        "$intervalLabel\n(${stringResource(id = R.string.settings_general_auto_update_daily_history_note)})"
                    } else {
                        intervalLabel
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.settings_general_auto_update_interval_one_week)) },
                    onClick = { onIntervalSelected(AutoUpdateInterval.ONE_WEEK); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.settings_general_auto_update_interval_one_day)) },
                    onClick = { onIntervalSelected(AutoUpdateInterval.ONE_DAY); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.settings_general_auto_update_interval_12_hours)) },
                    onClick = { onIntervalSelected(AutoUpdateInterval.TWELVE_HOURS); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.settings_general_auto_update_interval_one_hour)) },
                    onClick = { onIntervalSelected(AutoUpdateInterval.ONE_HOUR); expanded = false }
                )
            }
        }
    }
}


@Composable
fun AutoUpdateNextTimingSettingItem(
    settings: AppSettings,
    onTimingConfirmed: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val jst = TimeZone.getTimeZone("Asia/Tokyo")
    
    
    val effectiveA = settings.calculateEffectiveNextRunTimeAndA(System.currentTimeMillis()).first

    
    val isFirstRun = settings.isFirstRunAfterReschedule || settings.lastAutoUpdateMillis == 0L
    val isWeekly = settings.autoUpdateInterval == AutoUpdateInterval.ONE_WEEK
    val subtitle = if (isFirstRun) {
        val nextFormatted = TimeUtils.appendJstSuffix(
            if (isWeekly) {
                TimeUtils.formatToJstWithLocale(settings.nextScheduledUpdateMillis, "yyyy/MM/dd(EEE) HH:mm", LocaleUtils.effectiveLocale(context))
            } else {
                TimeUtils.formatToJst(settings.nextScheduledUpdateMillis, "yyyy/MM/dd HH:mm")
            }
        )
        "$nextFormatted\n${stringResource(id = R.string.settings_general_auto_update_rescheduled_warning)}"
    } else {
        stringResource(id = R.string.settings_general_auto_update_next_hint)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { showDialog = true },
                onClickLabel = stringResource(R.string.desc_select_date)
            )
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(id = R.string.settings_general_auto_update_next_timing),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        AutoUpdateNextTimingDialog(
            initialMillis = effectiveA,
            jst = jst,
            onDismiss = { showDialog = false },
            onConfirmed = { millis ->
                onTimingConfirmed(millis)
                showDialog = false
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoUpdateNextTimingDialog(
    initialMillis: Long,
    jst: TimeZone,
    onDismiss: () -> Unit,
    onConfirmed: (Long) -> Unit
) {
    val initialCal = Calendar.getInstance(jst).apply { timeInMillis = initialMillis }

    
    var selectedYear by remember { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(initialCal.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(initialCal.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    
    val selectedMillis = Calendar.getInstance(jst).apply {
        set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val nowMillis = System.currentTimeMillis()
    val isPast = selectedMillis <= nowMillis
    val minAdvanceMillis = nowMillis + AppSettings.MIN_SCHEDULE_ADVANCE_MILLIS
    val isWithinMinAdvance = selectedMillis <= minAdvanceMillis
    val isInvalid = isPast || isWithinMinAdvance

    val dateLabel = String.format(
        LocaleUtils.effectiveLocale(LocalContext.current),
        "%04d/%02d/%02d",
        selectedYear, selectedMonth + 1, selectedDay
    )
    val timeLabel = String.format(
        LocaleUtils.effectiveLocale(LocalContext.current),
        "%02d:%02d",
        selectedHour,
        selectedMinute
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TimeUtils.appendJstSuffix(stringResource(id = R.string.dialog_auto_update_timing_title))) },
        text = {
            Column {
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = { showDatePicker = true },
                            onClickLabel = stringResource(R.string.desc_select_date)
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_auto_update_timing_date),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = { showTimePicker = true },
                            onClickLabel = stringResource(R.string.desc_select_time)
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_auto_update_timing_time),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                if (isInvalid) {
                    Text(
                        text = stringResource(
                            id = if (isPast) R.string.dialog_auto_update_timing_error
                            else R.string.dialog_auto_update_timing_error_min_advance
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isInvalid,
                onClick = {
                    
                    if (!isInvalid) {
                        onConfirmed(selectedMillis)
                    }
                }
            ) {
                Text(stringResource(id = R.string.dialog_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    selectedYear = initialCal.get(Calendar.YEAR)
                    selectedMonth = initialCal.get(Calendar.MONTH)
                    selectedDay = initialCal.get(Calendar.DAY_OF_MONTH)
                    selectedHour = initialCal.get(Calendar.HOUR_OF_DAY)
                    selectedMinute = initialCal.get(Calendar.MINUTE)
                }) {
                    Text(stringResource(id = R.string.dialog_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        }
    )

    
    if (showDatePicker) {
        val utcMidnightMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = millis
                        }
                        selectedYear = utcCal.get(Calendar.YEAR)
                        selectedMonth = utcCal.get(Calendar.MONTH)
                        selectedDay = utcCal.get(Calendar.DAY_OF_MONTH)
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(id = R.string.dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(id = R.string.dialog_auto_update_timing_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    selectedHour = timePickerState.hour
                    selectedMinute = timePickerState.minute
                    showTimePicker = false
                }) {
                    Text(stringResource(id = R.string.dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}
