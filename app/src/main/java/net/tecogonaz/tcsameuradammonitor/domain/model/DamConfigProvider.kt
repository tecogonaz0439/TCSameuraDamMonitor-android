// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 与えられたダムIDに対応する[DamConfig]の設定情報を取得するためのプロバイダインターフェース。
 */
interface DamConfigProvider {
    /**
     * 指定されたダムIDの設定情報を取得します。
     *
     * @param targetDamId ダムID
     * @return 該当する[DamConfig]のインスタンス
     */
    fun get(targetDamId: String = AppSettings.DEFAULT_DAM_ID): DamConfig
}

/**
 * [DamConfigProvider]のデフォルトの実装クラス。
 */
@Singleton
class DefaultDamConfigProvider @Inject constructor() : DamConfigProvider {
    override fun get(targetDamId: String): DamConfig = getDamConfig(targetDamId)
}
