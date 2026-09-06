// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.util.AppNotificationManager
import net.tecogonaz.tcsameuradammonitor.worker.DamWorkManagerGateway
import net.tecogonaz.tcsameuradammonitor.worker.DamWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * 端末の起動完了イベント（[Intent.ACTION_BOOT_COMPLETED]）を受信するための[BroadcastReceiver]。
 *
 * 端末の再起動後に自動更新スケジュールの復元、ブート用の初期通知の表示、および設定に応じてバックグラウンドでの即時起動更新の実行要求を行います。
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    private companion object {
        /** 非同期ブロードキャスト受信処理のタイムアウト閾値（25秒） */
        const val ASYNC_TIMEOUT_MS = 25_000L
    }

    /** 設定情報の永続化リポジトリ */
    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** 最新観測データを永続キャッシュするリポジトリ */
    @Inject
    lateinit var damDataRepository: DamDataRepository

    /** 常駐通知マネージャ */
    @Inject
    lateinit var notificationManager: AppNotificationManager

    /** デバッグログ用リポジトリ */
    @Inject
    lateinit var debugLogRepository: DebugLogRepository

    /** WorkManagerとの連携ゲートウェイ */
    @Inject
    lateinit var damWorkManagerGateway: DamWorkManagerGateway

    /** 端末再起動時にDebug開始前のデータを復元するリポジトリ */
    @Inject
    lateinit var debugDataSessionRepository: DebugDataSessionRepository

    /**
     * ブロードキャストインテント受信時のトリガーメソッド。
     *
     * 端末の起動完了を検知した際に、WorkManager等のバックグラウンドスレッドを立ち上げるため、
     * [goAsync]を用いて非同期スコープで処理を開始します。
     *
     * @param context コンテキスト
     * @param intent 受信したインテント
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    withTimeout(ASYNC_TIMEOUT_MS) { handleBootCompleted() }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w("BootCompletedReceiver", "Boot handling failed.", e)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    /**
     * 起動完了時に実行される主要タスク。
     *
     * 1. 開発用のデバッグ設定（擬似エラーや強制オンライン等）を自動リセットします。
     * 2. アプリ設定に従ってブート通知を表示・キャンセルします。
     * 3. 起動時自動更新（updateOnBoot）が有効かつキャッシュデータが存在する場合は、即時にブート時バックグラウンドデータ同期をWorkManagerに登録（起動）します。
     */
    internal suspend fun handleBootCompleted() {
        if (settingsRepository.appSettingsFlow.first().debugModeEnabled) {
            debugDataSessionRepository.exitDebugMode().getOrThrow()
        }
        settingsRepository.updateSettings { it.resetDebugSettings() }
        val appSettings = settingsRepository.appSettingsFlow.first()
        if (appSettings.showNotification) {
            
            val cachedDamData = damDataRepository.damDataFlow.first()
            notificationManager.showBootNotification(cachedDamData, appSettings)
        } else {
            notificationManager.cancelBootNotification()
        }
        
        if (appSettings.updateOnBoot && damDataRepository.hasCachedData()) {
            damWorkManagerGateway.enqueueOneTimeWork(DamWorker.WORK_TYPE_BOOT)
        }
        
        debugLogRepository.addEntry("Boot completed. Notification and work scheduling finished.")
    }
}
