// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import net.tecogonaz.tcsameuradammonitor.widget.DamWidgetReceiver
import javax.inject.Inject

/**
 * 登録されているすべてのウィジェット（App Widget）に対して、即座に画面更新を要求するためのヘルパークラスです。
 *
 * @property context アプリケーションコンテキスト。
 */
open class WidgetUpdateRequester @Inject constructor(
    @ApplicationContext private val context: Context
) {
    open suspend fun updateAllWidgetsImmediately() {
        DamWidgetReceiver.updateAllWidgetsImmediately(context)
    }
}
