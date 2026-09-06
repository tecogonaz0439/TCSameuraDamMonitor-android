// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.repository

import net.tecogonaz.tcsameuradammonitor.domain.model.DatabaseVacuumResult


/**
 * Roomローカルデータベースのメンテナンス処理（VACUUM等による不要領域解放）を抽象化するリポジトリ。
 */
interface DatabaseMaintenanceRepository {

    /**
     * データベースに対してVACUUMコマンドを実行し、ファイルサイズを削減して最適化を行います。
     *
     * @return VACUUM処理結果を格納した[DatabaseVacuumResult]
     */
    suspend fun vacuumDatabase(): DatabaseVacuumResult
}
