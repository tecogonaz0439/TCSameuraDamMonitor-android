// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import net.tecogonaz.tcsameuradammonitor.domain.model.getDamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.SUDMONITOR_HOST
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext


/**
 * バックグラウンドでのダム観測データの取得（自動更新、手動更新、再起動時ブート更新、初回読込、
 * sudmonitor 日次過去データの取得）を担う[CoroutineWorker]の具現化クラス。
 *
 * 1. 取得した結果に応じて、次の自動更新実行日時の再スケジュールを[SettingsRepository]を介して調整・保存します。
 * 2. 各種イベント（成功、リトライ、ネットワーク圏外、パース例外等）の履歴レコードを[DebugLogRepository]を通じてデバッグログテーブルに永続化記録します。
 * 3. ネットワーク圏外状態やシミュレーションエラー設定（デバッグモード用）を遵守し、処理分岐を行います。
 */
@HiltWorker
class DamWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val debugLogRepository: DebugLogRepository,
    private val settingsRepository: SettingsRepository,
    private val damDataRepository: DamDataRepository,
    private val sudmonitorHistoryRepository: SudmonitorHistoryRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        /** ワーカーの処理タイプを指定するインプットデータキー */
        const val KEY_WORK_TYPE = "KEY_WORK_TYPE"
        
        /** エラー発生時のエラー文字列を格納するアウトプットデータキー */
        const val KEY_ERROR_MESSAGE = "KEY_ERROR_MESSAGE"

        /** 自動定期更新ワークのタイプ名（"AUTO"） */
        const val WORK_TYPE_AUTO = "AUTO"
        
        /** 手動即時更新ワークのタイプ名（"MANUAL"） */
        const val WORK_TYPE_MANUAL = "MANUAL"
        
        /** アプリ起動後の初回ロード更新ワークのタイプ名（"INITIAL"） */
        const val WORK_TYPE_INITIAL = "INITIAL"
        
        /** 端末再起動直後のブート更新ワークのタイプ名（"BOOT"） */
        const val WORK_TYPE_BOOT = "BOOT"

        /** sudmonitor 日次過去データ取得ワークのタイプ名（"SUDMONITOR_HISTORY"） */
        const val WORK_TYPE_SUDMONITOR_HISTORY = "SUDMONITOR_HISTORY"

        /** sudmonitor 日次過去データ取得ワークの取得トリガーを指定するインプットデータキー */
        const val KEY_SUDMONITOR_HISTORY_TRIGGER = "KEY_SUDMONITOR_HISTORY_TRIGGER"

        /**
         * sudmonitor 日次過去データ取得ワークのトリガー別タグ名を返します。
         *
         * [WorkInfo.tags] からトリガー種別を判別できるように、ワークタイプタグ
         * ([WORK_TYPE_SUDMONITOR_HISTORY]) に加えてトリガー別タグを付与するために使用します。
         * 初回起動（[SudmonitorHistoryTrigger.INITIAL]）とダム変更（[SudmonitorHistoryTrigger.TARGET_CHANGE]）は
         * リアルタイム側の「初回読込」と同等の初期ロード系として扱い、手動更新と区別します。
         *
         * @param trigger 取得トリガー
         * @return トリガー別タグ名
         */
        fun sudmonitorHistoryTriggerTag(trigger: SudmonitorHistoryTrigger): String =
            "SUDMONITOR_HISTORY_${trigger.name}"

        /** 定期自動更新失敗時の最大リトライ試行回数（3回までリトライ可能） */
        const val MAX_AUTO_RETRIES = 3
    }

    /**
     * バックグラウンドジョブの実行トリガーハンドラ。
     *
     * ワークタイプ（手動/自動/ブート/初回）を解析し、ネットワーク判定を行ってから[DamDataRepository.fetchLatestData]を起動します。
     * 結果（成功・失敗・リトライ）に応じたログの記録、スケジュール再計算、各種ステータス書き換えを行います。
     *
     * @return ジョブ実行成否を示す[Result] (success / failure / retry)
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val workType = inputData.getString(KEY_WORK_TYPE) ?: WORK_TYPE_AUTO

        if (workType == WORK_TYPE_SUDMONITOR_HISTORY) {
            return@withContext runSudmonitorHistoryWork()
        }

        
        val settings = settingsRepository.appSettingsFlow.first()
        
        val damNameEn = getDamConfig(settings.targetDamId).nameEn
        if (BuildConfig.DEBUG) {
            Log.d("DamWorker", "Executing fetch via WorkManager (type=$workType, dam=$damNameEn, attempt=${runAttemptCount + 1})...")
        }

        
        if (settings.debugModeEnabled && settings.debugSimulateMode == DebugSimulateMode.NETWORK_UNAVAILABLE) {
            return@withContext handleNetworkUnavailable(workType, simulated = true)
        }
        if (!settings.debugModeEnabled && !isNetworkAvailable()) {
            return@withContext handleNetworkUnavailable(workType, simulated = false)
        }

        val result = damDataRepository.fetchLatestData(
            recordLastFetchTimeMillis = shouldRecordLastFetchTimeMillis(workType, settings)
        )
        val dataSourceLabel = if (settings.realtimeDataSource == RealtimeDataSource.SUDMONITOR) {
            SUDMONITOR_HOST
        } else {
            "MLIT"
        }
        val workerResult = result.fold(
            ifLeft = { error ->
                when (workType) {
                    WORK_TYPE_AUTO -> handleAutoFailure(error)
                    else -> handleOneTimeFailure(workType, error)
                }
            },
            ifRight = { damData ->
                val fileTime = TimeUtils.parseToJstIso8601(damData.updatedAt, "yyyy/MM/dd HH:mm")
                    ?: damData.updatedAt
                val percentageTime = damData.storagePercentageTime?.let {
                    TimeUtils.parseToJstIso8601(it, "yyyy/MM/dd HH:mm") ?: it
                }
                when (workType) {
                    WORK_TYPE_AUTO -> {
                        val now = System.currentTimeMillis()
                        val settings = settingsRepository.appSettingsFlow.first()
                        val nextMillis = settings.calculateNextRunTimeAfterSuccess(now)
                        settingsRepository.updateSettings {
                            it.copy(
                                lastAutoUpdateMillis = now,
                                nextScheduledUpdateMillis = nextMillis,
                                isFirstRunAfterReschedule = false
                            )
                        }
                        val details = buildSuccessDetails(damNameEn, fileTime, percentageTime, dataSourceLabel)
                        debugLogRepository.addEntry("Auto update succeeded.", details)
                    }
                    WORK_TYPE_MANUAL -> debugLogRepository.addEntry(
                        "Manual update succeeded.",
                        buildSuccessDetails(damNameEn, fileTime, percentageTime, dataSourceLabel)
                    )
                    WORK_TYPE_INITIAL -> debugLogRepository.addEntry(
                        "Initial load succeeded.",
                        buildSuccessDetails(damNameEn, fileTime, percentageTime, dataSourceLabel)
                    )
                    WORK_TYPE_BOOT -> debugLogRepository.addEntry(
                        "Boot update succeeded.",
                        buildSuccessDetails(damNameEn, fileTime, percentageTime, dataSourceLabel)
                    )
                }
                Result.success()
            }
        )
        if (workType == WORK_TYPE_AUTO) {
            runSudmonitorHistoryAutoFetch()
        }
        workerResult
    }

    /**
     * sudmonitor 日次過去データの単発取得（初回起動 / ダム変更 / 手動）を実行します。
     *
     * ネットワーク事前チェックは既存のリアルタイム取得と同様に適用しますが、不可の場合は
     * ユーザーへの Snackbar を出さない方針のため、デバッグログのみ記録して静かに失敗とします
     * （既存 [handleNetworkUnavailable] は使用しません）。
     * 404（未蓄積）は [SudmonitorHistoryFetchResult.NotStored] として成功扱いで静かに完了します。
     * 失敗はデバッグログに記録し [Result.failure] とします。タグが
     * [WORK_TYPE_SUDMONITOR_HISTORY] のため、既存 one-time work 監視（MANUAL/INITIAL/BOOT タグ判別）
     * の Snackbar 対象には含まれません。
     *
     * @return ジョブ実行成否を示す[Result]
     */
    private suspend fun runSudmonitorHistoryWork(): Result {
        val triggerName = inputData.getString(KEY_SUDMONITOR_HISTORY_TRIGGER)
        val trigger = runCatching { SudmonitorHistoryTrigger.valueOf(triggerName.orEmpty()) }
            .getOrDefault(SudmonitorHistoryTrigger.INITIAL)

        val settings = settingsRepository.appSettingsFlow.first()
        if (settings.debugModeEnabled && settings.debugSimulateMode == DebugSimulateMode.NETWORK_UNAVAILABLE) {
            return handleSudmonitorHistoryNetworkUnavailable(simulated = true)
        }
        if (!settings.debugModeEnabled && !isNetworkAvailable()) {
            return handleSudmonitorHistoryNetworkUnavailable(simulated = false)
        }

        return sudmonitorHistoryRepository.fetchAndStore(
            damId = settings.targetDamId,
            damConfig = getDamConfig(settings.targetDamId),
            trigger = trigger
        ).fold(
            ifLeft = { error ->
                if (BuildConfig.DEBUG) {
                    Log.e(
                        "DamWorker",
                        "Sudmonitor history fetch failed (trigger=$trigger): ${error.message}"
                    )
                }
                debugLogRepository.addEntry(
                    "Sudmonitor history fetch failed.",
                    "Trigger: $trigger, Reason: ${error.message ?: "unknown error"}"
                )
                Result.failure()
            },
            ifRight = { fetchResult ->
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "DamWorker",
                        "Sudmonitor history fetch done (trigger=$trigger, result=${fetchResult.javaClass.simpleName})."
                    )
                }
                Result.success()
            }
        )
    }

    /**
     * ネットワーク圏外（または擬似的なオフライン）による sudmonitor 日次過去データ取得の静かな失敗処理。
     *
     * 日次過去データの失敗はユーザーへの Snackbar に表示しない方針のため、デバッグログと
     * [DebugLogRepository] への記録のみ行い、エラーメッセージを含めずに [Result.failure] を返します。
     *
     * @param simulated デバッグモードによる擬似的なオフライン設定である場合はtrue
     * @return 処理結果（常に失敗）
     */
    private suspend fun handleSudmonitorHistoryNetworkUnavailable(simulated: Boolean = false): Result {
        val reason = if (simulated) "Simulated network unavailable" else "Network unavailable"
        if (BuildConfig.DEBUG) {
            Log.w("DamWorker", "Sudmonitor history fetch skipped: $reason (attempt=${runAttemptCount + 1})")
        }
        debugLogRepository.addEntry("Sudmonitor history fetch skipped.", "Reason: $reason")
        return Result.failure()
    }

    /**
     * 自動更新（AUTO）実行後の sudmonitor 日次過去データの定期連動取得（D7）。
     *
     * realtime 取得の成否に関わらず呼び出され、[SudmonitorHistoryRepository.autoFetch] を実行します。
     * 失敗はログのみで握りつぶします（日次過去データの失敗はユーザーへ通知しない方針）。
     * ネットワーク不可で realtime 取得自体が行われなかった場合は呼び出されません（両者とも取得不能のため）。
     */
    private suspend fun runSudmonitorHistoryAutoFetch() {
        runCatching {
            val settings = settingsRepository.appSettingsFlow.first()
            sudmonitorHistoryRepository.autoFetch(
                interval = settings.autoUpdateInterval,
                now = System.currentTimeMillis(),
                damId = settings.targetDamId
            )
        }.onFailure { error ->
            if (BuildConfig.DEBUG) {
                Log.w("DamWorker", "Sudmonitor history auto fetch failed: ${error.message}")
            }
        }
    }

    private fun shouldRecordLastFetchTimeMillis(workType: String, settings: net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings): Boolean =
        !(workType == WORK_TYPE_MANUAL && settings.debugModeEnabled)

    
    /**
     * 定期自動更新ジョブ失敗時のエラー制御。
     *
     * 失敗ログを記録し、ポリシーに従ってリトライ（最大3回）または永続失敗とします。
     *
     * @param error 発生した例外
     * @return リトライ可能な場合は [Result.retry]、そうでない場合は [Result.failure]
     */
    private suspend fun handleAutoFailure(error: Throwable): Result {
        if (BuildConfig.DEBUG) {
            Log.e("DamWorker", "Auto update failed (attempt ${runAttemptCount + 1}/${MAX_AUTO_RETRIES + 1}): ${error.message}")
        }
        val reason = error.message ?: "unknown error"
        debugLogRepository.addEntry(
            "Auto update failed.",
            "Attempt: ${runAttemptCount + 1}/${MAX_AUTO_RETRIES + 1}, Reason: $reason"
        )
        return if (DamWorkerPolicy.shouldRetryAuto(runAttemptCount)) Result.retry() else Result.failure()
    }

    /**
     * 単発同期ジョブ失敗時のエラー制御。
     *
     * 失敗ログを記録し、対象ダムに応じたエラー表示文言を取得・構築し、失敗結果データに詰めて呼び出し元へ戻します。
     *
     * @param workType 単発処理タイプ（手動/ブート等）
     * @param error 発生した例外
     * @return 単発のエラーテキストを含んだ [Result.failure]
     */
    private suspend fun handleOneTimeFailure(workType: String, error: Throwable): Result {
        val label = DamWorkerPolicy.oneTimeFailureLabel(workType)
        if (BuildConfig.DEBUG) {
            Log.e("DamWorker", "$label failed: ${error.message}")
        }
        debugLogRepository.addEntry("$label failed.", "Reason: ${error.message ?: "unknown error"}")
        damDataRepository.setLoadStatus(DamLoadStatus.LOADING_FAILURE)

        val settings = settingsRepository.appSettingsFlow.first()
        val config = getDamConfig(settings.targetDamId)
        val damName = LocaleUtils.normalDamName(applicationContext, config)
        val isJa = LocaleUtils.isJapanese(applicationContext)
        val errorText = settings.getLoadingErrorText(isJa)
        val errorMsg = if (errorText.isNotEmpty()) "$damName $errorText".trim() else damName.trim()

        return Result.failure(workDataOf(KEY_ERROR_MESSAGE to errorMsg))
    }

    
    /**
     * ネットワーク圏外（または擬似的なオフライン）によるデータロード失敗時の処理。
     *
     * 自動定期ジョブ（AUTO）の場合はリトライ可能な範囲でリトライを促し、単発処理の場合はエラーメッセージを構築して失敗終了とします。
     *
     * @param workType ワーク処理タイプ（手動/自動等）
     * @param simulated デバッグモードによる擬似的なオフライン設定である場合はtrue
     * @return 処理結果に応じた [Result]
     */
    private suspend fun handleNetworkUnavailable(workType: String, simulated: Boolean = false): Result {
        val attempt = runAttemptCount + 1
        val reason = if (simulated) "Simulated network unavailable" else "Network unavailable"
        if (BuildConfig.DEBUG) {
            Log.w("DamWorker", "$reason (type=$workType, attempt=$attempt)")
        }

        return when (workType) {
            WORK_TYPE_AUTO -> {
                debugLogRepository.addEntry(
                    "Auto update failed.",
                    "Retry: $runAttemptCount/$MAX_AUTO_RETRIES, Reason: $reason"
                )
                if (DamWorkerPolicy.shouldRetryAuto(runAttemptCount)) Result.retry() else Result.failure()
            }
            else -> {
                val label = DamWorkerPolicy.oneTimeFailureLabel(workType)
                debugLogRepository.addEntry("$label failed.", "Reason: $reason")

                val settings = settingsRepository.appSettingsFlow.first()
                val config = getDamConfig(settings.targetDamId)
                val damName = LocaleUtils.normalDamName(applicationContext, config)
                val isJa2 = LocaleUtils.isJapanese(applicationContext)
                val networkUnavailableText = settings.getNetworkUnavailableText(isJa2)
                val errorMsg = if (networkUnavailableText.isNotEmpty()) {
                    "$damName $networkUnavailableText".trim()
                } else {
                    damName.trim()
                }
                
                damDataRepository.setLoadStatus(DamLoadStatus.NETWORK_UNAVAILABLE)
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to errorMsg))
            }
        }
    }

    
    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    
    /**
     * 成功ログの詳細文字列を構築します。
     *
     * @param damNameEn ダム名（英語表記）
     * @param dataTime 観測データ時刻
     * @param storageTime 貯水率のデータ時刻（nullの場合は省略）
     * @param dataSourceLabel データソース表示ラベル（sudmonitorホスト名または"MLIT"）
     * @return デバッグログへ記録する詳細文字列
     */
    private fun buildSuccessDetails(
        damNameEn: String,
        dataTime: String,
        storageTime: String?,
        dataSourceLabel: String
    ): String = buildString {
        append("Data Source: $dataSourceLabel, Dam name: $damNameEn, Data time: $dataTime")
        if (storageTime != null) append(", Data time (Storage): $storageTime")
    }
}
