// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 過去データ検索ダイアログ（[HistoricalDataSearchDialog]）の UI コンポーネントを検証する UI テストクラス。
 * 初期表示におけるデフォルト期間設定、検索実行コールバックの発火と日付フォーマット、
 * 重複する期間を指定した際のエラーメッセージ表示および検索ボタンの無効化処理、および
 * データ読み込み中やサーバーエラー発生時のインジケータ表示・操作ブロックの挙動をテストします。
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class HistoricalDataSearchDialogAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun initialState_rendersDefaultDatesAndEnabledSearch() {
        composeRule.setContent {
            MaterialTheme {
                HistoricalDataSearchDialog(
                    initialDamConfig = DamConfig.DEFAULT,
                    isLoading = false,
                    errorMessage = null,
                    onSearch = { _, _, _ -> },
                    onDismiss = {},
                    onCheckDuplicate = { _, _, _ -> false }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.historical_search_dialog_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.historical_search_start_date))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.historical_search_end_date))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.historical_search_button))
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun searchButton_invokesCallbackWithSelectedDamAndDefaultDateRange() {
        var searchedDam: DamConfig? = null
        var searchedStart: String? = null
        var searchedEnd: String? = null
        composeRule.setContent {
            MaterialTheme {
                HistoricalDataSearchDialog(
                    initialDamConfig = DamConfig.DEFAULT,
                    isLoading = false,
                    errorMessage = null,
                    onSearch = { dam, startDate, endDate ->
                        searchedDam = dam
                        searchedStart = startDate
                        searchedEnd = endDate
                    },
                    onDismiss = {},
                    onCheckDuplicate = { _, _, _ -> false }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.historical_search_button)).performClick()
        composeRule.waitForIdle()

        assertEquals(DamConfig.DEFAULT.id, searchedDam?.id)
        assertNotNull(searchedStart)
        assertNotNull(searchedEnd)
        assertEquals(8, searchedStart!!.length)
        assertEquals(8, searchedEnd!!.length)
    }

    @Test
    fun duplicateDateRange_disablesSearchAndShowsError() {
        var searchCount = 0
        composeRule.setContent {
            MaterialTheme {
                HistoricalDataSearchDialog(
                    initialDamConfig = DamConfig.DEFAULT,
                    isLoading = false,
                    errorMessage = null,
                    onSearch = { _, _, _ -> searchCount += 1 },
                    onDismiss = {},
                    onCheckDuplicate = { _, _, _ -> true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.historical_search_duplicate))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.historical_search_button))
            .assertIsNotEnabled()

        assertEquals(0, searchCount)
    }

    @Test
    fun loadingAndServerError_areRenderedAndActionsDisabled() {
        composeRule.setContent {
            MaterialTheme {
                HistoricalDataSearchDialog(
                    initialDamConfig = DamConfig.DEFAULT,
                    isLoading = true,
                    errorMessage = "history failed",
                    onSearch = { _, _, _ -> },
                    onDismiss = {},
                    onCheckDuplicate = { _, _, _ -> false }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.historical_search_dialog_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText("history failed").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.historical_search_searching))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.action_cancel))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }
}
