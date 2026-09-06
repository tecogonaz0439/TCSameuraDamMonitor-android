// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.repository

import net.tecogonaz.tcsameuradammonitor.domain.model.DebugLogEntry
import kotlinx.coroutines.flow.Flow


/**
 * アプリケーションの動作ログをローカルに永続化し、デバッグ画面等で閲覧・管理するためのリポジトリ。
 */
interface DebugLogRepository {

    /**
     * 保存されているすべてのデバッグログをタイムスタンプ降順で監視するための[Flow]を取得します。
     *
     * @return デバッグログエントリのリストを監視する[Flow]
     */
    fun getAllEntriesFlow(): Flow<List<DebugLogEntry>>

    /**
     * 新しいデバッグログエントリを追加します。
     *
     * @param message ログメッセージの概要
     * @param details 詳細情報（スタックトレースやパラメータ等、デフォルト: 空文字列）
     */
    suspend fun addEntry(message: String, details: String = "")

    /**
     * 保存されているすべてのデバッグログを削除します。
     */
    suspend fun deleteAll()
}
