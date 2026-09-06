// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import java.util.concurrent.TimeUnit


/**
 * Jetpack WorkManagerジョブのリクエスト生成およびキューイング制御（スケジュール・キャンセル）を処理するヘルパオブジェクト。
 */
object DamWorkScheduler {
    
    /** 定期的なデータ自動更新ジョブのユニークワーク名 */
    const val WORK_NAME = "DamAutoUpdateWork"
    
    /** 即時実行される手動更新用のユニークワーク名 */
    const val ONE_TIME_WORK_NAME = "DamManualWork"
    
    /** 端末再起動（ブート）時に即時実行されるブート同期用のユニークワーク名 */
    const val BOOT_ONE_TIME_WORK_NAME = "DamBootWork"

    /** sudmonitor 日次過去データ取得用の単発ユニークワーク名（既存 [ONE_TIME_WORK_NAME] とは共用しない） */
    const val SUDMONITOR_HISTORY_WORK_NAME = "DamSudmonitorHistoryManualWork"

    private fun buildPeriodicWorkRequest(intervalMillis: Long, initialDelay: Long) =
        PeriodicWorkRequestBuilder<DamWorker>(intervalMillis, TimeUnit.MILLISECONDS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(DamWorker.WORK_TYPE_AUTO)
            .setInputData(workDataOf(DamWorker.KEY_WORK_TYPE to DamWorker.WORK_TYPE_AUTO))
            .build()

    private fun buildOneTimeWorkRequest(workType: String) =
        OneTimeWorkRequestBuilder<DamWorker>()
            .setInputData(workDataOf(DamWorker.KEY_WORK_TYPE to workType))
            .addTag(workType)
            .build()

    // sudmonitor 日次過去データ取得は通信が必須のため、ネットワーク接続制約を付与する。
    // オフライン時に実行された場合も Worker 内で静かに失敗せず、接続回復後に自動で再実行される。
    private fun buildSudmonitorHistoryWorkRequest(trigger: SudmonitorHistoryTrigger) =
        OneTimeWorkRequestBuilder<DamWorker>()
            .setConstraints(Constraints(NetworkType.CONNECTED))
            .setInputData(
                workDataOf(
                    DamWorker.KEY_WORK_TYPE to DamWorker.WORK_TYPE_SUDMONITOR_HISTORY,
                    DamWorker.KEY_SUDMONITOR_HISTORY_TRIGGER to trigger.name
                )
            )
            .addTag(DamWorker.WORK_TYPE_SUDMONITOR_HISTORY)
            // トリガー別タグも付与し、UI側で「初回読込系(INITIAL/TARGET_CHANGE)」と「手動更新」を
            // WorkInfo 監視から判別できるようにする（リアルタイム側のタグ判別と同等）
            .addTag(DamWorker.sudmonitorHistoryTriggerTag(trigger))
            .build()

    /**
     * アプリの設定情報（[AppSettings]）から自動計算された次回実行遅延時間で、自動更新ジョブを新規スケジュールします。
     *
     * @param context コンテキスト
     * @param settings アプリ設定
     * @return 計算された次回実行予定時刻（ミリ秒タイムスタンプ）
     */
    fun scheduleWork(context: Context, settings: AppSettings): Long {
        val intervalMillis = settings.autoUpdateInterval.intervalMillis
        val now = System.currentTimeMillis()
        val nextRunMillis = settings.calculateNextRunTime(now)
        val initialDelay = maxOf(0L, nextRunMillis - now)

        val workRequest = buildPeriodicWorkRequest(intervalMillis, initialDelay)

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            workRequest
        )
        return nextRunMillis
    }

    /**
     * 指定されたピンポイントの次回実行時刻に基づいて、自動更新ジョブをWorkManagerに登録（リエンキュー）します。
     *
     * @param context コンテキスト
     * @param settings アプリ設定
     * @param nextRunMillis 実行目標時刻（ミリ秒タイムスタンプ）
     */
    fun scheduleWorkAtTime(context: Context, settings: AppSettings, nextRunMillis: Long) {
        val intervalMillis = settings.autoUpdateInterval.intervalMillis
        val now = System.currentTimeMillis()
        val initialDelay = maxOf(0L, nextRunMillis - now)

        val workRequest = buildPeriodicWorkRequest(intervalMillis, initialDelay)

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            workRequest
        )
    }

    /**
     * 即時実行の単発（手動）データ同期処理ジョブを登録します。
     *
     * @param context コンテキスト
     * @param workType 実行する処理の種別タグ（[DamWorker.WORK_TYPE_MANUAL]等）
     */
    fun enqueueOneTimeWork(context: Context, workType: String) {
        val workRequest = buildOneTimeWorkRequest(workType)

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * 実行中または待機中の単発データ同期を置き換えて、新しい同期処理を登録します。
     *
     * 対象ダム変更時など、既存の単発処理を残すと新しい対象の取得要求が破棄される場合に使用します。
     *
     * @param context コンテキスト
     * @param workType 処理種別タグ
     */
    fun replaceOneTimeWork(context: Context, workType: String) {
        val workRequest = buildOneTimeWorkRequest(workType)

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * 端末起動直後のバックグラウンド初期データ同期用の単発ジョブを登録します。
     *
     * @param context コンテキスト
     */
    fun enqueueBootWork(context: Context) {
        val workRequest = buildOneTimeWorkRequest(DamWorker.WORK_TYPE_BOOT)

        WorkManager.getInstance(context).enqueueUniqueWork(
            BOOT_ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * sudmonitor 日次過去データの単発取得ジョブを登録します。
     *
     * 既存の単発（リアルタイム）ジョブとは別のユニークワーク名を使用するため、互いに競合しません。
     * 実行待ち・実行中のジョブが既にある場合はそのまま維持します（[ExistingWorkPolicy.KEEP]）。
     * リクエストにはネットワーク接続制約（[NetworkType.CONNECTED]）を付与するため、
     * オフライン時に登録された初回取得（INITIAL）や手動更新（MANUAL）は接続回復後に自動実行されます。
     *
     * @param context コンテキスト
     * @param trigger 取得トリガー（初回起動 / ダム変更 / 手動 / 自動）
     */
    fun enqueueSudmonitorHistoryWork(context: Context, trigger: SudmonitorHistoryTrigger) {
        val workRequest = buildSudmonitorHistoryWorkRequest(trigger)

        WorkManager.getInstance(context).enqueueUniqueWork(
            SUDMONITOR_HISTORY_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * 実行待ち・実行中の sudmonitor 日次過去データ取得ジョブを置き換えて、新しい取得ジョブを登録します。
     *
     * ダム変更時など、既存ジョブを残すと新しい対象ダムの取得要求が破棄される場合に使用します
     * （[ExistingWorkPolicy.REPLACE]。D3: ダム変更時は常に再取得・上書き）。
     * [enqueueSudmonitorHistoryWork] と同じくネットワーク接続制約（[NetworkType.CONNECTED]）を付与します。
     *
     * @param context コンテキスト
     * @param trigger 取得トリガー
     */
    fun replaceSudmonitorHistoryWork(context: Context, trigger: SudmonitorHistoryTrigger) {
        val workRequest = buildSudmonitorHistoryWorkRequest(trigger)

        WorkManager.getInstance(context).enqueueUniqueWork(
            SUDMONITOR_HISTORY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * 登録されている自動更新ジョブをキャンセルします。
     *
     * @param context コンテキスト
     */
    fun cancelWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
