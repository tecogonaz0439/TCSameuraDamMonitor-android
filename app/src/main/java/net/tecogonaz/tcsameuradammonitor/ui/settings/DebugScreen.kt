// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.settings

import android.content.Intent
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugDatSelectionMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.domain.model.getDamConfig
import net.tecogonaz.tcsameuradammonitor.ui.common.mouseDragScroll
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import java.util.Calendar
import java.util.Locale

/**
 * 開発者向けの設定項目（デバッグモードの有効化、デバッグ用観測データの読込や期間指定、
 * データのエクスポート、エラーのシミュレート、DBのVacuum、動作ログの表示など）を提供するデバッグ設定画面のComposable関数です。
 *
 * リストは、タッチ、マウスホイール、およびマウス左ボタンドラッグによる滑らかなスクロールに対応しています。
 *
 * @param viewModel 設定およびデバッグ関連のロジックを管理する [SettingsViewModel]。
 * @param onBackClick 画面左上の戻るボタンがタップされたときの遷移コールバック。メイン画面へ戻ります。
 * @param onDebugLogClick デバッグログ一覧画面（[DebugLogScreen]）へ遷移するためのコールバック。
 * @param showTopAppBar 画面上部の TopAppBar を表示するかどうかのフラグ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onDebugLogClick: () -> Unit = {},
    showTopAppBar: Boolean = true
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isDebugModeSwitching by viewModel.isDebugModeSwitching.collectAsStateWithLifecycle()
    val debugModeSwitchError by viewModel.debugModeSwitchError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var exportUseUtf8 by remember { mutableStateOf(false) }
    var exportKind by remember { mutableStateOf(DebugDatKind.REALTIME) }
    var showDebugDataPeriodDialog by remember { mutableStateOf(false) }
    var hasRealtimeDatFile by remember { mutableStateOf(viewModel.hasRealtimeDatFile()) }
    var hasCurrentRealtimeDatFile by remember { mutableStateOf(viewModel.hasCurrentRealtimeDatFile()) }
    var hasHistoricalDailyDatFile by remember { mutableStateOf(false) }

    val realtimeSafLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.updateDebugRealtimeDatFileUri(uri.toString())
        }
    }
    val historicalDailySafLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.updateDebugHistoricalDailyDatFileUri(uri.toString())
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val error = viewModel.exportDatFile(exportKind, uri, exportUseUtf8)
                if (error != null) {
                    snackbarHostState.showSnackbar(error)
                }
            }
        }
    }
    LaunchedEffect(settings?.debugModeEnabled) {
        hasRealtimeDatFile = viewModel.hasRealtimeDatFile()
        hasCurrentRealtimeDatFile = viewModel.hasCurrentRealtimeDatFile()
        hasHistoricalDailyDatFile = viewModel.hasHistoricalDailyDatFile()
    }
    LaunchedEffect(debugModeSwitchError) {
        debugModeSwitchError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeDebugModeSwitchError()
        }
    }

    Scaffold(
        topBar = {
            if (showTopAppBar) {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.title_activity_debug)) },
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
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .mouseDragScroll(scrollState, Orientation.Vertical)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                SettingsCategoryTitle(stringResource(id = R.string.settings_header_debug))
                if (BuildConfig.DEBUG) {
                    SettingItemSwitch(
                        title = stringResource(id = R.string.settings_debug_mode),
                        description = stringResource(id = R.string.settings_debug_mode_desc),
                        checked = currentSettings.debugModeEnabled,
                        enabled = !isDebugModeSwitching,
                        onCheckedChange = viewModel::updateDebugModeEnabled
                    )
                    AnimatedVisibility(visible = currentSettings.debugModeEnabled) {
                        Column {
                            var realtimeDisplayName by remember { mutableStateOf("") }
                            var historicalDisplayName by remember { mutableStateOf("") }
                            val defaultName = stringResource(id = R.string.settings_debug_default_used)
                            LaunchedEffect(currentSettings.debugRealtimeDatFileUri) {
                                realtimeDisplayName = viewModel.resolveDisplayName(
                                    currentSettings.debugRealtimeDatFileUri,
                                    defaultName
                                )
                            }
                            LaunchedEffect(currentSettings.debugHistoricalDailyDatFileUri) {
                                historicalDisplayName = viewModel.resolveDisplayName(
                                    currentSettings.debugHistoricalDailyDatFileUri,
                                    defaultName
                                )
                            }
                            DebugDatSelector(
                                title = stringResource(R.string.settings_debug_dat_file_realtime_title),
                                mode = currentSettings.debugRealtimeDatFileMode,
                                selectedDisplayName = realtimeDisplayName,
                                latestLabel = stringResource(R.string.settings_debug_dat_file_latest_realtime),
                                selectedLabel = stringResource(R.string.settings_debug_dat_file_select_realtime),
                                latestEnabled = hasRealtimeDatFile,
                                onModeSelected = viewModel::updateDebugRealtimeDatFileMode,
                                onSelectFile = { realtimeSafLauncher.launch(arrayOf("*/*")) }
                            )
                            DebugDatSelector(
                                title = stringResource(R.string.settings_debug_dat_file_historical_daily_title),
                                mode = currentSettings.debugHistoricalDailyDatFileMode,
                                selectedDisplayName = historicalDisplayName,
                                latestLabel = stringResource(R.string.settings_debug_dat_file_latest_historical_daily),
                                selectedLabel = stringResource(R.string.settings_debug_dat_file_select_historical_daily),
                                latestEnabled = hasHistoricalDailyDatFile,
                                onModeSelected = viewModel::updateDebugHistoricalDailyDatFileMode,
                                onSelectFile = { historicalDailySafLauncher.launch(arrayOf("*/*")) }
                            )
                        }
                    }
                    AnimatedVisibility(visible = currentSettings.debugModeEnabled) {
                        Column {
                            var showPeriodDropdown by remember { mutableStateOf(false) }
                            var debugDataTimes by remember(
                                currentSettings.debugRealtimeDatFileMode,
                                currentSettings.debugRealtimeDatFileUri
                            ) {
                                mutableStateOf<List<Long>>(emptyList())
                            }
                            LaunchedEffect(
                                currentSettings.debugRealtimeDatFileMode,
                                currentSettings.debugRealtimeDatFileUri,
                                currentSettings.debugModeEnabled
                            ) {
                                debugDataTimes = if (currentSettings.debugModeEnabled) {
                                    viewModel.resolveDebugDataTimes(currentSettings)
                                } else {
                                    emptyList()
                                }
                            }
                            val allPeriodLabel = stringResource(id = R.string.settings_debug_data_period_all)
                            val periodLabel = debugPeriodLabel(
                                startMillis = debugDataTimes.firstOrNull(),
                                endMillis = currentSettings.debugRealtimeDataEndMillis,
                                maxMillis = debugDataTimes.lastOrNull(),
                                allPeriodLabel = allPeriodLabel
                            )
                            Box {
                                SettingItemClickable(
                                    title = stringResource(id = R.string.settings_debug_data_period),
                                    subtitle = periodLabel,
                                    onClick = { showPeriodDropdown = true }
                                )
                                DropdownMenu(
                                    expanded = showPeriodDropdown,
                                    onDismissRequest = { showPeriodDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.settings_debug_data_period_all)) },
                                        onClick = {
                                            showPeriodDropdown = false
                                            viewModel.updateDebugRealtimeDataPeriod(null, null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(id = R.string.settings_debug_data_period_custom)) },
                                        onClick = {
                                            showPeriodDropdown = false
                                            showDebugDataPeriodDialog = true
                                        }
                                    )
                                }
                            }
                            SettingItemSwitch(
                                title = stringResource(id = R.string.settings_debug_data_period_auto_advance),
                                description = stringResource(id = R.string.settings_debug_data_period_auto_advance_desc),
                                checked = currentSettings.debugRealtimeDataPeriodAutoAdvanceEnabled,
                                onCheckedChange = viewModel::updateDebugRealtimeDataPeriodAutoAdvanceEnabled
                            )
                        }
                    }
                    AnimatedVisibility(visible = !currentSettings.debugModeEnabled) {
                        Column {
                            if (hasCurrentRealtimeDatFile) {
                                DebugDatExportItem(
                                    title = stringResource(R.string.settings_debug_export_realtime_dat_file),
                                    subtitle = stringResource(R.string.settings_debug_export_realtime_dat_file_desc),
                                    onExport = { useUtf8 ->
                                        coroutineScope.launch {
                                            exportKind = DebugDatKind.REALTIME
                                            exportUseUtf8 = useUtf8
                                            exportLauncher.launch(
                                                viewModel.resolveExportFileName(DebugDatKind.REALTIME, useUtf8)
                                            )
                                        }
                                    }
                                )
                            }
                            if (hasHistoricalDailyDatFile) {
                                DebugDatExportItem(
                                    title = stringResource(R.string.settings_debug_export_historical_daily_dat_file),
                                    subtitle = stringResource(R.string.settings_debug_export_historical_daily_dat_file_desc),
                                    onExport = { useUtf8 ->
                                        coroutineScope.launch {
                                            exportKind = DebugDatKind.HISTORICAL_DAILY
                                            exportUseUtf8 = useUtf8
                                            exportLauncher.launch(
                                                viewModel.resolveExportFileName(DebugDatKind.HISTORICAL_DAILY, useUtf8)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                    AnimatedVisibility(visible = currentSettings.debugModeEnabled) {
                        var showSimulateDropdown by remember { mutableStateOf(false) }
                        val simulateModeLabel = when (currentSettings.debugSimulateMode) {
                            DebugSimulateMode.NONE -> stringResource(id = R.string.settings_debug_simulate_mode_none)
                            DebugSimulateMode.NETWORK_UNAVAILABLE -> stringResource(id = R.string.settings_debug_simulate_mode_network_unavailable)
                            DebugSimulateMode.LOADING_FAILURE -> stringResource(id = R.string.settings_debug_simulate_mode_loading_failure)
                        }
                        Box {
                            SettingItemClickable(
                                title = stringResource(id = R.string.settings_debug_simulate_mode),
                                subtitle = simulateModeLabel,
                                onClick = { showSimulateDropdown = true }
                            )
                            DropdownMenu(
                                expanded = showSimulateDropdown,
                                onDismissRequest = { showSimulateDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.settings_debug_simulate_mode_none)) },
                                    onClick = {
                                        showSimulateDropdown = false
                                        viewModel.updateDebugSimulateMode(DebugSimulateMode.NONE)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.settings_debug_simulate_mode_network_unavailable)) },
                                    onClick = {
                                        showSimulateDropdown = false
                                        viewModel.updateDebugSimulateMode(DebugSimulateMode.NETWORK_UNAVAILABLE)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.settings_debug_simulate_mode_loading_failure)) },
                                    onClick = {
                                        showSimulateDropdown = false
                                        viewModel.updateDebugSimulateMode(DebugSimulateMode.LOADING_FAILURE)
                                    }
                                )
                            }
                        }
                    }
                    SettingItemClickable(
                        title = stringResource(R.string.settings_database_vacuum),
                        subtitle = stringResource(R.string.settings_database_vacuum_desc),
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(viewModel.vacuumDatabase())
                            }
                        }
                    )
                }
                SettingItemClickable(
                    title = stringResource(R.string.settings_debug_log),
                    subtitle = stringResource(R.string.settings_debug_log_desc),
                    onClick = { onDebugLogClick() }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingItemClickable(
                    title = stringResource(R.string.settings_disable_debug),
                    onClick = {
                        viewModel.toggleDebugSettingsVisibility()
                        onBackClick()
                    }
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    if (showDebugDataPeriodDialog) {
        val currentSettings = settings
        if (currentSettings != null) {
            DebugDataPeriodDialog(
                settings = currentSettings,
                viewModel = viewModel,
                onDismiss = { showDebugDataPeriodDialog = false },
                onSave = { endMillis ->
                    viewModel.updateDebugRealtimeDataPeriod(null, endMillis)
                    showDebugDataPeriodDialog = false
                }
            )
        }
    }
}

@Composable
private fun DebugDatSelector(
    title: String,
    mode: DebugDatSelectionMode,
    selectedDisplayName: String,
    latestLabel: String,
    selectedLabel: String,
    latestEnabled: Boolean,
    onModeSelected: (DebugDatSelectionMode) -> Unit,
    onSelectFile: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val bundledLabel = stringResource(R.string.settings_debug_dat_file_builtin)
    val currentLabel = when (mode) {
        DebugDatSelectionMode.BUNDLED -> bundledLabel
        DebugDatSelectionMode.LATEST -> latestLabel
        DebugDatSelectionMode.USER_SELECTED -> selectedDisplayName.ifEmpty { selectedLabel }
    }
    Box {
        SettingItemClickable(title = title, subtitle = currentLabel, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(bundledLabel) },
                onClick = {
                    expanded = false
                    onModeSelected(DebugDatSelectionMode.BUNDLED)
                }
            )
            DropdownMenuItem(
                text = { Text(latestLabel) },
                enabled = latestEnabled,
                onClick = {
                    expanded = false
                    onModeSelected(DebugDatSelectionMode.LATEST)
                }
            )
            DropdownMenuItem(
                text = { Text(selectedLabel) },
                onClick = {
                    expanded = false
                    onSelectFile()
                }
            )
        }
    }
}

@Composable
private fun DebugDatExportItem(
    title: String,
    subtitle: String,
    onExport: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingItemClickable(title = title, subtitle = subtitle, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_debug_export_encoding_original)) },
                onClick = {
                    expanded = false
                    onExport(false)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_debug_export_encoding_utf8)) },
                onClick = {
                    expanded = false
                    onExport(true)
                }
            )
        }
    }
}

private fun Long.toDebugDisplayDateTime(): String {
    val cal = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply { timeInMillis = this@toDebugDisplayDateTime }
    return String.format(
        Locale.US,
        "%04d/%02d/%02d %02d:%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE)
    )
}

private fun debugPeriodLabel(
    startMillis: Long?,
    endMillis: Long?,
    maxMillis: Long?,
    allPeriodLabel: String
): String {
    if (startMillis == null || maxMillis == null || endMillis == null) {
        return allPeriodLabel
    }
    val effectiveEndMillis = if (endMillis >= maxMillis) maxMillis else endMillis
    if (effectiveEndMillis == maxMillis) {
        return allPeriodLabel
    }
    return "${startMillis.toDebugDisplayDateTime()} - " +
        TimeUtils.appendJstSuffix(effectiveEndMillis.toDebugDisplayDateTime())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugDataPeriodDialog(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onSave: (endMillis: Long) -> Unit
) {
    val context = LocalContext.current
    val appLocaleTag = LocaleUtils.effectiveLocale(context).toLanguageTag()
    val damConfig = getDamConfig(settings.targetDamId)
    val damName = LocaleUtils.normalDamName(appLocaleTag, damConfig)
    var availableTimes by remember(
        settings.debugRealtimeDatFileMode,
        settings.debugRealtimeDatFileUri
    ) { mutableStateOf<List<Long>>(emptyList()) }
    var selectedEndMillis by remember(settings.debugRealtimeDataEndMillis) {
        mutableStateOf(settings.debugRealtimeDataEndMillis)
    }
    var activeDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(settings.debugRealtimeDatFileMode, settings.debugRealtimeDatFileUri) {
        availableTimes = viewModel.resolveDebugDataTimes(settings)
        if (selectedEndMillis == null) {
            selectedEndMillis = availableTimes.lastOrNull()
        }
    }

    val minMillis = availableTimes.firstOrNull()
    val maxMillis = availableTimes.lastOrNull()
    val localError = if (availableTimes.isEmpty()) {
        stringResource(R.string.settings_debug_export_no_data)
    } else {
        null
    }

    fun roundedEndMillis(): Long? {
        val selected = selectedEndMillis ?: return maxMillis
        return availableTimes.lastOrNull { it <= selected } ?: availableTimes.firstOrNull()
    }

    fun applyRoundedEnd(millis: Long) {
        selectedEndMillis = availableTimes.lastOrNull { it <= millis } ?: availableTimes.firstOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_debug_data_period)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.historical_search_dam_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = damName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.historical_search_start_date),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = minMillis?.toDebugDisplayDateTime()?.substringBefore(" ")
                            ?: stringResource(R.string.historical_search_date_not_selected),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_debug_data_period_start_time),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = minMillis?.toDebugDisplayDateTime()?.substringAfter(" ")
                            ?: stringResource(R.string.historical_search_date_not_selected),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = availableTimes.isNotEmpty(),
                            onClick = { activeDatePicker = true },
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.desc_select_date)
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_debug_data_period_end_date),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = selectedEndMillis?.toDebugDisplayDateTime()?.substringBefore(" ")
                            ?: stringResource(R.string.historical_search_date_not_selected),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = availableTimes.isNotEmpty(),
                            onClick = {
                                val base = selectedEndMillis ?: maxMillis ?: return@clickable
                                val cal = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply { timeInMillis = base }
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val updated = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply {
                                            timeInMillis = base
                                            set(Calendar.HOUR_OF_DAY, hour)
                                            set(Calendar.MINUTE, minute)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.timeInMillis
                                        applyRoundedEnd(updated)
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            role = Role.Button
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_debug_data_period_end_time),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = selectedEndMillis?.toDebugDisplayDateTime()?.substringAfter(" ")
                            ?: stringResource(R.string.historical_search_date_not_selected),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                localError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (localError == null && minMillis != null && roundedEndMillis() != null) {
                    Text(
                        text = stringResource(
                            R.string.settings_debug_data_period_note,
                            minMillis.toDebugDisplayDateTime(),
                            TimeUtils.appendJstSuffix(roundedEndMillis()!!.toDebugDisplayDateTime())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                roundedEndMillis()?.takeIf { selectedEndMillis != null && it != selectedEndMillis }?.let {
                    Text(
                        text = it.toDebugDisplayDateTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { roundedEndMillis()?.let(onSave) },
                enabled = availableTimes.isNotEmpty()
            ) {
                Text(stringResource(R.string.settings_debug_data_period_set_button))
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(
                    onClick = {
                        selectedEndMillis = maxMillis
                    },
                    enabled = availableTimes.isNotEmpty()
                ) {
                    Text(stringResource(R.string.dialog_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )

    if (activeDatePicker && minMillis != null && maxMillis != null) {
        val selectableDates = remember(minMillis, maxMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val dayStart = TimeUtils.utcMidnightToJstDayStart(utcTimeMillis)
                    val minDay = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply {
                        timeInMillis = minMillis
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val maxDay = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply {
                        timeInMillis = maxMillis
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    return dayStart in minDay..maxDay
                }
            }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TimeUtils.toUtcMidnightFromJstDay(selectedEndMillis ?: maxMillis),
            selectableDates = selectableDates
        )
        DatePickerDialog(
            onDismissRequest = { activeDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val newDateStart = TimeUtils.utcMidnightToJstDayStart(utcMillis)
                        val current = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply {
                            timeInMillis = selectedEndMillis ?: maxMillis
                        }
                        selectedEndMillis = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply {
                            timeInMillis = newDateStart
                            set(Calendar.HOUR_OF_DAY, current.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, current.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis.let { candidate ->
                            availableTimes.lastOrNull { it <= candidate } ?: availableTimes.firstOrNull()
                        }
                    }
                    activeDatePicker = false
                }) { Text(stringResource(R.string.dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { activeDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
