// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.navigation

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import net.tecogonaz.tcsameuradammonitor.MainActivity
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.di.DatabaseModule
import net.tecogonaz.tcsameuradammonitor.di.RepositoryModule
import net.tecogonaz.tcsameuradammonitor.di.WorkerModule
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.DamListData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DatabaseMaintenanceRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamDataRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamWorkManagerGateway
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDatabaseMaintenanceRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeHistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeHistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.testutil.androidTestDamData
import net.tecogonaz.tcsameuradammonitor.ui.main.TestTags
import net.tecogonaz.tcsameuradammonitor.ui.settings.TARGET_DAM_SELECTOR_TEST_TAG
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils
import net.tecogonaz.tcsameuradammonitor.worker.DamWorkManagerGateway
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * アプリのメイン画面（[MainActivity]）における画面遷移とナビゲーションの動作を検証する UI テストクラス。
 * ナビゲーションドロワーを用いた各画面（メイン画面、過去データ検索、過去データ一覧、設定、デバッグログ）への遷移、
 * バックプレスの挙動、および特定のディープリンク（ウィジェットクリックやショートカット経由の起動）に
 * 応じた適切な画面への直接ナビゲーション処理を検証します。
 */
@HiltAndroidTest
@UninstallModules(
    RepositoryModule::class,
    DatabaseModule::class,
    WorkerModule::class
)
class MainActivityNavigationAndroidTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val settingsFake = HiltFakeSettingsRepository(net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings())
    private val damDataFake = HiltFakeDamDataRepository(androidTestDamData())
    private val debugLogFake = HiltFakeDebugLogRepository()
    private val historicalSearchFake = HiltFakeHistoricalSearchRepository()
    private val historicalComparisonFake = HiltFakeHistoricalComparisonRepository()
    private val databaseMaintenanceFake = HiltFakeDatabaseMaintenanceRepository()
    private val debugDataSessionFake = HiltFakeDebugDataSessionRepository()
    private val workGatewayFake = HiltFakeDamWorkManagerGateway()
    private val sudmonitorHistoryFake = HiltFakeSudmonitorHistoryRepository()

    @BindValue
    @JvmField
    val boundSettingsRepository: SettingsRepository = settingsFake

    @BindValue
    @JvmField
    val boundDamDataRepository: DamDataRepository = damDataFake

    @BindValue
    @JvmField
    val boundDebugLogRepository: DebugLogRepository = debugLogFake

    @BindValue
    @JvmField
    val boundHistoricalSearchRepository: HistoricalSearchRepository = historicalSearchFake

    @BindValue
    @JvmField
    val boundHistoricalComparisonRepository: HistoricalComparisonRepository = historicalComparisonFake

    @BindValue
    @JvmField
    val boundDatabaseMaintenanceRepository: DatabaseMaintenanceRepository = databaseMaintenanceFake

    @BindValue
    @JvmField
    val boundDebugDataSessionRepository: DebugDataSessionRepository = debugDataSessionFake

    @BindValue
    @JvmField
    val boundDamWorkManagerGateway: DamWorkManagerGateway = workGatewayFake

    @BindValue
    @JvmField
    val boundSudmonitorHistoryRepository: SudmonitorHistoryRepository = sudmonitorHistoryFake

    @Before
    fun setUp() {
        settingsFake.reset(net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings())
        damDataFake.reset(androidTestDamData(), DamLoadStatus.SUCCESS)
        debugLogFake.reset()
        historicalSearchFake.reset()
        workGatewayFake.reset()
        sudmonitorHistoryFake.reset()
        hiltRule.inject()
    }

    @Test
    fun drawerSettingsItem_navigatesToSettingsAndBackToMain() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForMainRoot()

            openDrawerIfNeeded()
            composeRule.onNodeWithText(targetString(R.string.nav_settings)).performClick()
            waitForText(targetString(R.string.settings_general_show_notification))

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitForMainRoot()
            assertMainContentVisible()
        }
    }

    @Test
    fun permanentSidebar_collapseAndExpand() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            if (!hasNodesWithTag(TestTags.PERMANENT_ROOT)) {
                return@use
            }

            composeRule.onNodeWithTag(TestTags.SIDEBAR_COLLAPSE_BUTTON).assertIsDisplayed()
            composeRule.onNodeWithTag(TestTags.SIDEBAR_EXPAND_BUTTON).assertDoesNotExist()

            composeRule.onNodeWithTag(TestTags.SIDEBAR_COLLAPSE_BUTTON).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                hasNodesWithTag(TestTags.SIDEBAR_EXPAND_BUTTON)
            }
            composeRule.onNodeWithTag(TestTags.SIDEBAR_COLLAPSE_BUTTON).assertDoesNotExist()
            composeRule.onNodeWithTag(TestTags.SIDEBAR_EXPAND_BUTTON).assertIsDisplayed()

            composeRule.onNodeWithTag(TestTags.SIDEBAR_EXPAND_BUTTON).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                hasNodesWithTag(TestTags.SIDEBAR_COLLAPSE_BUTTON)
            }
            composeRule.onNodeWithTag(TestTags.SIDEBAR_COLLAPSE_BUTTON).assertIsDisplayed()
            composeRule.onNodeWithTag(TestTags.SIDEBAR_EXPAND_BUTTON).assertDoesNotExist()
        }
    }

    @Test
    fun settingsTopBack_thenOpenDrawer_keepsMainContentVisible() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()

            openDrawerIfNeeded()
            composeRule.onNodeWithText(targetString(R.string.nav_settings)).performClick()
            waitForText(targetString(R.string.settings_general_show_notification))

            pressTopBack()
            waitForMainRoot()
            openDrawerIfNeeded()

            assertMainContentVisible()
            composeRule.onNodeWithText(targetString(R.string.nav_settings)).assertIsDisplayed()
        }
    }

    @Test
    fun historicalManageTopBack_thenOpenDrawer_keepsMainContentVisible() {
        historicalSearchFake.reset()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()

            openDrawerIfNeeded()
            composeRule.onNodeWithText(targetString(R.string.nav_historical_manage)).performClick()
            waitForText(targetString(R.string.title_historical_manage))

            pressTopBack()
            waitForMainRoot()
            openDrawerIfNeeded()

            assertMainContentVisible()
            composeRule.onNodeWithText(targetString(R.string.nav_historical_manage)).assertIsDisplayed()
        }
    }

    @Test
    fun appInfoTopBack_thenOpenDrawer_keepsMainContentVisible() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()

            openDrawerIfNeeded()
            composeRule.onNodeWithText(targetString(R.string.nav_app_info)).performClick()
            waitForText(targetString(R.string.app_info_title))

            pressTopBack()
            waitForMainRoot()
            openDrawerIfNeeded()

            assertMainContentVisible()
            composeRule.onNodeWithText(targetString(R.string.nav_app_info)).assertIsDisplayed()
        }
    }

    @Test
    fun applicationPreferencesIntent_startsAtSettingsAndBackFinishesActivity() {
        val intent = Intent(
            Intent.ACTION_APPLICATION_PREFERENCES,
            null,
            InstrumentationRegistry.getInstrumentation().targetContext,
            MainActivity::class.java
        )

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            waitForText(targetString(R.string.settings_general_show_notification))

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED
            }
        }
    }

    @Test
    fun applicationPreferencesNewIntent_navigatesFromMainToSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForMainRoot()

            scenario.onActivity {
                it.startActivity(
                    Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                        .setClass(it, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
            waitForText(targetString(R.string.settings_general_show_notification))
        }
    }

    @Test
    fun settingsStorageMessages_whenEnabled_displaysSameuraAndOtherSubsections() {
        settingsFake.reset(net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings())

        ActivityScenario.launch<MainActivity>(settingsPreferencesIntent()).use {
            waitForText(targetString(R.string.settings_general_show_notification))

            // マスターセクション（スイッチ既定ON）配下に2つのサブセクションが表示される
            composeRule.onNodeWithText(targetString(R.string.settings_storage_messages_sameura_title))
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText(targetString(R.string.settings_storage_messages_other_title))
                .performScrollTo()
                .assertIsDisplayed()
            // 早明浦ダム用は貯水率/貯水量ラベル、早明浦ダム以外用は従来のパーセントラベル
            composeRule.onNodeWithText(targetString(R.string.settings_storage_rate_label_80_100))
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText("80-100%")
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun settingsStorageMessages_whenDisabled_hidesBothSubsections() {
        settingsFake.reset(
            net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings(showStorageRateMessage = false)
        )

        ActivityScenario.launch<MainActivity>(settingsPreferencesIntent()).use {
            waitForText(targetString(R.string.settings_general_show_notification))

            // マスタースイッチOFF時は2つのサブセクションが表示されない
            org.junit.Assert.assertFalse(hasText(targetString(R.string.settings_storage_messages_sameura_title)))
            org.junit.Assert.assertFalse(hasText(targetString(R.string.settings_storage_messages_other_title)))
        }
    }

    @Test
    fun settingsStorageMessages_masterSwitchToggleControlsSubsections() {
        settingsFake.reset(net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings())

        ActivityScenario.launch<MainActivity>(settingsPreferencesIntent()).use {
            waitForText(targetString(R.string.settings_general_show_notification))
            val switchTitle = targetString(R.string.settings_show_storage_message)

            // スイッチをOFFにするとサブセクションが消える
            composeRule.onNodeWithText(switchTitle)
                .performScrollTo()
                .performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                !settingsFake.current.showStorageRateMessage
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                !hasText(targetString(R.string.settings_storage_messages_sameura_title)) &&
                    !hasText(targetString(R.string.settings_storage_messages_other_title))
            }

            // スイッチをONに戻すとサブセクションが再表示される
            composeRule.onNodeWithText(switchTitle)
                .performScrollTo()
                .performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                settingsFake.current.showStorageRateMessage
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                hasText(targetString(R.string.settings_storage_messages_sameura_title)) &&
                    hasText(targetString(R.string.settings_storage_messages_other_title))
            }
        }
    }

    @Test
    fun settingsDamSelectionItem_changesDamInDialogs_withoutExitingActivity() {
        val targetDam = DamListData.allDams.first {
            it.id != net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings.DEFAULT_DAM_ID
        }
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val targetDamName = LocaleUtils.normalDamName(
            targetContext.resources.configuration.locales[0].toLanguageTag(),
            targetDam
        )

        val settingsIntent = Intent(Intent.ACTION_APPLICATION_PREFERENCES)
            .setClass(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MainActivity::class.java
            )
        ActivityScenario.launch<MainActivity>(settingsIntent).use { scenario ->
            waitForText(targetString(R.string.settings_general_show_notification))

            composeRule.onNodeWithText(targetString(R.string.settings_dam_dam_name))
                .performScrollTo()
                .performClick()
            waitForText(targetString(R.string.dam_selection_title))
            composeRule.onNodeWithText(targetString(R.string.dam_selection_title))
                .assertIsDisplayed()

            composeRule.onNodeWithText(targetString(R.string.dialog_cancel)).performClick()
            org.junit.Assert.assertEquals(
                net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings.DEFAULT_DAM_ID,
                settingsFake.current.targetDamId
            )
            org.junit.Assert.assertEquals(
                androidx.lifecycle.Lifecycle.State.RESUMED,
                scenario.state
            )
            waitForText(targetString(R.string.settings_general_show_notification))

            composeRule.onNodeWithText(targetString(R.string.settings_dam_dam_name))
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithTag(TARGET_DAM_SELECTOR_TEST_TAG).performClick()

            composeRule.onNodeWithText(targetDamName)
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithText(targetString(R.string.dam_selection_select_action))
                .performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                settingsFake.current.targetDamId == targetDam.id
            }

            org.junit.Assert.assertEquals(
                androidx.lifecycle.Lifecycle.State.RESUMED,
                scenario.state
            )
            waitForText(targetString(R.string.settings_general_show_notification))
            composeRule.onNodeWithText(targetDamName).assertIsDisplayed()
        }
    }

    @Test
    fun drawerHistoricalManageItem_displaysSavedMetaThroughAppNavHost() {
        val meta = historicalSearchMeta(id = 100L)
        historicalSearchFake.reset(
            metaList = listOf(meta),
            dataByMetaId = mapOf(meta.id to historicalData())
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()

            openDrawerIfNeeded()
            composeRule.onNodeWithText(targetString(R.string.nav_historical_manage)).performClick()
            waitForText(targetString(R.string.title_historical_manage))
            waitForText("2026/05/01 01:00 - 2026/05/02 00:00")
        }
    }

    @Test
    fun drawerHistoricalManageItem_withNoSavedMetaDisplaysEmptyState() {
        historicalSearchFake.reset()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()

            openDrawerIfNeeded()
            composeRule.onNodeWithText(targetString(R.string.nav_historical_manage)).performClick()
            waitForText(targetString(R.string.title_historical_manage))
            waitForText(targetString(R.string.historical_manage_empty))
        }
    }

    @Test
    fun drawerAppInfoItem_navigatesToAppInfo() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()

            openDrawerIfNeeded()
            composeRule.onNodeWithText(targetString(R.string.nav_app_info)).performClick()
            waitForText(targetString(R.string.app_info_title))
        }
    }

    @Test
    fun drawerDebugItem_hiddenByDefault() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()

            openDrawerIfNeeded()

            org.junit.Assert.assertFalse(hasExactText(targetString(R.string.nav_debug)))
        }
    }

    @Test
    fun drawerDebugItem_whenVisibleNavigatesToDebugAndDebugLogReturnsToDebug() {
        settingsFake.reset(
            net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings(
                debugSettingsVisible = true,
                lastBootTime = currentBootTimeMillis(),
                initialAutoUpdateDialogShown = true
            )
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()

            openDrawerIfNeeded()
            clickDebugNavItem()
            waitForText(targetString(R.string.title_activity_debug))

            clickVisibleText(targetString(R.string.settings_debug_log))
            waitForText(targetString(R.string.title_activity_debug_log))
            pressTopBack()
            waitForText(targetString(R.string.title_activity_debug))
        }
    }

    @Test
    fun debugDisableButton_hidesDebugEntrySetting() {
        settingsFake.reset(
            net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings(
                debugSettingsVisible = true,
                debugModeEnabled = true,
                lastBootTime = currentBootTimeMillis(),
                initialAutoUpdateDialogShown = true,
                debugSimulateMode = net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode.LOADING_FAILURE
            )
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()

            openDrawerIfNeeded()
            clickDebugNavItem()
            waitForText(targetString(R.string.title_activity_debug))

            clickVisibleText(targetString(R.string.settings_disable_debug))
            composeRule.waitUntil(timeoutMillis = 5_000) {
                !settingsFake.current.debugSettingsVisible
            }

            org.junit.Assert.assertFalse(settingsFake.current.debugModeEnabled)
            org.junit.Assert.assertEquals(
                net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode.NONE,
                settingsFake.current.debugSimulateMode
            )
        }
    }

    @Test
    fun appInfoDetailItems_navigateToDetailRoutesAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForMainRoot()

            openDrawerIfNeeded()
            composeRule.onNodeWithText(targetString(R.string.nav_app_info)).performClick()
            waitForText(targetString(R.string.app_info_title))

            clickVisibleText(targetString(R.string.app_info_terms_of_use))
            waitForAppInfoDetail(appInfoLocaleAwareTitle(R.string.dialog_terms_of_use_title, R.string.dialog_terms_of_use_ja_title))
            pressDetailBack()
            waitForText(targetString(R.string.app_info_title))

            clickVisibleText(targetString(R.string.app_info_privacy_policy))
            waitForAppInfoDetail(appInfoLocaleAwareTitle(R.string.dialog_privacy_policy_title, R.string.dialog_privacy_policy_ja_title))
            pressDetailBack()
            waitForText(targetString(R.string.app_info_title))

            clickVisibleText(targetString(R.string.app_info_license))
            waitForAppInfoDetail(targetString(R.string.dialog_license_title))
            pressDetailBack()
            waitForText(targetString(R.string.app_info_title))

            clickVisibleText(targetString(R.string.app_info_oss_license))
            waitForAppInfoDetail(targetString(R.string.dialog_oss_license_title))
        }
    }

    @Test
    fun firstRunDialogDecline_staysOnMainRouteAndDoesNotScheduleAutoUpdate() {
        settingsFake.reset(
            net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings(
                initialAutoUpdateDialogShown = false
            )
        )
        damDataFake.reset(data = null, loadStatus = DamLoadStatus.INITIAL)

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()

            composeRule.onNodeWithText(targetString(R.string.dialog_initial_auto_update_title))
                .assertIsDisplayed()
            composeRule.onNodeWithText(targetString(R.string.action_no)).performClick()
            acceptNotificationPermissionDialogIfShown()
            waitForMainRoot()

            composeRule.onNodeWithTag(TestTags.MAIN_ROOT).assertIsDisplayed()
            org.junit.Assert.assertTrue(settingsFake.current.initialAutoUpdateDialogShown)
            org.junit.Assert.assertTrue(workGatewayFake.scheduled.isEmpty())
        }
    }

    private fun openDrawerIfNeeded() {
        val menuDescription = targetString(R.string.desc_menu)
        val menuNodes = composeRule.onAllNodesWithContentDescription(menuDescription).fetchSemanticsNodes()
        if (menuNodes.isNotEmpty()) {
            composeRule.onAllNodesWithContentDescription(menuDescription)[0].performClick()
            waitForText(targetString(R.string.nav_settings))
        }
    }

    private fun clickVisibleText(text: String) {
        composeRule.onNodeWithText(text)
            .performScrollTo()
            .performClick()
    }

    private fun clickDebugNavItem() {
        val localized = targetString(R.string.nav_debug)
        val text = when {
            hasText(localized) -> localized
            hasText("Debug") -> "Debug"
            else -> localized
        }
        clickVisibleText(text)
    }

    private fun pressDetailBack() {
        composeRule.onAllNodesWithContentDescription(targetString(R.string.desc_back))[0].performClick()
        composeRule.waitForIdle()
    }

    private fun pressTopBack() {
        composeRule.onAllNodesWithContentDescription(targetString(R.string.desc_back))[0].performClick()
    }

    private fun acceptNotificationPermissionDialogIfShown() {
        composeRule.waitForIdle()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val allowButtonSelectors: List<BySelector> = listOf(
            By.res("com.android.permissioncontroller", "permission_allow_button"),
            By.res("com.google.android.permissioncontroller", "permission_allow_button"),
            By.textContains("Allow"),
            By.textContains("許可")
        )
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            allowButtonSelectors.firstNotNullOfOrNull { selector ->
                device.findObject(selector)
            }?.let { button ->
                button.click()
                device.waitForIdle()
                composeRule.waitForIdle()
                return
            }
            SystemClock.sleep(100L)
        }
    }

    private fun waitForMainRoot() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasNodesWithTag(TestTags.MAIN_ROOT) || hasNodesWithTag(TestTags.PERMANENT_ROOT)
        }
        composeRule.waitForIdle()
    }

    private fun assertMainContentVisible() {
        if (hasNodesWithTag(TestTags.MAIN_ROOT)) {
            composeRule.onNodeWithTag(TestTags.MAIN_ROOT).assertIsDisplayed()
        } else {
            composeRule.onNodeWithTag(TestTags.PERMANENT_ROOT).assertIsDisplayed()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasText(text)
        }
        composeRule.waitForIdle()
    }

    private fun waitForAppInfoDetail(title: String) {
        val appInfoVersion = targetString(R.string.app_info_version)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasText(title) && !hasExactText(appInfoVersion)
        }
        composeRule.waitForIdle()
    }

    private fun hasNodesWithTag(tag: String): Boolean =
        runCatching {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)

    private fun hasText(text: String): Boolean =
        runCatching {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)

    private fun hasExactText(text: String): Boolean =
        runCatching {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)

    private fun targetString(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    /** 設定画面（SettingsScreen）を直接起動するための ACTION_APPLICATION_PREFERENCES インテント。 */
    private fun settingsPreferencesIntent(): Intent =
        Intent(Intent.ACTION_APPLICATION_PREFERENCES)
            .setClass(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MainActivity::class.java
            )

    private fun appInfoLocaleAwareTitle(nonJaTitleRes: Int, jaTitleRes: Int): String =
        if (targetString(R.string.app_info_terms_of_use) == targetString(R.string.app_info_terms_of_use_ja)) {
            targetString(jaTitleRes)
        } else {
            targetString(nonJaTitleRes)
        }

    private fun currentBootTimeMillis(): Long =
        System.currentTimeMillis() - SystemClock.elapsedRealtime()

    private fun historicalSearchMeta(id: Long): HistoricalSearchMeta =
        HistoricalSearchMeta(
            id = id,
            observationStationId = "1368080700010",
            observationStationName = "早明浦ダム",
            riverSystemName = "吉野川",
            riverName = "吉野川",
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501",
            fetchedAt = 0L,
            dataStartTimeStr = "2026/05/01 01:00",
            dataEndTimeStr = "2026/05/02 00:00",
            dataStartStoragePct = 80.0f,
            dataEndStoragePct = 81.0f,
            dataMinStoragePct = 80.0f,
            dataMaxStoragePct = 81.0f
        )

    private fun historicalData(): List<DamHistoricalData> =
        listOf(
            DamHistoricalData(
                time = "2026/05/01 01:00",
                catchmentAverageRainfall = 0.0f,
                storageVolume = 100_000f,
                inflow = 10.0f,
                outflow = 9.0f,
                storagePercentage = 80.0f
            ),
            DamHistoricalData(
                time = "2026/05/02 00:00",
                catchmentAverageRainfall = 0.0f,
                storageVolume = 100_100f,
                inflow = 10.5f,
                outflow = 9.5f,
                storagePercentage = 81.0f
            )
        )
}
