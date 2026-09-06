// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.testutil

import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend

/**
 * Instrumentation テスト用の擬似設定リポジトリ (Hilt Fake)。
 */
class HiltFakeSettingsRepository(initial: AppSettings) : SharedFakeSettingsRepository(initial)

/**
 * Instrumentation テスト用の擬似ダムデータリポジトリ (Hilt Fake)。
 */
class HiltFakeDamDataRepository(initialData: DamData? = null) : SharedFakeDamDataRepository(initialData)

/**
 * Instrumentation テスト用の擬似デバッグログリポジトリ (Hilt Fake)。
 */
class HiltFakeDebugLogRepository : SharedFakeDebugLogRepository()

/** Instrumentationテスト用の擬似Debug data sessionリポジトリです。 */
class HiltFakeDebugDataSessionRepository : SharedFakeDebugDataSessionRepository()

/**
 * Instrumentation テスト用の擬似過去データ検索リポジトリ (Hilt Fake)。
 */
class HiltFakeHistoricalSearchRepository : SharedFakeHistoricalSearchRepository()

/**
 * Instrumentation テスト用の擬似過去比較グラフリポジトリ (Hilt Fake)。
 */
class HiltFakeHistoricalComparisonRepository : SharedFakeHistoricalComparisonRepository()

/**
 * Instrumentation テスト用の擬似データベースメンテナンスリポジトリ (Hilt Fake)。
 */
class HiltFakeDatabaseMaintenanceRepository : SharedFakeDatabaseMaintenanceRepository() {
    init {
        result = net.tecogonaz.tcsameuradammonitor.domain.model.DatabaseVacuumResult(
            beforeSizeBytes = 0L,
            afterSizeBytes = 0L
        )
    }
}

/**
 * Instrumentation テスト用の擬似 sudmonitor 日次過去データリポジトリ (Hilt Fake)。
 */
class HiltFakeSudmonitorHistoryRepository : SharedFakeSudmonitorHistoryRepository()

/**
 * Instrumentation テスト用の擬似WorkManagerゲートウェイ (Hilt Fake)。
 */
class HiltFakeDamWorkManagerGateway : SharedFakeDamWorkManagerGateway()

/**
 * Instrumentation テスト用の擬似ネットワーク接続確認オブジェクト (Hilt Fake)。
 */
class HiltFakeNetworkAvailability(available: Boolean = true) : FakeNetworkAvailability(available)

/**
 * Instrumentation テスト用の擬似ダムデータ（DamData）を生成するヘルパー関数。
 *
 * @param storagePercentage 貯水率。デフォルトは 80.0f
 * @param historicalData 過去データ。デフォルトは [androidTestHistoricalData] で生成されたもの
 * @return テスト用データが設定された [DamData] インスタンス
 */
fun androidTestDamData(
    storagePercentage: Float? = 80.0f,
    historicalData: List<DamHistoricalData> = androidTestHistoricalData(storagePercentage)
): DamData =
    DamData(
        observationStationId = "1368080700010",
        observationStationName = "早明浦ダム",
        riverSystemName = "吉野川",
        riverName = "吉野川",
        updatedAt = "2026/05/18 05:15",
        catchmentAverageRainfall = 0.0f,
        storageVolume = 100000f,
        storageVolumeTrend = Trend.FLAT,
        inflow = 10.0f,
        outflow = 9.0f,
        storagePercentage = storagePercentage,
        storagePercentageTrend = Trend.FLAT,
        storagePercentageTime = "2026/05/18 05:15",
        storagePercentageDayChange = 0.0f,
        storagePercentageDayChangeTrend = Trend.FLAT,
        storagePercentageWeekChange = 0.0f,
        storagePercentageWeekChangeTrend = Trend.FLAT,
        historicalData = historicalData
    )

/**
 * Instrumentation テスト用の擬似履歴データ（DamHistoricalData）リストを生成するヘルパー関数。
 *
 * @param storagePercentage 基準となる貯水率。デフォルトは 80.0f
 * @return テスト用の履歴データが設定されたリスト
 */
fun androidTestHistoricalData(storagePercentage: Float? = 80.0f): List<DamHistoricalData> =
    listOf(
        DamHistoricalData(
            time = "2026/05/18 04:15",
            catchmentAverageRainfall = 0.0f,
            storageVolume = 99980f,
            inflow = 9.5f,
            outflow = 9.0f,
            storagePercentage = storagePercentage?.minus(0.1f)
        ),
        DamHistoricalData(
            time = "2026/05/18 05:15",
            catchmentAverageRainfall = 0.1f,
            storageVolume = 100000f,
            inflow = 10.0f,
            outflow = 9.0f,
            storagePercentage = storagePercentage
        )
    )
