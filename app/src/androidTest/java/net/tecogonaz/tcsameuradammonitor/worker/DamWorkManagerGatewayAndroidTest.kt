// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * バックグラウンド処理 WorkManager へのリクエスト発行を行う [DefaultDamWorkManagerGateway] の Instrumentation テストクラス。
 * WorkManager のテスト支援ヘルパー（`WorkManagerTestInitHelper`）を用いたテスト環境の構築、手動更新（MANUAL）タスク登録時の
 * Flow 経由の検知、自動更新（AUTO）スケジュール登録時のタスクタグ検知、およびスケジュール取り消し（Cancel）実行時に
 * Flow が適切に完了（Finished）ステータスを配信することを検証します。
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class DamWorkManagerGatewayAndroidTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var gateway: DefaultDamWorkManagerGateway

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        gateway = DefaultDamWorkManagerGateway(context)
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get()
    }

    @Test
    fun oneTimeWorkInfosFlow_emitsEnqueuedManualWork() = runTest {
        gateway.enqueueOneTimeWork(DamWorker.WORK_TYPE_MANUAL)

        val infos = withTimeout(3_000) {
            gateway.oneTimeWorkInfosFlow().first { it.isNotEmpty() }
        }

        assertTrue(infos.single().tags.contains(DamWorker.WORK_TYPE_MANUAL))
    }

    @Test
    fun replaceOneTimeWork_replacesManualWorkWithInitialWork() = runTest {
        gateway.enqueueOneTimeWork(DamWorker.WORK_TYPE_MANUAL)

        gateway.replaceOneTimeWork(DamWorker.WORK_TYPE_INITIAL)

        val infos = withTimeout(3_000) {
            gateway.oneTimeWorkInfosFlow().first { workInfos ->
                workInfos.any { it.tags.contains(DamWorker.WORK_TYPE_INITIAL) }
            }
        }
        assertTrue(infos.any { it.tags.contains(DamWorker.WORK_TYPE_INITIAL) })
        assertTrue(
            infos
                .filter { it.tags.contains(DamWorker.WORK_TYPE_MANUAL) }
                .all { it.state.isFinished }
        )
    }

    @Test
    fun autoWorkInfosFlow_emitsScheduledAutoWork() = runTest {
        val settings = AppSettings(autoUpdateInterval = AutoUpdateInterval.ONE_HOUR)

        gateway.scheduleWorkAtTime(settings, System.currentTimeMillis())

        val infos = withTimeout(3_000) {
            gateway.autoWorkInfosFlow().first { it.isNotEmpty() }
        }

        assertTrue(infos.single().tags.contains(DamWorker.WORK_TYPE_AUTO))
    }

    @Test
    fun cancelWork_emitsFinishedAutoWork() = runTest {
        val settings = AppSettings(autoUpdateInterval = AutoUpdateInterval.ONE_HOUR)
        gateway.scheduleWorkAtTime(settings, System.currentTimeMillis())

        gateway.cancelWork()

        val infos = withTimeout(3_000) {
            gateway.autoWorkInfosFlow().first { workInfos ->
                workInfos.singleOrNull()?.state?.isFinished == true
            }
        }
        assertTrue(infos.single().state.isFinished)
    }
}
