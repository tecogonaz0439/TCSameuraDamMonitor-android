// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta


@Entity(
    tableName = "historical_search_meta",
    indices = [
        Index(
            value = ["damConfigId", "searchBgnDate", "searchEndDate"],
            unique = true
        )
    ]
)
/**
 * 過去データ検索履歴メタ情報をデータベースに保存するためのエンティティクラス。
 *
 * @property id 自動生成される主キー
 * @property sortOrder 表示順をソートするためのインデックス順序（降順・昇順制御用）
 * @property observationStationId ダム観測所ID
 * @property observationStationName ダム観測所名（例: "早明浦ダム"）
 * @property riverSystemName 水系名（例: "吉野川水系"）
 * @property riverName 河川名（例: "吉野川"）
 * @property damConfigId 対象ダムのID（例: "sameura"）
 * @property searchBgnDate 過去データ検索の開始日 (フォーマット: YYYYMMDD)
 * @property searchEndDate 過去データ検索の終了日 (フォーマット: YYYYMMDD)
 * @property fetchedAt この過去データを取得・保存したローカルミリ秒タイムスタンプ
 * @property dataStartTimeStr 取得できた実際の観測データに含まれる最古の観測時間文字列（例: "2026/06/01 01:00"）
 * @property dataEndTimeStr 取得できた実際の観測データに含まれる最新の観測時間文字列（例: "2026/06/02 24:00"）
 * @property dataStartStoragePct 取得した期間の開始時点でのダム貯水率 (%)
 * @property dataEndStoragePct 取得した期間の終了時点でのダム貯水率 (%)
 * @property dataMinStoragePct 取得した期間内でのダム最低貯水率 (%)
 * @property dataMaxStoragePct 取得した期間内でのダム最高貯水率 (%)
 * @property isPinned ピン留めされているかどうかのフラグ
 */
data class HistoricalSearchMetaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val sortOrder: Int = 0,
    
    val observationStationId: String,
    
    val observationStationName: String,
    
    val riverSystemName: String,
    
    val riverName: String,
    
    val damConfigId: String,
    
    val searchBgnDate: String,
    
    val searchEndDate: String,
    
    val fetchedAt: Long,
    
    val dataStartTimeStr: String?,
    
    val dataEndTimeStr: String?,
    
    val dataStartStoragePct: Float?,
    
    val dataEndStoragePct: Float?,
    
    val dataMinStoragePct: Float?,
    
    val dataMaxStoragePct: Float?,
    
    val isPinned: Boolean = false
)


/**
 * データベースエンティティモデル[HistoricalSearchMetaEntity]をドメインモデル[HistoricalSearchMeta]にマッピング変換します。
 *
 * @return 変換された[HistoricalSearchMeta]インスタンス
 */
fun HistoricalSearchMetaEntity.toDomain(): HistoricalSearchMeta = HistoricalSearchMeta(
    id = id,
    observationStationId = observationStationId,
    observationStationName = observationStationName,
    riverSystemName = riverSystemName,
    riverName = riverName,
    damConfigId = damConfigId,
    searchBgnDate = searchBgnDate,
    searchEndDate = searchEndDate,
    fetchedAt = fetchedAt,
    dataStartTimeStr = dataStartTimeStr,
    dataEndTimeStr = dataEndTimeStr,
    dataStartStoragePct = dataStartStoragePct,
    dataEndStoragePct = dataEndStoragePct,
    dataMinStoragePct = dataMinStoragePct,
    dataMaxStoragePct = dataMaxStoragePct,
    isPinned = isPinned
)
