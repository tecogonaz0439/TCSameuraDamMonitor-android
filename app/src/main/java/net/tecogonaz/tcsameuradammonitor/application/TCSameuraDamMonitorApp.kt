// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.application

import android.app.Application
import android.util.Log
import androidx.glance.appwidget.updateAll
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.di.ApplicationScope
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.util.AppNotificationManager
import net.tecogonaz.tcsameuradammonitor.widget.DamWidgetProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory


/**
 * アプリケーションクラス。
 *
 * Hiltによる依存注入（DI）の起点であり、[Configuration.Provider]を実装してWorkManagerのカスタム構成を設定します。
 * また、アプリケーションの起動時にダムデータの更新監視を開始し、ウィジェットおよび通知の自動更新をハンドリングします。
 */
@HiltAndroidApp
class TCSameuraDamMonitorApp : Application(), Configuration.Provider {

    /** WorkManagerのバックグラウンドワーカー構築に使用するHiltのワーカーファクトリ */
    @Inject lateinit var workerFactory: HiltWorkerFactory
    
    /** ダム観測データの取得およびキャッシュ保持を担当するリポジトリ */
    @Inject lateinit var damDataRepository: DamDataRepository
    
    /** アプリ全体の各種設定を管理するリポジトリ */
    @Inject lateinit var settingsRepository: SettingsRepository

    /** Debug中のプロセス終了をまたいで通常データの待避・復元を管理するリポジトリ */
    @Inject lateinit var debugDataSessionRepository: DebugDataSessionRepository
    
    /** バックグラウンド更新通知の表示・更新制御を行うマネージャ */
    @Inject lateinit var appNotificationManager: AppNotificationManager
    
    /** アプリケーションのライフサイクル全体で動作するCoroutineスコープ */
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    /**
     * WorkManagerのカスタム設定。
     *
     * Hiltの[HiltWorkerFactory]をWorkManagerに登録して依存注入可能なワーカーの生成を可能にします。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
            
    /**
     * アプリケーション起動時の初期化処理。
     *
     * 親クラスのonCreateを呼び出し、[applicationScope]上でダム観測データ、アプリ設定、
     * ロード状態の最新フローを監視（combine）して、変化があった際にウィジェットと常駐通知を自動更新します。
     */
    override fun onCreate() {
        super.onCreate()

        
        applicationScope.launch {
            debugDataSessionRepository.recoverSessionOnStartup().onFailure { error ->
                if (BuildConfig.DEBUG) {
                    Log.w("TCSameuraDamMonitorApp", "Debug data session recovery failed.", error)
                }
            }
            combine(
                damDataRepository.damDataFlow,
                settingsRepository.appSettingsFlow,
                damDataRepository.loadStatus
            ) { damData, settings, loadStatus ->
                Triple(damData, settings, loadStatus)
            }.collect { (damData, settings, loadStatus) ->
                try {
                    DamWidgetProvider().updateAll(this@TCSameuraDamMonitorApp)
                    appNotificationManager.updateNotification(damData, settings, loadStatus)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w("TCSameuraDamMonitorApp", "Widget or notification update failed.", e)
                    }
                }
            }
        }
    }
}
