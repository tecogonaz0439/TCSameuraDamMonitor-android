// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AndroidManifest.xml におけるシステムブロードキャスト受信器（BroadcastReceiver）の登録状態を検証する Instrumentation テストクラス。
 * OS の端末起動完了（`ACTION_BOOT_COMPLETED`）に対する [BootCompletedReceiver]、システム言語変更（`ACTION_LOCALE_CHANGED`）に対する
 * [LocaleChangeReceiver]、およびタイムゾーン変更（`ACTION_TIMEZONE_CHANGED`）に対する [TimeZoneChangeReceiver] が、
 * それぞれ対応する意図したインテント（かつ exported=true）でのみ登録され、無関係なイベントには反応しないように
 * バインドされていることを検証します。
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class ReceiverManifestAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun bootCompletedReceiver_isManifestRegisteredForBootCompletedOnly() {
        assertResolvesReceiver(Intent.ACTION_BOOT_COMPLETED, BootCompletedReceiver::class.java.name)
        assertDoesNotResolveReceiver(Intent.ACTION_LOCALE_CHANGED, BootCompletedReceiver::class.java.name)
    }

    @Test
    fun localeChangeReceiver_isManifestRegisteredForLocaleChangedOnly() {
        assertResolvesReceiver(Intent.ACTION_LOCALE_CHANGED, LocaleChangeReceiver::class.java.name)
        assertDoesNotResolveReceiver(Intent.ACTION_TIMEZONE_CHANGED, LocaleChangeReceiver::class.java.name)
    }

    @Test
    fun timeZoneChangeReceiver_isManifestRegisteredForTimezoneChangedOnly() {
        assertResolvesReceiver(Intent.ACTION_TIMEZONE_CHANGED, TimeZoneChangeReceiver::class.java.name)
        assertDoesNotResolveReceiver(Intent.ACTION_BOOT_COMPLETED, TimeZoneChangeReceiver::class.java.name)
    }

    private fun assertResolvesReceiver(action: String, receiverClassName: String) {
        val receivers = resolveReceivers(action).map { it.activityInfo.name }
        assertTrue(receivers.toString(), receivers.contains(receiverClassName))
        val receiver = resolveReceivers(action).first { it.activityInfo.name == receiverClassName }
        assertEquals(context.packageName, receiver.activityInfo.packageName)
        assertTrue(receiver.activityInfo.exported)
    }

    private fun assertDoesNotResolveReceiver(action: String, receiverClassName: String) {
        val receivers = resolveReceivers(action).map { it.activityInfo.name }
        assertFalse(receivers.toString(), receivers.contains(receiverClassName))
    }

    private fun resolveReceivers(action: String) =
        context.packageManager.queryBroadcastReceivers(
            Intent(action).setPackage(context.packageName),
            android.content.pm.PackageManager.ResolveInfoFlags.of(0)
        )
}
