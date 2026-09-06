// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import net.tecogonaz.tcsameuradammonitor.MainActivity
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.DamListData
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import net.tecogonaz.tcsameuradammonitor.widget.WidgetDisplayFormatter
import net.tecogonaz.tcsameuradammonitor.ui.common.MISSING_PERCENTAGE_TEXT
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import net.tecogonaz.tcsameuradammonitor.domain.model.isAllObservationDataInvalid


/**
 * アプリの常駐通知および各種バックグラウンド更新通知の表示・更新制御を一括管理する通知マネージャクラス。
 *
 * ロケールに応じたダム情報の文字列生成（貯水率や前日比などを含む）、通知チャネルの作成、
 * 端末起動時のブート通知表示・キャンセル制御を担います。
 */
@Singleton
open class AppNotificationManager @Inject constructor(@ApplicationContext private val context: Context) {
    
    companion object {
        /** 通知チャネルID */
        private const val CHANNEL_ID = "DamUpdateChannel"

        /** 常駐通知用のユニークID */
        const val NOTIFICATION_ID = 1001

        /** 端末再起動（ブート）時の初期表示用通知ID */
        const val BOOT_NOTIFICATION_ID = 1002
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    
    private fun createMainPendingIntent(): PendingIntent {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    
    private fun formatTimeShort(timeStr: String): String {
        return TimeUtils.parseAndFormatToJstWithSuffix(timeStr, "yyyy/MM/dd H:m", "HH:mm") ?: ""
    }

    
    private fun formatDateMonthDay(timeStr: String): String {
        return TimeUtils.parseAndFormatToJst(timeStr, "yyyy/MM/dd H:m", "yyyy/MM/dd") ?: ""
    }

    
    private fun getDamName(settings: AppSettings): String {
        val damConfig = DamListData.allDams.find { it.id == settings.targetDamId }
        return damConfig?.let { LocaleUtils.normalDamName(context, it) } ?: ""
    }

    
    /**
     * 表示設定および取得ステータス、最新ダム観測データ情報に基づいて常駐通知用（またはバックグラウンド更新中用）の[Notification]オブジェクトを構築します。
     *
     * 1. 取得状態が異常な場合は、それぞれ初期メッセージ、オフラインメッセージ、または読み込み失敗メッセージを構築して返します。
     * 2. ダムデータが正常な場合は、ダム名、貯水率、観測日時、およびしきい値に応じた「貯水率メッセージ」を埋め込んだスタイリッシュなBigTextStyle通知を作成します。
     *
     * @param damData 観測データのドメインモデル
     * @param settings アプリ設定
     * @param loadStatus 最新のロードステータス
     * @return 構築された[Notification]オブジェクト
     */
    fun getForegroundNotification(
        damData: DamData?,
        settings: AppSettings,
        loadStatus: DamLoadStatus = DamLoadStatus.LOADING_FAILURE
    ): Notification {
        val damName = getDamName(settings)
        val pendingMainIntent = createMainPendingIntent()
        val isJa = LocaleUtils.isJapanese(context)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dam)
            .setOngoing(true)
            .setContentIntent(pendingMainIntent)

        
        val errorText = when (loadStatus) {
            DamLoadStatus.INITIAL -> settings.getInitialMessageText(isJa)
            DamLoadStatus.NETWORK_UNAVAILABLE ->
                settings.getNetworkUnavailableText(isJa).ifEmpty { settings.getLoadingErrorText(isJa) }
            DamLoadStatus.LOADING_FAILURE -> settings.getLoadingErrorText(isJa)
            DamLoadStatus.SUCCESS -> null
        }
        if (errorText != null && damData == null) {
            return builder
                .setContentTitle(damName)
                .setContentText(errorText)
                .build()
        }

        if (damData == null) {
            return builder
                .setContentTitle(damName)
                .setContentText(settings.getLoadingErrorText(isJa))
                .build()
        }

        
        val isAllInvalid = damData.isAllObservationDataInvalid()
        val percentage = damData.storagePercentage
        val isSameura = settings.targetDamId == AppSettings.DEFAULT_DAM_ID
        val targetTimeStr = damData.storagePercentageTime ?: damData.updatedAt
        val dateStr = formatDateMonthDay(targetTimeStr)
        val timeStr = formatTimeShort(targetTimeStr)
        val trendStr = if (percentage != null) WidgetDisplayFormatter.trendText(damData.storagePercentageTrend) else ""
        val percentageStr = if (percentage != null) {
            String.format(LocaleUtils.effectiveLocale(context), "%.2f%%", percentage)
        } else {
            MISSING_PERCENTAGE_TEXT
        }

        val (stateStr, msgStr) = settings.getStateForPercentage(
            percentage,
            isJa,
            isAllInvalid,
            isSameura,
            damData.storageVolumeForMessage
        )

        
        val collapsedText = listOfNotNull(
            stateStr.takeIf { it.isNotEmpty() },
            dateStr.takeIf { it.isNotEmpty() },
            timeStr.takeIf { it.isNotEmpty() },
            percentageStr,
            trendStr.takeIf { it.isNotEmpty() }
        ).joinToString(" ")

        
        val expandedLine1 = listOfNotNull(
            dateStr.takeIf { it.isNotEmpty() },
            timeStr.takeIf { it.isNotEmpty() },
            percentageStr,
            trendStr.takeIf { it.isNotEmpty() }
        ).joinToString(" ")

        
        val expandedLine2 = buildString {
            append(stateStr)
            if (msgStr.isNotEmpty()) append(" $msgStr")
        }.trim()

        val expandedText = if (expandedLine2.isNotEmpty()) "$expandedLine1\n$expandedLine2" else expandedLine1

        return builder
            .setContentTitle(damName)
            .setContentText(collapsedText)
            .setStyle(Notification.BigTextStyle().bigText(expandedText))
            .build()
    }

    
    /**
     * ロケール設定に従い、システム通知に表示する概要テキスト（1行形式）をフォーマット生成します。
     *
     * @param damData 観測データのドメインモデル
     * @param settings アプリ設定
     * @return 生成された通知メッセージ文字列。ダムデータがない場合はnull。
     */
    fun formatNotificationMessage(damData: DamData?, settings: AppSettings): String? {
        if (damData == null) return null

        val isJa = LocaleUtils.isJapanese(context)
        val damName = getDamName(settings)
        val percentage = damData.storagePercentage

        val targetTimeStr = damData.storagePercentageTime ?: damData.updatedAt
        val dateStr = formatDateMonthDay(targetTimeStr)
        val timeStr = formatTimeShort(targetTimeStr)
        val trendStr = if (percentage != null) WidgetDisplayFormatter.trendText(damData.storagePercentageTrend) else ""
        val percentageStr = if (percentage != null) {
            String.format(LocaleUtils.effectiveLocale(context), "%.2f%%", percentage)
        } else {
            MISSING_PERCENTAGE_TEXT
        }

        val dataLine = listOfNotNull(
            dateStr.takeIf { it.isNotEmpty() },
            timeStr.takeIf { it.isNotEmpty() },
            percentageStr,
            trendStr.takeIf { it.isNotEmpty() }
        ).joinToString(" ")

        val stateText = settings.getStateText(
            percentage,
            isJa,
            isSameura = settings.targetDamId == AppSettings.DEFAULT_DAM_ID,
            storageVolumeForMessage = damData.storageVolumeForMessage
        )
        return if (stateText.isNotEmpty()) "$damName $dataLine $stateText" else "$damName $dataLine"
    }

    /**
     * 更新成功時のSnackbarに表示するダム情報を複数行形式で生成します。
     *
     * 1行目にダム名、2行目に観測日時・貯水率・トレンド、3行目に状態とメッセージを配置します。
     * 貯水率メッセージが非表示または空の場合は3行目を省略します。
     *
     * @param damData 観測データのドメインモデル
     * @param settings アプリ設定
     * @return 生成されたSnackbarメッセージ文字列。ダムデータがない場合はnull。
     */
    fun formatSnackbarMessage(damData: DamData?, settings: AppSettings): String? {
        if (damData == null) return null

        val isJa = LocaleUtils.isJapanese(context)
        val damName = getDamName(settings)
        val percentage = damData.storagePercentage
        val targetTimeStr = damData.storagePercentageTime ?: damData.updatedAt
        val dateStr = formatDateMonthDay(targetTimeStr)
        val timeStr = formatTimeShort(targetTimeStr)
        val trendStr = if (percentage != null) WidgetDisplayFormatter.trendText(damData.storagePercentageTrend) else ""
        val percentageStr = if (percentage != null) {
            String.format(LocaleUtils.effectiveLocale(context), "%.2f%%", percentage)
        } else {
            MISSING_PERCENTAGE_TEXT
        }

        val dataLine = listOfNotNull(
            dateStr.takeIf { it.isNotEmpty() },
            timeStr.takeIf { it.isNotEmpty() },
            percentageStr,
            trendStr.takeIf { it.isNotEmpty() }
        ).joinToString(" ")
        val stateText = settings.getStateText(
            percentage,
            isJa,
            isAllDataInvalid = damData.isAllObservationDataInvalid(),
            isSameura = settings.targetDamId == AppSettings.DEFAULT_DAM_ID,
            storageVolumeForMessage = damData.storageVolumeForMessage
        )

        return listOf(damName, dataLine, stateText)
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /**
     * 表示設定および端末起動状態等を加味して、常駐の最新観測データ通知を更新または削除します。
     *
     * 設定で通知が非表示になっている場合や、端末再起動直後で一度も開かれておらず自動更新も走っていない場合は、通知をキャンセルします。
     *
     * @param damData 観測データのドメインモデル
     * @param settings アプリ設定
     * @param loadStatus ロード状態ステータス
     */
    open fun updateNotification(
        damData: DamData?,
        settings: AppSettings,
        loadStatus: DamLoadStatus = DamLoadStatus.LOADING_FAILURE
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        
        val currentBootTime = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        val isBootedButNotOpened = Math.abs(currentBootTime - settings.lastBootTime) > AppSettings.BOOT_TIME_TOLERANCE_MS
        val hasAutoUpdatedSinceBoot = settings.lastAutoUpdateMillis > currentBootTime

        
        if (!settings.showNotification || (isBootedButNotOpened && !hasAutoUpdatedSinceBoot)) {
            notificationManager.cancel(NOTIFICATION_ID)
            notificationManager.cancel(BOOT_NOTIFICATION_ID)
            return
        }
        
        notificationManager.cancel(BOOT_NOTIFICATION_ID)
        val notification = getForegroundNotification(damData, settings, loadStatus)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 端末再起動（ブート）時の初期表示用通知が現在アクティブ（表示中）である場合に、最新のダムデータと設定情報を用いてその表示内容をアップデートします。
     */
    open fun updateBootNotificationIfActive(damData: DamData? = null, settings: AppSettings? = null) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val isBootNotificationActive = notificationManager.activeNotifications.any {
            it.id == BOOT_NOTIFICATION_ID
        }
        if (isBootNotificationActive) {
            showBootNotification(damData, settings)
        }
    }

    /**
     * 端末再起動（ブート）時用の初期通知を表示します。
     *
     * 最新の観測データと設定が存在する場合は、そのデータを用いた詳細な常駐風通知を表示し、
     * まだデータがない場合はアプリの起動を促すシンプルな通知を表示します。
     */
    open fun showBootNotification(damData: DamData? = null, settings: AppSettings? = null) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        if (settings?.showNotification == false) {
            notificationManager.cancel(BOOT_NOTIFICATION_ID)
            return
        }

        if (damData != null && settings != null) {
            
            val notification = getForegroundNotification(damData, settings)
            notificationManager.notify(BOOT_NOTIFICATION_ID, notification)
        } else {
            
            val title = context.getString(R.string.app_name)
            val text = context.getString(R.string.boot_notification_open_app)
            val pendingIntent = createMainPendingIntent()
            val builder = Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_dam)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
            notificationManager.notify(BOOT_NOTIFICATION_ID, builder.build())
        }
    }

    
    /**
     * 表示されている端末再起動（ブート）時用初期通知をキャンセル（消去）します。
     */
    open fun cancelBootNotification() {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(BOOT_NOTIFICATION_ID)
    }
}
