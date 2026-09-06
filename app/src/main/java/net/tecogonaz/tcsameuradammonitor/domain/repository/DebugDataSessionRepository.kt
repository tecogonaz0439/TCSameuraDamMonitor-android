// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.repository

/** Debugデータセッションの起動時回復結果です。 */
enum class DebugDataSessionRecovery {
    /** 完全な待避を保持したままDebugセッションを継続しました。 */
    DEBUG_SESSION_CONTINUED,
    /** 通常モードで残っていた待避を復元し、後始末しました。 */
    NORMAL_DATA_RESTORED,
    /** 回復対象はありませんでした。 */
    NOTHING_TO_DO
}

/**
 * Debug ON直前のリアルタイム・日次データを待避し、OFF時に復元します。
 */
interface DebugDataSessionRepository {
    /** 現在データを完全に待避してからDebug設定を有効化します。 */
    suspend fun enterDebugMode(): Result<Unit>

    /** 待避データを復元してからDebug設定を無効化します。 */
    suspend fun exitDebugMode(): Result<Unit>

    /** プロセス停止中に残った一時データやDebugセッションを安全な状態へ回復します。 */
    suspend fun recoverSessionOnStartup(): Result<DebugDataSessionRecovery>
}
