// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 端末のインターネット接続状態（可用性）を確認するためのインターフェース。
 */
interface NetworkAvailability {
    /**
     * インターネットへの有効な接続が利用可能かどうかを判定します。
     *
     * @return インターネットに接続されている場合はtrue、そうでない場合はfalse
     */
    fun isNetworkAvailable(): Boolean
}

/**
 * ネットワーク機能のステータス情報スナップショット。
 *
 * @property hasActiveNetwork 端末がアクティブなネットワーク接続（Wi-Fi、モバイル回線等）を現在確立している場合はtrue
 * @property hasInternetCapability ネットワークがインターネット接続能力を有している場合はtrue
 * @property hasValidatedCapability ネットワークが実際に疎通可能（インターネットへのトラフィック疎通検証をパスした）状態である場合はtrue
 */
data class NetworkCapabilitySnapshot(
    val hasActiveNetwork: Boolean,
    val hasInternetCapability: Boolean,
    val hasValidatedCapability: Boolean
)

/**
 * スナップショット情報に基づいて、完全に疎通可能なインターネット接続が確立されているかを判定します。
 *
 * @param snapshot 判定対象のネットワークスナップショット
 * @return 3つの条件がすべて満たされている場合はtrue
 */
fun isNetworkAvailable(snapshot: NetworkCapabilitySnapshot): Boolean =
    snapshot.hasActiveNetwork &&
        snapshot.hasInternetCapability &&
        snapshot.hasValidatedCapability

/**
 * Androidの[ConnectivityManager]を利用して端末のインターネット接続性を確認する、[NetworkAvailability]の具現化クラス。
 */
@Singleton
class RealNetworkAvailability @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkAvailability {
    
    /**
     * 端末の現在のネットワーク状態をリアルタイム判定します。
     *
     * アクティブなネットワークを調べ、インターネット機能（NET_CAPABILITY_INTERNET）と
     * 接続疎通検証（NET_CAPABILITY_VALIDATED）がともにパスしているかを判定します。
     *
     * @return 疎通可能なネットワーク接続が利用できる場合はtrue
     */
    override fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return isNetworkAvailable(
            NetworkCapabilitySnapshot(
                hasActiveNetwork = true,
                hasInternetCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                hasValidatedCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        )
    }
}
