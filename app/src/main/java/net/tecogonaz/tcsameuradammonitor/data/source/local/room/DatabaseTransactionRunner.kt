// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Roomデータベースにおけるトランザクション処理の実行を抽象化するインターフェース。
 *
 * 複数のDAO操作をアトミックに実行するために使用されます。
 */
interface DatabaseTransactionRunner {
    /**
     * トランザクション内でサスペンド処理を実行します。
     *
     * @param block 実行する任意のサスペンド処理ブロック
     * @return 処理ブロックの戻り値
     */
    suspend fun <T> withTransaction(block: suspend () -> T): T
}

/**
 * Roomの実態データベースを使用して[DatabaseTransactionRunner]を実装するクラス。
 */
@Singleton
class RoomDatabaseTransactionRunner @Inject constructor(
    private val database: DamDatabase
) : DatabaseTransactionRunner {
    override suspend fun <T> withTransaction(block: suspend () -> T): T =
        database.withTransaction(block)
}
