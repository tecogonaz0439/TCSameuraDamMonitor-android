// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.annotation.AnnotationTarget
import kotlin.annotation.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton


/**
 * アプリケーションのグローバルな非同期処理で使用するカスタムCoroutineスコープを識別するためのHilt修飾子（Qualifier）。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
annotation class ApplicationScope

/**
 * アプリケーション全体で共有するコルーチンスコープを提供するHiltモジュール。
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    
    /**
     * アプリケーションのライフサイクル全体で動作する[CoroutineScope]を生成して提供します。
     *
     * SupervisorJobとDispatchers.IOを使用し、子コルーチンの失敗が他へ伝播するのを防ぎます。
     *
     * @return シングルトンとして管理される[CoroutineScope]
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
