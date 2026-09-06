// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 実機の ConnectivityManager API に接続してネットワーク状況を確認する [RealNetworkAvailability] の Instrumentation テストクラス。
 * Android OS のネットワークサービスバインド時に、クラッシュを引き起こさず安全にネットワーク接続有無の取得メソッド
 * （`isNetworkAvailable`）を実行できることを検証します。
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class RealNetworkAvailabilityAndroidTest {
    @Test
    fun isNetworkAvailable_androidWrapperDoesNotCrash() {
        val context: Context = ApplicationProvider.getApplicationContext()

        RealNetworkAvailability(context).isNetworkAvailable()
    }
}
