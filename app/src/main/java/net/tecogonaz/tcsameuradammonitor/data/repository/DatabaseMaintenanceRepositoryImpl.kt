// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamDatabase
import net.tecogonaz.tcsameuradammonitor.domain.model.DatabaseVacuumResult
import net.tecogonaz.tcsameuradammonitor.domain.repository.DatabaseMaintenanceRepository


/**
 * [DatabaseMaintenanceRepository]の実装クラス。
 *
 * ローカルのRoomデータベースに対してVACUUMコマンドを実行し、
 * データ消去等により発生した不要領域を解放してファイルサイズを削減します。
 */
class DatabaseMaintenanceRepositoryImpl @Inject constructor(
    private val database: DamDatabase,
    @param:ApplicationContext private val context: Context
) : DatabaseMaintenanceRepository {

    /**
     * ローカルデータベースの最適化処理（VACUUM実行および前後サイズチェック）を行います。
     *
     * トランザクションログファイル（WAL/SHM）のチェックポイントを作成・切り詰めてから、
     * 前後のファイル合計サイズ（データベース本体および付随する一時ファイル群）を取得・比較します。
     *
     * @return 最適化前後のデータベースファイルサイズを格納した[DatabaseVacuumResult]
     */
    override suspend fun vacuumDatabase(): DatabaseVacuumResult = withContext(Dispatchers.IO) {
        val sqliteDatabase = database.openHelper.writableDatabase
        sqliteDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        val beforeSizeBytes = databaseFilesSize()
        sqliteDatabase.execSQL("VACUUM")
        sqliteDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        val afterSizeBytes = databaseFilesSize()

        DatabaseVacuumResult(
            beforeSizeBytes = beforeSizeBytes,
            afterSizeBytes = afterSizeBytes
        )
    }

    private fun databaseFilesSize(): Long {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        return listOf(
            databaseFile,
            context.getDatabasePath("$DATABASE_NAME-wal"),
            context.getDatabasePath("$DATABASE_NAME-shm")
        ).sumOf { file -> if (file.exists()) file.length() else 0L }
    }

    private companion object {
        const val DATABASE_NAME = "dam_database"
    }
}
