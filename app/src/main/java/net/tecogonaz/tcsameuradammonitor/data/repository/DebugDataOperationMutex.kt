// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * データ更新とデバッグセッションの待避・復元を直列化するアプリ共通ロックです。
 *
 * リアルタイム・日次Repositoryは保存処理を、[DebugDataSessionRepositoryImpl]は
 * snapshot/restore全体をこのロックで囲み、更新途中の状態を待避しないようにします。
 */
@Singleton
class DebugDataOperationMutex @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
