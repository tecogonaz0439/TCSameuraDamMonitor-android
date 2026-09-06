// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.usecase

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import net.tecogonaz.tcsameuradammonitor.ui.main.AppLinkInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * 端末内にインストールされているアプリのうち、特定のURL（例: 国土交通省のウェブサイト等）を開くことができる利用可能なアプリケーション一覧をクエリするユースケース。
 */
class QueryAvailableAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * 指定されたURLを開くためのインテントを処理できるインストール済みのアプリ情報を取得し、表示名の昇順でソートして返します。
     *
     * @param url 開く対象の外部URL
     * @return インテントを処理可能なアプリの情報のリスト（[AppLinkInfo]）
     */
    suspend operator fun invoke(url: String): List<AppLinkInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        val packageManager = context.packageManager
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map {
                AppLinkInfo(
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name,
                    name = it.loadLabel(packageManager).toString(),
                    icon = it.loadIcon(packageManager)
                )
            }
            .sortedBy { it.name }
    }
}
