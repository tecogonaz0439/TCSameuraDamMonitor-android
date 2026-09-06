// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model


/**
 * ドメイン層の過去データ検索履歴メタ情報を保持するデータモデル。
 *
 * @property id 検索履歴のメタID
 * @property observationStationId ダムの観測所ID
 * @property observationStationName ダム観測所名（例: "早明浦ダム"）
 * @property riverSystemName 水系名
 * @property riverName 河川名
 * @property damConfigId 対象ダムのID（例: "sameura"）
 * @property searchBgnDate 検索開始日 (フォーマット: YYYYMMDD)
 * @property searchEndDate 検索終了日 (フォーマット: YYYYMMDD)
 * @property fetchedAt この過去データを取得・保存したローカルミリ秒タイムスタンプ
 * @property dataStartTimeStr 取得データ中の最古の観測日時文字列
 * @property dataEndTimeStr 取得データ中の最新の観測日時文字列
 * @property dataStartStoragePct 検索期間開始時点のダム貯水率 (%)
 * @property dataEndStoragePct 検索期間終了時点のダム貯水率 (%)
 * @property dataMinStoragePct 検索期間中のダム最低貯水率 (%)
 * @property dataMaxStoragePct 検索期間中のダム最高貯水率 (%)
 * @property isPinned ピン留めされているかどうかのフラグ
 */
data class HistoricalSearchMeta(
    val id: Long,
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
