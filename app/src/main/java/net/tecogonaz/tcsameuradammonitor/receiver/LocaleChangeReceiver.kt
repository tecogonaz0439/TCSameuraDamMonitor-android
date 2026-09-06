// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.util.AppNotificationManager
import net.tecogonaz.tcsameuradammonitor.widget.DamWidgetReceiver


/**
 * 端末のロケール（言語・国設定）変更イベント（[Intent.ACTION_LOCALE_CHANGED]）を受信するための[BroadcastReceiver]。
 *
 * ロケールが日本語から英語、あるいはその逆に変更された際に、通知やウィジェットに表示されるテキスト（ダム名、状態メッセージ、単位等）を
 * 即座にローカライズされた適切な表示言語に切り替えて更新します。
 */
@AndroidEntryPoint
class LocaleChangeReceiver : BroadcastReceiver() {
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

    /**
     * ロケール変更インテントを受信した際の処理起点。
     *
     * 該当する変更インテントを確認後、非同期で通知やウィジェットのリロードをトリガーします。
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCALE_CHANGED || !isTargetPackage(context, intent)) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(ASYNC_TIMEOUT_MS) { handleLocaleChanged(context) }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w("LocaleChangeReceiver", "Locale change handling failed.", e)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * ロケール変更発生時の通知およびウィジェットの文言更新処理。
     *
     * 1. 永続設定のメモリキャッシュを無効化（クリア）して再読み込みを促します。
     * 2. 最新の観測データとしきい値設定から、ロケールに合った常駐通知およびブート通知を更新します。
     * 3. [updateWidgets]が有効な場合、ホーム画面のウィジェットも強制的に再描画します。
     */
    internal suspend fun handleLocaleChanged(context: Context, updateWidgets: Boolean = true) {
        settingsRepository.invalidateSettingsCache()

        val settings = settingsRepository.appSettingsFlow.first()
        val damData = damDataRepository.damDataFlow.first()
        val loadStatus = damDataRepository.loadStatus.value

        notificationManager.updateNotification(damData, settings, loadStatus)
        notificationManager.updateBootNotificationIfActive(damData, settings)
        if (updateWidgets) {
            DamWidgetReceiver.updateAllWidgetsImmediately(context)
        }
    }

    private fun isTargetPackage(context: Context, intent: Intent): Boolean {
        val packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
        return packageName == null || packageName == context.packageName
    }
}
