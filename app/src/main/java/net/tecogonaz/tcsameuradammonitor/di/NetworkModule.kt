// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.tecogonaz.tcsameuradammonitor.data.source.remote.MlitEndpointConfig
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorEndpointConfig
import javax.inject.Singleton

/**
 * 国土交通省ダムデータ取得用のネットワーク関連設定を注入するためのHiltモジュール。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    /**
     * 国土交通省（MLIT）の各種データ取得用エンドポイント（接続先URL）の設定を提供します。
     *
     * @return 本番環境用の[MlitEndpointConfig]インスタンス
     */
    @Provides
    @Singleton
    fun provideMlitEndpointConfig(): MlitEndpointConfig =
        MlitEndpointConfig.production()

    @Provides
    @Singleton
    fun provideSudmonitorEndpointConfig(): SudmonitorEndpointConfig =
        SudmonitorEndpointConfig.production()
}
