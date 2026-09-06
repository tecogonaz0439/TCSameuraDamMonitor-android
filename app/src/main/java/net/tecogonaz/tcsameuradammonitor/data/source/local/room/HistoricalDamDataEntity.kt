// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData


@Entity(
    tableName = "historical_dam_data",
    foreignKeys = [
        ForeignKey(
            entity = HistoricalSearchMetaEntity::class,
            parentColumns = ["id"],
            childColumns = ["searchMetaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["searchMetaId"])]
)
/**
 * 過去ダム観測履歴明細データレコードをデータベースに保存するためのエンティティクラス。
 *
 * @property id 自動生成される主キー
 * @property searchMetaId 親の検索履歴メタデータレコード（[HistoricalSearchMetaEntity]）への外部キーID
 * @property timeStr 観測された日時文字列（例: "2026/06/01 12:00"）
 * @property timeMillis 観測日時のミリ秒タイムスタンプ
 * @property catchmentAverageRainfall 流域平均雨量 (mm/h)
 * @property storageVolume 貯水量 (×10³m³)
 * @property inflow 流入量 (m³/s)
 * @property outflow 放流量 (m³/s)
 * @property storagePercentage 貯水率 (%)
 */
data class HistoricalDamDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val searchMetaId: Long,
    
    val timeStr: String,
    
    val timeMillis: Long,
    
    val catchmentAverageRainfall: Float?,
    
    val storageVolume: Float?,
    
    val inflow: Float?,
    
    val outflow: Float?,
    
    val storagePercentage: Float?
)


/**
 * データベースエンティティモデル[HistoricalDamDataEntity]をドメインモデル[DamHistoricalData]へマッピング変換します。
 *
 * @return 変換された[DamHistoricalData]インスタンス
 */
fun HistoricalDamDataEntity.toDomain(): DamHistoricalData = DamHistoricalData(
    time = timeStr,
    catchmentAverageRainfall = catchmentAverageRainfall,
    storagePercentage = storagePercentage,
    storageVolume = storageVolume,
    inflow = inflow,
    outflow = outflow
)


/**
 * ドメイン層の過去ダムデータモデル[DamHistoricalData]をデータベース明細保存用エンティティモデル[HistoricalDamDataEntity]に変換します。
 *
 * @param timeMillis パース済みのミリ秒タイムスタンプ
 * @param searchMetaId 親となる検索メタデータレコードのID
 * @return 変換された[HistoricalDamDataEntity]インスタンス
 */
fun DamHistoricalData.toHistoricalEntity(timeMillis: Long, searchMetaId: Long): HistoricalDamDataEntity = HistoricalDamDataEntity(
    searchMetaId = searchMetaId,
    timeStr = time,
    timeMillis = timeMillis,
    catchmentAverageRainfall = catchmentAverageRainfall,
    storageVolume = storageVolume,
    inflow = inflow,
    outflow = outflow,
    storagePercentage = storagePercentage
)
