// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.tecogonaz.tcsameuradammonitor.data.repository.DamDataRepositoryImpl
import net.tecogonaz.tcsameuradammonitor.data.repository.DatabaseMaintenanceRepositoryImpl
import net.tecogonaz.tcsameuradammonitor.data.repository.DebugLogRepositoryImpl
import net.tecogonaz.tcsameuradammonitor.data.repository.DebugDataSessionRepositoryImpl
import net.tecogonaz.tcsameuradammonitor.data.repository.HistoricalComparisonRepositoryImpl
import net.tecogonaz.tcsameuradammonitor.data.repository.HistoricalSearchRepositoryImpl
import net.tecogonaz.tcsameuradammonitor.data.repository.SettingsRepositoryImpl
import net.tecogonaz.tcsameuradammonitor.data.repository.SudmonitorHistoryRepositoryImpl
import net.tecogonaz.tcsameuradammonitor.data.source.local.AndroidAssetBytesReader
import net.tecogonaz.tcsameuradammonitor.data.source.local.AssetBytesReader
import net.tecogonaz.tcsameuradammonitor.data.source.local.HistoricalComparisonAssetStore
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfigProvider
import net.tecogonaz.tcsameuradammonitor.domain.model.DefaultDamConfigProvider
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DatabaseMaintenanceRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import javax.inject.Singleton

/**
 * ドメイン層のリポジトリインターフェースと、データ層の具体的な実装クラスをバインドするHiltモジュール。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    /**
     * 最新のダム観測データの取得・保持を処理するリポジトリの実装クラス[DamDataRepositoryImpl]をバインドします。
     */
    @Binds
    abstract fun bindDamDataRepository(
        impl: DamDataRepositoryImpl
    ): DamDataRepository

    /**
     * Roomデータベースのメンテナンス（VACUUM等によるデータ整理）を処理するリポジトリの実装クラス[DatabaseMaintenanceRepositoryImpl]をバインドします。
     */
    @Binds
    abstract fun bindDatabaseMaintenanceRepository(
        impl: DatabaseMaintenanceRepositoryImpl
    ): DatabaseMaintenanceRepository

    /**
     * アプリ全体の各種設定値の永続化管理を処理するリポジトリの実装クラス[SettingsRepositoryImpl]をバインドします。
     */
    @Binds
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    /**
     * デバッグログの出力・保存を処理するリポジトリの実装クラス[DebugLogRepositoryImpl]をバインドします。
     */
    @Binds
    abstract fun bindDebugLogRepository(
        impl: DebugLogRepositoryImpl
    ): DebugLogRepository

    /** Debug ON直前の表示データ待避とOFF時の復元を提供します。 */
    @Binds
    abstract fun bindDebugDataSessionRepository(
        impl: DebugDataSessionRepositoryImpl
    ): DebugDataSessionRepository

    /**
     * 過去ダムデータ検索結果および検索履歴の永続化を処理するリポジトリの実装クラス[HistoricalSearchRepositoryImpl]をバインドします。
     */
    @Binds
    abstract fun bindHistoricalSearchRepository(
        impl: HistoricalSearchRepositoryImpl
    ): HistoricalSearchRepository

    /**
     * 監視対象ダム（早明浦ダム等）の静的設定を提供するプロバイダの実装クラス[DefaultDamConfigProvider]をバインドします。
     */
    @Binds
    abstract fun bindDamConfigProvider(
        impl: DefaultDamConfigProvider
    ): DamConfigProvider

    /**
     * 過去比較グラフ用データを提供するリポジトリの実装クラス[HistoricalComparisonRepositoryImpl]をバインドします。
     */
    @Binds
    abstract fun bindHistoricalComparisonRepository(
        impl: HistoricalComparisonRepositoryImpl
    ): HistoricalComparisonRepository

    /**
     * sudmonitor の日次過去データ（直近31暦日）の取得・保存を処理するリポジトリの実装クラス[SudmonitorHistoryRepositoryImpl]をバインドします。
     */
    @Binds
    abstract fun bindSudmonitorHistoryRepository(
        impl: SudmonitorHistoryRepositoryImpl
    ): SudmonitorHistoryRepository

    companion object {

        /**
         * バンドルassets内の過去datファイルを読み込むストア[HistoricalComparisonAssetStore]を提供します。
         */
        @Provides
        @Singleton
        fun provideHistoricalComparisonAssetStore(
            reader: AssetBytesReader,
            parser: DamFileParser
        ): HistoricalComparisonAssetStore = HistoricalComparisonAssetStore(reader, parser)

        /**
         * Androidのassetsからファイルを読み出す[AssetBytesReader]実装を提供します。
         */
        @Provides
        @Singleton
        fun provideAssetBytesReader(@ApplicationContext context: Context): AssetBytesReader =
            AndroidAssetBytesReader(context)
    }
}
