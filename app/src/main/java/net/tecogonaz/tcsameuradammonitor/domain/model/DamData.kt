// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

import kotlinx.serialization.Serializable
/**
 * ダムの最新観測データ（流域平均雨量、貯水量、流入量、放流量、貯水率、変化トレンド等）を保持するドメインモデル。
 *
 * @property observationStationId 観測所ID
 * @property observationStationName 観測所名（例: "早明浦ダム"）
 * @property riverSystemName 水系名（例: "吉野川水系"）
 * @property riverName 河川名（例: "吉野川"）
 * @property updatedAt データ更新日時文字列（例: "2026/06/01 12:00"）
 * @property catchmentAverageRainfall 流域平均雨量 (mm/10min)
 * @property storageVolume 貯水量 (×10³m³)
 * @property storageVolumeTrend 貯水量の変化トレンド (UP/DOWN/FLAT/UNKNOWN)
 * @property inflow 流入量 (m³/s)
 * @property outflow 放流量 (m³/s)
 * @property storagePercentage 貯水率 (%)
 * @property storagePercentageTrend 貯水率の変化トレンド
 * @property storagePercentageTime 貯水率の前日・前週比比較対象時刻文字列
 * @property storagePercentageDayChange 貯水率の前日比差分 (%ポイント)
 * @property storagePercentageDayChangeTrend 貯水率の前日比増減トレンド
 * @property storagePercentageWeekChange 貯水率の前週比差分 (%ポイント)
 * @property storagePercentageWeekChangeTrend 貯水率の前週比増減トレンド
 * @property storageVolumeForMessage 欠測前の最新正常貯水量 (×10³m³)。貯水率メッセージ分類用
 * @property historicalData グラフ表示用の24時間などの過去時系列データリスト
 */
data class DamData(
    val observationStationId: String,
    val observationStationName: String, 
    val riverSystemName: String, 
    val riverName: String, 
    val updatedAt: String, 
    val catchmentAverageRainfall: Float?, 
    val storageVolume: Float?, 
    val storageVolumeTrend: Trend,
    val inflow: Float?, 
    val outflow: Float?, 
    val storagePercentage: Float?, 
    val storagePercentageTrend: Trend,
    val storagePercentageTime: String?, 
    val storagePercentageDayChange: Float?,
    val storagePercentageDayChangeTrend: Trend,
    val storagePercentageWeekChange: Float?,
    val storagePercentageWeekChangeTrend: Trend,
    val storageVolumeForMessage: Float? = null,
    val historicalData: List<DamHistoricalData> = emptyList()
)

/**
 * ダムの過去時系列（10分または1時間間隔）の履歴観測データモデル。
 *
 * @property time 観測日時文字列（例: "2026/06/01 12:00"）
 * @property catchmentAverageRainfall 流域平均雨量 (mm/10min または mm/h)
 * @property storagePercentage 貯水率 (%)
 * @property storageVolume 貯水量 (×10³m³)
 * @property inflow 流入量 (m³/s)
 * @property outflow 放流量 (m³/s)
 */
@Serializable
data class DamHistoricalData(
    val time: String, 
    val catchmentAverageRainfall: Float? = null, 
    val storagePercentage: Float?,
    val storageVolume: Float?,
    val inflow: Float?,
    val outflow: Float?
)

/**
 * ダムの諸量の推移トレンド（上昇、下降、横ばい、未知）を表す列挙型。
 */
enum class Trend {
    /** 上昇（推移上昇） */
    UP,
    /** 下降（推移下降） */
    DOWN,
    /** 横ばい */
    FLAT,
    /** 未知・取得失敗等 */
    UNKNOWN
}


/**
 * 取得したすべての観測データ（雨量、貯水量、流入、放流、貯水率）が同時にnull（欠測）になっているかどうかを判定します。
 * 全ての観測項目が欠測している期間の全観測データ配信停止中の判定に使用します。
 *
 * @return すべての観測データが欠損している場合はtrue
 */
fun DamData.isAllObservationDataInvalid(): Boolean {
    return storageVolume == null &&
        inflow == null &&
        outflow == null &&
        catchmentAverageRainfall == null &&
        storagePercentage == null
}
