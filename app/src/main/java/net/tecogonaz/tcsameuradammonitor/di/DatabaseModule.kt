// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.di

import android.content.Context
import androidx.room.Room
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamDatabase
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DatabaseTransactionRunner
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DebugLogDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.HistoricalSearchDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.RoomDatabaseTransactionRunner
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ローカルデータベース（Room）関連のオブジェクトとデータアクセスオブジェクト（DAO）を注入するためのHiltモジュール。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Roomの[DamDatabase]のインスタンスを作成してシングルトンとして提供します。
     *
     * @param context アプリケーションコンテキスト
     * @return ビルド済みの[DamDatabase]インスタンス
     */
    @Provides
    @Singleton
    fun provideDamDatabase(@ApplicationContext context: Context): DamDatabase {
        return Room.databaseBuilder(
            context,
            DamDatabase::class.java,
            "dam_database"
        )
            .build()
     }

    /**
     * ダムの最新観測データへのデータアクセスオブジェクト[DamDao]を提供します。
     *
     * @param database [DamDatabase]のインスタンス
     * @return [DamDao]の実装クラス
     */
    @Provides
    @Singleton
    fun provideDamDao(database: DamDatabase): DamDao {
        return database.damDao()
    }

    /**
     * アプリ内動作デバッグ用のログ永続化を担当する[DebugLogDao]を提供します。
     *
     * @param database [DamDatabase]のインスタンス
     * @return [DebugLogDao]の実装クラス
     */
    @Provides
    @Singleton
    fun provideDebugLogDao(database: DamDatabase): DebugLogDao {
        return database.debugLogDao()
    }

    /**
     * 過去ダムデータ検索結果および検索履歴メタデータを管理する[HistoricalSearchDao]を提供します。
     *
     * @param database [DamDatabase]のインスタンス
     * @return [HistoricalSearchDao]の実装クラス
     */
    @Provides
    @Singleton
    fun provideHistoricalSearchDao(database: DamDatabase): HistoricalSearchDao {
        return database.historicalSearchDao()
    }

    /**
     * sudmonitor の日次過去データ（保存行・観測明細）を管理する[SudmonitorHistoryDao]を提供します。
     *
     * @param database [DamDatabase]のインスタンス
     * @return [SudmonitorHistoryDao]の実装クラス
     */
    @Provides
    @Singleton
    fun provideSudmonitorHistoryDao(database: DamDatabase): SudmonitorHistoryDao {
        return database.sudmonitorHistoryDao()
    }

    /**
     * Roomのデータベーストランザクション処理を実行するためのトランザクションランナー[DatabaseTransactionRunner]をバインドして提供します。
     *
     * @param runner Room用のトランザクションランナー実装クラスである[RoomDatabaseTransactionRunner]
     * @return [DatabaseTransactionRunner]インターフェース
     */
    @Provides
    @Singleton
    fun provideDatabaseTransactionRunner(
        runner: RoomDatabaseTransactionRunner
    ): DatabaseTransactionRunner = runner
}
