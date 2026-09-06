// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.worker

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jetpack WorkManager によるバックグラウンド自動更新および単発（手動）データ同期処理のゲートウェイインターフェース。
 */
interface DamWorkManagerGateway {
    /**
     * 自動定期更新ジョブの動作ステータス情報（[WorkInfo]）のフローを監視します。
     *
     * @return 定期更新ジョブ状態リストの監視フロー
     */
    fun autoWorkInfosFlow(): Flow<List<WorkInfo>>

    /**
     * 単発（即時手動更新など）ジョブの動作ステータス情報のフローを監視します。
     *
     * @return 単発更新ジョブ状態リストの監視フロー
     */
    fun oneTimeWorkInfosFlow(): Flow<List<WorkInfo>>

    /**
     * 端末起動時ブート更新ジョブの動作ステータス情報のフローを監視します。
     *
     * @return ブート更新ジョブ状態リストの監視フロー
     */
    fun bootWorkInfosFlow(): Flow<List<WorkInfo>>

    /**
     * sudmonitor 日次過去データ取得ジョブの動作ステータス情報（[WorkInfo]）のフローを監視します。
     *
     * @return 日次過去データ取得ジョブ状態リストの監視フロー
     */
    fun sudmonitorHistoryWorkInfosFlow(): Flow<List<WorkInfo>>

    /**
     * 即時実行の単発データ同期ジョブをキューに登録（エンキュー）します。
     *
     * @param workType 実行する処理種別の識別ラベル
     */
    fun enqueueOneTimeWork(workType: String)

    /**
     * 既存の単発データ同期を置き換えて、指定した処理をキューに登録します。
     *
     * @param workType 実行する処理種別の識別ラベル
     */
    fun replaceOneTimeWork(workType: String)

    /**
     * sudmonitor 日次過去データの単発取得ジョブをキューに登録（エンキュー）します。
     *
     * 実行待ち・実行中のジョブが既にある場合はそのまま維持します（[androidx.work.ExistingWorkPolicy.KEEP]）。
     *
     * @param trigger 取得トリガー（初回起動 / ダム変更 / 手動 / 自動）
     */
    fun enqueueSudmonitorHistoryWork(trigger: SudmonitorHistoryTrigger)

    /**
     * 既存の sudmonitor 日次過去データ取得ジョブを置き換えて、指定したトリガーの取得ジョブを登録します。
     *
     * ダム変更時などに使用します（[androidx.work.ExistingWorkPolicy.REPLACE]。D3: 常に再取得・上書き）。
     *
     * @param trigger 取得トリガー
     */
    fun replaceSudmonitorHistoryWork(trigger: SudmonitorHistoryTrigger)

    /**
     * 指定されたアプリ設定および次回実行日時に基づいて、次回自動更新ジョブ（バックグラウンド）をスケジュールします。
     *
     * @param settings アプリ設定（[AppSettings]）
     * @param nextRunMillis 実行時刻（ミリ秒タイムスタンプ）
     */
    fun scheduleWorkAtTime(settings: AppSettings, nextRunMillis: Long)

    /**
     * スケジュールされている自動更新ジョブをキャンセルします。
     */
    fun cancelWork()
}

/**
 * [DamWorkManagerGateway]の実装クラス。
 *
 * [DamWorkScheduler]の静的スケジューラ処理を仲介し、WorkManagerシステムへ登録・キャンセル処理を行います。
 */
@Singleton
class DefaultDamWorkManagerGateway @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : DamWorkManagerGateway {
    override fun autoWorkInfosFlow(): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(DamWorkScheduler.WORK_NAME)

    override fun oneTimeWorkInfosFlow(): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(DamWorkScheduler.ONE_TIME_WORK_NAME)

    override fun bootWorkInfosFlow(): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(DamWorkScheduler.BOOT_ONE_TIME_WORK_NAME)

    override fun sudmonitorHistoryWorkInfosFlow(): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(DamWorkScheduler.SUDMONITOR_HISTORY_WORK_NAME)

    override fun enqueueOneTimeWork(workType: String) {
        DamWorkScheduler.enqueueOneTimeWork(context, workType)
    }

    override fun replaceOneTimeWork(workType: String) {
        DamWorkScheduler.replaceOneTimeWork(context, workType)
    }

    override fun enqueueSudmonitorHistoryWork(trigger: SudmonitorHistoryTrigger) {
        DamWorkScheduler.enqueueSudmonitorHistoryWork(context, trigger)
    }

    override fun replaceSudmonitorHistoryWork(trigger: SudmonitorHistoryTrigger) {
        DamWorkScheduler.replaceSudmonitorHistoryWork(context, trigger)
    }

    override fun scheduleWorkAtTime(settings: AppSettings, nextRunMillis: Long) {
        DamWorkScheduler.scheduleWorkAtTime(context, settings, nextRunMillis)
    }

    override fun cancelWork() {
        DamWorkScheduler.cancelWork(context)
    }
}
