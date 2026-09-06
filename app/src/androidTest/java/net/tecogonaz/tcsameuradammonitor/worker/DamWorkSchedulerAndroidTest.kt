// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
/**
 * バックグラウンド処理の登録スケジュール管理を行う [DamWorkScheduler] の Instrumentation テストクラス。
 * WorkManager（`WorkManagerTestInitHelper`）を用いたテスト環境において、手動更新（MANUAL）、自動更新（AUTO）、
 * および起動時ワンタイム更新（BOOT）などの各 WorkRequest が一意（UniqueWork）の名前と適切なタスクタグを付与された状態で
 * エンキューされること、および自動更新のキャンセル実行時に正しくタスクが取り消されることを検証します。
 */
class DamWorkSchedulerAndroidTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get()
    }

    @Test
    fun enqueueOneTimeWork_registersManualUniqueWorkWithTag() {
        DamWorkScheduler.enqueueOneTimeWork(context, DamWorker.WORK_TYPE_MANUAL)

        val workInfo = workManager
            .getWorkInfosForUniqueWork(DamWorkScheduler.ONE_TIME_WORK_NAME)
            .get()
            .single()

        assertTrue(DamWorker.WORK_TYPE_MANUAL in workInfo.tags)
    }

    @Test
    fun replaceOneTimeWork_replacesExistingManualWorkWithInitialWork() {
        DamWorkScheduler.enqueueOneTimeWork(context, DamWorker.WORK_TYPE_MANUAL)

        DamWorkScheduler.replaceOneTimeWork(context, DamWorker.WORK_TYPE_INITIAL)

        val workInfos = workManager
            .getWorkInfosForUniqueWork(DamWorkScheduler.ONE_TIME_WORK_NAME)
            .get()
        assertTrue(workInfos.any { DamWorker.WORK_TYPE_INITIAL in it.tags })
        assertTrue(
            workInfos
                .filter { DamWorker.WORK_TYPE_MANUAL in it.tags }
                .all { it.state.isFinished }
        )
    }

    @Test
    fun scheduleWorkAtTime_registersAutoWork() {
        val settings = AppSettings(autoUpdateInterval = AutoUpdateInterval.ONE_HOUR)

        DamWorkScheduler.scheduleWorkAtTime(context, settings, System.currentTimeMillis())

        val workInfo = workManager
            .getWorkInfosForUniqueWork(DamWorkScheduler.WORK_NAME)
            .get()
            .single()

        assertTrue(DamWorker.WORK_TYPE_AUTO in workInfo.tags)
    }

    @Test
    fun cancelWork_cancelsRegisteredAutoWork() {
        val settings = AppSettings(autoUpdateInterval = AutoUpdateInterval.ONE_HOUR)
        DamWorkScheduler.scheduleWorkAtTime(context, settings, System.currentTimeMillis())

        DamWorkScheduler.cancelWork(context)

        val workInfos = workManager
            .getWorkInfosForUniqueWork(DamWorkScheduler.WORK_NAME)
            .get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos.single().state.isFinished)
    }

    @Test
    fun enqueueBootWork_registersBootUniqueWorkWithTag() {
        DamWorkScheduler.enqueueBootWork(context)

        val workInfo = workManager
            .getWorkInfosForUniqueWork(DamWorkScheduler.BOOT_ONE_TIME_WORK_NAME)
            .get()
            .single()

        assertTrue(DamWorker.WORK_TYPE_BOOT in workInfo.tags)
    }

    @Test
    fun enqueueSudmonitorHistoryWork_requiresConnectedNetwork() {
        DamWorkScheduler.enqueueSudmonitorHistoryWork(context, SudmonitorHistoryTrigger.INITIAL)

        val workInfo = workManager
            .getWorkInfosForUniqueWork(DamWorkScheduler.SUDMONITOR_HISTORY_WORK_NAME)
            .get()
            .single()

        assertTrue(DamWorker.WORK_TYPE_SUDMONITOR_HISTORY in workInfo.tags)
        // トリガー別タグも付与され、UI側の回転アイコン判別（初回読込系 vs 手動更新）に使われる
        assertTrue(
            DamWorker.sudmonitorHistoryTriggerTag(SudmonitorHistoryTrigger.INITIAL) in workInfo.tags
        )
        assertEquals(NetworkType.CONNECTED, getRequiredNetworkType(workInfo.id.toString()))
    }

    @Test
    fun replaceSudmonitorHistoryWork_requiresConnectedNetwork() {
        DamWorkScheduler.replaceSudmonitorHistoryWork(context, SudmonitorHistoryTrigger.TARGET_CHANGE)

        val workInfo = workManager
            .getWorkInfosForUniqueWork(DamWorkScheduler.SUDMONITOR_HISTORY_WORK_NAME)
            .get()
            .single()

        assertTrue(DamWorker.WORK_TYPE_SUDMONITOR_HISTORY in workInfo.tags)
        // トリガー別タグも付与され、UI側の回転アイコン判別（初回読込系 vs 手動更新）に使われる
        assertTrue(
            DamWorker.sudmonitorHistoryTriggerTag(SudmonitorHistoryTrigger.TARGET_CHANGE) in workInfo.tags
        )
        assertEquals(NetworkType.CONNECTED, getRequiredNetworkType(workInfo.id.toString()))
    }

    /**
     * 指定IDの WorkSpec に設定された要求ネットワーク型を取得する。
     * [WorkInfo] は制約を公開しないため、WorkManager の WorkDatabase 経由で参照する。
     */
    private fun getRequiredNetworkType(workSpecId: String): NetworkType {
        val workDatabase = (workManager as WorkManagerImpl).workDatabase
        return workDatabase.workSpecDao().getWorkSpec(workSpecId)!!.constraints.requiredNetworkType
    }
}
