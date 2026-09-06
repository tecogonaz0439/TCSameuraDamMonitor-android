// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.worker


/**
 * バックグラウンドワーカーの実行リトライやエラーラベル変換などのビジネスルールをカプセル化したポリシーオブジェクト。
 */
internal object DamWorkerPolicy {
    /**
     * 自動定期更新ジョブのリトライ回数に基づいて、再試行すべきかどうかを判定します。
     *
     * @param runAttemptCount 現在の累積試行回数
     * @return リトライ上限数を超えていない場合はtrue
     */
    fun shouldRetryAuto(runAttemptCount: Int): Boolean =
        runAttemptCount < DamWorker.MAX_AUTO_RETRIES

    /**
     * 単発同期ジョブの実行タイプに対応する表示用エラーラベル文字列を返します。
     *
     * @param workType ジョブ種別
     * @return エラーログ等に書き出すためのラベル文字列
     */
    fun oneTimeFailureLabel(workType: String): String =
        when (workType) {
            DamWorker.WORK_TYPE_MANUAL -> "Manual update"
            DamWorker.WORK_TYPE_BOOT -> "Boot update"
            else -> "Initial load"
        }
}
