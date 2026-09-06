// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.Spacer
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import net.tecogonaz.tcsameuradammonitor.MainActivity
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import net.tecogonaz.tcsameuradammonitor.domain.model.DamListData
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_PERCENTAGE_TEXT
import net.tecogonaz.tcsameuradammonitor.ui.theme.trendAccentColor
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import net.tecogonaz.tcsameuradammonitor.domain.model.isAllObservationDataInvalid

/**
 * Jetpack Glance を使用してホーム画面に早明浦ダムなどのダム諸量データ（貯水率や更新日時など）を表示するウィジェットプロバイダ。
 *
 * ウィジェットの表示サイズ（横幅）に応じて、フォントサイズやレイアウトを動的に調整するレスポンシブな表示に対応しています。
 * また、初回のデータロード待ち状態や通信エラーなどの各種状態に応じた表示を切り替えます。
 */
class DamWidgetProvider : GlanceAppWidget() {
    /**
     * ウィジェットのサイズ計測モード。
     * 各ウィジェットインスタンスの正確なサイズを取得して動的にレイアウトを調整するため、[SizeMode.Exact] を指定します。
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    /**
     * ウィジェットの内容（Composition）を提供します。
     *
     * 依存オブジェクトのリポジトリ等から現在の貯水率データやアプリ設定をフロー経由で取得し、
     * UIコンポーザブル [DamWidgetContent] へデータを引き渡してレンダリングを行います。
     *
     * @param context コンテキスト
     * @param id ウィジェットを識別するユニークな GlanceId
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, DamWidgetEntryPoint::class.java)
        
        val initialDamData = entryPoint.damDataRepository().damDataFlow.first()
        val initialSettings = entryPoint.settingsRepository().appSettingsFlow.first()
        val initialLoadStatus = entryPoint.damDataRepository().loadStatus.value
        val initialSettingsSnapshot = WidgetSettingsSnapshot(
            settings = initialSettings,
            localeTag = LocaleUtils.effectiveLocale(context).toLanguageTag()
        )
        val settingsSnapshotFlow = entryPoint.settingsRepository().appSettingsFlow
            .map { settings ->
                WidgetSettingsSnapshot(
                    settings = settings,
                    localeTag = LocaleUtils.effectiveLocale(context).toLanguageTag()
                )
            }

        provideContent {
            val damData by entryPoint.damDataRepository().damDataFlow.collectAsState(initial = initialDamData)
            val settingsSnapshot by settingsSnapshotFlow.collectAsState(initial = initialSettingsSnapshot)
            val loadStatus by entryPoint.damDataRepository().loadStatus.collectAsState(initial = initialLoadStatus)
            val lastFetchTime = entryPoint.damDataRepository().getLastFetchTimeMillis()

            DamWidgetContent(
                context = context,
                damData = damData,
                settings = settingsSnapshot.settings,
                lastFetchTime = lastFetchTime,
                loadStatus = loadStatus,
                localeTag = settingsSnapshot.localeTag
            )
        }
    }

    /**
     * ウィジェットの全体のUIレイアウトおよび状態判定を管理する主要なコンポーザブル。
     *
     * 端末の再起動状態、データ未読込（初回読込待ち）状態、通信エラー等の判定を行い、
     * 状況に応じて初期表示、エラー表示、または貯水率データの表示に振り分けます。
     * ウィジェット全体はクリック可能になっており、タップするとアプリのメイン画面（[MainActivity]）を開きます。
     *
     * @param context コンテキスト
     * @param damData 取得されたダムの諸量データ（[DamData]）
     * @param settings アプリの表示設定（[AppSettings]）
     * @param lastFetchTime 前回のデータ取得完了時刻（ミリ秒）
     * @param loadStatus 現在のデータロード状況（[DamLoadStatus]）
     * @param localeTag 言語設定を表すロケールタグ（例: "ja"、"en"）
     */
    @androidx.compose.runtime.Composable
    internal fun DamWidgetContent(
        context: Context,
        damData: DamData?,
        settings: AppSettings,
        lastFetchTime: Long,
        loadStatus: DamLoadStatus = DamLoadStatus.INITIAL,
        localeTag: String
    ) {
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            action = android.content.Intent.ACTION_MAIN
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val currentSize = LocalSize.current
        val contentWidthDp = (currentSize.width.value - WIDGET_HORIZONTAL_PADDING_DP * 2).coerceAtLeast(0f)
        val widgetBackgroundColor = GlanceTheme.colors.widgetBackground
        val widgetTextColor = GlanceTheme.colors.onSurface
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(widgetBackgroundColor)
                    .padding(12.dp)
                    .clickable(actionStartActivity(intent)),
                horizontalAlignment = Alignment.Start
            ) {
                val currentBootTime = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
                val isBootedButNotOpened =
                    Math.abs(currentBootTime - settings.lastBootTime) > AppSettings.BOOT_TIME_TOLERANCE_MS
                val hasAutoUpdatedSinceBoot = settings.lastAutoUpdateMillis > currentBootTime
                val isInitialLoadPending = damData == null &&
                    lastFetchTime == 0L &&
                    loadStatus == DamLoadStatus.INITIAL &&
                    settings.lastLoadResultMessage.isEmpty()
                
                val showPrompt = isInitialLoadPending || (damData == null && isBootedButNotOpened && !hasAutoUpdatedSinceBoot)

            when {
                showPrompt -> WidgetInitialContent(
                    settings = settings,
                    localeTag = localeTag,
                    contentWidthDp = contentWidthDp,
                    widgetTextColor = widgetTextColor
                )
                damData == null -> WidgetErrorContent(
                    settings = settings,
                    loadStatus = loadStatus,
                    localeTag = localeTag,
                    contentWidthDp = contentWidthDp,
                    widgetTextColor = widgetTextColor
                )
                else -> WidgetDataContent(
                    context = context,
                    damData = damData,
                    settings = settings,
                    localeTag = localeTag,
                    widgetTextColor = widgetTextColor
                )
            }
            }
        }
    }

    /**
     * 初回読込待ちの際に表示するウィジェットのコンテンツ。
     *
     * アプリ起動後やデータ未ロード時の「初回読込」中において、
     * 対象ダム名と読み込み中メッセージ（「貯水率メッセージ」などの初期状態）をウィジェットに表示します。
     *
     * @param settings アプリの表示設定（[AppSettings]）
     * @param localeTag 言語設定のロケールタグ
     * @param contentWidthDp ウィジェットのコンテンツ有効幅（dp単位）
     * @param widgetTextColor テキスト描画色のカラープロバイダ
     */
    @androidx.compose.runtime.Composable
    private fun WidgetInitialContent(
        settings: AppSettings,
        localeTag: String,
        contentWidthDp: Float,
        widgetTextColor: ColorProvider
    ) {
        val isJapanese = LocaleUtils.isJapanese(localeTag)
        val damConfig = DamListData.allDams.find { it.id == settings.targetDamId }
        val damName = damConfig?.let {
            getDamNameForOtherMessage(localeTag, it, contentWidthDp)
        } ?: ""
        val initialText = settings.getInitialMessageText(isJapanese)
        val totalSize = LocalSize.current
        val (horizontalBlocks, verticalBlocks) = calculateBlocks(totalSize.width.value, totalSize.height.value)
        val useCompactFont = isCompactFont(verticalBlocks, horizontalBlocks)
        val damNameFontSize = if (useCompactFont) COMPACT_DAM_NAME_FONT_SIZE_SP.sp else NORMAL_DAM_NAME_FONT_SIZE_SP.sp
        val othersFontSize = if (useCompactFont) COMPACT_OTHERS_FONT_SIZE_SP.sp else NORMAL_OTHERS_FONT_SIZE_SP.sp

        WidgetDamNameRow(
            damName = damName,
            color = widgetTextColor,
            fontSize = damNameFontSize
        )

        
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = initialText,
                style = TextStyle(
                    color = widgetTextColor,
                    fontSize = othersFontSize
                ),
                maxLines = 2
            )
        }
    }

    /**
     * データ取得エラーが発生した際に表示するウィジェットのコンテンツ。
     *
     * 通信エラーやサーバーエラーの際に、状況に合わせたエラーメッセージ
     *（「ネットワーク未接続」や「読込エラー」など）を表示します。
     *
     * @param settings アプリの表示設定（[AppSettings]）
     * @param loadStatus 発生したエラーの種類を示すロードステータス（[DamLoadStatus]）
     * @param localeTag 言語設定のロケールタグ
     * @param contentWidthDp ウィジェットのコンテンツ有効幅（dp単位）
     * @param widgetTextColor テキスト描画色のカラープロバイダ
     */
    @androidx.compose.runtime.Composable
    private fun WidgetErrorContent(
        settings: AppSettings,
        loadStatus: DamLoadStatus,
        localeTag: String,
        contentWidthDp: Float,
        widgetTextColor: ColorProvider
    ) {
        val isJapanese = LocaleUtils.isJapanese(localeTag)
        val damConfig = DamListData.allDams.find { it.id == settings.targetDamId }
        val damName = damConfig?.let {
            getDamNameForOtherMessage(localeTag, it, contentWidthDp)
        } ?: ""
        val errorText = when (loadStatus) {
            DamLoadStatus.INITIAL -> settings.getInitialMessageText(isJapanese)
            DamLoadStatus.NETWORK_UNAVAILABLE -> settings.getNetworkUnavailableText(isJapanese)
            DamLoadStatus.LOADING_FAILURE,
            DamLoadStatus.SUCCESS -> settings.getLoadingErrorText(isJapanese)
        }
        val totalSize = LocalSize.current
        val (horizontalBlocks, verticalBlocks) = calculateBlocks(totalSize.width.value, totalSize.height.value)
        val useCompactFont = isCompactFont(verticalBlocks, horizontalBlocks)
        val damNameFontSize = if (useCompactFont) COMPACT_DAM_NAME_FONT_SIZE_SP.sp else NORMAL_DAM_NAME_FONT_SIZE_SP.sp
        val othersFontSize = if (useCompactFont) COMPACT_OTHERS_FONT_SIZE_SP.sp else NORMAL_OTHERS_FONT_SIZE_SP.sp

        WidgetDamNameRow(
            damName = damName,
            color = widgetTextColor,
            fontSize = damNameFontSize
        )

        
        
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = errorText,
                style = TextStyle(
                    color = widgetTextColor,
                    fontSize = othersFontSize
                ),
                maxLines = 2
            )
        }
    }

    /**
     * 正常にダムデータが取得できている場合に表示するウィジェットのコンテンツ。
     *
     * ダム名、最新のデータ取得日時、貯水率パーセンテージとトレンド矢印、
     * 前日比（横3ブロック以上の場合）、および設定に応じた「貯水率メッセージ」をレイアウトして表示します。
     *
     * @param damData 表示対象のダムデータ（[DamData]）
     * @param settings アプリの表示設定（[AppSettings]）
     * @param localeTag 言語設定のロケールタグ
     * @param widgetTextColor 通常テキストの描画色カラープロバイダ
     */
    @androidx.compose.runtime.Composable
    private fun WidgetDataContent(
        context: Context,
        damData: DamData,
        settings: AppSettings,
        localeTag: String,
        widgetTextColor: ColorProvider
    ) {
        val isJapanese = LocaleUtils.isJapanese(localeTag)
        val damConfig = DamListData.allDams.find { it.id == settings.targetDamId }
        val damName = damConfig?.let { LocaleUtils.normalDamName(localeTag, it) } ?: ""
        val percentage = damData.storagePercentage
        val isAllDataInvalid = damData.isAllObservationDataInvalid()
        val isSameura = settings.targetDamId == AppSettings.DEFAULT_DAM_ID
        val totalSize = LocalSize.current
        val (horizontalBlocks, verticalBlocks) = calculateBlocks(totalSize.width.value, totalSize.height.value)
        val useCompactFont = isCompactFont(verticalBlocks, horizontalBlocks)
        val damNameFontSize = if (useCompactFont) COMPACT_DAM_NAME_FONT_SIZE_SP.sp else NORMAL_DAM_NAME_FONT_SIZE_SP.sp
        val othersFontSize = if (useCompactFont) COMPACT_OTHERS_FONT_SIZE_SP.sp else NORMAL_OTHERS_FONT_SIZE_SP.sp

        val stateStr: String
        val msgStr: String
        if (percentage == null) {
            val (s, m) = settings.getStateForPercentage(null, isJapanese, isAllDataInvalid, isSameura)
            stateStr = s
            msgStr = m
        } else {
            val (s, m) = settings.getStateForPercentage(
                percentage,
                isJapanese,
                isAllDataInvalid = false,
                isSameura = isSameura,
                storageVolumeForMessage = damData.storageVolumeForMessage
            )
            stateStr = s
            msgStr = m
        }

        val trend = if (percentage != null) damData.storagePercentageTrend else Trend.UNKNOWN
        val locale = LocaleUtils.localeForTag(localeTag)

        val targetTimeStrWidget = damData.storagePercentageTime ?: damData.updatedAt
        val dateFormatted = TimeUtils.parseAndFormatToJst(
            targetTimeStrWidget, "yyyy/MM/dd H:m", "yyyy/MM/dd"
        ) ?: ""
        val timeFormatted = TimeUtils.parseAndFormatToJstWithSuffix(
            targetTimeStrWidget, "yyyy/MM/dd H:m", "HH:mm"
        ) ?: ""
        val dateTimeText = buildDateTimePrefix(dateFormatted, timeFormatted)

        val percentageTrendColorProvider = getTrendColorProvider(
            trend = trend,
            defaultColor = widgetTextColor
        )

        val percentageAndTrend = WidgetDisplayFormatter.percentageAndTrendText(
            percentage = percentage,
            trend = trend,
            locale = locale,
            missingPercentageText = MISSING_PERCENTAGE_TEXT
        )

        val dayChangeLabel = localizedString(
            context = context,
            localeTag = localeTag,
            id = R.string.summary_storage_percentage_day_change
        )
        val dayChangeText = WidgetDisplayFormatter.dayChangeText(
            label = dayChangeLabel,
            dayChange = damData.storagePercentageDayChange,
            trend = damData.storagePercentageDayChangeTrend,
            locale = locale
        )
        val dayChangeColorProvider = getTrendColorProvider(
            trend = damData.storagePercentageDayChangeTrend,
            defaultColor = widgetTextColor
        )

        val footerText = if (msgStr.isNotEmpty()) "$stateStr $msgStr" else stateStr
        val showFooterLine = settings.showStorageRateMessage

        val isTwoColumns = horizontalBlocks == 2
        val isThreeOrMoreColumns = horizontalBlocks >= 3

        WidgetDamNameRow(
            damName = damName,
            color = widgetTextColor,
            fontSize = damNameFontSize
        )

        if (isTwoColumns) {
            
            Text(
                text = dateTimeText,
                style = TextStyle(color = widgetTextColor, fontSize = othersFontSize),
                maxLines = 1
            )

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = percentageAndTrend,
                    style = TextStyle(
                        color = percentageTrendColorProvider,
                        fontSize = othersFontSize
                    ),
                    maxLines = 1
                )
            }
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateTimeText,
                    style = TextStyle(color = widgetTextColor, fontSize = othersFontSize),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = percentageAndTrend,
                    style = TextStyle(
                        color = percentageTrendColorProvider,
                        fontSize = othersFontSize
                    ),
                    maxLines = 1
                )
            }

            if (isThreeOrMoreColumns) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = dayChangeText,
                        style = TextStyle(
                            color = dayChangeColorProvider,
                            fontSize = othersFontSize
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        if (showFooterLine) {
            
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = footerText,
                    style = TextStyle(color = widgetTextColor, fontSize = othersFontSize),
                    maxLines = 1
                )
            }
        }
    }

    /**
     * ダム名を行レイアウトで表示するコンポーザブル。
     *
     * @param damName 表示するダム名
     * @param color テキストの色
     * @param fontSize フォントサイズ
     * @param modifier レイアウト修飾子
     */
    @androidx.compose.runtime.Composable
    private fun WidgetDamNameRow(
        damName: String,
        color: ColorProvider,
        fontSize: androidx.compose.ui.unit.TextUnit,
        modifier: GlanceModifier = GlanceModifier
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = damName,
                style = TextStyle(
                    color = color,
                    fontSize = fontSize
                ),
                maxLines = 1
            )
        }
    }

    /**
     * 貯水率の変動傾向（トレンド）に基づいて、適用すべきテキスト色のカラープロバイダを返します。
     *
     * @param trend トレンド（[Trend]）
     * @param defaultColor デフォルトのカラー
     * @return 該当するカラープロバイダ
     */
    private fun getTrendColorProvider(
        trend: Trend?,
        defaultColor: ColorProvider
    ): ColorProvider = trendAccentColor(trend)?.let(::ColorProvider) ?: defaultColor

    /**
     * エラーメッセージや初期読込時のメッセージ表示欄に収まるよう、必要に応じて省略名（ショート名）を考慮したダム名を取得します。
     *
     * @param localeTag ロケールタグ
     * @param damConfig ダム設定情報（[DamConfig]）
     * @param contentWidthDp ウィジェットのコンテンツ有効幅
     * @return メッセージ表示用に適したダム名
     */
    private fun getDamNameForOtherMessage(
        localeTag: String,
        damConfig: DamConfig,
        contentWidthDp: Float
    ): String {
        val normalName = LocaleUtils.normalDamName(localeTag, damConfig)
        val shouldUseCompactName = !LocaleUtils.isJapanese(localeTag) &&
            isTextOverflowEstimated(normalName, contentWidthDp, COMPACT_OTHERS_FONT_SIZE_SP)
        return if (shouldUseCompactName) LocaleUtils.shortDamName(localeTag, damConfig) else normalName
    }

    /**
     * ウィジェットのサイズ（dp）から、ランチャーグリッド上のブロック数（縦・横）を計算します。
     *
     * @param widthDp ウィジェットの横幅（dp）
     * @param heightDp ウィジェットの縦幅（dp）
     * @return 横ブロック数と縦ブロック数のペア
     */
    private fun calculateBlocks(widthDp: Float, heightDp: Float): Pair<Int, Int> {
        val horizontalBlocks = ((widthDp + WIDGET_CELL_OFFSET_DP) / WIDGET_CELL_BASE_DP).toInt().coerceAtLeast(1)
        val verticalBlocks = ((heightDp + WIDGET_CELL_OFFSET_DP) / WIDGET_CELL_BASE_DP).toInt().coerceAtLeast(1)
        return horizontalBlocks to verticalBlocks
    }

    /**
     * 指定されたブロック数に基づいて、縮小フォントを使用すべきかどうかを判定します。
     *
     * @param verticalBlocks 縦ブロック数
     * @param horizontalBlocks 横ブロック数
     * @return 縮小フォントを使用する場合は true
     */
    private fun isCompactFont(verticalBlocks: Int, horizontalBlocks: Int): Boolean =
        verticalBlocks == 1 || (verticalBlocks >= 2 && horizontalBlocks <= 3)

    /**
     * テキストがウィジェット幅を超過するかどうかを見積もります。
     *
     * @param text 対象のテキスト
     * @param contentWidthDp ウィジェットのコンテンツ有効幅
     * @param fontSizeSp フォントサイズ
     * @return 超過すると見積もられる場合は true、収まる場合は false
     */
    private fun isTextOverflowEstimated(text: String, contentWidthDp: Float, fontSizeSp: Float): Boolean =
        WidgetDisplayFormatter.isTextOverflowEstimated(text, contentWidthDp, fontSizeSp)

    /**
     * 日付文字列と時刻文字列を結合してウィジェット表示用の日時接頭辞を構築します。
     *
     * @param dateFormatted フォーマットされた日付文字列
     * @param timeFormatted フォーマットされた時刻文字列
     * @return 結合された日時接頭辞
     */
    private fun buildDateTimePrefix(dateFormatted: String, timeFormatted: String) =
        WidgetDisplayFormatter.buildDateTimePrefix(dateFormatted, timeFormatted)

    /**
     * 明示されたアプリ内ロケールで文字列リソースを取得します。
     */
    private fun localizedString(context: Context, localeTag: String, id: Int): String {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(LocaleUtils.localeForTag(localeTag))
        }
        return context.createConfigurationContext(configuration).getString(id)
    }

    /**
     * ウィジェットの表示に利用される設定（[AppSettings]）と言語設定（ロケールタグ）の不変スナップショット。
     *
     * @property settings 現在のアプリ設定情報
     * @property localeTag 現在適用されているロケールタグ（例: "ja", "en"）
     */
    private data class WidgetSettingsSnapshot(
        val settings: AppSettings,
        val localeTag: String
    )

    private companion object {
        const val NORMAL_DAM_NAME_FONT_SIZE_SP = 18f
        const val NORMAL_OTHERS_FONT_SIZE_SP = 16f
        const val COMPACT_DAM_NAME_FONT_SIZE_SP = 14f
        const val COMPACT_OTHERS_FONT_SIZE_SP = 12f
        const val WIDGET_HORIZONTAL_PADDING_DP = 12f
        const val WIDGET_CELL_BASE_DP = 70f
        const val WIDGET_CELL_OFFSET_DP = 30f
    }
}
