// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.widget

import android.content.Context
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.material3.ColorProviders
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import net.tecogonaz.tcsameuradammonitor.testutil.androidTestDamData
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Glance ベースのホーム画面ウィジェットUI表示クラス [DamWidgetProvider] の Instrumentation テストクラス。
 * Glance AppWidget Unit Testing API を用いて、ウィジェットサイズに応じたダムデータの表示（ダム名、貯水率、
 * トレンド記号、前日比）、および初期状態の案内プロンプト表示を検証します。
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class DamWidgetProviderGlanceAndroidTest {
    private val provider = DamWidgetProvider()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun widgetContent_withDamData_rendersDamNamePercentageAndLaunchAction() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(width = 260.dp, height = 120.dp))
        provideWidgetContent(
            damData = androidTestDamData(storagePercentage = 80.0f)
                .copy(storagePercentageTrend = Trend.UP)
        )

        onNode(hasText("早明浦ダム")).assertExists()
        onNode(hasText("80.00% ↗")).assertExists()
        onNode(hasText("前日比 +0.00% →")).assertExists()
        onNode(hasClickAction()).assertExists()
    }

    @Test
    fun widgetContent_withDamData_twoColumnLayout_rendersDamNamePercentage() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(width = 130.dp, height = 200.dp))
        provideWidgetContent(
            damData = androidTestDamData(storagePercentage = 80.0f)
                .copy(storagePercentageTrend = Trend.UP)
        )

        onNode(hasText("早明浦ダム")).assertExists()
        onNode(hasText("80.00% ↗")).assertExists()
    }

    @Test
    fun widgetContent_withDamData_noDayChange_rendersPlaceholder() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(width = 260.dp, height = 120.dp))
        provideWidgetContent(
            damData = androidTestDamData(storagePercentage = 80.0f)
                .copy(
                    storagePercentageTrend = Trend.FLAT,
                    storagePercentageDayChange = null,
                    storagePercentageDayChangeTrend = Trend.UNKNOWN
                )
        )

        onNode(hasText("早明浦ダム")).assertExists()
        onNode(hasText("80.00%")).assertExists()
        onNode(hasText("前日比 -- %")).assertExists()
    }

    @Test
    fun widgetContent_withEnglishLocale_localizesDayChangeLabel() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(width = 260.dp, height = 120.dp))
        provideWidgetContent(
            damData = androidTestDamData(storagePercentage = 80.0f),
            localeTag = "en"
        )

        onNode(hasText("Day-over-day +0.00% →")).assertExists()
    }

    @Test
    fun widgetContent_withoutDamData_rendersInitialPrompt() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(width = 260.dp, height = 120.dp))
        val settings = AppSettings(
            stateInitialMessage = "初回",
            msgInitialMessageJa = "アプリを開いて更新してください"
        )
        provideWidgetContent(damData = null, settings = settings)

        onNode(hasText("早明浦ダム")).assertExists()
        onNode(hasText(settings.getInitialMessageText(isJapanese = true))).assertExists()
    }

    private fun GlanceAppWidgetUnitTest.provideWidgetContent(
        damData: DamData?,
        settings: AppSettings = AppSettings(),
        loadStatus: DamLoadStatus = DamLoadStatus.INITIAL,
        localeTag: String = "ja-JP"
    ) {
        provideComposable {
            GlanceTheme(colors = ColorProviders(lightColorScheme())) {
                provider.DamWidgetContent(
                    context = context,
                    damData = damData,
                    settings = settings,
                    lastFetchTime = 0L,
                    loadStatus = loadStatus,
                    localeTag = localeTag
                )
            }
        }
    }
}
