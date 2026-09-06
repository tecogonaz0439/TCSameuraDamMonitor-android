// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateScheduler
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionKey
import net.tecogonaz.tcsameuradammonitor.domain.model.MainCardExpansionState
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonMetric
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.model.getDamConfig
import net.tecogonaz.tcsameuradammonitor.domain.usecase.QueryAvailableAppsUseCase
import net.tecogonaz.tcsameuradammonitor.data.source.remote.NetworkAvailability
import net.tecogonaz.tcsameuradammonitor.util.AppNotificationManager
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.worker.DamWorker
import net.tecogonaz.tcsameuradammonitor.worker.DamWorkManagerGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import net.tecogonaz.tcsameuradammonitor.domain.model.isAllObservationDataInvalid
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import kotlin.random.Random

/**
 * メイン画面（MainScreen）のUI状態を表すデータクラスです。
 *
 * リアルタイムデータや過去の観測データの取得状態、各種表示用データ、エラーメッセージなどを一元管理します。
 *
 * @property isAutoUpdateRunning バックグラウンドでの自動更新が実行中であるかどうかのフラグ。
 * @property isManualUpdateRunning 手動更新が実行中であるかどうかのフラグ。
 * @property isInitialLoadRunning アプリ起動時の初回読込が実行中であるかどうかのフラグ。
 * @property isBootUpdateRunning 端末起動時のブート更新が実行中であるかどうかのフラグ。
 * @property isBootWorkPending 端末起動時のデータ取得タスクが保留中であるかどうかのフラグ。
 * @property damConfig 現在選択されているダムの設定情報（[DamConfig]）。
 * @property damData 取得した最新の観測データ（[DamData]）。貯水量、流入量、放流量、流域平均雨量、貯水率メッセージなどを含みます。
 * @property damLoadStatus リアルタイムデータのロード状態（[DamLoadStatus]）。
 * @property errorMessage リアルタイムデータ取得時のエラーメッセージ。
 * @property availableApps リンク連携可能な外部地図アプリやブラウザアプリ（[AppLinkInfo]）のリスト。
 * @property showInitialAutoUpdateDialog 初回起動時に自動更新の設定を促すダイアログを表示するかどうかのフラグ。
 * @property isHistoricalMode 現在「過去データモード」で表示しているかどうかのフラグ（falseの場合は「リアルタイムモード」）。
 * @property historicalDamConfig 過去データモードで表示対象となっているダムの設定情報。
 * @property historicalDamMeta 過去データモードで選択中の過去データ検索結果のメタ情報（[HistoricalSearchMeta]）。
 * @property historicalAllData 選択された過去データ検索結果に含まれるすべての履歴データリスト。
 * @property historicalVisibleData 絞り込まれて現在テーブルやグラフに表示可能な履歴データリスト。
 * @property historicalDisplayData 履歴一覧で段階的に読み込まれ（ページングされ）現在レンダリング対象となっている履歴データリスト。
 * @property historicalDisplayFromMillis 履歴一覧で表示中の最古の日時のタイムスタンプ（ミリ秒）。
 * @property historicalVisibleFromMillis 期間選択フィルターによって絞り込まれた表示開始日時のタイムスタンプ（ミリ秒）。
 * @property historicalVisibleToMillis 期間選択フィルターによって絞り込まれた表示終了日時のタイムスタンプ（ミリ秒）。
 * @property isHistoricalDisplayRangeFiltered 過去データ表示において、日付選択（期間選択フィルター）が適用されているかどうかのフラグ。
 * @property historicalDisplayStartDate 期間選択フィルターの開始日テキスト（yyyy/MM/dd）。
 * @property historicalDisplayEndDate 期間選択フィルターの終了日テキスト（yyyy/MM/dd）。
 * @property isAllHistoricalLoaded すべての過去データが読み込み完了したかどうかのフラグ。
 * @property isHistoricalSearchLoading 過去データの検索（ダウンロード）処理が実行中であるかどうかのフラグ。
 * @property isHistoricalLoadingMore 過去データ一覧での追加読み込み（スクロールによるページング）が実行中であるかどうかのフラグ。
 * @property historicalErrorMessage 過去データ処理におけるエラーメッセージ。
 * @property historicalMetaList メイン画面のドロワーに表示する、保存済みの過去データ検索メタ情報のリスト。
 * @property allHistoricalMetaList システムで保持するすべての過去データ検索メタ情報のリスト。
 * @property historicalComparisonStates 過去比較グラフのメトリックごとの読み込み状態・データ・エラー（[HistoricalComparisonMetricState]）のマップ。
 * @property isSudmonitorHistoryMode 現在「過去データ(日次)モード」で表示しているかどうかのフラグ（falseの場合は「リアルタイムモード」）。
 * @property sudmonitorHistory sudmonitor 日次過去データの保存行（[SudmonitorHistory]）。
 * @property sudmonitorHistoryDamConfig 過去データ(日次)モードで表示対象となっているダムの設定情報（保存行が未保存の間も対象ダムの設定を保持する）。
 * @property sudmonitorHistoryAllData sudmonitor 日次過去データの全観測行リスト（時刻昇順）。
 * @property sudmonitorHistoryVisibleData 期間絞り込み後・画面描画用の観測行リスト。
 * @property sudmonitorHistoryDisplayData 過去データ(日次)一覧で段階的に読み込まれ（ページングされ）現在レンダリング対象となっている観測行リスト。
 * @property sudmonitorHistoryDisplayFromMillis 過去データ(日次)一覧で表示中の最古の日時のタイムスタンプ（ミリ秒）。
 * @property sudmonitorHistoryVisibleFromMillis 期間絞り込みによって指定された表示開始日時のタイムスタンプ（ミリ秒）。
 * @property sudmonitorHistoryVisibleToMillis 期間絞り込みによって指定された表示終了日時のタイムスタンプ（ミリ秒）。
 * @property isSudmonitorHistoryRangeFiltered 過去データ(日次)表示において、日付選択（期間絞り込み）が適用されているかどうかのフラグ。
 * @property sudmonitorHistoryDisplayStartDate 期間絞り込みの開始日テキスト（yyyy/MM/dd）。
 * @property sudmonitorHistoryDisplayEndDate 期間絞り込みの終了日テキスト（yyyy/MM/dd）。
 * @property isSudmonitorHistoryUpdateRunning 過去データ(日次)の取得（初回 / ダム変更 / 手動）が実行中であるかどうかのフラグ。
 * @property isSudmonitorHistoryInitialRunning 過去データ(日次)の初回読込系（初回起動 / ダム変更）が実行中であるかどうかのフラグ。
 * @property isSudmonitorHistoryManualRunning 過去データ(日次)の手動更新が実行中であるかどうかのフラグ。
 */
data class MainUiState(
    val isAutoUpdateRunning: Boolean = false,
    val isManualUpdateRunning: Boolean = false,
    val isInitialLoadRunning: Boolean = false,
    val isBootUpdateRunning: Boolean = false,
    
    val isBootWorkPending: Boolean = false,
    val damConfig: DamConfig? = null,
    val damData: DamData? = null,
    val damLoadStatus: DamLoadStatus = DamLoadStatus.INITIAL,
    val errorMessage: String? = null,
    
    val availableApps: List<AppLinkInfo> = emptyList(),
    
    val showInitialAutoUpdateDialog: Boolean = false,
    
    val showInitialNotificationPermissionRequest: Boolean = false,
    
    val isHistoricalMode: Boolean = false,
    
    val historicalDamConfig: DamConfig? = null,
    
    val historicalDamMeta: HistoricalSearchMeta? = null,
    
    val historicalAllData: List<DamHistoricalData> = emptyList(),
    
    val historicalVisibleData: List<DamHistoricalData> = emptyList(),
    
    val historicalDisplayData: List<DamHistoricalData> = emptyList(),
    
    val historicalDisplayFromMillis: Long = Long.MAX_VALUE,
    
    val historicalVisibleFromMillis: Long = 0L,
    
    val historicalVisibleToMillis: Long = 0L,
    
    val isHistoricalDisplayRangeFiltered: Boolean = false,
    
    val historicalDisplayStartDate: String? = null,
    
    val historicalDisplayEndDate: String? = null,
    
    val isAllHistoricalLoaded: Boolean = false,
    
    val isHistoricalSearchLoading: Boolean = false,
    
    val isHistoricalLoadingMore: Boolean = false,
    
    val historicalErrorMessage: String? = null,
    
    val historicalMetaList: List<HistoricalSearchMeta> = emptyList(),
    
    val allHistoricalMetaList: List<HistoricalSearchMeta> = emptyList(),
    
    val historicalComparisonStates: Map<HistoricalComparisonMetric, HistoricalComparisonMetricState> = emptyMap(),

    val isSudmonitorHistoryMode: Boolean = false,

    val sudmonitorHistory: SudmonitorHistory? = null,

    val sudmonitorHistoryDamConfig: DamConfig? = null,

    val sudmonitorHistoryAllData: List<DamHistoricalData> = emptyList(),

    val sudmonitorHistoryVisibleData: List<DamHistoricalData> = emptyList(),

    val sudmonitorHistoryDisplayData: List<DamHistoricalData> = emptyList(),

    val sudmonitorHistoryDisplayFromMillis: Long = Long.MAX_VALUE,

    val sudmonitorHistoryVisibleFromMillis: Long = 0L,

    val sudmonitorHistoryVisibleToMillis: Long = 0L,

    val isSudmonitorHistoryRangeFiltered: Boolean = false,

    val sudmonitorHistoryDisplayStartDate: String? = null,

    val sudmonitorHistoryDisplayEndDate: String? = null,

    val isSudmonitorHistoryUpdateRunning: Boolean = false,

    val isSudmonitorHistoryInitialRunning: Boolean = false,

    val isSudmonitorHistoryManualRunning: Boolean = false,
) {

    /**
     * 現在の表示モードに応じて表示対象となるダム設定を返します。
     *
     * 過去データ(日次)表示では [sudmonitorHistoryDamConfig]、過去データ検索結果の表示では
     * [historicalDamConfig]、リアルタイム表示では [damConfig] を解決します。
     * 観測データ(グラフ)Card の年比較チップ表示可否など、モードごとに表示ダムが異なる判定は
     * 本プロパティを正としてください。
     */
    val displayedDamConfig: DamConfig?
        get() = when {
            isSudmonitorHistoryMode -> sudmonitorHistoryDamConfig
            isHistoricalMode -> historicalDamConfig
            else -> damConfig
        }
}

/**
 * 過去比較グラフの読み込み状態を表す列挙型。
 *
 * @property IDLE 未読み込み。
 * @property LOADING 読み込み中。
 * @property READY 読み込み成功（データ取得済み）。
 * @property ERROR 読み込み失敗。
 */
enum class HistoricalComparisonLoadState {
    IDLE,
    LOADING,
    READY,
    ERROR,
}

/**
 * 過去比較グラフの単一メトリック（貯水率・貯水量）の読み込み状態を表すデータクラス。
 *
 * @property loadState 読み込み状態（[HistoricalComparisonLoadState]）。
 * @property data 読み込みに成功した過去比較データ（[HistoricalComparisonData]）。
 * @property error 読み込みに失敗した場合のエラー。
 * @property windowStartMillis 比較期間の開始エポックミリ秒（含む）。
 * @property windowEndMillis 比較期間の終了エポックミリ秒（含む）。
 * @property mainYear 読込要求時に指定された主系列年（日次データ更新時の再読込に使用する）。
 */
data class HistoricalComparisonMetricState(
    val loadState: HistoricalComparisonLoadState = HistoricalComparisonLoadState.IDLE,
    val data: HistoricalComparisonData? = null,
    val error: Throwable? = null,
    val windowStartMillis: Long = 0L,
    val windowEndMillis: Long = 0L,
    val mainYear: Int? = null,
)

private data class HistoricalDisplayState(
    val displayData: List<DamHistoricalData>,
    val displayFromMillis: Long,
    val isAllLoaded: Boolean
)

private data class SudmonitorHistoryDisplayState(
    val displayData: List<DamHistoricalData>,
    val displayFromMillis: Long,
    val isAllLoaded: Boolean
)

/**
 * メイン画面（MainScreen）のビジネスロジックおよびUI状態を管理する [ViewModel] です。
 *
 * リアルタイムのダム諸量データ（貯水率、貯水量、流入量、放流量、流域平均雨量など）の取得、
 * 自動更新や手動更新のトリガーおよびクールダウン制御、
 * 過去データ（履歴データ）の検索・保存・日付選択フィルター、
 * 外部地図アプリ連携などのステートフローを提供します。
 *
 * @property context アプリケーションコンテキスト。
 * @property settingsRepository アプリ設定リポジトリ。
 * @property damDataRepository ダム観測データリポジトリ。
 * @property debugLogRepository デバッグログリポジトリ。
 * @property historicalSearchRepository 過去データ検索結果リポジトリ。
 * @property historicalComparisonRepository 過去比較グラフ用データリポジトリ。
 * @property appNotificationManager 通知マネージャー。
 * @property queryAvailableAppsUseCase リンクを開くための対応アプリ一覧クエリユースケース。
 * @property damWorkManagerGateway WorkManagerと連携して自動更新などのジョブを実行するためのゲートウェイ。
 * @property networkAvailability ネットワーク接続状況の確認用クラス。
 * @property sudmonitorHistoryRepository sudmonitor 日次過去データの取得・保存・読出しリポジトリ。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val damDataRepository: DamDataRepository,
    private val debugLogRepository: DebugLogRepository,
    private val historicalSearchRepository: HistoricalSearchRepository,
    private val historicalComparisonRepository: HistoricalComparisonRepository,
    private val appNotificationManager: AppNotificationManager,
    private val queryAvailableAppsUseCase: QueryAvailableAppsUseCase,
    private val damWorkManagerGateway: DamWorkManagerGateway,
    private val networkAvailability: NetworkAvailability,
    private val sudmonitorHistoryRepository: SudmonitorHistoryRepository
) : ViewModel() {

    companion object {
        
        const val REFRESH_COOLDOWN_MINUTES = 10
        
        private const val HISTORICAL_INITIAL_HOURS = 6
        
        const val HISTORICAL_SEARCH_MAX_STORED_COUNT = HistoricalSearchRepository.MAX_STORED_META_COUNT

        private const val MAX_PINNED_META_COUNT = 5
        
        private const val DATE_FORMAT_YYYYMMDD = "yyyyMMdd"
        
        private const val JST_TIMEZONE_ID = "Asia/Tokyo"

        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }

    
    val appSettings: StateFlow<AppSettings?> = settingsRepository.appSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val mainCardExpansionState: StateFlow<MainCardExpansionState?> =
        settingsRepository.mainCardExpansionStateFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    
    private val _snackbarChannel = Channel<String>(Channel.CONFLATED)
    val snackbarMessage: Flow<String> = _snackbarChannel.receiveAsFlow()

    private val _availableApps = MutableStateFlow<List<AppLinkInfo>>(emptyList())

    
    private var currentHistoricalMetaId: Long = 0L

    private var transientViewedMetaId: Long? = null

    private val comparisonJobs = mutableMapOf<HistoricalComparisonMetric, Job>()
    private val comparisonGenerations = mutableMapOf<HistoricalComparisonMetric, Long>()

    
    fun emitSnackbarMessage(message: String) {
        _snackbarChannel.trySend(message)
    }

    /**
     * 指定したメイン画面Cardの展開状態を保存済みの現在値から反転する。
     */
    fun toggleMainCardExpansion(key: MainCardExpansionKey) {
        viewModelScope.launch {
            settingsRepository.toggleMainCardExpansion(key)
        }
    }
    
    fun updateLastLoadResultMessage(message: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(lastLoadResultMessage = message) }
        }
    }

    val lastFetchTimeMillis: Long
        get() = damDataRepository.getLastFetchTimeMillis()

    init {
        initBootAndDialog()
        initLoadStatusObservation()
        initWorkObservation()
        initSudmonitorHistoryObservation()
        initAvailableAppsState()
        initHistoricalMetaList()
    }

    private fun initBootAndDialog() {
        viewModelScope.launch {
            val appSettings = settingsRepository.appSettingsFlow.first()
            showInitialAutoUpdateDialogIfNeeded(appSettings)
            initAutoUpdateCustomTimings(appSettings)
            val isFirstFetchAfterBoot = initBootAndInitialLoad(appSettings)
            initDamConfigObservation()
            initDataObservation(isFirstFetchAfterBoot)
        }
    }

    private fun initLoadStatusObservation() {
        viewModelScope.launch {
            damDataRepository.loadStatus.collect { status ->
                _uiState.update { it.copy(damLoadStatus = status) }
            }
        }
    }

    private fun initWorkObservation() {
        initAutoWorkObservation()
        initOneTimeWorkObservation()
        initBootWorkObservation()
        initSudmonitorHistoryWorkObservation()
    }

    private fun initAutoWorkObservation() {
        viewModelScope.launch {
            damWorkManagerGateway
                .autoWorkInfosFlow()
                .collect { workInfos ->
                    val running = workInfos.any { it.state == WorkInfo.State.RUNNING }
                    _uiState.update { it.copy(isAutoUpdateRunning = running) }
                }
        }
    }

    private fun initOneTimeWorkObservation() {
        viewModelScope.launch {
            
            var initialized = false
            var previousState: WorkInfo.State? = null
            damWorkManagerGateway
                .oneTimeWorkInfosFlow()
                .collect { workInfos ->
                    val current = workInfos.firstOrNull()
                    val currentState = current?.state

                    if (!initialized) {
                        
                        previousState = currentState
                        initialized = true
                    } else {
                        
                        
                        if (currentState == WorkInfo.State.FAILED && previousState != WorkInfo.State.FAILED) {
                            val errorMsg = current.outputData.getString(DamWorker.KEY_ERROR_MESSAGE)
                            if (!errorMsg.isNullOrEmpty()) {
                                emitSnackbarMessage(errorMsg)
                                updateLastLoadResultMessage(errorMsg)
                            }
                        }
                        previousState = currentState
                    }

                    val isRunning = currentState == WorkInfo.State.RUNNING
                    val isManual = isRunning && current?.tags?.contains(DamWorker.WORK_TYPE_MANUAL) == true
                    val isInitial = isRunning && current?.tags?.contains(DamWorker.WORK_TYPE_INITIAL) == true
                    _uiState.update { it.copy(
                        isManualUpdateRunning = isManual,
                        isInitialLoadRunning = isInitial
                    ) }
                }
        }
    }

    private fun initBootWorkObservation() {
        viewModelScope.launch {
            damWorkManagerGateway
                .bootWorkInfosFlow()
                .collect { workInfos ->
                    val running = workInfos.any { it.state == WorkInfo.State.RUNNING }
                    _uiState.update { it.copy(isBootUpdateRunning = running) }
                }
        }
    }

    private fun initSudmonitorHistoryWorkObservation() {
        viewModelScope.launch {
            damWorkManagerGateway
                .sudmonitorHistoryWorkInfosFlow()
                .collect { workInfos ->
                    // ワークタイプタグでトリガー種別を判別する。
                    // 初回読込系（INITIAL / TARGET_CHANGE）はリアルタイム側の「初回読込」と同等に
                    // 自動更新アイコンの回転へ、MANUAL は手動更新アイコンの回転へ反映する
                    val running = workInfos.any { it.state == WorkInfo.State.RUNNING }
                    val initialRunning = workInfos.any { info ->
                        info.state == WorkInfo.State.RUNNING &&
                            (DamWorker.sudmonitorHistoryTriggerTag(SudmonitorHistoryTrigger.INITIAL) in info.tags ||
                                DamWorker.sudmonitorHistoryTriggerTag(SudmonitorHistoryTrigger.TARGET_CHANGE) in info.tags)
                    }
                    val manualRunning = workInfos.any { info ->
                        info.state == WorkInfo.State.RUNNING &&
                            DamWorker.sudmonitorHistoryTriggerTag(SudmonitorHistoryTrigger.MANUAL) in info.tags
                    }
                    _uiState.update {
                        it.copy(
                            isSudmonitorHistoryUpdateRunning = running,
                            isSudmonitorHistoryInitialRunning = initialRunning,
                            isSudmonitorHistoryManualRunning = manualRunning
                        )
                    }
                }
        }
    }

    /**
     * 対象ダムと機能ゲート（過去データ取得元が sudmonitor）に応じて、日次過去データの保存行
     * （Room Flow）をリアクティブに監視し、手動・自動更新やデバッグセッションの待避・復元を
     * 表示状態へ自動反映します。
     *
     * - 保存行の更新（書込）ごとに、保存行状態（サイドバー常設エントリ・サマリーCard）を更新します。
     * - 保存行の内容変化時は比較グラフを再読込し、過去データ(日次)モード表示中なら表示データも再読込します。
     * - 保存行が null になった場合（行削除など）は、機能ゲートが有効な間は日次モードを維持したまま
     *   表示状態だけをクリアします。機能ゲート無効化時はモードを離脱して状態を全クリアします。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun initSudmonitorHistoryObservation() {
        viewModelScope.launch {
            var previous: SudmonitorHistory? = null
            settingsRepository.appSettingsFlow
                .map { settings -> settings.targetDamId to settings.historicalDataSource }
                .distinctUntilChanged()
                .flatMapLatest { (damId, historicalDataSource) ->
                    // emit値と一緒に監視開始時点のゲート状態・対象ダムIDへ渡し、null emit時の
                    // ゲート判定と表示対象ダム設定の再構築に使う
                    val gateEnabled = historicalDataSource == RealtimeDataSource.SUDMONITOR
                    val historyFlow = if (gateEnabled) {
                        sudmonitorHistoryRepository.historyFlow(damId)
                    } else {
                        flowOf(null)
                    }
                    historyFlow.map { history -> gateEnabled to damId to history }
                }
                .collect { (gateAndDam, history) ->
                    val (gateEnabled, damId) = gateAndDam
                    applySudmonitorHistoryEmission(previous, gateEnabled, damId, history)
                    previous = history
                }
        }
    }

    /**
     * 日次過去データ保存行のRoom Flow emitを表示状態へ適用します。
     *
     * - 機能ゲート無効時の null emit はモード離脱+状態全クリア（既存挙動）。
     * - 機能ゲート有効時の null emit（行未保存・初回取得前など）は日次モードを維持し、
     *   履歴由来の表示状態のみをクリアして表示対象ダム設定（[MainUiState.sudmonitorHistoryDamConfig]）
     *   を現在の対象ダムから再構築する（サイドバーにダム名のみの常設エントリを出し続けるため）。
     * - 保存行変化時は状態更新・表示再読込・比較グラフ再読込を行う。
     *
     * @param previous 前回emitされた保存行（初回は null）
     * @param isGateEnabled 今回の監視開始時点での機能ゲート（過去データ取得元が sudmonitor）の有効性
     * @param targetDamId 今回の監視対象ダムの観測所ID
     * @param history 今回emitされた保存行（未保存・機能ゲート無効時は null）
     */
    private suspend fun applySudmonitorHistoryEmission(
        previous: SudmonitorHistory?,
        isGateEnabled: Boolean,
        targetDamId: String,
        history: SudmonitorHistory?
    ) {
        if (history == null) {
            if (!isGateEnabled) {
                // 機能ゲート無効化時は従来どおり日次モードを離脱して全状態をクリアする
                if (_uiState.value.isSudmonitorHistoryMode) {
                    switchToRealtimeMode()
                }
                clearSudmonitorHistoryData()
                return
            }
            // ゲート有効かつ行が無い場合（初回起動で未取得・Debug復元で空バックアップ等）は
            // 日次モードを維持し、履歴由来の表示状態だけをクリアする。
            // 表示対象ダム設定は現在の対象ダムから再構築するため、空状態のままダム切替にも追従する。
            clearSudmonitorHistoryDisplayState()
            _uiState.update { state ->
                state.copy(
                    sudmonitorHistory = null,
                    sudmonitorHistoryDamConfig = getDamConfig(targetDamId)
                )
            }
            return
        }
        if (previous == history) return

        val damChanged = previous != null && previous.damId != history.damId
        if (damChanged) {
            if (_uiState.value.isSudmonitorHistoryMode) {
                switchToRealtimeMode()
            }
            clearSudmonitorHistoryData()
        }

        _uiState.update { it.copy(sudmonitorHistory = history) }

        if (_uiState.value.isSudmonitorHistoryMode) {
            reloadSudmonitorHistoryDisplay(history)
        }
        refreshLoadedHistoricalComparisons()
    }

    private fun initAvailableAppsState() {
        viewModelScope.launch {
            combine(
                _uiState,
                _availableApps
            ) { _, apps ->
                apps
            }.distinctUntilChanged()
                .collect { apps ->
                    _uiState.update { it.copy(availableApps = apps) }
                }
        }
    }

    private fun initHistoricalMetaList() {
        viewModelScope.launch {
            refreshAllMetaList()
        }
    }

    private fun showInitialAutoUpdateDialogIfNeeded(appSettings: AppSettings) {
        if (!appSettings.initialAutoUpdateDialogShown) {
            _uiState.update { it.copy(showInitialAutoUpdateDialog = true) }
        }
    }

    private suspend fun initAutoUpdateCustomTimings(appSettings: AppSettings) {
        val now = System.currentTimeMillis()
        val needsAInit = appSettings.autoUpdateCustomTimingMillisWeekly == 0L ||
            appSettings.autoUpdateCustomTimingMillisDaily == 0L ||
            appSettings.autoUpdateCustomTimingMillis12Hours == 0L ||
            appSettings.autoUpdateCustomTimingMillisHourly == 0L
        if (!needsAInit) return

        val randomMinuteOfDay = Random.nextInt(
            AppSettings.INITIAL_AUTO_UPDATE_RANDOM_START_HOUR * 60 + AppSettings.INITIAL_AUTO_UPDATE_RANDOM_START_MINUTE,
            AppSettings.INITIAL_AUTO_UPDATE_RANDOM_END_HOUR * 60 + AppSettings.INITIAL_AUTO_UPDATE_RANDOM_END_MINUTE + 1
        )
        val randomHour = randomMinuteOfDay / 60
        val randomMinute = randomMinuteOfDay % 60

        settingsRepository.updateSettings { settings ->
            settings.copy(
                autoUpdateCustomTimingMillisWeekly = if (settings.autoUpdateCustomTimingMillisWeekly == 0L) {
                    settings.copy(autoUpdateInterval = AutoUpdateInterval.ONE_WEEK)
                        .calculateInitialCustomTiming(now, randomHour, randomMinute)
                } else {
                    settings.autoUpdateCustomTimingMillisWeekly
                },
                autoUpdateCustomTimingMillisDaily = if (settings.autoUpdateCustomTimingMillisDaily == 0L) {
                    settings.copy(autoUpdateInterval = AutoUpdateInterval.ONE_DAY)
                        .calculateInitialCustomTiming(now, randomHour, randomMinute)
                } else {
                    settings.autoUpdateCustomTimingMillisDaily
                },
                autoUpdateCustomTimingMillis12Hours = if (settings.autoUpdateCustomTimingMillis12Hours == 0L) {
                    settings.copy(autoUpdateInterval = AutoUpdateInterval.TWELVE_HOURS)
                        .calculateInitialCustomTiming(now, randomHour, randomMinute)
                } else {
                    settings.autoUpdateCustomTimingMillis12Hours
                },
                autoUpdateCustomTimingMillisHourly = if (settings.autoUpdateCustomTimingMillisHourly == 0L) {
                    settings.copy(autoUpdateInterval = AutoUpdateInterval.ONE_HOUR)
                        .calculateInitialCustomTiming(now, randomHour, randomMinute)
                } else {
                    settings.autoUpdateCustomTimingMillisHourly
                }
            )
        }
    }

    private suspend fun initBootAndInitialLoad(appSettings: AppSettings): Boolean {
        val currentBootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val isNewBoot = Math.abs(currentBootTime - appSettings.lastBootTime) > AppSettings.BOOT_TIME_TOLERANCE_MS
        if (isNewBoot) {
            settingsRepository.updateSettings { it.resetDebugSettings().copy(lastBootTime = currentBootTime) }
        }

        // sudmonitor 日次過去データの初回取得: 機能ゲート有効かつ対象ダムの行が未保存の場合のみ enqueue
        // （起動をブロックしない。失敗は許容。既存 realtime の INITIAL とは別のユニークワーク名を使用）。
        if (appSettings.historicalDataSource == RealtimeDataSource.SUDMONITOR) {
            val historyRow = runCatching {
                sudmonitorHistoryRepository.findByDamId(appSettings.targetDamId)
            }.getOrNull()
            if (historyRow == null) {
                damWorkManagerGateway.enqueueSudmonitorHistoryWork(SudmonitorHistoryTrigger.INITIAL)
            }
        }

        val hasData = damDataRepository.damDataFlow.first() != null
        if (hasData) return false

        
        damWorkManagerGateway.enqueueOneTimeWork(DamWorker.WORK_TYPE_INITIAL)
        return isNewBoot
    }

    private fun initDamConfigObservation() {
        viewModelScope.launch {
            var prevDamId: String? = null
            settingsRepository.appSettingsFlow.collect { settings ->
                val config = getDamConfig(settings.targetDamId)
                _uiState.update { it.copy(damConfig = config) }
                if (prevDamId != null && prevDamId != settings.targetDamId) {
                    switchToRealtimeMode()
                    clearHistoricalData()
                    clearHistoricalComparison()
                }
                prevDamId = settings.targetDamId
            }
        }
    }

    private suspend fun initDataObservation(isFirstFetchAfterBoot: Boolean) {
        var showSnackbarForFirstBootFetch = isFirstFetchAfterBoot
        var previousPercentTime: String? = null
        damDataRepository.damDataFlow.collect { data ->
            val loadStatus = damDataRepository.loadStatus.value

            
            if (loadStatus == DamLoadStatus.NETWORK_UNAVAILABLE) {
                val settings = settingsRepository.appSettingsFlow.first()
                val config = getDamConfig(settings.targetDamId)
                val damName = config?.let { LocaleUtils.normalDamName(context, it) } ?: ""
                val isJa = LocaleUtils.isJapanese(context)
                val text = settings.getNetworkUnavailableText(isJa)
                if (text.isNotEmpty()) {
                    val msg = "$damName $text".trim()
                    emitSnackbarMessage(msg)
                    updateLastLoadResultMessage(msg)
                }
                if (data != null) {
                    previousPercentTime = data.storagePercentageTime ?: data.updatedAt
                    _uiState.update { it.copy(damData = data, errorMessage = null) }
                }
                return@collect
            }

            if (data == null) {
                previousPercentTime = null
                _uiState.update { it.copy(damData = null) }
                return@collect
            }

            val currentPercentTime = data.storagePercentageTime ?: data.updatedAt
            val settings = settingsRepository.appSettingsFlow.first()
            val config = getDamConfig(settings.targetDamId)
            val damName = config?.let { LocaleUtils.normalDamName(context, it) } ?: ""
            val isJa = LocaleUtils.isJapanese(context)
            val isAllInvalid = data.isAllObservationDataInvalid()
            val wasLastAllInvalid = settings.wasLastDataAllInvalid

            
            if (isAllInvalid != wasLastAllInvalid) {
                settingsRepository.updateSettings { it.copy(wasLastDataAllInvalid = isAllInvalid) }
            }

            if (isAllInvalid) {
                
                val snackbarMsg = if (wasLastAllInvalid) {
                    
                    settings.getStateText(
                        null,
                        isJa,
                        isAllDataInvalid = true,
                        isSameura = settings.targetDamId == AppSettings.DEFAULT_DAM_ID
                    )
                } else {
                    
                    settings.getDataDistributionStoppedText(isJa)
                }
                if (snackbarMsg.isNotEmpty()) {
                    val msg = "$damName $snackbarMsg".trim()
                    emitSnackbarMessage(msg)
                    updateLastLoadResultMessage(msg)
                }
            } else {
                
                if (wasLastAllInvalid) {
                    
                    val resumedText = settings.getDataDistributionResumedText(isJa)
                    if (resumedText.isNotEmpty()) {
                        emitSnackbarMessage("$damName $resumedText".trim())
                    }
                    
                    
                    val pct = data.storagePercentage
                    if (pct != null) {
                        val rateMsg = appNotificationManager.formatSnackbarMessage(data, settings)
                        if (rateMsg != null) {
                            emitSnackbarMessage(rateMsg)
                            updateLastLoadResultMessage(rateMsg)
                        }
                    }
                } else {
                    
                    val message = appNotificationManager.formatSnackbarMessage(data, settings)
                    if (message != null) {
                        updateLastLoadResultMessage(message)
                        val isTimeChanged = previousPercentTime != null && previousPercentTime != currentPercentTime
                        if (isTimeChanged || (showSnackbarForFirstBootFetch && previousPercentTime != null)) {
                            showSnackbarForFirstBootFetch = false
                            emitSnackbarMessage(message)
                        }
                    }
                }
            }

            previousPercentTime = currentPercentTime
            _uiState.update {
                it.copy(
                    damData = data,
                    errorMessage = null
                )
            }
        }
    }

    
    fun queryAvailableApps(url: String) {
        viewModelScope.launch {
            _availableApps.value = queryAvailableAppsUseCase(url)
        }
    }

    
    fun clearAvailableApps() {
        _availableApps.value = emptyList()
    }

    
    fun showAutoUpdateStatus() {
        viewModelScope.launch {
            if (_uiState.value.isAutoUpdateRunning) {
                emitSnackbarMessage(context.getString(R.string.main_autorenew_in_progress))
                return@launch
            }
            val settings = settingsRepository.appSettingsFlow.first()
            if (!settings.autoUpdateEnabled) {
                emitSnackbarMessage(context.getString(R.string.main_autorenew_disabled))
                return@launch
            }
            val intervalLabel = context.getString(
                when (settings.autoUpdateInterval) {
                    AutoUpdateInterval.ONE_WEEK -> R.string.settings_general_auto_update_interval_one_week
                    AutoUpdateInterval.ONE_DAY -> R.string.settings_general_auto_update_interval_one_day
                    AutoUpdateInterval.TWELVE_HOURS -> R.string.settings_general_auto_update_interval_12_hours
                    AutoUpdateInterval.ONE_HOUR -> R.string.settings_general_auto_update_interval_one_hour
                }
            )
            val isWeekly = settings.autoUpdateInterval == AutoUpdateInterval.ONE_WEEK
            val isFirstRun = settings.isFirstRunAfterReschedule || settings.lastAutoUpdateMillis == 0L
            val message = if (isFirstRun) {
                val locale = LocaleUtils.effectiveLocale(context)
                val nextFormatted = TimeUtils.appendJstSuffix(
                    if (isWeekly) {
                        TimeUtils.formatToJstWithLocale(settings.nextScheduledUpdateMillis, "yyyy/MM/dd(EEE) HH:mm", locale)
                    } else {
                        TimeUtils.formatToJst(settings.nextScheduledUpdateMillis, "yyyy/MM/dd HH:mm")
                    }
                )
                context.getString(R.string.main_autorenew_enabled_first, intervalLabel, nextFormatted)
            } else {
                val locale = LocaleUtils.effectiveLocale(context)
                val lastFormatted = TimeUtils.appendJstSuffix(
                    if (isWeekly) {
                        TimeUtils.formatToJstWithLocale(settings.lastAutoUpdateMillis, "yyyy/MM/dd(EEE) HH:mm", locale)
                    } else {
                        TimeUtils.formatToJst(settings.lastAutoUpdateMillis, "yyyy/MM/dd HH:mm")
                    }
                )
                context.getString(R.string.main_autorenew_enabled_with_last, intervalLabel, lastFormatted)
            }
            emitSnackbarMessage(message)
        }
    }

    /**
     * 過去データ(日次)の自動更新状態を Snackbar で表示します。
     *
     * 日次過去データの定期取得は既存の自動更新スケジューラを共用するため、再スケジュールは行いません。
     * 間隔表示はクールダウン考慮の実効間隔（1時間 / 12時間 は「1日」）を使用し、前回(実績)は
     * 日次過去データ保存行の [SudmonitorHistory.fetchedAtEpochMs] を表示します（プラン §4.2・Web版準拠）。
     * 行未保存（未取得）の場合は既存の次回予定（自動更新のスケジュール表示）を代用します。
     */
    fun showSudmonitorHistoryAutoUpdateStatus() {
        viewModelScope.launch {
            if (_uiState.value.isSudmonitorHistoryUpdateRunning) {
                emitSnackbarMessage(context.getString(R.string.main_autorenew_in_progress))
                return@launch
            }
            val settings = settingsRepository.appSettingsFlow.first()
            if (!settings.autoUpdateEnabled) {
                emitSnackbarMessage(context.getString(R.string.main_autorenew_disabled))
                return@launch
            }
            val effectiveInterval = effectiveAutoUpdateIntervalForDailyHistory(settings.autoUpdateInterval)
            val intervalLabel = context.getString(
                when (effectiveInterval) {
                    AutoUpdateInterval.ONE_WEEK -> R.string.settings_general_auto_update_interval_one_week
                    AutoUpdateInterval.ONE_DAY -> R.string.settings_general_auto_update_interval_one_day
                    AutoUpdateInterval.TWELVE_HOURS -> R.string.settings_general_auto_update_interval_12_hours
                    AutoUpdateInterval.ONE_HOUR -> R.string.settings_general_auto_update_interval_one_hour
                }
            )
            val history = _uiState.value.sudmonitorHistory
                ?: sudmonitorHistoryRepository.findByDamId(settings.targetDamId)
            val message = if (history != null) {
                val lastFormatted = TimeUtils.appendJstSuffix(
                    TimeUtils.formatToJst(history.fetchedAtEpochMs, "yyyy/MM/dd HH:mm")
                )
                context.getString(R.string.main_autorenew_enabled_with_last, intervalLabel, lastFormatted)
            } else {
                val locale = LocaleUtils.effectiveLocale(context)
                val nextFormatted = TimeUtils.appendJstSuffix(
                    TimeUtils.formatToJstWithLocale(settings.nextScheduledUpdateMillis, "yyyy/MM/dd HH:mm", locale)
                )
                context.getString(R.string.main_autorenew_enabled_first, intervalLabel, nextFormatted)
            }
            emitSnackbarMessage(message)
        }
    }

    
    fun markInitialAutoUpdateDialogShown() {
        viewModelScope.launch {
            val appSettings = settingsRepository.appSettingsFlow.first()
            initAutoUpdateCustomTimings(appSettings)
            settingsRepository.updateSettings { it.copy(initialAutoUpdateDialogShown = true) }
            _uiState.update { it.copy(
                showInitialAutoUpdateDialog = false,
                showInitialNotificationPermissionRequest = true
            ) }
        }
    }

    
    fun markInitialNotificationPermissionRequested() {
        _uiState.update { it.copy(showInitialNotificationPermissionRequest = false) }
    }

    
    fun updateShowNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showNotification = enabled) }
        }
    }

    
    fun enableAutoUpdateFromInitialDialog() {
        viewModelScope.launch {
            val settings = settingsRepository.appSettingsFlow.first()
            val now = System.currentTimeMillis()
            val (rawNext, updatedA) = settings.calculateEffectiveNextRunTimeAndA(now)
            val effectiveNext = AutoUpdateScheduler.advanceIfWithinWindow(
                settings.autoUpdateInterval, rawNext, now
            )
            
            settingsRepository.updateSettings { s ->
                var updated = s.copy(
                    autoUpdateEnabled = true,
                    nextScheduledUpdateMillis = effectiveNext,
                    isFirstRunAfterReschedule = true
                )
                if (updatedA != s.currentCustomTimingMillis) {
                    updated = updated.withCustomTimingMillis(updatedA)
                }
                updated
            }
            val updatedSettings = settingsRepository.appSettingsFlow.first()
            damWorkManagerGateway.scheduleWorkAtTime(updatedSettings, effectiveNext)
            val intervalName = updatedSettings.autoUpdateInterval.name
            val nextStr = TimeUtils.formatToJstIso8601(effectiveNext)
            debugLogRepository.addEntry(
                "Auto update enabled (initial dialog).",
                "Interval: $intervalName, Next scheduled: $nextStr"
            )
        }
    }

    /**
     * リアルタイムデータの手動更新クールダウン終了時刻（ミリ秒）を返します。
     *
     * sudmonitor 取得成功時に保存された [AppSettings.manualRefreshAvailableAtMillis]
     * （`X-TCS-Next-Update-At` 由来）があればその値を、なければ従来どおり
     * `lastFetchTimeMillis + [REFRESH_COOLDOWN_MINUTES]` を返します（UI の cooldown Snackbar 時刻表示用）。
     *
     * @return クールダウン終了時刻のミリ秒。最終取得が無い場合は null
     */
    val refreshAvailableAtMillis: Long?
        get() {
            val saved = appSettings.value?.manualRefreshAvailableAtMillis
            if (saved != null) return saved
            val lastFetch = lastFetchTimeMillis
            return if (lastFetch == 0L) null else lastFetch + REFRESH_COOLDOWN_MINUTES * 60 * 1000L
        }

    
    fun canRefresh(now: Long = System.currentTimeMillis()): Boolean {
        val state = uiState.value
        if (state.isAutoUpdateRunning || state.isManualUpdateRunning || state.isInitialLoadRunning || state.isBootUpdateRunning) return false
        val settings = appSettings.value
        if (settings?.debugModeEnabled == true) {
            return true
        }
        // sudmonitor 取得成功時に保存された次回更新予定時刻（X-TCS-Next-Update-At 由来）が
        // ある場合はその時刻で判定し、無い場合のみ従来の 10 分 cooldown を維持する（§7）。
        val savedAvailableAt = settings?.manualRefreshAvailableAtMillis
        if (savedAvailableAt != null) {
            return now >= savedAvailableAt
        }
        val lastFetchMinutes = lastFetchTimeMillis / (60 * 1000)
        val nowMinutes = now / (60 * 1000)
        val elapsedMinutes = nowMinutes - lastFetchMinutes
        return lastFetchTimeMillis == 0L || elapsedMinutes >= REFRESH_COOLDOWN_MINUTES
    }

    
    fun fetchData(isManual: Boolean = false, isInitialLoad: Boolean = false) {
        if (_uiState.value.isAutoUpdateRunning || _uiState.value.isBootUpdateRunning) return
        if (isManual) {
            viewModelScope.launch {
                val settings = settingsRepository.appSettingsFlow.first()
                if (!settings.debugModeEnabled && !networkAvailability.isNetworkAvailable()) {
                    // WorkManager には enqueue せず、即座に「ネットワーク接続なし」の Snackbar を表示する
                    buildManualRefreshOfflineMessage(settings)?.let { msg ->
                        emitSnackbarMessage(msg)
                        updateLastLoadResultMessage(msg)
                    }
                    return@launch
                }
                damWorkManagerGateway.enqueueOneTimeWork(DamWorker.WORK_TYPE_MANUAL)
            }
            return
        }
        val workType = when {
            isInitialLoad -> DamWorker.WORK_TYPE_INITIAL
            else -> DamWorker.WORK_TYPE_AUTO
        }
        damWorkManagerGateway.enqueueOneTimeWork(workType)
    }

    /**
     * 手動更新のオフライン事前チェック用に、対象ダム名+「ネットワーク接続なし」メッセージを構築します。
     *
     * リアルタイム手動更新と日次過去データの手動更新で同一の文言を使うための共通ヘルパです。
     *
     * @param settings 現在のアプリ設定
     * @return Snackbar 表示用メッセージ。空文字しか構築できない場合は null
     */
    private suspend fun buildManualRefreshOfflineMessage(settings: AppSettings): String? {
        val config = getDamConfig(settings.targetDamId)
        val damName = config?.let { LocaleUtils.normalDamName(context, it) } ?: ""
        val isJa = LocaleUtils.isJapanese(context)
        val networkUnavailableText = settings.getNetworkUnavailableText(isJa)
        val msg = if (networkUnavailableText.isNotEmpty()) {
            "$damName $networkUnavailableText".trim()
        } else {
            damName.trim()
        }
        return msg.ifEmpty { null }
    }

    private fun parseSearchDateStartMillis(date: String): Long? =
        TimeUtils.parseJstMillis(date, DATE_FORMAT_YYYYMMDD)

    private fun searchDateEndMillis(date: String): Long? =
        parseSearchDateStartMillis(date)?.let { startMillis ->
            Calendar.getInstance(TimeZone.getTimeZone(JST_TIMEZONE_ID)).apply {
                timeInMillis = startMillis
                add(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
        }

    private fun parseHistoricalDataTimeMillis(time: String): Long? =
        TimeUtils.parseJstMillisAllow24Hour(time, "yyyy/MM/dd HH:mm")

    private suspend fun buildHistoricalDisplayState(
        metaId: Long,
        visibleData: List<DamHistoricalData>,
        visibleFromMillis: Long,
        visibleToMillis: Long
    ): HistoricalDisplayState {
        if (visibleData.isEmpty()) {
            return HistoricalDisplayState(
                displayData = emptyList(),
                displayFromMillis = Long.MAX_VALUE,
                isAllLoaded = true
            )
        }

        val jst = TimeZone.getTimeZone(JST_TIMEZONE_ID)
        val newestMillis = visibleData.lastOrNull()?.time
            ?.let(::parseHistoricalDataTimeMillis)
            ?: visibleToMillis
        val oldestMillis = visibleData.firstOrNull()?.time
            ?.let(::parseHistoricalDataTimeMillis)
            ?: visibleFromMillis
        val initialFromMillis = Calendar.getInstance(jst).apply {
            timeInMillis = newestMillis
            add(Calendar.HOUR_OF_DAY, -HISTORICAL_INITIAL_HOURS)
        }.timeInMillis
        val queryFromMillis = maxOf(initialFromMillis, visibleFromMillis)
        val initialData =
            historicalSearchRepository.getDataByMetaIdAndTimeRange(metaId, queryFromMillis, visibleToMillis)
                .reversed()

        val paginationFromMillis = Calendar.getInstance(jst).apply {
            timeInMillis = newestMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val isAllLoaded = initialFromMillis <= oldestMillis || queryFromMillis <= visibleFromMillis

        return HistoricalDisplayState(
            displayData = initialData,
            displayFromMillis = paginationFromMillis,
            isAllLoaded = isAllLoaded
        )
    }

    /**
     * sudmonitor 日次過去データの表示状態（ページング起点・初回表示6時間相当）を構築します。
     *
     * 既存 [buildHistoricalDisplayState] のロジックを日次用に複製したものです。観測行の読出しは
     * [SudmonitorHistoryRepository.getObservationsByTimeRange] を使用します。
     *
     * @param damId 対象ダムの観測所ID
     * @param visibleData 期間絞り込み後（または全期間）の観測行リスト（時刻昇順）
     * @param visibleFromMillis 表示対象期間の開始ミリ秒
     * @param visibleToMillis 表示対象期間の終了ミリ秒
     * @return 表示データ・ページング起点・全件読込済みフラグ
     */
    private suspend fun buildSudmonitorHistoryDisplayState(
        damId: String,
        visibleData: List<DamHistoricalData>,
        visibleFromMillis: Long,
        visibleToMillis: Long
    ): SudmonitorHistoryDisplayState {
        if (visibleData.isEmpty()) {
            return SudmonitorHistoryDisplayState(
                displayData = emptyList(),
                displayFromMillis = Long.MAX_VALUE,
                isAllLoaded = true
            )
        }

        val jst = TimeZone.getTimeZone(JST_TIMEZONE_ID)
        val newestMillis = visibleData.lastOrNull()?.time
            ?.let(::parseHistoricalDataTimeMillis)
            ?: visibleToMillis
        val oldestMillis = visibleData.firstOrNull()?.time
            ?.let(::parseHistoricalDataTimeMillis)
            ?: visibleFromMillis
        val initialFromMillis = Calendar.getInstance(jst).apply {
            timeInMillis = newestMillis
            add(Calendar.HOUR_OF_DAY, -HISTORICAL_INITIAL_HOURS)
        }.timeInMillis
        val queryFromMillis = maxOf(initialFromMillis, visibleFromMillis)
        val initialData = sudmonitorHistoryRepository
            .getObservationsByTimeRange(damId, queryFromMillis, visibleToMillis)
            .reversed()

        val paginationFromMillis = Calendar.getInstance(jst).apply {
            timeInMillis = newestMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val isAllLoaded = initialFromMillis <= oldestMillis || queryFromMillis <= visibleFromMillis

        return SudmonitorHistoryDisplayState(
            displayData = initialData,
            displayFromMillis = paginationFromMillis,
            isAllLoaded = isAllLoaded
        )
    }

    
    fun fetchHistoricalData(damConfig: DamConfig, startDate: String, endDate: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isHistoricalSearchLoading = true, historicalErrorMessage = null) }
            historicalSearchRepository.fetchAndStore(damConfig, startDate, endDate)
                .fold(
                    ifLeft = { error ->
                        _uiState.update { it.copy(
                            isHistoricalSearchLoading = false,
                            historicalErrorMessage = error.message ?: context.getString(R.string.historical_search_error_unknown)
                        ) }
                    },
                    ifRight = { meta ->
                        currentHistoricalMetaId = meta.id
                        if (!meta.isPinned) {
                            transientViewedMetaId = meta.id
                        }
                        val newestMillis = historicalSearchRepository.getNewestTimeMillisByMetaId(meta.id) ?: 0L

                        
                        val allData = historicalSearchRepository.getAllDataByMetaId(meta.id)
                        val oldestMillis = historicalSearchRepository.getOldestTimeMillisByMetaId(meta.id) ?: 0L
                        val displayState = buildHistoricalDisplayState(
                            metaId = meta.id,
                            visibleData = allData,
                            visibleFromMillis = oldestMillis,
                            visibleToMillis = newestMillis
                        )

                        _uiState.update { it.copy(
                            isHistoricalSearchLoading = false,
                            isHistoricalMode = true,
                            isSudmonitorHistoryMode = false,
                            historicalDamConfig = damConfig,
                            historicalDamMeta = meta,
                            historicalAllData = allData,
                            historicalVisibleData = allData,
                            historicalDisplayData = displayState.displayData,
                            historicalDisplayFromMillis = displayState.displayFromMillis,
                            historicalVisibleFromMillis = oldestMillis,
                            historicalVisibleToMillis = newestMillis,
                            isHistoricalDisplayRangeFiltered = false,
                            historicalDisplayStartDate = null,
                            historicalDisplayEndDate = null,
                            isAllHistoricalLoaded = displayState.isAllLoaded,
                            historicalErrorMessage = null
                        ) }
                        refreshAllMetaList()
                    }
                )
        }
    }

    
    fun resetHistoricalDisplayToInitial() {
        viewModelScope.launch {
            val state = _uiState.value
            val visibleData = state.historicalVisibleData
            val visibleFromMillis = state.historicalVisibleFromMillis
            val visibleToMillis = state.historicalVisibleToMillis
            val displayState = buildHistoricalDisplayState(
                metaId = currentHistoricalMetaId,
                visibleData = visibleData,
                visibleFromMillis = visibleFromMillis,
                visibleToMillis = visibleToMillis
            )

            _uiState.update { it.copy(
                historicalDisplayData = displayState.displayData,
                historicalDisplayFromMillis = displayState.displayFromMillis,
                isAllHistoricalLoaded = displayState.isAllLoaded
            ) }
        }
    }

    
    fun switchToRealtimeMode() {
        _uiState.update { state ->
            state.copy(
                isHistoricalMode = false,
                historicalVisibleData = state.historicalAllData,
                isHistoricalDisplayRangeFiltered = false,
                historicalDisplayStartDate = null,
                historicalDisplayEndDate = null,
                isSudmonitorHistoryMode = false,
                sudmonitorHistoryVisibleData = state.sudmonitorHistoryAllData,
                isSudmonitorHistoryRangeFiltered = false,
                sudmonitorHistoryDisplayStartDate = null,
                sudmonitorHistoryDisplayEndDate = null
            )
        }
    }

    
    private fun clearHistoricalData() {
        currentHistoricalMetaId = 0L
        _uiState.update { it.copy(
            historicalDamConfig = null,
            historicalDamMeta = null,
            historicalAllData = emptyList(),
            historicalVisibleData = emptyList(),
            historicalDisplayData = emptyList(),
            historicalDisplayFromMillis = Long.MAX_VALUE,
            historicalVisibleFromMillis = 0L,
            historicalVisibleToMillis = 0L,
            isHistoricalDisplayRangeFiltered = false,
            historicalDisplayStartDate = null,
            historicalDisplayEndDate = null,
            isAllHistoricalLoaded = false,
            isHistoricalSearchLoading = false,
            isHistoricalLoadingMore = false,
            historicalErrorMessage = null
        ) }
    }

    /**
     * 過去データ(日次)モードの表示状態（保存行・表示対象ダム設定・履歴由来の全表示データ）をクリアします。
     *
     * [initSudmonitorHistoryObservation]（機能ゲート無効化時の null emit）とダム変更分岐に呼び出されます。
     * モードの離脱は [switchToRealtimeMode] が担当します。
     */
    private fun clearSudmonitorHistoryData() {
        _uiState.update { it.copy(
            sudmonitorHistory = null,
            sudmonitorHistoryDamConfig = null
        ) }
        clearSudmonitorHistoryDisplayState()
    }

    /**
     * 過去データ(日次)モードの履歴由来の表示状態（全観測データ・期間絞り込み・表示データ）をクリアします。
     *
     * 保存行（[MainUiState.sudmonitorHistory]）と表示対象ダム設定
     * （[MainUiState.sudmonitorHistoryDamConfig]）は変更しません。
     * 機能ゲート有効で行が未保存の間も日次モード・ダム名表示を維持するために使用します。
     */
    private fun clearSudmonitorHistoryDisplayState() {
        _uiState.update { it.copy(
            sudmonitorHistoryAllData = emptyList(),
            sudmonitorHistoryVisibleData = emptyList(),
            sudmonitorHistoryDisplayData = emptyList(),
            sudmonitorHistoryDisplayFromMillis = Long.MAX_VALUE,
            sudmonitorHistoryVisibleFromMillis = 0L,
            sudmonitorHistoryVisibleToMillis = 0L,
            isSudmonitorHistoryRangeFiltered = false,
            sudmonitorHistoryDisplayStartDate = null,
            sudmonitorHistoryDisplayEndDate = null
        ) }
    }

    
    /**
     * 過去比較グラフの指定メトリックについて、指定期間のデータ読み込みを開始します。
     *
     * 過去比較グラフは早明浦ダム限定機能のため、表示中ダム（[MainUiState.displayedDamConfig]。
     * モード別に解決される）が早明浦以外（[AppSettings.DEFAULT_DAM_ID]以外）の場合や
     * ダム設定が未確定の場合は何も行いません。
     * 同一メトリック・同一期間の読み込みが完了済み（READY）の場合は再読み込みせずにキャッシュを返します（screen lifetime cache）。
     * 読み込み中（LOADING）の再要求や期間変更の場合は実行中のジョブをキャンセルして読み直します。
     * 古い読み込みの完了結果が新しい選択を上書きしないよう、世代カウンタで結果を破棄します。
     *
     * @param metric 比較対象のダム諸量（[HistoricalComparisonMetric]）
     * @param windowStartMillis 比較期間の開始エポックミリ秒（含む）
     * @param windowEndMillis 比較期間の終了エポックミリ秒（含む）
     * @param mainYear 主系列年。通常の過去データ表示では表示対象データの年（表示窓開始のJST年）を
     * 指定し、リアルタイム・過去データ(日次)表示ではnull（JST現在年を主系列年とする従来挙動）。
     * READYキャッシュ判定はwindow一致のみで行う（mainYearはwindow開始から一意に定まるため）
     */
    fun ensureHistoricalComparison(
        metric: HistoricalComparisonMetric,
        windowStartMillis: Long,
        windowEndMillis: Long,
        mainYear: Int? = null
    ) {
        val config = _uiState.value.displayedDamConfig
        if (config == null || config.id != AppSettings.DEFAULT_DAM_ID) return

        val currentState = _uiState.value.historicalComparisonStates[metric]
        if (currentState?.loadState == HistoricalComparisonLoadState.LOADING) return
        if (currentState?.loadState == HistoricalComparisonLoadState.READY &&
            currentState.windowStartMillis == windowStartMillis &&
            currentState.windowEndMillis == windowEndMillis
        ) return

        comparisonJobs[metric]?.cancel()
        val generation = (comparisonGenerations[metric] ?: 0L) + 1L
        comparisonGenerations[metric] = generation

        val damId = config.id
        comparisonJobs[metric] = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    historicalComparisonStates = state.historicalComparisonStates + (
                        metric to HistoricalComparisonMetricState(
                            loadState = HistoricalComparisonLoadState.LOADING,
                            windowStartMillis = windowStartMillis,
                            windowEndMillis = windowEndMillis,
                            mainYear = mainYear
                        )
                    )
                )
            }
            val result = historicalComparisonRepository.loadComparison(
                damId = damId,
                metric = metric,
                windowStartMillis = windowStartMillis,
                windowEndMillis = windowEndMillis,
                mainYear = mainYear
            )
            if (comparisonGenerations[metric] != generation) return@launch
            result.fold(
                ifLeft = { error ->
                    _uiState.update { state ->
                        state.copy(
                            historicalComparisonStates = state.historicalComparisonStates + (
                                metric to HistoricalComparisonMetricState(
                                    loadState = HistoricalComparisonLoadState.ERROR,
                                    error = error,
                                    windowStartMillis = windowStartMillis,
                                    windowEndMillis = windowEndMillis,
                                    mainYear = mainYear
                                )
                            )
                        )
                    }
                },
                ifRight = { data ->
                    _uiState.update { state ->
                        state.copy(
                            historicalComparisonStates = state.historicalComparisonStates + (
                                metric to HistoricalComparisonMetricState(
                                    loadState = HistoricalComparisonLoadState.READY,
                                    data = data,
                                    windowStartMillis = windowStartMillis,
                                    windowEndMillis = windowEndMillis,
                                    mainYear = mainYear
                                )
                            )
                        )
                    }
                }
            )
        }
    }

    /**
     * 過去比較グラフの全メトリックの読み込みジョブをキャンセルし、状態をクリアします。
     * 対象ダムが早明浦以外へ変更されたタイミング（[initDamConfigObservation]のダム変更分岐）で呼び出されます。
     */
    private fun clearHistoricalComparison() {
        comparisonJobs.values.forEach { it.cancel() }
        comparisonJobs.clear()
        comparisonGenerations.clear()
        _uiState.update { it.copy(historicalComparisonStates = emptyMap()) }
    }

    fun switchToHistoricalMode(metaId: Long? = null) {
        if (metaId != null) {
            viewModelScope.launch {
                val domainMeta = historicalSearchRepository.getMetaById(metaId) ?: return@launch
                val config = getDamConfig(domainMeta.damConfigId)
                currentHistoricalMetaId = metaId
                if (!domainMeta.isPinned) {
                    transientViewedMetaId = metaId
                }

                val newestMillis = historicalSearchRepository.getNewestTimeMillisByMetaId(metaId) ?: 0L
                val allData = historicalSearchRepository.getAllDataByMetaId(metaId)
                val oldestMillis = historicalSearchRepository.getOldestTimeMillisByMetaId(metaId) ?: 0L
                val displayState = buildHistoricalDisplayState(
                    metaId = metaId,
                    visibleData = allData,
                    visibleFromMillis = oldestMillis,
                    visibleToMillis = newestMillis
                )

                _uiState.update { it.copy(
                    isHistoricalMode = true,
                    isSudmonitorHistoryMode = false,
                    historicalDamConfig = config,
                    historicalDamMeta = domainMeta,
                    historicalAllData = allData,
                    historicalVisibleData = allData,
                    historicalDisplayData = displayState.displayData,
                    historicalDisplayFromMillis = displayState.displayFromMillis,
                    historicalVisibleFromMillis = oldestMillis,
                    historicalVisibleToMillis = newestMillis,
                    isHistoricalDisplayRangeFiltered = false,
                    historicalDisplayStartDate = null,
                    historicalDisplayEndDate = null,
                    isAllHistoricalLoaded = displayState.isAllLoaded,
                    historicalErrorMessage = null
                ) }
                refreshAllMetaList()
            }
        } else if (_uiState.value.historicalDamConfig != null) {
            _uiState.update { it.copy(
                isHistoricalMode = true,
                isSudmonitorHistoryMode = false
            ) }
        }
    }

    /**
     * 過去データ(日次)モードへ切り替えます。
     *
     * 対象ダムに保存行（[SudmonitorHistoryRepository.findByDamId]）がある場合は全観測行を読み込み、
     * 既存 [switchToHistoricalMode] と同じ初期表示（最新から6時間相当）で状態を設定します。
     * 行が未保存の場合でも日次モードへは遷移し、保存行を null・履歴由来の表示状態を空のまま
     * 表示対象ダム設定だけを設定します（初回取得失敗後もサマリーCardのプレースホルダーと
     * 手動更新アイコンで取得を促すため）。
     */
    fun switchToSudmonitorHistoryMode() {
        viewModelScope.launch {
            val settings = settingsRepository.appSettingsFlow.first()
            // 機能ゲート（過去データの取得元が sudmonitor）が無効な間は表示経路も無効化する（プラン §1.3）
            if (settings.historicalDataSource != RealtimeDataSource.SUDMONITOR) return@launch
            val history = sudmonitorHistoryRepository.findByDamId(settings.targetDamId)
            if (history == null) {
                // 行未保存でも日次モードへ入る。保存行は null、表示対象ダム設定のみ設定し、
                // 履歴由来の表示状態は空にする（手動更新で初回取得できる）
                clearSudmonitorHistoryDisplayState()
                _uiState.update { state ->
                    state.copy(
                        isHistoricalMode = false,
                        isSudmonitorHistoryMode = true,
                        sudmonitorHistory = null,
                        sudmonitorHistoryDamConfig = getDamConfig(settings.targetDamId)
                    )
                }
                return@launch
            }
            loadSudmonitorHistoryIntoState(history)
        }
    }

    /**
     * 保存行と全観測行を読み込み、過去データ(日次)モードの表示状態を設定します。
     *
     * @param history 対象ダムの日次過去データ保存行
     */
    private suspend fun loadSudmonitorHistoryIntoState(history: SudmonitorHistory) {
        val config = getDamConfig(history.damId)
        val allData = sudmonitorHistoryRepository.getAllObservations(history.damId)
        val oldestMillis = allData.firstOrNull()?.time?.let(::parseHistoricalDataTimeMillis) ?: 0L
        val newestMillis = allData.lastOrNull()?.time?.let(::parseHistoricalDataTimeMillis) ?: 0L
        val displayState = buildSudmonitorHistoryDisplayState(
            damId = history.damId,
            visibleData = allData,
            visibleFromMillis = oldestMillis,
            visibleToMillis = newestMillis
        )

        _uiState.update { it.copy(
            isHistoricalMode = false,
            isSudmonitorHistoryMode = true,
            sudmonitorHistory = history,
            sudmonitorHistoryDamConfig = config,
            sudmonitorHistoryAllData = allData,
            sudmonitorHistoryVisibleData = allData,
            sudmonitorHistoryDisplayData = displayState.displayData,
            sudmonitorHistoryDisplayFromMillis = displayState.displayFromMillis,
            sudmonitorHistoryVisibleFromMillis = oldestMillis,
            sudmonitorHistoryVisibleToMillis = newestMillis,
            isSudmonitorHistoryRangeFiltered = false,
            sudmonitorHistoryDisplayStartDate = null,
            sudmonitorHistoryDisplayEndDate = null
        ) }
    }

    /**
     * 日次過去データの取得（初回 / 手動）を要求します。
     *
     * 手動更新は [SudmonitorHistoryTrigger.MANUAL] で enqueue します。失敗時のエラー Snackbar は
     * 表示しません（日次過去データの失敗は静か。既存 [fetchData] の手動更新とは異なる）。
     * 手動更新のみ既存 [fetchData] と同じオフライン事前チェックを行い、ネットワーク接続がない場合は
     * enqueue せずに同一の「ネットワーク接続なし」Snackbar を表示します
     * （INITIAL は対象外。Debug mode ON 時も事前チェックをスキップする）。
     * 実行中表示には日次用の [MainUiState.isSudmonitorHistoryUpdateRunning] を使用します。
     * 取得成功後の表示反映は [initSudmonitorHistoryObservation] が保存行の Room Flow 監視で自動的に行う。
     * WorkManager 側にもネットワーク接続制約が付与されるため、オフライン時に登録された
     * INITIAL は接続回復後に自動実行されます。
     *
     * @param isManual 手動更新である場合はtrue（false の場合は初回取得扱いで enqueue）
     */
    fun fetchSudmonitorHistoryData(isManual: Boolean = false) {
        viewModelScope.launch {
            // 機能ゲート（過去データの取得元が sudmonitor）が無効な間は手動更新経路も無効化する（プラン §1.3）
            val settings = settingsRepository.appSettingsFlow.first()
            if (settings.historicalDataSource != RealtimeDataSource.SUDMONITOR) return@launch
            if (isManual && !settings.debugModeEnabled && !networkAvailability.isNetworkAvailable()) {
                buildManualRefreshOfflineMessage(settings)?.let(::emitSnackbarMessage)
                return@launch
            }
            damWorkManagerGateway.enqueueSudmonitorHistoryWork(
                if (isManual) SudmonitorHistoryTrigger.MANUAL else SudmonitorHistoryTrigger.INITIAL
            )
        }
    }

    /**
     * 対象ダムの日次過去データの手動更新クールダウン終了時刻を返します。
     *
     * 保存済み [SudmonitorHistory.nextUpdateAtEpochMs] を返し、行未保存・取得前は null
     * （= いつでも手動更新可）です。
     *
     * @param damId 対象ダムの観測所ID
     * @return クールダウン終了時刻のミリ秒、未保存の場合は null
     */
    suspend fun manualRefreshAvailableAt(damId: String): Long? =
        sudmonitorHistoryRepository.manualRefreshAvailableAt(damId)

    /**
     * 指定された保存行から、表示中の過去データ(日次)モードの表示状態を再読込します。
     *
     * 期間絞り込みが有効でも新保存行の読込済み期間外なら解除して全期間表示へ戻します
     * （デバッグ復元などで期間が変わる場合への対応）。
     *
     * @param history 対象ダムの日次過去データ保存行（Room Flow のemit値）
     */
    private suspend fun reloadSudmonitorHistoryDisplay(history: SudmonitorHistory) {
        val state = _uiState.value
        if (!state.isSudmonitorHistoryMode) return
        val allData = sudmonitorHistoryRepository.getAllObservations(history.damId)
        val oldestMillis = allData.firstOrNull()?.time?.let(::parseHistoricalDataTimeMillis) ?: 0L
        val newestMillis = allData.lastOrNull()?.time?.let(::parseHistoricalDataTimeMillis) ?: 0L
        val periodStart = history.periodStartEpochMs
        val periodEndExclusive = history.periodEndEpochMs?.plus(MILLIS_PER_DAY)
        val filterStillValid = state.isSudmonitorHistoryRangeFiltered &&
            state.sudmonitorHistoryVisibleFromMillis > 0L &&
            state.sudmonitorHistoryVisibleToMillis > 0L &&
            periodStart != null && periodEndExclusive != null &&
            state.sudmonitorHistoryVisibleFromMillis >= periodStart &&
            state.sudmonitorHistoryVisibleToMillis <= periodEndExclusive
        val visibleData = if (filterStillValid) {
            sudmonitorHistoryRepository.getObservationsByTimeRange(
                history.damId,
                state.sudmonitorHistoryVisibleFromMillis,
                state.sudmonitorHistoryVisibleToMillis
            )
        } else {
            allData
        }
        val visibleFromMillis = if (filterStillValid) state.sudmonitorHistoryVisibleFromMillis else oldestMillis
        val visibleToMillis = if (filterStillValid) state.sudmonitorHistoryVisibleToMillis else newestMillis
        val displayState = buildSudmonitorHistoryDisplayState(
            damId = history.damId,
            visibleData = visibleData,
            visibleFromMillis = visibleFromMillis,
            visibleToMillis = visibleToMillis
        )

        _uiState.update { it.copy(
            sudmonitorHistory = history,
            sudmonitorHistoryDamConfig = getDamConfig(history.damId),
            sudmonitorHistoryAllData = allData,
            sudmonitorHistoryVisibleData = visibleData,
            sudmonitorHistoryDisplayData = displayState.displayData,
            sudmonitorHistoryDisplayFromMillis = displayState.displayFromMillis,
            sudmonitorHistoryVisibleFromMillis = visibleFromMillis,
            sudmonitorHistoryVisibleToMillis = visibleToMillis,
            isSudmonitorHistoryRangeFiltered = filterStillValid,
            sudmonitorHistoryDisplayStartDate = if (filterStillValid) it.sudmonitorHistoryDisplayStartDate else null,
            sudmonitorHistoryDisplayEndDate = if (filterStillValid) it.sudmonitorHistoryDisplayEndDate else null
        ) }
    }

    /**
     * 読込済み（READY）または読込中（LOADING）の過去比較グラフを再読込します。
     *
     * 比較グラフの主系列年（今年）系列は読込済み日次過去データとの合成のため、日次保存行の更新時に
     * 同じ期間・主系列年で読み直し、最新の日次データを反映します。ERROR は自動retryしません。
     */
    private fun refreshLoadedHistoricalComparisons() {
        val states = _uiState.value.historicalComparisonStates
        if (states.isEmpty()) return
        states.forEach { (metric, metricState) ->
            if (metricState.loadState != HistoricalComparisonLoadState.READY &&
                metricState.loadState != HistoricalComparisonLoadState.LOADING
            ) {
                return@forEach
            }
            comparisonJobs[metric]?.cancel()
            _uiState.update { state ->
                state.copy(
                    historicalComparisonStates = state.historicalComparisonStates + (
                        metric to metricState.copy(
                            loadState = HistoricalComparisonLoadState.IDLE,
                            data = null,
                            error = null
                        )
                    )
                )
            }
            ensureHistoricalComparison(
                metric = metric,
                windowStartMillis = metricState.windowStartMillis,
                windowEndMillis = metricState.windowEndMillis,
                mainYear = metricState.mainYear
            )
        }
    }

    /**
     * 過去データ(日次)表示に期間絞り込みを適用します。
     *
     * 既存 [applyHistoricalDisplayRange] のロジックを日次用に複製したもので、境界は読込済み期間
     * （保存行の `periodStartEpochMs` 〜 `periodEndEpochMs + 1日`）に制限されます。
     * [fromMillis] は開始日の JST 00:00、[toMillis] は終了日の翌日 00:00（終了日を含む）を表します。
     * 全期間と同一の指定は絞り込み解除（[resetSudmonitorHistoryDisplayRange]）として扱います。
     *
     * @param fromMillis 絞り込み開始日時のミリ秒（JST 00:00）
     * @param toMillis 絞り込み終了日時の翌日 00:00 のミリ秒（終了日を含む）
     */
    fun applySudmonitorHistoryDisplayRange(fromMillis: Long, toMillis: Long) {
        viewModelScope.launch {
            val state = _uiState.value
            val history = state.sudmonitorHistory ?: return@launch
            val periodStart = history.periodStartEpochMs ?: return@launch
            val periodEnd = history.periodEndEpochMs ?: return@launch
            val periodEndExclusive = periodEnd + MILLIS_PER_DAY
            if (fromMillis > toMillis || fromMillis < periodStart || toMillis > periodEndExclusive) {
                return@launch
            }
            if (fromMillis == periodStart && toMillis == periodEndExclusive) {
                if (state.isSudmonitorHistoryRangeFiltered) {
                    resetSudmonitorHistoryDisplayRange()
                }
                return@launch
            }

            val visibleData = sudmonitorHistoryRepository.getObservationsByTimeRange(
                history.damId,
                fromMillis,
                toMillis
            )
            val displayState = buildSudmonitorHistoryDisplayState(
                damId = history.damId,
                visibleData = visibleData,
                visibleFromMillis = fromMillis,
                visibleToMillis = toMillis
            )

            _uiState.update { it.copy(
                sudmonitorHistoryVisibleData = visibleData,
                sudmonitorHistoryDisplayData = displayState.displayData,
                sudmonitorHistoryDisplayFromMillis = displayState.displayFromMillis,
                sudmonitorHistoryVisibleFromMillis = fromMillis,
                sudmonitorHistoryVisibleToMillis = toMillis,
                isSudmonitorHistoryRangeFiltered = true,
                sudmonitorHistoryDisplayStartDate = formatSudmonitorHistoryDate(fromMillis),
                sudmonitorHistoryDisplayEndDate = formatSudmonitorHistoryDate(toMillis - MILLIS_PER_DAY)
            ) }
        }
    }

    /**
     * 過去データ(日次)表示の期間絞り込みを解除し、全期間の表示へ復元します。
     */
    fun resetSudmonitorHistoryDisplayRange() {
        viewModelScope.launch {
            val state = _uiState.value
            val history = state.sudmonitorHistory ?: return@launch
            val allData = state.sudmonitorHistoryAllData.ifEmpty {
                sudmonitorHistoryRepository.getAllObservations(history.damId)
            }
            val oldestMillis = allData.firstOrNull()?.time?.let(::parseHistoricalDataTimeMillis) ?: 0L
            val newestMillis = allData.lastOrNull()?.time?.let(::parseHistoricalDataTimeMillis) ?: 0L
            val displayState = buildSudmonitorHistoryDisplayState(
                damId = history.damId,
                visibleData = allData,
                visibleFromMillis = oldestMillis,
                visibleToMillis = newestMillis
            )

            _uiState.update { it.copy(
                sudmonitorHistoryAllData = allData,
                sudmonitorHistoryVisibleData = allData,
                sudmonitorHistoryDisplayData = displayState.displayData,
                sudmonitorHistoryDisplayFromMillis = displayState.displayFromMillis,
                sudmonitorHistoryVisibleFromMillis = oldestMillis,
                sudmonitorHistoryVisibleToMillis = newestMillis,
                isSudmonitorHistoryRangeFiltered = false,
                sudmonitorHistoryDisplayStartDate = null,
                sudmonitorHistoryDisplayEndDate = null
            ) }
        }
    }

    /**
     * ミリ秒を JST の `yyyy/MM/dd` 表記へ変換します（期間絞り込みの表示日付用）。
     *
     * @param millis 変換対象のミリ秒タイムスタンプ
     * @return 変換された日付文字列
     */
    private fun formatSudmonitorHistoryDate(millis: Long): String =
        TimeUtils.formatToJst(millis, "yyyy/MM/dd")

    
    fun applyHistoricalDisplayRange(startDate: String, endDate: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val meta = state.historicalDamMeta ?: return@launch
            val searchStartMillis = parseSearchDateStartMillis(meta.searchBgnDate) ?: return@launch
            val searchEndStartMillis = parseSearchDateStartMillis(meta.searchEndDate) ?: return@launch
            val startMillis = parseSearchDateStartMillis(startDate) ?: return@launch
            val endStartMillis = parseSearchDateStartMillis(endDate) ?: return@launch
            val endMillis = searchDateEndMillis(endDate) ?: return@launch
            if (startMillis > endStartMillis ||
                startMillis < searchStartMillis ||
                endStartMillis > searchEndStartMillis
            ) {
                return@launch
            }
            if (startDate == meta.searchBgnDate && endDate == meta.searchEndDate) {
                if (state.isHistoricalDisplayRangeFiltered) {
                    resetHistoricalDisplayRange()
                }
                return@launch
            }

            val visibleData = historicalSearchRepository.getDataByMetaIdAndTimeRange(
                metaId = meta.id,
                fromMillis = startMillis,
                toMillis = endMillis
            )
            val displayState = buildHistoricalDisplayState(
                metaId = meta.id,
                visibleData = visibleData,
                visibleFromMillis = startMillis,
                visibleToMillis = endMillis
            )

            _uiState.update { it.copy(
                historicalVisibleData = visibleData,
                historicalDisplayData = displayState.displayData,
                historicalDisplayFromMillis = displayState.displayFromMillis,
                historicalVisibleFromMillis = startMillis,
                historicalVisibleToMillis = endMillis,
                isHistoricalDisplayRangeFiltered = true,
                historicalDisplayStartDate = startDate,
                historicalDisplayEndDate = endDate,
                isAllHistoricalLoaded = displayState.isAllLoaded,
                historicalErrorMessage = null
            ) }
        }
    }

    
    fun resetHistoricalDisplayRange() {
        viewModelScope.launch {
            val state = _uiState.value
            val meta = state.historicalDamMeta ?: return@launch
            val allData = state.historicalAllData.ifEmpty {
                historicalSearchRepository.getAllDataByMetaId(meta.id)
            }
            val oldestMillis = historicalSearchRepository.getOldestTimeMillisByMetaId(meta.id) ?: 0L
            val newestMillis = historicalSearchRepository.getNewestTimeMillisByMetaId(meta.id) ?: 0L
            val displayState = buildHistoricalDisplayState(
                metaId = meta.id,
                visibleData = allData,
                visibleFromMillis = oldestMillis,
                visibleToMillis = newestMillis
            )

            _uiState.update { it.copy(
                historicalAllData = allData,
                historicalVisibleData = allData,
                historicalDisplayData = displayState.displayData,
                historicalDisplayFromMillis = displayState.displayFromMillis,
                historicalVisibleFromMillis = oldestMillis,
                historicalVisibleToMillis = newestMillis,
                isHistoricalDisplayRangeFiltered = false,
                historicalDisplayStartDate = null,
                historicalDisplayEndDate = null,
                isAllHistoricalLoaded = displayState.isAllLoaded,
                historicalErrorMessage = null
            ) }
        }
    }

    
    fun getDamConfig(damConfigId: String): DamConfig? = net.tecogonaz.tcsameuradammonitor.domain.model.getDamConfig(damConfigId)

    
    private suspend fun refreshAllMetaList() {
        val allMetaList = historicalSearchRepository.getAllMetaList()

        val transientMeta = transientViewedMetaId?.let { tid ->
            allMetaList.find { it.id == tid }
        }
        val pinnedList = allMetaList.filter { it.isPinned }

        val navList = buildList {
            if (transientMeta != null && !transientMeta.isPinned) {
                add(transientMeta)
            }
            for (meta in pinnedList) {
                if (none { it.id == meta.id }) {
                    add(meta)
                }
            }
        }

        _uiState.update { it.copy(
            historicalMetaList = navList,
            allHistoricalMetaList = allMetaList
        ) }
    }

    
    fun deleteHistoricalData(metaId: Long) {
        viewModelScope.launch {
            historicalSearchRepository.deleteHistoricalData(metaId)
            if (transientViewedMetaId == metaId) {
                transientViewedMetaId = null
            }
            if (currentHistoricalMetaId == metaId) {
                currentHistoricalMetaId = 0L
                _uiState.update { it.copy(
                    isHistoricalMode = false,
                    historicalDamConfig = null,
                    historicalDamMeta = null,
                    historicalAllData = emptyList(),
                    historicalVisibleData = emptyList(),
                    historicalDisplayData = emptyList(),
                    historicalDisplayFromMillis = Long.MAX_VALUE,
                    historicalVisibleFromMillis = 0L,
                    historicalVisibleToMillis = 0L,
                    isHistoricalDisplayRangeFiltered = false,
                    historicalDisplayStartDate = null,
                    historicalDisplayEndDate = null,
                    isAllHistoricalLoaded = false,
                    isHistoricalSearchLoading = false,
                    isHistoricalLoadingMore = false,
                    historicalErrorMessage = null
                ) }
            }
            refreshAllMetaList()
        }
    }

    
    fun reorderHistoricalMeta(orderedIds: List<Long>) {
        viewModelScope.launch {
            historicalSearchRepository.reorderHistoricalMeta(orderedIds)
            refreshAllMetaList()
        }
    }

    
    fun deleteAllHistoricalData() {
        viewModelScope.launch {
            historicalSearchRepository.deleteAllHistoricalData()
            currentHistoricalMetaId = 0L
            transientViewedMetaId = null
            _uiState.update { it.copy(
                isHistoricalMode = false,
                historicalDamConfig = null,
                historicalDamMeta = null,
                historicalAllData = emptyList(),
                historicalVisibleData = emptyList(),
                historicalDisplayData = emptyList(),
                historicalDisplayFromMillis = Long.MAX_VALUE,
                historicalVisibleFromMillis = 0L,
                historicalVisibleToMillis = 0L,
                isHistoricalDisplayRangeFiltered = false,
                historicalDisplayStartDate = null,
                historicalDisplayEndDate = null,
                isAllHistoricalLoaded = false,
                isHistoricalSearchLoading = false,
                isHistoricalLoadingMore = false,
                historicalErrorMessage = null
            ) }
            refreshAllMetaList()
        }
    }

    
    fun setPinned(metaId: Long, isPinned: Boolean) {
        viewModelScope.launch {
            if (isPinned) {
                val pinnedCount = historicalSearchRepository.countPinnedMeta()
                val allMetaList = historicalSearchRepository.getAllMetaList()
                val targetMeta = allMetaList.find { it.id == metaId }
                if (targetMeta != null && !targetMeta.isPinned && pinnedCount >= MAX_PINNED_META_COUNT) {
                    emitSnackbarMessage(context.getString(R.string.historical_manage_pin_limit_message))
                    return@launch
                }
            }
            historicalSearchRepository.setPinned(metaId, isPinned)
            if (!isPinned) {
                if (currentHistoricalMetaId == metaId && _uiState.value.isHistoricalMode) {
                    transientViewedMetaId = metaId
                } else if (transientViewedMetaId == metaId) {
                    transientViewedMetaId = null
                }
            }
            refreshAllMetaList()
        }
    }

    
    suspend fun checkDuplicate(damConfigId: String, startDate: String, endDate: String): Boolean =
        historicalSearchRepository.checkDuplicate(damConfigId, startDate, endDate)

    
    suspend fun canOpenHistoricalSearchDialog(): Boolean =
        historicalSearchRepository.getStoredMetaCount() < HISTORICAL_SEARCH_MAX_STORED_COUNT
}

/**
 * 過去データ(日次)の自動更新における実効間隔を返します。
 *
 * 自動更新の定期スケジューラはリアルタイムと共用するため、間隔 1時間 / 12時間 の場合は
 * 日次過去データのクールダウン（保存済み `nextUpdateAtEpochMs`）により実質1日1回の更新になります
 * （D7・プラン §3.3）。Snackbar の間隔表示用の純粋関数です。
 *
 * @param interval 自動更新の設定間隔
 * @return 過去データ(日次)の実効間隔（1時間 / 12時間 の場合は [AutoUpdateInterval.ONE_DAY]）
 */
internal fun effectiveAutoUpdateIntervalForDailyHistory(interval: AutoUpdateInterval): AutoUpdateInterval =
    if (interval == AutoUpdateInterval.ONE_HOUR || interval == AutoUpdateInterval.TWELVE_HOURS) {
        AutoUpdateInterval.ONE_DAY
    } else {
        interval
    }
