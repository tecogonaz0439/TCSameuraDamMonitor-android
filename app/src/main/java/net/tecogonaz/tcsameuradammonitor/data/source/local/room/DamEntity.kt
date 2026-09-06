// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 最新ダム観測データをローカルキャッシュとして格納するためのデータベースエンティティクラス。
 *
 * 国土交通省からリアルタイム更新された早明浦ダム等の最新諸量（流域平均雨量、貯水量、流入量、放流量、貯水率、および前日比・前週比）と、
 * 表示用トレンド情報、過去24時間等の履歴グラフ用の時系列データ（JSON表現）を保持します。
 *
 * @property observationStationId 観測所ID
 * @property observationStationName 観測所名（例: "早明浦ダム"）
 * @property riverSystemName 水系名（例: "吉野川水系"）
 * @property riverName 河川名（例: "地蔵寺川"、"吉野川"等）
 * @property updatedAt 観測日時文字列（例: "2026/06/01 12:00"）
 * @property catchmentAverageRainfall 流域平均雨量 (mm/10min)
 * @property storageVolume 貯水量 (×10³m³)
 * @property storageVolumeTrend 貯水量の変化トレンド (UP/DOWN/FLAT)
 * @property inflow 流入量 (m³/s)
 * @property outflow 放流量 (m³/s)
 * @property storagePercentage 貯水率 (%)
 * @property storagePercentageTrend 貯水率の変化トレンド
 * @property storagePercentageTime 貯水率の前日・前週比比較対象時刻文字列
 * @property storagePercentageDayChange 貯水率の前日比差分 (%ポイント)
 * @property storagePercentageDayChangeTrend 貯水率の前日比増減トレンド
 * @property storagePercentageWeekChange 貯水率の前週比差分 (%ポイント)
 * @property storagePercentageWeekChangeTrend 貯水率の前週比増減トレンド
 * @property storageVolumeForMessage 貯水率メッセージ分類用の欠測前の最新正常貯水量 (×10³m³)
 * @property historicalDataJson グラフ描画用の24時間などの過去時系列データを保持するJSON形式の文字列
 * @property lastFetchTimeMillis データをサーバーまたはデバッグから取得したローカル時刻のミリ秒タイムスタンプ
 */
@Entity(tableName = "dam_data")
@Serializable
data class DamEntity(
    @PrimaryKey
    val observationStationId: String,
    val observationStationName: String,
    val riverSystemName: String,
    val riverName: String,
    val updatedAt: String,
    val catchmentAverageRainfall: Float?,
    val storageVolume: Float?,
    val storageVolumeTrend: String,
    val inflow: Float?,
    val outflow: Float?,
    val storagePercentage: Float?,
    val storagePercentageTrend: String,
    val storagePercentageTime: String?,
    val storagePercentageDayChange: Float?,
    val storagePercentageDayChangeTrend: String,
    val storagePercentageWeekChange: Float?,
    val storagePercentageWeekChangeTrend: String,
    @ColumnInfo(name = "storage_volume_for_message")
    val storageVolumeForMessage: Float?,
    val historicalDataJson: String,
    val lastFetchTimeMillis: Long
)

/**
 * ドメイン層のダムデータモデル[DamData]をデータベース保存用のエンティティモデル[DamEntity]に変換します。
 *
 * @param fetchTimeMillis 取得時のミリ秒タイムスタンプ
 * @return 変換された[DamEntity]インスタンス
 */
fun DamData.toEntity(fetchTimeMillis: Long): DamEntity {
    return DamEntity(
        observationStationId = observationStationId,
        observationStationName = observationStationName,
        riverSystemName = riverSystemName,
        riverName = riverName,
        updatedAt = updatedAt,
        catchmentAverageRainfall = catchmentAverageRainfall,
        storageVolume = storageVolume,
        storageVolumeTrend = storageVolumeTrend.name,
        inflow = inflow,
        outflow = outflow,
        storagePercentage = storagePercentage,
        storagePercentageTrend = storagePercentageTrend.name,
        storagePercentageTime = storagePercentageTime,
        storagePercentageDayChange = storagePercentageDayChange,
        storagePercentageDayChangeTrend = storagePercentageDayChangeTrend.name,
        storagePercentageWeekChange = storagePercentageWeekChange,
        storagePercentageWeekChangeTrend = storagePercentageWeekChangeTrend.name,
        storageVolumeForMessage = storageVolumeForMessage,
        historicalDataJson = Json.encodeToString(historicalData),
        lastFetchTimeMillis = fetchTimeMillis
    )
}

/**
 * データベースエンティティモデル[DamEntity]をドメイン層のダムデータモデル[DamData]へマッピング変換します。
 *
 * @return 変換された[DamData]インスタンス
 */
fun DamEntity.toDomain(): DamData {
    return DamData(
        observationStationId = observationStationId,
        observationStationName = observationStationName,
        riverSystemName = riverSystemName,
        riverName = riverName,
        updatedAt = updatedAt,
        catchmentAverageRainfall = catchmentAverageRainfall,
        storageVolume = storageVolume,
        storageVolumeTrend = Trend.valueOf(storageVolumeTrend),
        inflow = inflow,
        outflow = outflow,
        storagePercentage = storagePercentage,
        storagePercentageTrend = Trend.valueOf(storagePercentageTrend),
        storagePercentageTime = storagePercentageTime,
        storagePercentageDayChange = storagePercentageDayChange,
        storagePercentageDayChangeTrend = Trend.valueOf(storagePercentageDayChangeTrend),
        storagePercentageWeekChange = storagePercentageWeekChange,
        storagePercentageWeekChangeTrend = Trend.valueOf(storagePercentageWeekChangeTrend),
        storageVolumeForMessage = storageVolumeForMessage,
        historicalData = try {
            Json.decodeFromString<List<DamHistoricalData>>(historicalDataJson)
        } catch (e: Exception) {
            android.util.Log.w("DamEntity", "Failed to decode historicalDataJson", e)
            emptyList()
        }
    )
}
