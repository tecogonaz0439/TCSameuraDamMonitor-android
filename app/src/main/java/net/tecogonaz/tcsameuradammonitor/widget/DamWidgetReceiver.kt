// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Jetpack Glance を使用したホーム画面ウィジェットの更新イベントを受信するブロードキャストレシーバー。
 *
 * システムまたはアプリからのウィジェット更新要求をトリガーとして、[DamWidgetProvider] を介して
 * ウィジェットのUIレイアウトおよび表示データを更新します。
 */
class DamWidgetReceiver : GlanceAppWidgetReceiver() {
    /**
     * レシーバーが管理するウィジェットプロバイダ（[DamWidgetProvider]）のインスタンス。
     */
    override val glanceAppWidget: GlanceAppWidget = DamWidgetProvider()

    /**
     * ブロードキャストインテントを受信した際に呼び出されます。
     * 親クラス [GlanceAppWidgetReceiver] の標準的な更新処理を実行します。
     *
     * @param context コンテキスト
     * @param intent 受信したインテント
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }

    companion object {
        /**
         * すべての有効なホーム画面ウィジェットを直ちに強制更新します。
         *
         * 新たにダムの貯水率データを取得・受信した際や、設定が変更された際に、
         * バックグラウンド（I/Oスレッド）でウィジェットの表示内容を即時更新するために呼び出されます。
         *
         * @param context コンテキスト
         */
        suspend fun updateAllWidgetsImmediately(context: Context) = withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            DamWidgetProvider().updateAll(appContext)
        }
    }
}
