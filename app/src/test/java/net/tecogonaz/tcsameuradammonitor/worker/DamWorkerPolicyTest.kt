// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * バックグラウンド実行ポリシー [DamWorkerPolicy] のユニットテストクラス。
 * 自動更新が失敗した際の再試行回数制御の上限チェックロジック（`shouldRetryAuto`）、および
 * 失敗したタスク種別（手動、起動時、初期ロードなど）に対応するユーザー向けの表示ラベル出力ロジックが
 * 正しく動作することを検証します。
 */
class DamWorkerPolicyTest {
    @Test
    fun shouldRetryAuto_retriesUntilMaxAutoRetries() {
        assertTrue(DamWorkerPolicy.shouldRetryAuto(0))
        assertTrue(DamWorkerPolicy.shouldRetryAuto(DamWorker.MAX_AUTO_RETRIES - 1))
        assertFalse(DamWorkerPolicy.shouldRetryAuto(DamWorker.MAX_AUTO_RETRIES))
    }

    @Test
    fun oneTimeFailureLabel_mapsWorkTypeToUserVisibleLogPrefix() {
        assertEquals("Manual update", DamWorkerPolicy.oneTimeFailureLabel(DamWorker.WORK_TYPE_MANUAL))
        assertEquals("Boot update", DamWorkerPolicy.oneTimeFailureLabel(DamWorker.WORK_TYPE_BOOT))
        assertEquals("Initial load", DamWorkerPolicy.oneTimeFailureLabel(DamWorker.WORK_TYPE_INITIAL))
        assertEquals("Initial load", DamWorkerPolicy.oneTimeFailureLabel("UNKNOWN"))
    }
}
