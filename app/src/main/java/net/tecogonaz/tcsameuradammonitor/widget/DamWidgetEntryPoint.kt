// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.widget

import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Jetpack Glance アプリウィジェット内で Hilt による依存性注入（DI）を行うためのエントリーポイント。
 *
 * ウィジェットは Android システムが管理するレシーバから生成されるため、通常の @Inject ではなく、
 * EntryPointAccessors を使用してリポジトリのインスタンスを取得します。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DamWidgetEntryPoint {
    /**
     * 最新のダム観測データを管理する[DamDataRepository]を取得します。
     */
    fun damDataRepository(): DamDataRepository

    /**
     * アプリ設定情報を管理する[SettingsRepository]を取得します。
     */
    fun settingsRepository(): SettingsRepository
}
