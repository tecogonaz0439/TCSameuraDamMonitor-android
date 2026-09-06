// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.ui.settings.DamSelectDialog
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private fun parseSearchDateStartMillis(date: String): Long? =
    TimeUtils.parseJstMillis(date, "yyyyMMdd")

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

private fun Long.toHistoricalRangeDisplayDate(): String {
    val cal = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply { timeInMillis = this@toHistoricalRangeDisplayDate }
    return String.format(
        Locale.US,
        "%04d/%02d/%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

private fun Long.toHistoricalRangeSearchDate(): String {
    val cal = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply { timeInMillis = this@toHistoricalRangeSearchDate }
    return String.format(
        Locale.US,
        "%04d%02d%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

private fun Long.toNextDayHistoricalRangeDisplayDate(): String {
    val cal = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply {
        timeInMillis = this@toNextDayHistoricalRangeDisplayDate
        add(Calendar.DAY_OF_MONTH, 1)
    }
    return String.format(
        Locale.US,
        "%04d/%02d/%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}


/**
 * 過去の観測データを検索・取得するための検索ダイアログを表示するComposable関数です。
 *
 * 対象ダムの選択、開始日付、終了日付を指定し、選択期間が正しいかどうかのバリデーション（例: 未来の日付でないか、30日以内の範囲か）を行います。
 * 重複して同じデータを検索しないようにするチェック [onCheckDuplicate] も行います。
 *
 * @param initialDamConfig ダイアログの初期表示に使用するダム設定。
 * @param isLoading 検索（データ取得）処理中かどうかのフラグ。
 * @param errorMessage 取得エラーなどが発生した場合にダイアログ内に表示するエラーメッセージ。
 * @param onSearch 検索実行ボタンが押下されたときのコールバック（ダム設定、開始日付(yyyyMMdd)、終了日付(yyyyMMdd)）。
 * @param onDismiss ダイアログを閉じる操作時のコールバック。
 * @param onCheckDuplicate 同一の検索条件（ダムID、開始日付、終了日付）が既に存在するか判定するサスペンド関数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoricalDataSearchDialog(
    initialDamConfig: DamConfig,
    isLoading: Boolean,
    errorMessage: String?,
    onSearch: (damConfig: DamConfig, startDate: String, endDate: String) -> Unit,
    onDismiss: () -> Unit,
    onCheckDuplicate: suspend (String, String, String) -> Boolean
) {
    val appLocale = LocaleUtils.effectiveLocale(LocalContext.current)
    val appLocaleTag = appLocale.toLanguageTag()
    
    
    val jst = TimeUtils.JST_TIMEZONE
    fun todayStartMillis(): Long = Calendar.getInstance(jst).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val yesterdayMillis = remember {
        Calendar.getInstance(jst).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis
    }
    val thirtyDaysAgoMillis = remember {
        Calendar.getInstance(jst).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -30)
        }.timeInMillis
    }
    val earliestSearchDateMillis = remember {
        Calendar.getInstance(jst).apply {
            set(2002, Calendar.JUNE, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    var startDateMillis by remember { mutableStateOf<Long?>(thirtyDaysAgoMillis) }
    var endDateMillis by remember { mutableStateOf<Long?>(yesterdayMillis) }
    
    var selectedDamConfig by remember { mutableStateOf(initialDamConfig) }
    
    var showDamSelectDialog by remember { mutableStateOf(false) }
    
    var activePicker by remember { mutableStateOf<String?>(null) }
    
    var localError by remember { mutableStateOf<String?>(null) }
    
    var duplicateError by remember { mutableStateOf<String?>(null) }

    
    fun Long.toDisplayDate(): String {
        val cal = Calendar.getInstance(jst).apply { timeInMillis = this@toDisplayDate }
        return String.format(
            appLocale,
            "%04d/%02d/%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    
    fun Long.toSearchDate(): String {
        val cal = Calendar.getInstance(jst).apply { timeInMillis = this@toSearchDate }
        return String.format(
            java.util.Locale.US,
            "%04d%02d%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    
    val historicalSearchSelectableDates = remember(earliestSearchDateMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = utcTimeMillis
                }
                val jstDayStart = Calendar.getInstance(jst).apply {
                    set(
                        utcCal.get(Calendar.YEAR),
                        utcCal.get(Calendar.MONTH),
                        utcCal.get(Calendar.DAY_OF_MONTH),
                        0,
                        0,
                        0
                    )
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                return jstDayStart >= earliestSearchDateMillis && jstDayStart < todayStartMillis()
            }

            override fun isSelectableYear(year: Int): Boolean {
                return year in 2002..Calendar.getInstance(jst).get(Calendar.YEAR)
            }
        }
    }

    val notSelectedLabel = stringResource(R.string.historical_search_date_not_selected)
    val errorDateRange = stringResource(R.string.historical_search_error_date_range)


    val errorDateRangeTooLong = stringResource(R.string.historical_search_error_date_range_too_long)
    val errorTodayDate = stringResource(R.string.historical_search_error_today_date)
    val errorTooOldDate = stringResource(R.string.historical_search_error_too_old_date)
    val errorDuplicate = stringResource(R.string.historical_search_duplicate)
    val periodNoteFormat = stringResource(R.string.historical_search_period_note)

    
    fun validateDates(start: Long?, end: Long?): String? {
        if (start == null || end == null) return notSelectedLabel
        val today = todayStartMillis()
        if (start > end) return errorDateRange
        if (start < earliestSearchDateMillis || end < earliestSearchDateMillis) return errorTooOldDate
        if (end >= today) return errorTodayDate
        if ((end - start) / 86_400_000L > 30L) return errorDateRangeTooLong
        return null
    }

    
    fun Long.toNextDayDisplayDate(): String {
        val cal = Calendar.getInstance(jst).apply {
            timeInMillis = this@toNextDayDisplayDate
            add(Calendar.DAY_OF_MONTH, 1)
        }
        return String.format(
            appLocale,
            "%04d/%02d/%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    
    LaunchedEffect(startDateMillis, endDateMillis, selectedDamConfig) {
        val start = startDateMillis
        val end = endDateMillis
        
        localError = validateDates(start, end)
        
        if (localError == null && start != null && end != null) {
            val isDuplicate = onCheckDuplicate(selectedDamConfig.id, start.toSearchDate(), end.toSearchDate())
            duplicateError = if (isDuplicate) errorDuplicate else null
        } else {
            duplicateError = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.historical_search_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !isLoading,
                            onClick = { showDamSelectDialog = true },
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.desc_select_dam)
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.historical_search_dam_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = LocaleUtils.normalDamName(
                            appLocaleTag,
                            selectedDamConfig
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !isLoading,
                            onClick = { activePicker = "start" },
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.desc_select_date)
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.historical_search_start_date),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = startDateMillis?.toDisplayDate() ?: notSelectedLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !isLoading,
                            onClick = { activePicker = "end" },
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.desc_select_date)
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.historical_search_end_date),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = endDateMillis?.toDisplayDate() ?: notSelectedLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                when {
                    localError != null -> Text(
                        text = localError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )

                    duplicateError != null -> Text(
                        text = duplicateError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )

                    startDateMillis != null && endDateMillis != null -> Text(
                        text = String.format(
                            periodNoteFormat,
                            startDateMillis!!.toDisplayDate(),
                            endDateMillis!!.toNextDayDisplayDate()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    
                    val start = startDateMillis
                    val end = endDateMillis
                    localError = validateDates(start, end)
                    if (localError == null && duplicateError == null && start != null && end != null) {
                        onSearch(selectedDamConfig, start.toSearchDate(), end.toSearchDate())
                    }
                },
                enabled = !isLoading && localError == null && duplicateError == null
            ) {
                Text(
                    if (isLoading) stringResource(R.string.historical_search_searching)
                    else stringResource(R.string.historical_search_button)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isLoading) onDismiss() }, enabled = !isLoading) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    
    if (activePicker == "start") {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TimeUtils.toUtcMidnightFromJstDay(startDateMillis),
            selectableDates = historicalSearchSelectableDates
        )
        DatePickerDialog(
            onDismissRequest = { activePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val newStart = TimeUtils.utcMidnightToJstDayStart(utcMillis)
                        startDateMillis = newStart
                        val curEnd = endDateMillis
                        if (curEnd != null && newStart > curEnd) {
                            endDateMillis = newStart
                        }
                        endDateMillis?.let { end ->
                            if (end != newStart && end - newStart > 30 * 86_400_000L) {
                                endDateMillis = newStart + 30 * 86_400_000L
                            }
                        }
                    }
                    activePicker = null
                }) { Text(stringResource(R.string.dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { activePicker = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    
    if (activePicker == "end") {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TimeUtils.toUtcMidnightFromJstDay(endDateMillis),
            selectableDates = historicalSearchSelectableDates
        )
        DatePickerDialog(
            onDismissRequest = { activePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val newEnd = TimeUtils.utcMidnightToJstDayStart(utcMillis)
                        endDateMillis = newEnd
                        val curStart = startDateMillis
                        if (curStart != null && curStart > newEnd) {
                            startDateMillis = newEnd
                        }
                        startDateMillis?.let { start ->
                            if (start != newEnd && newEnd - start > 30 * 86_400_000L) {
                                startDateMillis = newEnd - 30 * 86_400_000L
                            }
                        }
                    }
                    activePicker = null
                }) { Text(stringResource(R.string.dialog_save)) }

            },
            dismissButton = {
                TextButton(onClick = { activePicker = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    
    if (showDamSelectDialog) {
        net.tecogonaz.tcsameuradammonitor.ui.settings.DamSelectDialog(
            onDismiss = { showDamSelectDialog = false },
            onDamSelected = { damId ->
                val config = net.tecogonaz.tcsameuradammonitor.domain.model.DamListData.allDams.find { it.id == damId }
                if (config != null) selectedDamConfig = config
                showDamSelectDialog = false
            }
        )
    }
}

/**
 * 保存された過去の観測データ（検索結果）の中で、グラフやテーブルに表示するデータ期間を
 * さらに絞り込むための範囲選択（日付選択）ダイアログを表示するComposable関数です。
 *
 * @param damConfig 対象のダム設定。
 * @param meta 選択された過去データ検索結果のメタ情報（[HistoricalSearchMeta]）。
 * @param initialStartDate 現在選択されている表示開始日（yyyy/MM/dd）。
 * @param initialEndDate 現在選択されている表示終了日（yyyy/MM/dd）。
 * @param onDisplay 表示絞り込み実行時のコールバック（絞り込み開始日(yyyyMMdd)、絞り込み終了日(yyyyMMdd)）。
 * @param onDismiss ダイアログを閉じる操作時のコールバック。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoricalDataRangeDialog(
    damConfig: DamConfig,
    meta: HistoricalSearchMeta,
    initialStartDate: String,
    initialEndDate: String,
    onDisplay: (startDate: String, endDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    val appLocaleTag = LocaleUtils.effectiveLocale(LocalContext.current).toLanguageTag()
    val searchStartMillis = remember(meta.searchBgnDate) {
        parseSearchDateStartMillis(meta.searchBgnDate)
    } ?: return
    val searchEndMillis = remember(meta.searchEndDate) {
        parseSearchDateStartMillis(meta.searchEndDate)
    } ?: return
    val initialStartMillis = remember(initialStartDate, meta.searchBgnDate) {
        parseSearchDateStartMillis(initialStartDate) ?: searchStartMillis
    }
    val initialEndMillis = remember(initialEndDate, meta.searchEndDate) {
        parseSearchDateStartMillis(initialEndDate) ?: searchEndMillis
    }

    var startDateMillis by remember(meta.id, initialStartDate) { mutableStateOf<Long?>(initialStartMillis) }
    var endDateMillis by remember(meta.id, initialEndDate) { mutableStateOf<Long?>(initialEndMillis) }
    var activePicker by remember { mutableStateOf<String?>(null) }

    val notSelectedLabel = stringResource(R.string.historical_search_date_not_selected)
    val errorDateRange = stringResource(R.string.historical_search_error_date_range)
    val errorOutOfBounds = stringResource(R.string.historical_range_error_out_of_bounds)
    val periodNoteFormat = stringResource(R.string.historical_search_period_note)

    fun validateDates(start: Long?, end: Long?): String? {
        if (start == null || end == null) return notSelectedLabel
        if (start > end) return errorDateRange
        if (start < searchStartMillis || end > searchEndMillis) return errorOutOfBounds
        return null
    }

    val localError = validateDates(startDateMillis, endDateMillis)

    val selectableDates = remember(searchStartMillis, searchEndMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val jstDayStart = TimeUtils.utcMidnightToJstDayStart(utcTimeMillis)
                return jstDayStart in searchStartMillis..searchEndMillis
            }

            override fun isSelectableYear(year: Int): Boolean {
                val startYear = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply {
                    timeInMillis = searchStartMillis
                }.get(Calendar.YEAR)
                val endYear = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply {
                    timeInMillis = searchEndMillis
                }.get(Calendar.YEAR)
                return year in startYear..endYear
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.historical_range_dialog_title)) },
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
                        text = LocaleUtils.normalDamName(appLocaleTag, damConfig),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = { activePicker = "start" },
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.desc_select_date)
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.historical_search_start_date),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = startDateMillis?.toHistoricalRangeDisplayDate() ?: notSelectedLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = { activePicker = "end" },
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.desc_select_date)
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.historical_search_end_date),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = endDateMillis?.toHistoricalRangeDisplayDate() ?: notSelectedLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                when {
                    localError != null -> Text(
                        text = localError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )

                    startDateMillis != null && endDateMillis != null -> Text(
                        text = String.format(
                            periodNoteFormat,
                            startDateMillis!!.toHistoricalRangeDisplayDate(),
                            endDateMillis!!.toNextDayHistoricalRangeDisplayDate()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = startDateMillis
                    val end = endDateMillis
                    if (validateDates(start, end) == null && start != null && end != null) {
                        onDisplay(start.toHistoricalRangeSearchDate(), end.toHistoricalRangeSearchDate())
                    }
                },
                enabled = localError == null
            ) {
                Text(stringResource(R.string.historical_range_display_button))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        startDateMillis = searchStartMillis
                        endDateMillis = searchEndMillis
                    }
                ) {
                    Text(stringResource(R.string.dialog_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )

    if (activePicker == "start") {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TimeUtils.toUtcMidnightFromJstDay(startDateMillis),
            selectableDates = selectableDates
        )
        DatePickerDialog(
            onDismissRequest = { activePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val newStart = TimeUtils.utcMidnightToJstDayStart(utcMillis)
                        startDateMillis = newStart
                        val currentEnd = endDateMillis
                        if (currentEnd != null && newStart > currentEnd) {
                            endDateMillis = newStart
                        }
                    }
                    activePicker = null
                }) { Text(stringResource(R.string.dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { activePicker = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (activePicker == "end") {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TimeUtils.toUtcMidnightFromJstDay(endDateMillis),
            selectableDates = selectableDates
        )
        DatePickerDialog(
            onDismissRequest = { activePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val newEnd = TimeUtils.utcMidnightToJstDayStart(utcMillis)
                        endDateMillis = newEnd
                        val currentStart = startDateMillis
                        if (currentStart != null && currentStart > newEnd) {
                            startDateMillis = newEnd
                        }
                    }
                    activePicker = null
                }) { Text(stringResource(R.string.dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { activePicker = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * 過去データ(日次)表示のグラフやテーブルに表示するデータ期間をさらに絞り込むための
 * 範囲選択（日付選択）ダイアログです。
 *
 * 既存の [HistoricalDataRangeDialog] と同じ構造で、境界は読込済み期間
 * （[periodStartMillis] 〜 [periodEndMillis] の前日まで）に制限されます。
 *
 * @param damConfig 対象のダム設定。
 * @param initialStartDate 現在選択されている表示開始日（yyyy/MM/dd）。
 * @param initialEndDate 現在選択されている表示終了日（yyyy/MM/dd）。
 * @param periodStartMillis 選択可能な開始日（JST 00:00）。
 * @param periodEndMillis 選択可能な終了日の翌日 00:00（排他的。読込済み期間の終了日は前日）。
 * @param onDisplay 表示絞り込み実行時のコールバック（開始日のJST 00:00、終了日の翌日 00:00）。
 * @param onDismiss ダイアログを閉じる操作時のコールバック。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SudmonitorHistoryRangeDialog(
    damConfig: DamConfig,
    initialStartDate: String,
    initialEndDate: String,
    periodStartMillis: Long,
    periodEndMillis: Long,
    onDisplay: (fromMillis: Long, toMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val appLocaleTag = LocaleUtils.effectiveLocale(LocalContext.current).toLanguageTag()
    val lastSelectableDayMillis = periodEndMillis - MILLIS_PER_DAY
    val initialStartMillis = remember(initialStartDate, periodStartMillis) {
        (TimeUtils.parseJstMillis(initialStartDate, "yyyy/MM/dd")
            ?: periodStartMillis).coerceIn(periodStartMillis, lastSelectableDayMillis)
    }
    val initialEndMillis = remember(initialEndDate, periodEndMillis) {
        (TimeUtils.parseJstMillis(initialEndDate, "yyyy/MM/dd")
            ?: lastSelectableDayMillis).coerceIn(periodStartMillis, lastSelectableDayMillis)
    }

    var startDateMillis by remember(periodStartMillis, initialStartDate) { mutableStateOf<Long?>(initialStartMillis) }
    var endDateMillis by remember(periodEndMillis, initialEndDate) { mutableStateOf<Long?>(initialEndMillis) }
    var activePicker by remember { mutableStateOf<String?>(null) }

    val notSelectedLabel = stringResource(R.string.historical_search_date_not_selected)
    val errorDateRange = stringResource(R.string.historical_search_error_date_range)
    val errorOutOfBounds = stringResource(R.string.historical_range_error_out_of_bounds)
    val periodNoteFormat = stringResource(R.string.historical_search_period_note)

    fun validateDates(start: Long?, end: Long?): String? {
        if (start == null || end == null) return notSelectedLabel
        if (start > end) return errorDateRange
        if (start < periodStartMillis || end > lastSelectableDayMillis) return errorOutOfBounds
        return null
    }

    val localError = validateDates(startDateMillis, endDateMillis)

    val selectableDates = remember(periodStartMillis, lastSelectableDayMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val jstDayStart = TimeUtils.utcMidnightToJstDayStart(utcTimeMillis)
                return jstDayStart in periodStartMillis..lastSelectableDayMillis
            }

            override fun isSelectableYear(year: Int): Boolean {
                val startYear = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply {
                    timeInMillis = periodStartMillis
                }.get(Calendar.YEAR)
                val endYear = Calendar.getInstance(TimeUtils.JST_TIMEZONE).apply {
                    timeInMillis = lastSelectableDayMillis
                }.get(Calendar.YEAR)
                return year in startYear..endYear
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.historical_range_dialog_title)) },
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
                        text = LocaleUtils.normalDamName(appLocaleTag, damConfig),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = { activePicker = "start" },
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.desc_select_date)
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.historical_search_start_date),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = startDateMillis?.toHistoricalRangeDisplayDate() ?: notSelectedLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = { activePicker = "end" },
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.desc_select_date)
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.historical_search_end_date),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = endDateMillis?.toHistoricalRangeDisplayDate() ?: notSelectedLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                when {
                    localError != null -> Text(
                        text = localError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )

                    startDateMillis != null && endDateMillis != null -> Text(
                        text = String.format(
                            periodNoteFormat,
                            startDateMillis!!.toHistoricalRangeDisplayDate(),
                            endDateMillis!!.toNextDayHistoricalRangeDisplayDate()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = startDateMillis
                    val end = endDateMillis
                    if (validateDates(start, end) == null && start != null && end != null) {
                        onDisplay(start, end + MILLIS_PER_DAY)
                    }
                },
                enabled = localError == null
            ) {
                Text(stringResource(R.string.historical_range_display_button))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        startDateMillis = periodStartMillis
                        endDateMillis = lastSelectableDayMillis
                    }
                ) {
                    Text(stringResource(R.string.dialog_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )

    if (activePicker == "start") {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TimeUtils.toUtcMidnightFromJstDay(startDateMillis),
            selectableDates = selectableDates
        )
        DatePickerDialog(
            onDismissRequest = { activePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val newStart = TimeUtils.utcMidnightToJstDayStart(utcMillis)
                        startDateMillis = newStart
                        val currentEnd = endDateMillis
                        if (currentEnd != null && newStart > currentEnd) {
                            endDateMillis = newStart
                        }
                    }
                    activePicker = null
                }) { Text(stringResource(R.string.dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { activePicker = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (activePicker == "end") {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TimeUtils.toUtcMidnightFromJstDay(endDateMillis),
            selectableDates = selectableDates
        )
        DatePickerDialog(
            onDismissRequest = { activePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val newEnd = TimeUtils.utcMidnightToJstDayStart(utcMillis)
                        endDateMillis = newEnd
                        val currentStart = startDateMillis
                        if (currentStart != null && currentStart > newEnd) {
                            startDateMillis = newEnd
                        }
                    }
                    activePicker = null
                }) { Text(stringResource(R.string.dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { activePicker = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
