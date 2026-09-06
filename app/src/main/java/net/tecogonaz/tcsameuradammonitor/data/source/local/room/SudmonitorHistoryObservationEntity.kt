// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import kotlinx.serialization.Serializable


/**
 * sudmonitor の日次過去データ（直近31暦日）の観測行を表すエンティティ。
 *
 * [damId] は [SudmonitorHistoryEntity] への外部キー。ダム単位の削除は CASCADE に頼らず
 * [SudmonitorHistoryDao] 側で明示的に実施する（置換保存時に既存観測行を一括削除する）。
 *
 * @property damId 対象ダムの観測所ID（[SudmonitorHistoryEntity] への外部キー）
 * @property rowNo 同一ダム内の行番号（0始まり）。[damId] と合わせて主キーを構成する
 * @property timeText 観測日時の生文字列（例: "2026/08/01 24:00"。24:00表記は生のまま保持）
 * @property timeEpochMs 観測日時のミリ秒タイムスタンプ（24:00 は翌日 00:00 に正規化済み）
 * @property rainfallHourlyMm 流域平均雨量 (mm/h)。欠測は null
 * @property storageVolume1000m3 貯水量 (×10³m³)。欠測は null
 * @property inflowM3s 流入量 (m³/s)。欠測は null
 * @property outflowM3s 放流量 (m³/s)。欠測は null
 * @property storageRatePct 貯水率 (%)。欠測は null
 */
@Entity(
    tableName = "sudmonitor_history_observation",
    foreignKeys = [
        ForeignKey(
            entity = SudmonitorHistoryEntity::class,
            parentColumns = ["damId"],
            childColumns = ["damId"]
        )
    ],
    indices = [Index(value = ["damId", "timeEpochMs"])],
    primaryKeys = ["damId", "rowNo"]
)
@Serializable
data class SudmonitorHistoryObservationEntity(
    val damId: String,

    val rowNo: Int,

    val timeText: String,

    val timeEpochMs: Long,

    val rainfallHourlyMm: Float?,

    val storageVolume1000m3: Float?,

    val inflowM3s: Float?,

    val outflowM3s: Float?,

    val storageRatePct: Float?
)


/**
 * データベースエンティティモデル[SudmonitorHistoryObservationEntity]をドメインモデル[DamHistoricalData]へマッピング変換します。
 *
 * 既存の過去データ表示 Card 群（観測データ / 観測データ(一覧) / 観測データ(グラフ)）の data 源として使うため、
 * 観測時刻には生文字列 [SudmonitorHistoryObservationEntity.timeText] をそのまま使用します。
 *
 * @return 変換された[DamHistoricalData]インスタンス
 */
fun SudmonitorHistoryObservationEntity.toDomain(): DamHistoricalData = DamHistoricalData(
    time = timeText,
    catchmentAverageRainfall = rainfallHourlyMm,
    storagePercentage = storageRatePct,
    storageVolume = storageVolume1000m3,
    inflow = inflowM3s,
    outflow = outflowM3s
)
