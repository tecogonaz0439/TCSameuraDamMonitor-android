// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.receiver

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.util.AppNotificationManager
import net.tecogonaz.tcsameuradammonitor.widget.DamWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject


/**
 * 端末のタイムゾーン変更イベント（[Intent.ACTION_TIMEZONE_CHANGED]）を受信するための[BroadcastReceiver]。
 *
 * タイムゾーンが変更された（例: 日本（JST）から他国へ移動、あるいはその逆）際に、
 * 日本標準時以外の端末で時刻の隣に表示されるべき "(JST)" サフィックスの追加・削除などの時間表現切替を即座に行い、
 * 常駐通知やホーム画面ウィジェットを再描画して表示の整合性を担保します。
 */
@AndroidEntryPoint
class TimeZoneChangeReceiver : BroadcastReceiver() {
    private companion object {
        /** 非同期ブロードキャスト受信処理のタイムアウト閾値（25秒） */
        const val ASYNC_TIMEOUT_MS = 25_000L
    }

    /** 通知マネージャ */
    @Inject
    lateinit var notificationManager: AppNotificationManager

    /** 設定情報の永続化リポジトリ */
    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** 最新観測データを永続キャッシュするリポジトリ */
    @Inject
    lateinit var damDataRepository: DamDataRepository

    /**
     * タイムゾーン変更インテントを受信した際の処理起点。
     *
     * 非同期でタイムゾーン再計算を伴う通知・ウィジェットの再構築処理を行います。
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(ASYNC_TIMEOUT_MS) { handleTimeZoneChanged(context) }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w("TimeZoneChangeReceiver", "Timezone change handling failed.", e)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * タイムゾーン変更の適用処理。
     *
     * JST以外のロケール・タイムゾーンに応じた時系列テキスト整形を促し、
     * 常駐通知およびホームウィジェットを最新情報でアップデートします。
     */
    internal suspend fun handleTimeZoneChanged(context: Context, updateWidgets: Boolean = true) {
        val settings = settingsRepository.appSettingsFlow.first()
        val damData = damDataRepository.damDataFlow.first()
        val loadStatus = damDataRepository.loadStatus.value

        
        notificationManager.updateNotification(damData, settings, loadStatus)

        if (!updateWidgets) return

        
        val widgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, DamWidgetReceiver::class.java)
        val appWidgetIds = widgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isNotEmpty()) {
            val updateIntent = Intent(context, DamWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(updateIntent)
        }
    }
}
