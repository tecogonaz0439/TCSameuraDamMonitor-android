// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.ui.theme.TCSameuraDamMonitorTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 観測履歴データ一覧カード（[ObservationHistoryCard]）の操作 UI を検証するテストクラス。
 */
class ObservationHistoryCardAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showAllPeriodActionUsesClickableFullWidthChipAndInvokesCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var clicked = false

        composeRule.setContent {
            TCSameuraDamMonitorTheme(dynamicColor = false) {
                ObservationHistoryCard(
                    historicalData = historyRows(),
                    isExpanded = true,
                    onExpandToggle = {},
                    onShowAllPeriod = { clicked = true }
                )
            }
        }

        val label = context.getString(R.string.main_history_load_all)
        composeRule.onNodeWithText(label)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertTrue(clicked)
    }

    private fun historyRows(): List<DamHistoricalData> =
        listOf(
            DamHistoricalData("2026/05/01 01:00", 0.1f, 101f, 11f, 10f, 61f),
            DamHistoricalData("2026/05/01 02:00", 0.2f, 102f, 12f, 11f, 62f)
        )
}
