// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.testutil

import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend

/**
 * テスト用の擬似ダムデータ（DamData）を生成するヘルパー関数。
 * 引数で更新日時や貯水率を任意に指定でき、その他の項目にはデフォルト値が埋められます。
 *
 * @param updatedAt 更新日時。デフォルトは "2026/05/18 05:15"
 * @param storagePercentage 貯水率。デフォルトは 80.0f
 * @return テスト用データが設定された [DamData] インスタンス
 */
fun damData(
    updatedAt: String = "2026/05/18 05:15",
    storagePercentage: Float? = 80.0f
): DamData = DamData(
    observationStationId = "1368080700010",
    observationStationName = "早明浦ダム",
    riverSystemName = "吉野川",
    riverName = "吉野川",
    updatedAt = updatedAt,
    catchmentAverageRainfall = 0.0f,
    storageVolume = 100000f,
    storageVolumeTrend = Trend.FLAT,
    inflow = 10.0f,
    outflow = 9.0f,
    storagePercentage = storagePercentage,
    storagePercentageTrend = Trend.FLAT,
    storagePercentageTime = updatedAt,
    storagePercentageDayChange = 0.0f,
    storagePercentageDayChangeTrend = Trend.FLAT,
    storagePercentageWeekChange = 0.0f,
    storagePercentageWeekChangeTrend = Trend.FLAT,
    historicalData = listOf(
        DamHistoricalData(
            time = updatedAt,
            catchmentAverageRainfall = 0.0f,
            storagePercentage = storagePercentage,
            storageVolume = 100000f,
            inflow = 10.0f,
            outflow = 9.0f
        )
    )
)
