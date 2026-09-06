// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.repository

import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionKey
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionState
import kotlinx.coroutines.flow.Flow

/**
 * アプリケーションの各種設定（テーマ、対象ダム、自動更新間隔、デバッグモード等）の永続化管理を行うリポジトリ。
 */
interface SettingsRepository {
    
    /**
     * アプリケーション設定の現在値（[AppSettings]）をリアルタイムで監視するための[Flow]。
     */
    val appSettingsFlow: Flow<AppSettings>

    /**
     * メイン画面のCard開閉状態を監視するFlow。
     */
    val mainCardExpansionStateFlow: Flow<MainCardExpansionState>
    
    /**
     * 現在の設定値を更新します。
     *
     * @param transform 現在の設定を受け取って新しい設定を返すサスペンド関数
     */
    suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings)

    /**
     * 指定したメイン画面Cardの展開状態を反転する。
     */
    suspend fun toggleMainCardExpansion(key: MainCardExpansionKey)
    
    /**
     * キャッシュされている設定のメモリキャッシュをクリアして再読み込みを促します。
     */
    suspend fun invalidateSettingsCache()
}
