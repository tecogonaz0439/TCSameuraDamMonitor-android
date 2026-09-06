// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryEntity


/**
 * sudmonitor の日次過去データ（`/v1/history/{damId}/latest.dat`、直近31暦日）の保存行を表すドメインモデル。
 *
 * ダムごとに1件のみ保持し（D3: ダム変更時は上書き）、既存の過去データ検索結果（[HistoricalSearchMeta]）とは
 * 独立に管理する（要件6: 管理画面・検索一覧からの分離）。
 *
 * @property damId 対象ダムの観測所ID
 * @property periodStartEpochMs 実カバレッジの開始日（JST 00:00）のミリ秒。ヘッダ欠落・非パース時は null（カバレッジ不明）
 * @property periodEndEpochMs 実カバレッジの終了日（JST 00:00）のミリ秒。until が 00:00（24:00表記）の場合は前日まで
 * @property status 取得状態（現状は取得成功のみ保存されるため常に [STATUS_SUCCESS]）
 * @property rowCount 保存した観測行数
 * @property firstStorageRatePct 期間先頭の貯水率（%）。欠測は null
 * @property lastStorageRatePct 期間末尾の貯水率（%）。欠測は null
 * @property minStorageRatePct 期間内の最小貯水率（%）。欠測は null
 * @property maxStorageRatePct 期間内の最大貯水率（%）。欠測は null
 * @property fetchedAtEpochMs このデータを取得・保存したローカルミリ秒タイムスタンプ
 * @property nextUpdateAtEpochMs 次回更新予定時刻（`X-TCS-Next-Update-At` 由来。欠落時は取得日の翌日 00:13 JST のフォールバック値、D5）
 * @property rawDatPath 生 .dat バイト列の best-effort 保存先パス（cacheDir）。保存失敗時は null
 * @property updatedAtEpochMs この行を最後に更新したローカルミリ秒タイムスタンプ
 */
data class SudmonitorHistory(
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
) {
    /**
     * このドメインモデルをRoomエンティティ[SudmonitorHistoryEntity]へ変換します。
     *
     * @return 変換された[SudmonitorHistoryEntity]インスタンス
     */
    fun toEntity(): SudmonitorHistoryEntity = SudmonitorHistoryEntity(
        damId = damId,
        periodStartEpochMs = periodStartEpochMs,
        periodEndEpochMs = periodEndEpochMs,
        status = status,
        rowCount = rowCount,
        firstStorageRatePct = firstStorageRatePct,
        lastStorageRatePct = lastStorageRatePct,
        minStorageRatePct = minStorageRatePct,
        maxStorageRatePct = maxStorageRatePct,
        fetchedAtEpochMs = fetchedAtEpochMs,
        nextUpdateAtEpochMs = nextUpdateAtEpochMs,
        rawDatPath = rawDatPath,
        updatedAtEpochMs = updatedAtEpochMs
    )

    companion object {
        /** 保存行の取得成功状態。現状は取得成功時のみ行が保存されるため、この値のみが使用される。 */
        const val STATUS_SUCCESS = "SUCCESS"

        /**
         * Roomエンティティ[SudmonitorHistoryEntity]をドメインモデル[SudmonitorHistory]へ変換します。
         *
         * @param entity 変換対象のエンティティ
         * @return 変換された[SudmonitorHistory]インスタンス
         */
        fun fromEntity(entity: SudmonitorHistoryEntity): SudmonitorHistory = SudmonitorHistory(
            damId = entity.damId,
            periodStartEpochMs = entity.periodStartEpochMs,
            periodEndEpochMs = entity.periodEndEpochMs,
            status = entity.status,
            rowCount = entity.rowCount,
            firstStorageRatePct = entity.firstStorageRatePct,
            lastStorageRatePct = entity.lastStorageRatePct,
            minStorageRatePct = entity.minStorageRatePct,
            maxStorageRatePct = entity.maxStorageRatePct,
            fetchedAtEpochMs = entity.fetchedAtEpochMs,
            nextUpdateAtEpochMs = entity.nextUpdateAtEpochMs,
            rawDatPath = entity.rawDatPath,
            updatedAtEpochMs = entity.updatedAtEpochMs
        )
    }
}
