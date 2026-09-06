// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import net.tecogonaz.tcsameuradammonitor.data.source.local.SettingsDataStore
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionKey
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionState
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SettingsRepository]の実装クラス。
 *
 * [SettingsDataStore]（Jetpack DataStore）をラップし、アプリの設定情報の永続化、
 * 取得のための監視フロー提供、更新処理、メモリキャッシュクリアを行います。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore
) : SettingsRepository {

    /**
     * アプリ設定の現在値（[AppSettings]）を監視するための[Flow]。
     */
    override val appSettingsFlow: Flow<AppSettings> = dataStore.appSettingsFlow
    override val mainCardExpansionStateFlow: Flow<MainCardExpansionState> =
        dataStore.mainCardExpansionStateFlow

    /**
     * 設定の変更処理を実行します。
     *
     * @param transform 設定を受け取り、更新された新しい設定を生成して返す関数
     */
    override suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings) {
        dataStore.updateSettings(transform)
    }

    override suspend fun toggleMainCardExpansion(key: MainCardExpansionKey) {
        dataStore.toggleMainCardExpansion(key)
    }

    /**
     * DataStoreキャッシュのリフレッシュ（再読込）を強制的に行います。
     */
    override suspend fun invalidateSettingsCache() {
        dataStore.triggerRefresh()
    }
}
