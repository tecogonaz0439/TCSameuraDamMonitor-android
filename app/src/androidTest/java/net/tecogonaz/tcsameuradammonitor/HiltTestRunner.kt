// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Android 計測テスト（Instrumentation Test）において、依存関係注入フレームワークの Hilt を有効にするための
 * カスタム JUnit テストランナー。
 * テスト実行時の [Application] インスタンスとして、Hilt テスト専用の [HiltTestApplication] を起動するように設定します。
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context
    ): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
