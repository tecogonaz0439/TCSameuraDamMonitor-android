// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.util

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamListData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import net.tecogonaz.tcsameuradammonitor.testutil.androidTestDamData
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils.appendJstSuffix
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone

/**
 * 通知表示制御クラス [AppNotificationManager] の Instrumentation テストクラス。
 * OS の通知チャンネル（無音設定）の自動作成、通知権限（`POST_NOTIFICATIONS`）をシェルコマンドで付与した状態での
 * 通知生成処理、フォアグラウンドサービス用通知に埋め込まれるタイトルや文字列（貯水率や日時）のフォーマット検証、
 * データ取得失敗時の通知文面の自動切り替え、および通知非表示設定時の既存通知のキャンセル動作をテストします。
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class AppNotificationManagerAndroidTest {
    private lateinit var context: Context
    private lateinit var platformNotificationManager: NotificationManager
    private lateinit var appNotificationManager: AppNotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
            .close()
        platformNotificationManager = context.getSystemService(NotificationManager::class.java)
        platformNotificationManager.cancel(AppNotificationManager.NOTIFICATION_ID)
        platformNotificationManager.cancel(AppNotificationManager.BOOT_NOTIFICATION_ID)
        appNotificationManager = AppNotificationManager(context)
    }

    @After
    fun tearDown() {
        platformNotificationManager.cancel(AppNotificationManager.NOTIFICATION_ID)
        platformNotificationManager.cancel(AppNotificationManager.BOOT_NOTIFICATION_ID)
    }

    @Test
    fun init_createsDamUpdateNotificationChannelWithoutSound() {
        val channel = platformNotificationManager.notificationChannels
            .firstOrNull { it.name == context.getString(R.string.notification_channel_name) }

        assertNotNull(channel)
        assertNull(channel!!.sound)
    }

    @Test
    fun foregroundNotification_withDamData_formatsCollapsedAndExpandedContent() {
        // 二重サフィックス（「(JST) (JST)」）回帰を非JSTタイムゾーンで確定検出するため、既定TZをUTCへ変更する
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val notification = appNotificationManager.getForegroundNotification(
                damData = androidTestDamData(storagePercentage = 80.0f),
                settings = AppSettings()
            )

            assertEquals(expectedDamName(), notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            assertTrue(text, text.contains("80.00%"))
            assertTrue(text, text.contains("05:15 (JST)"))
            assertEquals(text, 1, text.split("(JST)").size - 1)
            assertFalse(text, text.contains("(JST) (JST)"))
            val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
            assertTrue(bigText, bigText.contains("80.00%"))
            assertTrue(bigText, bigText.contains("05:15 (JST)"))
            assertEquals(bigText, 1, bigText.split("(JST)").size - 1)
            assertFalse(bigText, bigText.contains("(JST) (JST)"))
            assertNotNull(notification.contentIntent)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun foregroundNotification_withMissingDataUsesLoadStatusMessage() {
        val notification = appNotificationManager.getForegroundNotification(
            damData = null,
            settings = AppSettings(
                stateInitialMessage = "I",
                stateNetworkUnavailable = "N",
                stateLoadingError = "E"
            ),
            loadStatus = DamLoadStatus.NETWORK_UNAVAILABLE
        )

        assertEquals(expectedDamName(), notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("N", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNotNull(notification.contentIntent)
    }

    @Test
    fun formatNotificationMessage_withNullDataReturnsNull() {
        assertNull(appNotificationManager.formatNotificationMessage(null, AppSettings()))
    }

    @Test
    fun formatNotificationMessage_withDamDataIncludesDamNameDateTimeAndPercentage() {
        val message = appNotificationManager.formatNotificationMessage(
            androidTestDamData(storagePercentage = 80.0f),
            AppSettings()
        )

        assertNotNull(message)
        assertTrue(message!!, message.contains(expectedDamName()))
        assertTrue(message, message.contains("2026/05/18"))
        assertTrue(message, message.contains("05:15"))
        assertTrue(message, message.contains("80.00%"))
        assertTrue(message, !message.contains("\n"))
    }

    @Test
    fun formatSnackbarMessage_withDamDataFormatsThreeLines() {
        val message = appNotificationManager.formatSnackbarMessage(
            androidTestDamData(storagePercentage = 80.0f).copy(storagePercentageTrend = Trend.UP),
            AppSettings(
                state80_100 = "○",
                msg80_100 = "Healthy",
                msg80_100Ja = "良好"
            )
        )

        assertEquals(
            listOf(
                expectedDamName(),
                "2026/05/18 ${appendJstSuffix("05:15")} 80.00% ↗",
                if (LocaleUtils.isJapanese(context)) "○ 良好" else "○ Healthy"
            ),
            message?.lines()
        )
    }

    @Test
    fun formatSnackbarMessage_withStorageRateMessageDisabledFormatsTwoLines() {
        val message = appNotificationManager.formatSnackbarMessage(
            androidTestDamData(storagePercentage = 80.0f),
            AppSettings(showStorageRateMessage = false)
        )

        assertEquals(
            listOf(expectedDamName(), "2026/05/18 ${appendJstSuffix("05:15")} 80.00% →"),
            message?.lines()
        )
    }

    @Test
    fun formatSnackbarMessage_usesUpdatedAtFallbackAndExpectedTrendSymbols() {
        val expectedTrends = mapOf(
            Trend.UP to "↗",
            Trend.DOWN to "↘",
            Trend.FLAT to "→",
            Trend.UNKNOWN to ""
        )

        expectedTrends.forEach { (trend, symbol) ->
            val message = appNotificationManager.formatSnackbarMessage(
                androidTestDamData(storagePercentage = 75.5f).copy(
                    updatedAt = "2026/05/19 06:25",
                    storagePercentageTime = null,
                    storagePercentageTrend = trend
                ),
                AppSettings(showStorageRateMessage = false)
            )
            val expectedDataLine = listOf("2026/05/19", appendJstSuffix("06:25"), "75.50%", symbol)
                .filter { it.isNotEmpty() }
                .joinToString(" ")

            assertEquals(expectedDataLine, message?.lines()?.get(1))
        }
    }

    @Test
    fun formatSnackbarMessage_withOtherDamUsesGeneralPresetMessages() {
        val dam = DamListData.allDams.first { it.id != AppSettings.DEFAULT_DAM_ID }
        val settings = AppSettings(
            targetDamId = dam.id,
            otherState80_100 = "○",
            otherMsg80_100 = "Healthy",
            otherMsg80_100Ja = "良好"
        )

        // 早明浦ダム以外のダムでは貯水率のみで分類し、other* フィールド（一般向けプリセット）のメッセージを表示する
        val message = appNotificationManager.formatSnackbarMessage(
            androidTestDamData(storagePercentage = 80.0f).copy(
                observationStationId = dam.id,
                observationStationName = dam.nameJa,
                riverSystemName = dam.waterSystem,
                riverName = dam.river
            ).copy(storagePercentageTrend = Trend.UP),
            settings
        )

        assertEquals(
            listOf(
                LocaleUtils.normalDamName(context, dam),
                "2026/05/18 ${appendJstSuffix("05:15")} 80.00% ↗",
                if (LocaleUtils.isJapanese(context)) "○ 良好" else "○ Healthy"
            ),
            message?.lines()
        )
    }

    @Test
    fun formatSnackbarMessage_withOtherDamIgnoresStorageVolumeClassification() {
        val dam = DamListData.allDams.first { it.id != AppSettings.DEFAULT_DAM_ID }
        val settings = AppSettings(
            targetDamId = dam.id,
            otherState40_60 = "○",
            otherMsg40_60 = "Healthy",
            otherMsg40_60Ja = "良好"
        )

        // 貯水量が早明浦ダムのしきい値（80,000×10³m³超）を超えていても、
        // 早明浦ダム以外は貯水率のみで分類する（59.9%→40_60）
        val message = appNotificationManager.formatSnackbarMessage(
            androidTestDamData(storagePercentage = 59.9f).copy(
                observationStationId = dam.id,
                observationStationName = dam.nameJa,
                riverSystemName = dam.waterSystem,
                riverName = dam.river,
                storageVolumeForMessage = 100000f
            ),
            settings
        )

        assertEquals(
            listOf(
                LocaleUtils.normalDamName(context, dam),
                "2026/05/18 ${appendJstSuffix("05:15")} 59.90% →",
                if (LocaleUtils.isJapanese(context)) "○ 良好" else "○ Healthy"
            ),
            message?.lines()
        )
    }

    @Test
    fun updateNotification_whenNotificationsDisabledCancelsExistingNotifications() {
        appNotificationManager.showBootNotification()
        waitUntilActiveNotificationsContain(AppNotificationManager.BOOT_NOTIFICATION_ID)

        appNotificationManager.updateNotification(
            damData = androidTestDamData(),
            settings = AppSettings(showNotification = false)
        )

        waitUntilActiveNotificationsDoNotContain(AppNotificationManager.BOOT_NOTIFICATION_ID)
        val activeIds = platformNotificationManager.activeNotifications.map { it.id }
        assertTrue(activeIds.none { it == AppNotificationManager.NOTIFICATION_ID })
        assertTrue(activeIds.none { it == AppNotificationManager.BOOT_NOTIFICATION_ID })
    }

    private fun waitUntilActiveNotificationsContain(id: Int) {
        val deadline = System.currentTimeMillis() + 2_000L
        while (System.currentTimeMillis() < deadline) {
            if (platformNotificationManager.activeNotifications.any { it.id == id }) return
            Thread.sleep(50L)
        }
        fail("Expected notification $id to become active before testing cancellation.")
    }

    private fun waitUntilActiveNotificationsDoNotContain(id: Int) {
        val deadline = System.currentTimeMillis() + 2_000L
        while (System.currentTimeMillis() < deadline) {
            if (platformNotificationManager.activeNotifications.none { it.id == id }) return
            Thread.sleep(50L)
        }
    }

    private fun expectedDamName(): String =
        LocaleUtils.normalDamName(context, DamListData.allDams.first { it.id == AppSettings().targetDamId })
}
