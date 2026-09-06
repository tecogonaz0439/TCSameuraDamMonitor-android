// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.glance.appwidget.SizeMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ホーム画面ウィジェットのブロードキャスト受信クラス [DamWidgetReceiver] の Instrumentation テストクラス。
 * Glance ウィジェットエンジン [DamWidgetProvider] が登録されていること、OS標準のウィジェット更新インテント
 * （`ACTION_APPWIDGET_UPDATE`）に対して受信器（Receiver）がマニフェスト上で正しくバインドされていること、
 * および独自の更新インテントに対しては不要に反応しない（マニフェストから露出していない）ことを検証します。
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class DamWidgetReceiverAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun glanceAppWidget_isDamWidgetProvider() {
        val receiver = DamWidgetReceiver()

        assertTrue(receiver.glanceAppWidget is DamWidgetProvider)
        assertEquals(SizeMode.Exact, (receiver.glanceAppWidget as DamWidgetProvider).sizeMode)
    }

    @Test
    fun appWidgetUpdateBroadcast_resolvesDamWidgetReceiver() {
        val receivers = queryReceivers(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE))

        assertTrue(receivers.any { it.activityInfo.name == DamWidgetReceiver::class.java.name })
    }

    @Test
    fun customReloadBroadcast_doesNotResolveDamWidgetReceiver() {
        val receivers = queryReceivers(Intent("net.tecogonaz.tcsameuradammonitor.widget.ACTION_RELOAD"))

        assertFalse(receivers.any { it.activityInfo.name == DamWidgetReceiver::class.java.name })
    }

    private fun queryReceivers(intent: Intent) =
        context.packageManager.queryBroadcastReceivers(
            intent.setPackage(context.packageName),
            PackageManager.ResolveInfoFlags.of(0)
        )
}
