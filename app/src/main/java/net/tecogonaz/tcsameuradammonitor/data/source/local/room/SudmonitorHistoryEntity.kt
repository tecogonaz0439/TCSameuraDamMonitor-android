// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
import kotlinx.serialization.Serializable


/**
 * sudmonitor の日次過去データ（`/v1/history/{damId}/latest.dat`、直近31暦日）の保存行を表すエンティティ。
 *
 * ダムごとに1件のみ保持し（[damId] を主キー）、ダム変更時は既存行を上書きする。
 * 既存の過去データ検索結果テーブル（`historical_search_meta` / `historical_dam_data`）とは一切結合しない。
 *
 * @property damId 対象ダムの観測所ID（主キー。ダムごと1件）
 * @property periodStartEpochMs 実カバレッジの開始日（JST 00:00）のミリ秒。`X-TCS-History-Since` 由来。ヘッダ欠落・非パース時は null（カバレッジ不明）
 * @property periodEndEpochMs 実カバレッジの終了日（JST 00:00）のミリ秒。`X-TCS-History-Until` 由来。until が 00:00（24:00表記）の場合は前日まで
 * @property status 取得状態（現状は取得成功のみ保存されるため常に [SudmonitorHistory.STATUS_SUCCESS]）
 * @property rowCount 保存した観測行数
 * @property firstStorageRatePct 期間先頭の貯水率（%）。欠測は null
 * @property lastStorageRatePct 期間末尾の貯水率（%）。欠測は null
 * @property minStorageRatePct 期間内の最小貯水率（%）。欠測は null
 * @property maxStorageRatePct 期間内の最大貯水率（%）。欠測は null
 * @property fetchedAtEpochMs このデータを取得・保存したローカルミリ秒タイムスタンプ
 * @property nextUpdateAtEpochMs 次回更新予定時刻（`X-TCS-Next-Update-At` 由来。欠落時は取得日の翌日 00:13 JST のフォールバック値）
 * @property rawDatPath 生 .dat バイト列の best-effort 保存先パス（cacheDir）。保存失敗時は null
 * @property updatedAtEpochMs この行を最後に更新したローカルミリ秒タイムスタンプ
 */
@Entity(tableName = "sudmonitor_history")
@Serializable
data class SudmonitorHistoryEntity(
    @PrimaryKey
    val damId: String,

    val periodStartEpochMs: Long?,

    val periodEndEpochMs: Long?,

    val status: String,

    val rowCount: Int,

    val firstStorageRatePct: Float?,

    val lastStorageRatePct: Float?,

    val minStorageRatePct: Float?,

    val maxStorageRatePct: Float?,

    val fetchedAtEpochMs: Long,

    val nextUpdateAtEpochMs: Long,

    val rawDatPath: String?,

    val updatedAtEpochMs: Long
)
