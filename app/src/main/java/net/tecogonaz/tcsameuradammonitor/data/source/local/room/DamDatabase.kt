// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.Database
import androidx.room.RoomDatabase


/**
 * アプリケーションのすべての永続化データ（最新ダム観測データ、デバッグログ、過去データ検索結果、
 * sudmonitor 日次過去データ）を管理するRoomデータベース。
 */
@Database(
    entities = [
        DamEntity::class,
        DebugLogEntity::class,
        HistoricalDamDataEntity::class,
        HistoricalSearchMetaEntity::class,
        SudmonitorHistoryEntity::class,
        SudmonitorHistoryObservationEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class DamDatabase : RoomDatabase() {

    /**
     * 最新のリアルタイム観測データを管理する[DamDao]のインスタンスを取得します。
     */
    abstract fun damDao(): DamDao

    /**
     * アプリデバッグ用動作ログを管理する[DebugLogDao]のインスタンスを取得します。
     */
    abstract fun debugLogDao(): DebugLogDao

    /**
     * 過去ダムデータ検索履歴および履歴明細を管理する[HistoricalSearchDao]のインスタンスを取得します。
     */
    abstract fun historicalSearchDao(): HistoricalSearchDao

    /**
     * sudmonitor の日次過去データとその観測明細を管理する[SudmonitorHistoryDao]のインスタンスを取得します。
     */
    abstract fun sudmonitorHistoryDao(): SudmonitorHistoryDao
}
