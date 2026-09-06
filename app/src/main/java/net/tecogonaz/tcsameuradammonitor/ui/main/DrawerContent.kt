// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.outlined.Adb
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_PERCENTAGE_TEXT
import net.tecogonaz.tcsameuradammonitor.ui.common.mouseDragScroll
import net.tecogonaz.tcsameuradammonitor.ui.theme.trendAccentColor
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import net.tecogonaz.tcsameuradammonitor.domain.model.isAllObservationDataInvalid

internal fun drawerTrendAccentColor(trend: Trend?, isSelected: Boolean): Color? =
    if (isSelected) null else trendAccentColor(trend)

@Composable
private fun DrawerRealtimeInfoSection(
    damName: String,
    dateTimeStr: String,
    percentageStr: String,
    trend: Trend?,
    line3: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val trendSymbol = when (trend) {
        Trend.UP -> "↗"
        Trend.DOWN -> "↘"
        else -> "→"
    }
    
    val trendColor = drawerTrendAccentColor(trend, isSelected) ?: Color.Unspecified

    NavigationDrawerItem(
        icon = { Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null) },
        label = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (damName.isNotEmpty()) {
                    Text(
                        text = damName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (dateTimeStr.isNotEmpty()) {
                    
                    
                    Text(
                        text = buildAnnotatedString {
                            append("$dateTimeStr ")
                            if (percentageStr == MISSING_PERCENTAGE_TEXT) {
                                append(percentageStr)
                            } else if (percentageStr.isNotEmpty()) {
                                withStyle(SpanStyle(color = trendColor)) {
                                    append("$percentageStr $trendSymbol")
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (line3.isNotEmpty()) {
                    Text(
                        text = line3,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        selected = isSelected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(damName)
                    if (dateTimeStr.isNotEmpty()) append(", $dateTimeStr")
                    if (percentageStr.isNotEmpty() && percentageStr != MISSING_PERCENTAGE_TEXT) {
                        append(", $percentageStr")
                        append(" ")
                        append(trendSymbol)
                    }
                    if (line3.isNotEmpty()) append(", $line3")
                }
            }
    )
}


@Composable
private fun DrawerHistoricalInfoSection(
    line1: String,
    line2: String,
    line3: String,
    isSelected: Boolean,
    isPinned: Boolean = false,
    onClick: () -> Unit
) {
    
    var isLine2MultiLine by remember { mutableStateOf(false) }

    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = if (isPinned) Icons.Outlined.PushPin else Icons.Outlined.Timeline,
                contentDescription = null
            )
        },
        label = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (line1.isNotEmpty()) {
                    Text(
                        text = line1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (line2.isNotEmpty()) {
                    Text(
                        text = line2,
                        style = MaterialTheme.typography.bodyMedium,
                        onTextLayout = { textLayoutResult ->
                            isLine2MultiLine = textLayoutResult.lineCount > 1
                        }
                    )
                }
                
                if (line3.isNotEmpty() && !isLine2MultiLine) {
                    Text(
                        text = line3,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        selected = isSelected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(line1)
                    if (line2.isNotEmpty()) append(", $line2")
                    if (line3.isNotEmpty()) append(", $line3")
                }
            }
    )
}


@Composable
private fun DrawerSourceSection(
    lastFetchDateStr: String,
    availableApps: List<AppLinkInfo>,
    onOpenUrl: (String) -> Unit,
    onOpenUrlWith: (String, AppLinkInfo) -> Unit,
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit,
    onQueryApps: (String) -> Unit,
    onClearApps: () -> Unit
) {
    val dbUrl = "https://www1.river.go.jp/"
    val pdlUrl = "https://www1.river.go.jp/WDBrules_20251210.pdf"

    Column(modifier = Modifier.fillMaxWidth()) {
        
        LinkNavigationDrawerItem(
            title = stringResource(R.string.nav_source_db),
            icon = Icons.Outlined.Link,
            url = dbUrl,
            availableApps = availableApps,
            onOpenUrl = onOpenUrl,
            onOpenUrlWith = onOpenUrlWith,
            onShareText = onShareText,
            onCopied = onCopied,
            onQueryApps = onQueryApps,
            onClearApps = onClearApps,
            textToCopy = stringResource(R.string.nav_source_db),
            modifier = Modifier.fillMaxWidth()
        )
        
        LinkNavigationDrawerItem(
            title = stringResource(R.string.nav_source_pdl),
            icon = Icons.Outlined.Description,
            url = pdlUrl,
            availableApps = availableApps,
            onOpenUrl = onOpenUrl,
            onOpenUrlWith = onOpenUrlWith,
            onShareText = onShareText,
            onCopied = onCopied,
            onQueryApps = onQueryApps,
            onClearApps = onClearApps,
            textToCopy = stringResource(R.string.nav_source_pdl),
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(
            text = stringResource(R.string.nav_source_credit, lastFetchDateStr),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
        )
    }
}


@Composable
internal fun DrawerContent(
    isPermanent: Boolean,
    contentPage: ContentPage,
    realtimeDamData: DamData?,
    realtimeDamConfig: DamConfig?,
    realtimeLoadStatus: DamLoadStatus,
    appSettings: AppSettings?,
    isHistoricalMode: Boolean,
    onRealtimeClick: () -> Unit,
    onHistoricalSearchClick: () -> Unit,
    onManageHistoricalClick: () -> Unit,
    onShowMain: () -> Unit,
    historicalMetaList: List<HistoricalSearchMeta>,
    currentHistoricalMetaId: Long,
    getDamConfig: (String) -> DamConfig?,
    onSwitchToHistoricalMode: (Long) -> Unit,
    isDailyHistoryEnabled: Boolean,
    sudmonitorHistory: SudmonitorHistory?,
    isSudmonitorHistoryMode: Boolean,
    onSwitchToSudmonitorHistoryMode: () -> Unit,
    lastFetchDateStr: String,
    availableApps: List<AppLinkInfo>,
    onDebugClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAppInfoClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenUrlWith: (String, AppLinkInfo) -> Unit,
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit,
    onQueryApps: (String) -> Unit,
    onClearApps: () -> Unit,
    onCollapseSidebarClick: (() -> Unit)? = null
) {

    val appLocale = LocaleUtils.effectiveLocale(LocalContext.current)
    val isJaLocale = appLocale.language == Locale.JAPANESE.language
    val localeTag = appLocale.toLanguageTag()
    val realtimeDamName = realtimeDamConfig?.let { LocaleUtils.normalDamName(localeTag, it) } ?: ""
    val realtimePercentage = realtimeDamData?.storagePercentage
    val realtimeTrend = realtimeDamData?.storagePercentageTrend
    val realtimeTime = realtimeDamData?.storagePercentageTime ?: realtimeDamData?.updatedAt ?: ""
    val realtimeTimeStr = if (realtimeTime.isNotEmpty()) {
        TimeUtils.appendJstSuffix(
            TimeUtils.parseAndFormatToJst(realtimeTime, "yyyy/MM/dd H:m", "yyyy/MM/dd HH:mm") ?: realtimeTime
        )
    } else {
        ""
    }
    val percentageStr = if (realtimePercentage != null) {
        String.format(appLocale, "%.2f%%", realtimePercentage)
    } else if (realtimeDamData != null) {
        MISSING_PERCENTAGE_TEXT
    } else {
        ""
    }
    val (stateStr, msgStr) = if (appSettings != null) {
        when {
            realtimeDamData != null -> {
                val isAllInvalid = realtimeDamData.isAllObservationDataInvalid()
                appSettings.getStateForPercentage(
                    realtimePercentage,
                    isJaLocale,
                    isAllInvalid,
                    appSettings.targetDamId == AppSettings.DEFAULT_DAM_ID,
                    realtimeDamData.storageVolumeForMessage
                )
            }
            realtimeLoadStatus == DamLoadStatus.NETWORK_UNAVAILABLE ->
                appSettings.stateNetworkUnavailable to (if (isJaLocale) appSettings.msgNetworkUnavailableJa else appSettings.msgNetworkUnavailable)

            realtimeLoadStatus == DamLoadStatus.LOADING_FAILURE ->
                appSettings.stateLoadingError to (if (isJaLocale) appSettings.msgLoadingErrorJa else appSettings.msgLoadingError)

            else -> "" to ""
        }
    } else {
        "" to ""
    }
    val realtimeLine3 = buildString {
        if (stateStr.isNotEmpty()) append(stateStr)
        if (msgStr.isNotEmpty()) {
            if (stateStr.isNotEmpty()) append(" ")
            append(msgStr)
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .mouseDragScroll(scrollState, Orientation.Vertical)
            .verticalScroll(scrollState)
    ) {
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoResizingSingleLineText(
                text = stringResource(R.string.activity_main_title),
                style = MaterialTheme.typography.titleLarge
            )
            if (onCollapseSidebarClick != null) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onCollapseSidebarClick,
                    modifier = Modifier.testTag(TestTags.SIDEBAR_COLLAPSE_BUTTON)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuOpen,
                        contentDescription = stringResource(R.string.desc_collapse_sidebar)
                    )
                }
            }
        }
        HorizontalDivider()
        
        Text(
            text = stringResource(R.string.nav_realtime_data),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() }
        )
        
        DrawerRealtimeInfoSection(
            damName = realtimeDamName,
            dateTimeStr = if (realtimeDamData != null) realtimeTimeStr else "",
            percentageStr = if (realtimeDamData != null) percentageStr else "",
            trend = realtimeTrend,
            line3 = realtimeLine3,
            isSelected = !isHistoricalMode && !isSudmonitorHistoryMode,
            onClick = when {
                isHistoricalMode || isSudmonitorHistoryMode -> {
                    onRealtimeClick
                }

                isPermanent && contentPage != ContentPage.MAIN -> {
                    onShowMain
                }

                else -> {
                    {}
                }
            }
        )
        HorizontalDivider()
        
        // 過去データ(日次)セクション: 機能ゲート有効時は常時表示する。
        // 保存行が未取得の間はダム名のみのエントリを出し（期間行・貯水率推移行は省略）、
        // 初回取得失敗後も手動更新への導線を維持する（ゲート無効時は従来どおり非表示）。
        if (isDailyHistoryEnabled) {
            Text(
                text = stringResource(R.string.nav_daily_history_data),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                    .semantics { heading() }
            )
            val dailyDamName = sudmonitorHistory?.let { history ->
                getDamConfig(history.damId)
                    ?.let { LocaleUtils.normalDamName(localeTag, it) }
                    ?: history.damId
            } ?: appSettings?.targetDamId?.let { damId ->
                getDamConfig(damId)?.let { LocaleUtils.normalDamName(localeTag, it) }
            }.orEmpty()
            val dailyLine2 = if (sudmonitorHistory != null) {
                buildString {
                    val startStr = sudmonitorHistory.periodStartEpochMs
                        ?.let { "${TimeUtils.formatToJst(it, "yyyy/MM/dd")} 01:00" }
                        ?: ""
                    // periodEndEpochMs は最終被覆日の JST 00:00（日単位規約）のため、
                    // 終端表記は最終行の 24:00 = 翌日 00:00 として +1日で表示する。
                    // 通常の過去データと同じく、端末TZがJST以外の場合は終端にのみ (JST) を付与する（開始は無し）
                    val endStr = sudmonitorHistory.periodEndEpochMs
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
            } else {
                ""
            }
            val dailyLine3 = if (sudmonitorHistory != null) {
                run {
                    val startPct = sudmonitorHistory.firstStorageRatePct
                    val endPct = sudmonitorHistory.lastStorageRatePct
                    val minPct = sudmonitorHistory.minStorageRatePct
                    val maxPct = sudmonitorHistory.maxStorageRatePct
                    if (startPct != null && endPct != null) {
                        val rangeStr = if (minPct != null && maxPct != null) {
                            " (${
                                String.format(
                                    appLocale,
                                    "%.2f",
                                    minPct
                                )
                            }% ~ ${String.format(appLocale, "%.2f", maxPct)}%)"
                        } else ""
                        "${
                            String.format(
                                appLocale,
                                "%.2f",
                                startPct
                            )
                        }% → ${String.format(appLocale, "%.2f", endPct)}%$rangeStr"
                    } else MISSING_PERCENTAGE_TEXT
                }
            } else {
                ""
            }
            DrawerHistoricalInfoSection(
                line1 = dailyDamName,
                line2 = dailyLine2,
                line3 = dailyLine3,
                isSelected = isSudmonitorHistoryMode,
                isPinned = false,
                onClick = when {
                    !isSudmonitorHistoryMode -> {
                        onSwitchToSudmonitorHistoryMode
                    }

                    isPermanent && contentPage != ContentPage.MAIN -> {
                        onShowMain
                    }

                    else -> {
                        {}
                    }
                }
            )
            HorizontalDivider()
        }
        
        Text(
            text = stringResource(R.string.nav_historical_data),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() }
        )
        for (meta in historicalMetaList) {
            val metaDamName =
                getDamConfig(meta.damConfigId)?.let { LocaleUtils.normalDamName(localeTag, it) } ?: meta.damConfigId
            val metaLine2 = "${meta.searchBgnDate.toYmdSlash()} 01:00 - " +
                    TimeUtils.appendJstSuffix("${meta.searchEndDate.toNextDayYmdSlash()} 00:00")
            val metaLine3 = run {
                val startPct = meta.dataStartStoragePct
                val endPct = meta.dataEndStoragePct
                val minPct = meta.dataMinStoragePct
                val maxPct = meta.dataMaxStoragePct
                if (startPct != null && endPct != null) {
                    val rangeStr = if (minPct != null && maxPct != null) {
                        " (${
                            String.format(
                                appLocale,
                                "%.2f",
                                minPct
                            )
                        }% ~ ${String.format(appLocale, "%.2f", maxPct)}%)"
                    } else ""
                    "${
                        String.format(
                            appLocale,
                            "%.2f",
                            startPct
                        )
                    }% → ${String.format(appLocale, "%.2f", endPct)}%$rangeStr"
                } else MISSING_PERCENTAGE_TEXT
            }
            val isMetaSelected = meta.id == currentHistoricalMetaId && isHistoricalMode
            DrawerHistoricalInfoSection(
                line1 = metaDamName,
                line2 = metaLine2,
                line3 = metaLine3,
                isSelected = isMetaSelected,
                isPinned = meta.isPinned,
                onClick = when {
                    !isMetaSelected -> {
                        { onSwitchToHistoricalMode(meta.id) }
                    }

                    isPermanent && contentPage != ContentPage.MAIN -> {
                        onShowMain
                    }

                    else -> {
                        {}
                    }
                }
            )
        }
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_historical_search)) },
            selected = false,
            onClick = {
                onHistoricalSearchClick()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.MoreHoriz, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_historical_manage)) },
            selected = contentPage == ContentPage.HISTORICAL_MANAGE,
            onClick = {
                onManageHistoricalClick()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        HorizontalDivider()
        
        Text(
            text = stringResource(R.string.nav_source),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() }
        )
        DrawerSourceSection(
            lastFetchDateStr = lastFetchDateStr,
            availableApps = availableApps,
            onOpenUrl = onOpenUrl,
            onOpenUrlWith = onOpenUrlWith,
            onShareText = onShareText,
            onCopied = onCopied,
            onQueryApps = onQueryApps,
            onClearApps = onClearApps
        )
        HorizontalDivider()
        if (appSettings?.debugSettingsVisible == true) {
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Adb, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_debug)) },
                selected = contentPage == ContentPage.DEBUG ||
                        contentPage == ContentPage.DEBUG_LOG,
                onClick = {
                    onDebugClick()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
            selected = contentPage == ContentPage.SETTINGS,
            onClick = {
                onSettingsClick()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_app_info)) },
            selected = contentPage == ContentPage.APP_INFO ||
                    contentPage == ContentPage.APP_INFO_LICENSE ||
                    contentPage == ContentPage.APP_INFO_TERMS_OF_USE ||
                    contentPage == ContentPage.APP_INFO_TERMS_OF_USE_JA ||
                    contentPage == ContentPage.APP_INFO_PRIVACY_POLICY ||
                    contentPage == ContentPage.APP_INFO_PRIVACY_POLICY_JA ||
                    contentPage == ContentPage.APP_INFO_OSS_LICENSE,
            onClick = {
                onAppInfoClick()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

private val ymdSlashFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)

private fun String.toYmdSlash(): String =
    if (length == 8) "${substring(0, 4)}/${substring(4, 6)}/${substring(6, 8)}" else this

private fun String.toNextDayYmdSlash(): String =
    runCatching {
        LocalDate.parse(this, DateTimeFormatter.BASIC_ISO_DATE)
            .plusDays(1)
            .format(ymdSlashFormatter)
    }.getOrElse { toYmdSlash() }
