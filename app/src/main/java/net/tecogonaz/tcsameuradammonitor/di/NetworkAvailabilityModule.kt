// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.tecogonaz.tcsameuradammonitor.data.source.remote.NetworkAvailability
import net.tecogonaz.tcsameuradammonitor.data.source.remote.RealNetworkAvailability

/**
 * ネットワークの可用性（接続状態）監視に関するコンポーネントをバインドするHiltモジュール。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkAvailabilityModule {
    
    /**
     * 端末のネットワーク接続状態を判定する[NetworkAvailability]インターフェースに対して、
     * 実際のシステムサービスを利用した実装クラスである[RealNetworkAvailability]をバインドします。
     *
     * @param impl [RealNetworkAvailability]のインスタンス
     * @return [NetworkAvailability]インターフェース
     */
    @Binds
    abstract fun bindNetworkAvailability(
        impl: RealNetworkAvailability
    ): NetworkAvailability
}
