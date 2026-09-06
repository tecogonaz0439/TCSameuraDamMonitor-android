// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ネットワークの接続状態を確認するユーティリティ [isNetworkAvailable] のユニットテストクラス。
 * 接続されたアクティブなネットワークが存在し、かつインターネットへのルーティングおよび疎通確認（Validated）が
 * 取れている場合にのみネットワーク接続中（`true`）と判定し、それ以外の不完全な状態（一部の権限がないなど）では
 * 非接続（`false`）と判定することを確認します。
 */
class NetworkAvailabilityTest {
    @Test
    fun isNetworkAvailable_requiresActiveValidatedInternet() {
        assertTrue(
            isNetworkAvailable(
                NetworkCapabilitySnapshot(
                    hasActiveNetwork = true,
                    hasInternetCapability = true,
                    hasValidatedCapability = true
                )
            )
        )

        assertFalse(
            isNetworkAvailable(
                NetworkCapabilitySnapshot(
                    hasActiveNetwork = true,
                    hasInternetCapability = true,
                    hasValidatedCapability = false
                )
            )
        )
        assertFalse(
            isNetworkAvailable(
                NetworkCapabilitySnapshot(
                    hasActiveNetwork = true,
                    hasInternetCapability = false,
                    hasValidatedCapability = true
                )
            )
        )
        assertFalse(
            isNetworkAvailable(
                NetworkCapabilitySnapshot(
                    hasActiveNetwork = false,
                    hasInternetCapability = true,
                    hasValidatedCapability = true
                )
            )
        )
    }
}
