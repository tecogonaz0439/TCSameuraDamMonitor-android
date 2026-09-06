// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.tecogonaz.tcsameuradammonitor.worker.DamWorkManagerGateway
import net.tecogonaz.tcsameuradammonitor.worker.DefaultDamWorkManagerGateway

/**
 * バックグラウンドデータ取得を管理するWorkManagerとのゲートウェイをバインドするHiltモジュール。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {
    
    /**
     * WorkManagerによる自動更新ジョブのスケジュール・キャンセル制御を行う[DamWorkManagerGateway]に対して、
     * 実際の実装クラスである[DefaultDamWorkManagerGateway]をバインドします。
     *
     * @param impl [DefaultDamWorkManagerGateway]のインスタンス
     * @return [DamWorkManagerGateway]インターフェース
     */
    @Binds
    abstract fun bindDamWorkManagerGateway(
        impl: DefaultDamWorkManagerGateway
    ): DamWorkManagerGateway
}
