// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import android.content.Context
import android.util.Log
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlinx.coroutines.flow.first
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.data.source.local.HistoricalDatFileTable
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DatabaseTransactionRunner
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.HistoricalSearchDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.HistoricalSearchMetaEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.toDomain
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.toHistoricalEntity
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DatUrlParseError
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamNetworkDataSource
import net.tecogonaz.tcsameuradammonitor.data.source.remote.NetworkAvailability
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorHistoricalClient
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorHistoricalResult
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.SUDMONITOR_HOST
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import net.tecogonaz.tcsameuradammonitor.util.catchNonCancellation
import net.tecogonaz.tcsameuradammonitor.util.catchNonCancellationSuspend
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton


/**
 * [HistoricalSearchRepository]の実装クラス。
 *
 * ローカルのアセットまたはネットワーク（国土交通省の過去データ検索画面）から指定期間の過去ダム観測データを取得し、
 * ローカルデータベースの検索履歴メタデータと検索履歴データテーブルへ保存・管理します。
 *
 * 検索プランナは次の優先順位でデータソースを選択します。
 * 1. アセット内の静的ファイル（[HistoricalDatFileTable]）で検索期間全体がカバーできる場合は通信を行わずアセットから高速ロードします。
 * 2. アセットでカバーできない場合は、設定（[AppSettings.historicalDataSource]）が
 *    sudmonitor（[RealtimeDataSource.SUDMONITOR]）ならば「バンドル → sudmonitor（月次 → 日次）→ MLIT」の順に
 *    カスケード検索し、残ギャップが空になった場合のみ検索結果を保存します。
 *    読込済みの sudmonitor 日次過去データ（専用テーブル `sudmonitor_history` / `sudmonitor_history_observation`）は
 *    バンドルと同格のローカルカバレッジとして扱い、該当区間の sudmonitor 再アクセスを削減します（D8）。
 *    また、検索期間が読込済み期間と完全に同一の場合はカスケードを実行せずに拒否します（D2）。
 * 3. カスケードで網羅できない場合（404・5xx・タイムアウト・ヘッダ欠落・カバレッジ不足等）は、
 *    部分取得を破棄して検索区間全体を国土交通省（MLIT）から取得します。
 *
 * 検索の成功・失敗はデバッグログ（[DebugLogRepository]）へ記録されます。
 * バンドルのみで完結した成功は記録されず、事前チェック/検証エラー（重複・保存上限・非対応ダム・
 * ネットワーク事前チェック失敗）も失敗ログは出力しません。
 */
@Singleton
class HistoricalSearchRepositoryImpl @Inject constructor(
    private val networkDataSource: DamNetworkDataSource,
    private val fileParser: DamFileParser,
    private val transactionRunner: DatabaseTransactionRunner,
    private val historicalSearchDao: HistoricalSearchDao,
    private val sudmonitorHistoryDao: SudmonitorHistoryDao,
    private val networkAvailability: NetworkAvailability,
    private val sudmonitorHistoricalClient: SudmonitorHistoricalClient,
    private val settingsRepository: SettingsRepository,
    private val debugLogRepository: DebugLogRepository,
    @ApplicationContext private val context: Context
) : HistoricalSearchRepository {
    /**
     * 実装内部の診断用エラー。
     *
     * public Repository APIは `Either<Throwable, T>` を維持し、ViewModel / Workerの表示文言を変えずに
     * parserのtyped errorをこの実装内でlocalized Throwableへ変換する。
     */
    private sealed interface HistoricalSearchError {
        data class DatUrlNotFound(val parseError: DatUrlParseError) : HistoricalSearchError

        /** 検索期間が sudmonitor 日次過去データの読込済み期間と完全に同一のため拒否するエラー（D2）。 */
        data object SudmonitorDailyHistoryLoaded : HistoricalSearchError
    }

    companion object {
        /** 履歴データ表示用時間フォーマット (JST基準) */
        private const val TIME_FORMAT = "yyyy/MM/dd HH:mm"
        
        /** 日付のみのデータパース用フォーマット */
        private const val DATE_ONLY_FORMAT = "yyyyMMdd"
        
        /** .datファイル内日付レコード用フォーマット */
        private const val DAT_DATE_FORMAT = "yyyy/M/d"
        
        /** 1日のミリ秒表現 */
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

        /** デバッグログ用の期間フォーマット（例: "2026-07-01T00:00:00+09:00"） */
        private const val PERIOD_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX"

        /** デバッグログで使うMLITデータソースの表示名 */
        private const val MLIT_DATA_SOURCE_LABEL = "MLIT"

        /** デバッグログ記録時のタイトル */
        private const val SUCCESS_LOG_TITLE = "Historical search succeeded."

        /** デバッグログ記録時のタイトル */
        private const val FAILURE_LOG_TITLE = "Historical search failed."

        /** カスケード失敗時のwarnログ用タグ */
        private const val LOG_TAG = "HistoricalSearchRepositoryImpl"
    }

    
    /**
     * アセットファイル定義テーブルの中から、要求された期間（[startDate]から[endDate]）をカバーしている、
     * あるいは重なっているエントリをフィルタリングして開始日時の昇順で取得します。
     *
     * @param stationId 観測所ID (ダムの観測所ID)
     * @param startDate 要求された検索開始日 (フォーマット: YYYYMMDD)
     * @param endDate 要求された検索終了日 (フォーマット: YYYYMMDD)
     * @return 条件に合致するアセットエントリのリスト
     */
    private fun findCoveringEntries(
        stationId: String,
        startDate: String,
        endDate: String
    ): List<HistoricalDatFileTable.Entry> {
        val requiredStartDate = TimeUtils.parseJstMillis(startDate, DATE_ONLY_FORMAT) ?: return emptyList()
        val requiredEndDate = TimeUtils.parseJstMillis(endDate, DATE_ONLY_FORMAT) ?: return emptyList()
        return HistoricalDatFileTable.entries
             .filter { it.stationId == stationId }
             .filter { entry ->
                 val entryStart = TimeUtils.parseJstMillis(entry.startDatetime.take(8), DATE_ONLY_FORMAT)
                     ?: return@filter false
                 val entryEnd = TimeUtils.parseJstMillis(entry.endDatetime.take(8), DATE_ONLY_FORMAT)
                     ?: return@filter false
                 entryStart <= requiredEndDate && entryEnd >= requiredStartDate
             }
             .sortedBy { it.startDatetime }
     }

    /**
     * 指定されたダムと期間の過去観測履歴データをロードしてデータベースに永続化します。
     *
     * 1. 重複検索の防止、sudmonitor 日次過去データの読込済み期間と完全に同一の検索の拒否（D2）、
     *    および保存上限数（最大16件）の事前チェックを行います。
     * 2. アセットにデータが存在する場合はアセットファイル（[HistoricalDatFileTable]）からロードを試みます。
     * 3. アセットでカバーできない場合は、設定のデータソースがsudmonitorならば「バンドル → sudmonitor（月次 → 日次）→ MLIT」の
     *    カスケード検索を実行し、全期間をカバーできた場合のみ保存します（[fetchAndStoreSudmonitorCascade]）。
     * 4. カスケードで網羅できない場合、または設定がMLIT直結の場合は、国土交通省の過去データ検索画面にアクセスし、
     *    .datファイルを動的スクレイピングして取得・解析します（[fetchAndStoreFromMlit]）。
     * 5. 取得したメタ情報と明細データを同一のデータベーストランザクション内で保存します。
     *
     * 成功・失敗のデバッグログは本メソッドの分岐内で完結して記録します。バンドル完結の成功と、
     * 事前チェック/検証エラー（重複・保存上限・非対応ダム・ネットワーク事前チェック失敗）はログを出力しません。
     * DAO / network availabilityなどの通常例外はLeft化し、Coroutine cancellationは再スローします。
     *
     * @param damConfig 対象ダムの静的設定（[DamConfig]）
     * @param startDate 検索開始日（フォーマット: YYYYMMDD）
     * @param endDate 検索終了日（フォーマット: YYYYMMDD）
     * @return 成功時は保存完了した検索メタデータ[HistoricalSearchMeta]、失敗時は例外を包んだ[Either]
     */
    override suspend fun fetchAndStore(
        damConfig: DamConfig,
        startDate: String,
        endDate: String
    ): Either<Throwable, HistoricalSearchMeta> = either {
        val searchUrl = damConfig.historicalSearchUrl(startDate, endDate)
        ensure(searchUrl.isNotEmpty()) {
            IllegalArgumentException(context.getString(R.string.historical_search_error_dam_not_supported))
        }

        val duplicateCount = catchNonCancellationSuspend {
            historicalSearchDao.countDuplicates(damConfig.id, startDate, endDate)
        }.bind()
        ensure(duplicateCount == 0) {
            IllegalStateException(context.getString(R.string.historical_search_error_duplicate))
        }

        // D2: 検索拒否 — ゲート有効かつ対象ダムの sudmonitor_history 行が存在し、
        // 検索期間が行の [periodStart, periodEnd]（日付区間）と完全に同一の場合のみ拒否
        val sudmonitorDailyLoaded = catchNonCancellationSuspend {
            isSudmonitorDailyLoadedExactMatch(damConfig.id, startDate, endDate)
        }.bind()
        ensure(!sudmonitorDailyLoaded) {
            HistoricalSearchError.SudmonitorDailyHistoryLoaded.toThrowable()
        }

        val maxCount = HistoricalSearchRepository.MAX_STORED_META_COUNT
        val storedMetaCount = catchNonCancellationSuspend {
            historicalSearchDao.countMeta()
        }.bind()
        ensure(storedMetaCount < maxCount) {
            Exception(context.resources.getQuantityString(
                R.plurals.historical_search_error_max_count,
                maxCount, maxCount
            ))
        }

        // 分岐1: バンドル全区間カバー（成功ログは出力しない）
        if (canCoverFromLocalFiles(damConfig.id, startDate, endDate)) {
            val (meta, dataList) = loadFromLocalFiles(damConfig, startDate, endDate).bind()
            storeHistoricalData(meta, dataList).bind()
        } else {
            // ネットワーク確認はカスケード・MLITのいずれでも共通の事前チェック
            val isNetworkAvailable = catchNonCancellationSuspend {
                networkAvailability.isNetworkAvailable()
            }.bind()
            ensure(isNetworkAvailable) {
                Exception(context.getString(R.string.historical_search_error_network_unavailable))
            }
            val historicalDataSource = catchNonCancellationSuspend {
                settingsRepository.appSettingsFlow.first().historicalDataSource
            }.bind()

            if (historicalDataSource == RealtimeDataSource.SUDMONITOR) {
                // 分岐2: sudmonitorカスケード（失敗時は検索区間全体をMLITで取得）
                when (val cascade = fetchAndStoreSudmonitorCascade(damConfig, startDate, endDate)) {
                    is SudmonitorCascadeResult.Success -> {
                        val meta = try {
                            storeHistoricalData(cascade.meta, cascade.dataList).bind()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            logHistoricalFailure(damConfig, startDate, endDate, e)
                            throw e
                        }
                        logHistoricalSuccess(damConfig, startDate, endDate, SUDMONITOR_HOST)
                        meta
                    }
                    is SudmonitorCascadeResult.FallbackToMlit ->
                        fetchAndStoreFromMlit(damConfig, startDate, endDate, searchUrl).bind()
                }
            } else {
                // 分岐3: 既存のMLIT全区間取得
                fetchAndStoreFromMlit(damConfig, startDate, endDate, searchUrl).bind()
            }
        }
    }

    /**
     * カスケード検索の結果を表す内部型。
     *
     * [Success]はバンドルとsudmonitorのフラグメントだけで検索期間全体をカバーできたことを示し、
     * [FallbackToMlit]は何らかの理由（404・5xx・タイムアウト・ヘッダ欠落・ヘッダ非パース・カバレッジ不足・
     * フラグメントのパース失敗等）で網羅できないため、検索区間全体をMLITで取得し直す必要があることを示す。
     */
    private sealed interface SudmonitorCascadeResult {
        data class Success(
            val meta: HistoricalSearchMeta,
            val dataList: List<DamHistoricalData>
        ) : SudmonitorCascadeResult

        data object FallbackToMlit : SudmonitorCascadeResult
    }

    /**
     * 「バンドル → sudmonitor（月次 → 日次）」のカスケード検索プランナ。
     *
     * バンドルと読込済み sudmonitor 日次過去データ（専用テーブル、D8）のカバー区間の補集合（ギャップ）を求め、
     * ギャップと交差する月の月次ファイルを [SudmonitorHistoricalClient.fetchMonthly] で取得し、それでも残るギャップを
     * [SudmonitorHistoricalClient.fetchLatest] で補完します。各レスポンスの since/until ヘッダーを
     * [OffsetDateTime.parse] で解釈して日単位（JST）のカバー区間を累積し、残ギャップが空になった場合のみ、
     * バンドル・ローカル・sudmonitor の全フラグメントを優先度順（バンドル ≧ ローカル > 月次 > latest）でマージして
     * [SudmonitorCascadeResult.Success] を返します。
     *
     * カバレッジ計算では、until が `00:00`（24:00表記）の場合は前日までをカバーとみなします。
     * ヘッダ欠落・パース失敗は「カバレッジ不明」としてカバーなし扱いにし、過剰なカバレッジ主張は行いません。
     * ローカルカバレッジの統合は「ゲート有効（本カスケードの呼出条件で保証される）かつ行が存在する場合」のみで、
     * 行未保存時は従来どおりバンドルカバレッジのみから開始します。
     *
     * @param damConfig 対象ダムの静的設定（[DamConfig]）
     * @param startDate 検索開始日（フォーマット: YYYYMMDD）
     * @param endDate 検索終了日（フォーマット: YYYYMMDD）
     * @return カスケード成功時はマージ済みメタデータとデータのペア、網羅不可時は[SudmonitorCascadeResult.FallbackToMlit]
     */
    private suspend fun fetchAndStoreSudmonitorCascade(
        damConfig: DamConfig,
        startDate: String,
        endDate: String
    ): SudmonitorCascadeResult = try {
        val requiredStartDate = TimeUtils.parseJstMillis(startDate, DATE_ONLY_FORMAT)
            ?: return SudmonitorCascadeResult.FallbackToMlit
        val requiredEndDate = TimeUtils.parseJstMillis(endDate, DATE_ONLY_FORMAT)
            ?: return SudmonitorCascadeResult.FallbackToMlit

        // バンドルフラグメント（dedupe優先度: バンドル ≧ ローカル > 月次 > latest）
        val bundleFragments = mutableListOf<ByteArray>()
        for (entry in findCoveringEntries(damConfig.id, startDate, endDate)) {
            val bytes = runCatching {
                context.assets.open(entry.filePath).use { it.readBytes() }
            }.getOrNull() ?: return SudmonitorCascadeResult.FallbackToMlit
            bundleFragments += bytes
        }

        // D8: 読込済み日次過去データのローカルカバレッジ（ゲート有効は本カスケードの呼出条件で保証済み）。
        // ローカル観測行はパース済みのため、ネットワーク取得・再パースをせずそのままフラグメントへ載せる
        val loadedCoverage = sudmonitorLoadedCoverage(damConfig.id, startDate, endDate)
        val localObservations = if (loadedCoverage != null) {
            catchNonCancellationSuspend {
                sudmonitorHistoryDao.getAllObservations(damConfig.id).map { it.toDomain() }
            }.getOrNull().orEmpty()
        } else {
            emptyList()
        }

        // 初期カバー区間（バンドル + ローカル）と残ギャップ
        var covered = mergeRanges(
            coveredRanges(damConfig.id, startDate, endDate) + listOfNotNull(loadedCoverage)
        )
        var gaps = complementOf(covered, requiredStartDate, requiredEndDate)

        // ギャップと交差する月の月次ファイルを取得
        val sudmonitorFragments = mutableListOf<ByteArray>()
        for (month in monthsIntersecting(gaps)) {
            if (gaps.isEmpty()) break
            when (val fetched = sudmonitorHistoricalClient.fetchMonthly(damConfig.id, month)) {
                is Either.Left ->
                    Log.w(LOG_TAG, "sudmonitor 月次履歴の取得に失敗しました。dam=${damConfig.id}, month=$month", fetched.value)
                is Either.Right -> {
                    val result = fetched.value ?: continue // 404: その月はカバーなし
                    val coverage = sudmonitorCoverage(result, requiredStartDate, requiredEndDate)
                        ?: continue // ヘッダ欠落・パース失敗はカバーなし扱い
                    covered = mergeRanges(covered + listOf(coverage))
                    gaps = complementOf(covered, requiredStartDate, requiredEndDate)
                    sudmonitorFragments += result.bytes
                }
            }
        }

        // 残ギャップがあれば日次（latest）で補完
        if (gaps.isNotEmpty()) {
            when (val fetched = sudmonitorHistoricalClient.fetchLatest(damConfig.id)) {
                is Either.Left ->
                    Log.w(LOG_TAG, "sudmonitor 日次履歴の取得に失敗しました。dam=${damConfig.id}", fetched.value)
                is Either.Right -> {
                    val result = fetched.value
                    if (result != null) {
                        val coverage = sudmonitorCoverage(result, requiredStartDate, requiredEndDate)
                        if (coverage != null) {
                            covered = mergeRanges(covered + listOf(coverage))
                            gaps = complementOf(covered, requiredStartDate, requiredEndDate)
                            sudmonitorFragments += result.bytes
                        }
                    }
                }
            }
        }

        // 残ギャップが空になった場合のみ成功（部分取得は破棄してMLITへ委譲）
        if (gaps.isNotEmpty()) return SudmonitorCascadeResult.FallbackToMlit

        // 全フラグメントを優先度順（バンドル → ローカル → 月次 → latest）でパース・マージ
        // ローカル観測行はパース済み（Room の観測明細）のため再パースせず、バンドルと月次の間にそのまま載せる
        val parsedFragments = mutableListOf<Pair<HistoricalSearchMeta, List<DamHistoricalData>>>()
        for (bytes in bundleFragments) {
            when (val parsed = fileParser.parseHistoricalDatToDataList(bytes, damConfig.id, startDate, endDate)) {
                is Either.Left -> return SudmonitorCascadeResult.FallbackToMlit
                is Either.Right -> parsedFragments += parsed.value
            }
        }
        if (localObservations.isNotEmpty()) {
            parsedFragments += buildSudmonitorHistoryLocalMeta(damConfig, startDate, endDate) to localObservations
        }
        for (bytes in sudmonitorFragments) {
            when (val parsed = fileParser.parseHistoricalDatToDataList(bytes, damConfig.id, startDate, endDate)) {
                is Either.Left -> return SudmonitorCascadeResult.FallbackToMlit
                is Either.Right -> parsedFragments += parsed.value
            }
        }
        if (parsedFragments.isEmpty()) return SudmonitorCascadeResult.FallbackToMlit

        val (meta, dataList) = mergeFragments(parsedFragments, requiredStartDate, requiredEndDate)
        SudmonitorCascadeResult.Success(meta, dataList)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(LOG_TAG, "sudmonitor カスケード検索に失敗したため MLIT へ委譲します。dam=${damConfig.id}", e)
        SudmonitorCascadeResult.FallbackToMlit
    }

    /**
     * 検索期間全体を国土交通省（MLIT）から取得して保存します。
     *
     * HTML取得・DatUrl抽出・.dat取得・パースのいずれかが失敗した場合は、失敗デバッグログ（Data Source: MLIT）を
     * 記録してLeft化します。保存失敗はMLITフェッチ/パース失敗ではないためログは出力しません。
     *
     * @param damConfig 対象ダムの静的設定（[DamConfig]）
     * @param startDate 検索開始日（フォーマット: YYYYMMDD）
     * @param endDate 検索終了日（フォーマット: YYYYMMDD）
     * @param searchUrl MLITの過去データ検索画面URL
     * @return 成功時は保存完了した検索メタデータ[HistoricalSearchMeta]、失敗時は例外を包んだ[Either]
     */
    private suspend fun fetchAndStoreFromMlit(
        damConfig: DamConfig,
        startDate: String,
        endDate: String,
        searchUrl: String
    ): Either<Throwable, HistoricalSearchMeta> = either {
        val (meta, dataList) = when (val fetched = fetchMlitHistoricalData(damConfig, startDate, endDate, searchUrl)) {
            is Either.Left -> {
                logHistoricalFailure(damConfig, startDate, endDate, fetched.value)
                fetched.bind()
            }
            is Either.Right -> fetched.value
        }
        val storedMeta = storeHistoricalData(meta, dataList).bind()
        logHistoricalSuccess(damConfig, startDate, endDate, MLIT_DATA_SOURCE_LABEL)
        storedMeta
    }

    /**
     * 国土交通省（MLIT）からHTMLと.datを取得してパースします（デバッグログなし）。
     *
     * @param damConfig 対象ダムの静的設定（[DamConfig]）
     * @param startDate 検索開始日（フォーマット: YYYYMMDD）
     * @param endDate 検索終了日（フォーマット: YYYYMMDD）
     * @param searchUrl MLITの過去データ検索画面URL
     * @return 成功時はパース済みのメタデータとデータのペア、失敗時は例外を包んだ[Either]
     */
    private suspend fun fetchMlitHistoricalData(
        damConfig: DamConfig,
        startDate: String,
        endDate: String,
        searchUrl: String
    ): Either<Throwable, Pair<HistoricalSearchMeta, List<DamHistoricalData>>> = either {
        val htmlBytes = networkDataSource.fetchBytes(searchUrl).bind()
        val datUrl = parseHistoricalDatUrl(htmlBytes)
            .mapLeft { it.toThrowable() }
            .bind()
        val datBytes = networkDataSource.fetchBytes(datUrl).bind()
        fileParser.parseHistoricalDatToDataList(datBytes, damConfig.id, startDate, endDate).bind()
    }

    
    /**
     * 要求された検索期間（[startDate]から[endDate]）が、アセット内にあらかじめ内包されている過去データファイル群で
     * 完全にカバーされている（＝欠損なく日付が連続して網羅されている）かどうかを判定します。
     *
     * @param stationId 観測所ID (ダムの観測所ID)
     * @param startDate 検索開始日 (フォーマット: YYYYMMDD)
     * @param endDate 検索終了日 (フォーマット: YYYYMMDD)
     * @return アセットファイルで完全にカバー可能な場合はtrue、そうでない（通信が必要な）場合はfalse
     */
    private fun canCoverFromLocalFiles(stationId: String, startDate: String, endDate: String): Boolean {
        val requiredStartDate = TimeUtils.parseJstMillis(startDate, DATE_ONLY_FORMAT) ?: return false
        val requiredEndDate = TimeUtils.parseJstMillis(endDate, DATE_ONLY_FORMAT) ?: return false
        // クリップ済み・マージ済みのカバー区間が [requiredStartDate, requiredEndDate] を1区間で覆う場合のみ完全カバー
        val ranges = coveredRanges(stationId, startDate, endDate)
        return ranges.isNotEmpty() &&
            ranges.first().first <= requiredStartDate &&
            ranges.first().last >= requiredEndDate
    }

    /**
     * 要求された検索期間（[startDate]から[endDate]）をアセット内の過去データファイル群がカバーする区間一覧を返します。
     *
     * 日単位（各日のJST 0時0分のミリ秒表現）で、重複・隣接（前区間の翌日から始まる）を統合し、
     * [requiredStartDate, requiredEndDate] にクリップしたカバー区間を昇順で返します。
     * 検索期間をパースできない場合は空リストを返します。
     *
     * @param stationId 観測所ID (ダムの観測所ID)
     * @param startDate 検索開始日 (フォーマット: YYYYMMDD)
     * @param endDate 検索終了日 (フォーマット: YYYYMMDD)
     * @return カバー区間のリスト（開始日時ミリ秒の昇順）
     */
    private fun coveredRanges(stationId: String, startDate: String, endDate: String): List<LongRange> {
        val requiredStartDate = TimeUtils.parseJstMillis(startDate, DATE_ONLY_FORMAT) ?: return emptyList()
        val requiredEndDate = TimeUtils.parseJstMillis(endDate, DATE_ONLY_FORMAT) ?: return emptyList()

        val intervals = findCoveringEntries(stationId, startDate, endDate).mapNotNull { entry ->
            val entryStartDate = TimeUtils.parseJstMillis(entry.startDatetime.take(8), DATE_ONLY_FORMAT)
                ?: return@mapNotNull null
            val entryEndDate = TimeUtils.parseJstMillis(entry.endDatetime.take(8), DATE_ONLY_FORMAT)
                ?: return@mapNotNull null
            entryStartDate..entryEndDate
        }

        return mergeRanges(intervals).mapNotNull { range ->
            val clippedStart = maxOf(range.first, requiredStartDate)
            val clippedEnd = minOf(range.last, requiredEndDate)
            if (clippedStart > clippedEnd) null else clippedStart..clippedEnd
        }
    }

    /**
     * ソートされていない日単位の区間リストをマージして、重複・隣接（前区間の翌日から始まる）のない昇順の区間リストにします。
     *
     * @param ranges マージ対象の区間リスト（日単位の開始日時ミリ秒）
     * @return マージ済みの区間リスト（昇順）
     */
    private fun mergeRanges(ranges: List<LongRange>): List<LongRange> {
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<LongRange>()
        for (range in sorted) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last + MILLIS_PER_DAY) {
                merged[merged.size - 1] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }

    /**
     * カバー区間の補集合（ギャップ）を [requiredStartDate, requiredEndDate] の範囲内で列挙します。
     *
     * @param covered 日単位のカバー区間リスト（[mergeRanges]済み・昇順）
     * @param requiredStartDate 検索期間の開始日時ミリ秒
     * @param requiredEndDate 検索期間の終了日時ミリ秒
     * @return カバーされていない区間のリスト（昇順）
     */
    private fun complementOf(
        covered: List<LongRange>,
        requiredStartDate: Long,
        requiredEndDate: Long
    ): List<LongRange> {
        if (covered.isEmpty()) return listOf(requiredStartDate..requiredEndDate)
        val gaps = mutableListOf<LongRange>()
        var cursor = requiredStartDate
        for (range in covered) {
            if (range.first > cursor) {
                gaps += cursor..(range.first - MILLIS_PER_DAY)
            }
            cursor = maxOf(cursor, range.last + MILLIS_PER_DAY)
        }
        if (cursor <= requiredEndDate) {
            gaps += cursor..requiredEndDate
        }
        return gaps
    }

    /**
     * sudmonitorの履歴レスポンスの since/until ヘッダーを解釈して、日単位（JST）のカバー区間を計算します。
     *
     * since/until は [OffsetDateTime.parse]（ISO-8601）で解釈し、ヘッダ欠落・パース失敗は「カバレッジ不明」として
     * null（カバーなし扱い）を返します。until が `00:00`（24:00表記）の場合は前日までをカバーとみなします。
     * 計算結果は [requiredStartDate, requiredEndDate] にクリップして返します。
     *
     * @param result sudmonitorの履歴レスポンス
     * @param requiredStartDate 検索期間の開始日時ミリ秒
     * @param requiredEndDate 検索期間の終了日時ミリ秒
     * @return カバー区間、ヘッダ欠落・パース失敗・要求期間と交差しない場合はnull
     */
    private fun sudmonitorCoverage(
        result: SudmonitorHistoricalResult,
        requiredStartDate: Long,
        requiredEndDate: Long
    ): LongRange? {
        val since = result.since?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return null
        val until = result.until?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return null
        val startDay = since.atZoneSameInstant(TimeUtils.JST_ZONE).toLocalDate()
        val untilDay = until.atZoneSameInstant(TimeUtils.JST_ZONE).toLocalDate()
        // until が 00:00 の場合は 24:00 表記（前日まで）として扱う
        val endDay = if (until.toLocalTime() == LocalTime.MIDNIGHT) untilDay.minusDays(1) else untilDay
        val startMillis = startDay.atStartOfDay(TimeUtils.JST_ZONE).toInstant().toEpochMilli()
        val endMillis = endDay.atStartOfDay(TimeUtils.JST_ZONE).toInstant().toEpochMilli()
        if (endMillis < startMillis) return null
        val clippedStart = maxOf(startMillis, requiredStartDate)
        val clippedEnd = minOf(endMillis, requiredEndDate)
        if (clippedStart > clippedEnd) return null
        return clippedStart..clippedEnd
    }

    /**
     * 対象ダムの読込済み sudmonitor 日次過去データ（`sudmonitor_history` 行）の実カバレッジを、
     * 要求された検索期間（[startDate]から[endDate]）にクリップした日単位（JST）の区間として返します（D8）。
     *
     * `periodStartEpochMs` / `periodEndEpochMs` は保存規約上すでに「最終被覆日の JST 00:00」の日単位ミリ秒であり、
     * [coveredRanges] の日単位区間（開始日〜終了日の包摂）と同じ境界条件でそのまま統合できる。
     * ヘッダ欠落（null）の行・行未保存・要求期間と交差しない場合は null（カバレッジ主張なし）を返します。
     *
     * @param damId 対象ダムの観測所ID
     * @param startDate 検索開始日（フォーマット: YYYYMMDD）
     * @param endDate 検索終了日（フォーマット: YYYYMMDD）
     * @return クリップ済みのカバー区間、カバレッジ不明・交差なしの場合は null
     */
    private suspend fun sudmonitorLoadedCoverage(
        damId: String,
        startDate: String,
        endDate: String
    ): LongRange? {
        val requiredStartDate = TimeUtils.parseJstMillis(startDate, DATE_ONLY_FORMAT) ?: return null
        val requiredEndDate = TimeUtils.parseJstMillis(endDate, DATE_ONLY_FORMAT) ?: return null
        val row = catchNonCancellationSuspend {
            sudmonitorHistoryDao.findByDamId(damId)
        }.getOrNull() ?: return null
        val periodStartEpochMs = row.periodStartEpochMs ?: return null
        val periodEndEpochMs = row.periodEndEpochMs ?: return null
        if (periodEndEpochMs < periodStartEpochMs) return null
        val clippedStart = maxOf(periodStartEpochMs, requiredStartDate)
        val clippedEnd = minOf(periodEndEpochMs, requiredEndDate)
        if (clippedStart > clippedEnd) return null
        return clippedStart..clippedEnd
    }

    /**
     * D2: 検索拒否の判定を行います。
     *
     * 機能ゲート（[AppSettings.historicalDataSource] が [RealtimeDataSource.SUDMONITOR]）が有効かつ、
     * 対象ダムに `sudmonitor_history` 行が存在し、検索期間（[startDate]/[endDate]）が行の
     * [SudmonitorHistoryEntity.periodStartEpochMs]/[periodEndEpochMs] が示す日付区間と**完全に同一**の場合のみ
     * true を返します。期間ヘッダ欠落（null）の行・行未保存・ゲート無効は拒否対象外（false）です。
     * 真部分集合・上位集合・部分重複は拒否しません（D8 のローカルカバレッジ＋残ギャップのカスケードで提供）。
     *
     * 完全一致判定は、periodStart/periodEnd が「最終被覆日の JST 00:00（包摂）」を表す保存規約に合わせ、
     * 開始日・終了日の JST 日付をそれぞれ periodStartEpochMs / periodEndEpochMs の日付と直接比較します
     * （[coveredRanges] と同じ日単位の境界条件）。
     *
     * @param damId 対象ダムの観測所ID
     * @param startDate 検索開始日（フォーマット: YYYYMMDD）
     * @param endDate 検索終了日（フォーマット: YYYYMMDD）
     * @return 拒否すべき場合は true
     */
    private suspend fun isSudmonitorDailyLoadedExactMatch(
        damId: String,
        startDate: String,
        endDate: String
    ): Boolean {
        val settings = catchNonCancellationSuspend {
            settingsRepository.appSettingsFlow.first()
        }.getOrNull() ?: return false
        if (settings.historicalDataSource != RealtimeDataSource.SUDMONITOR) return false
        val row = catchNonCancellationSuspend {
            sudmonitorHistoryDao.findByDamId(damId)
        }.getOrNull() ?: return false
        val periodStartEpochMs = row.periodStartEpochMs ?: return false
        val periodEndEpochMs = row.periodEndEpochMs ?: return false
        val startMillis = TimeUtils.parseJstMillis(startDate, DATE_ONLY_FORMAT) ?: return false
        val endMillis = TimeUtils.parseJstMillis(endDate, DATE_ONLY_FORMAT) ?: return false
        return startMillis == periodStartEpochMs && endMillis == periodEndEpochMs
    }

    /**
     * 読込済み sudmonitor 日次過去データ（ローカル観測行）のフラグメント用メタデータを組み立てます（D8）。
     *
     * 観測明細は Room の `sudmonitor_history_observation` からパース済みのまま取得するため、ヘッダ解析・再パースは
     * 行いません。メタデータの検索期間は要求された検索期間（[startDate]/[endDate]）をそのまま引き継ぎます
     * （既存 [DamFileParser] のメタデータ構築と同じ情報源・同じフィールド構成）。
     *
     * @param damConfig 対象ダムの静的設定（[DamConfig]）
     * @param startDate 検索開始日（フォーマット: YYYYMMDD）
     * @param endDate 検索終了日（フォーマット: YYYYMMDD）
     * @return ローカル観測行フラグメントの[HistoricalSearchMeta]
     */
    private fun buildSudmonitorHistoryLocalMeta(
        damConfig: DamConfig,
        startDate: String,
        endDate: String
    ): HistoricalSearchMeta =
        HistoricalSearchMeta(
            id = 0L,
            observationStationId = damConfig.id,
            observationStationName = damConfig.nameJa,
            riverSystemName = damConfig.waterSystem,
            riverName = damConfig.river,
            damConfigId = damConfig.id,
            searchBgnDate = startDate,
            searchEndDate = endDate,
            fetchedAt = System.currentTimeMillis(),
            dataStartTimeStr = null,
            dataEndTimeStr = null,
            dataStartStoragePct = null,
            dataEndStoragePct = null,
            dataMinStoragePct = null,
            dataMaxStoragePct = null
        )

    /**
     * ギャップ区間と交差する月（[YearMonth]）を昇順で列挙します。
     *
     * 例: 2026-07-15〜2026-08-01 のギャップ → 2026-07, 2026-08。
     *
     * @param gaps 日単位のギャップ区間リスト
     * @return ギャップと交差する月のリスト（昇順・重複なし）
     */
    private fun monthsIntersecting(gaps: List<LongRange>): List<YearMonth> {
        val months = sortedSetOf<YearMonth>()
        for (gap in gaps) {
            val startMonth = YearMonth.from(Instant.ofEpochMilli(gap.first).atZone(TimeUtils.JST_ZONE))
            val endMonth = YearMonth.from(Instant.ofEpochMilli(gap.last).atZone(TimeUtils.JST_ZONE))
            var month = startMonth
            while (!month.isAfter(endMonth)) {
                months += month
                month = month.plusMonths(1)
            }
        }
        return months.toList()
    }

    /**
     * 優先度順（バンドル ≧ ローカル > 月次 > latest）のフラグメントをマージして、epochキーで重複排除・昇順ソートします。
     *
     * 各データ行の epoch キーは `TimeUtils.parseJstMillisAllow24Hour(item.time, TIME_FORMAT)`（24:00対応）で計算し、
     * 同一 epoch は先に出現した高優先度ソースの行を採用します。メタデータは最初のフラグメントのものを使用します。
     * 検索期間外の行は除外します。
     *
     * @param fragments 優先度順に並んだパース済みフラグメントのリスト
     * @param requiredStartDate 検索期間の開始日時ミリ秒
     * @param requiredEndDate 検索期間の終了日時ミリ秒
     * @return マージ・重複排除・ソート済みのメタデータとデータのペア
     */
    private fun mergeFragments(
        fragments: List<Pair<HistoricalSearchMeta, List<DamHistoricalData>>>,
        requiredStartDate: Long,
        requiredEndDate: Long
    ): Pair<HistoricalSearchMeta, List<DamHistoricalData>> {
        val meta = fragments.first().first
        val mergedData = mutableListOf<DamHistoricalData>()
        val seenEpochs = hashSetOf<Long>()
        for ((_, dataList) in fragments) {
            for (item in dataList) {
                val datePart = item.time.substringBefore(" ")
                val itemDate = TimeUtils.parseJstMillis(datePart, DAT_DATE_FORMAT) ?: continue
                if (itemDate !in requiredStartDate..requiredEndDate) continue
                val epoch = TimeUtils.parseJstMillisAllow24Hour(item.time, TIME_FORMAT) ?: continue
                if (seenEpochs.add(epoch)) {
                    mergedData += item
                }
            }
        }
        mergedData.sortBy { item ->
            TimeUtils.parseJstMillisAllow24Hour(item.time, TIME_FORMAT) ?: Long.MAX_VALUE
        }
        return meta to mergedData
    }

    /**
     * 過去データ検索の成功デバッグログを記録します（best-effort）。
     *
     * バンドル完結（ローカルファイルのみ）の成功では呼び出さない。
     *
     * @param damConfig 対象ダムの静的設定（[DamConfig]）
     * @param startDate 検索開始日（フォーマット: YYYYMMDD）
     * @param endDate 検索終了日（フォーマット: YYYYMMDD）
     * @param dataSource データソース表示名（sudmonitorホストまたは "MLIT"）
     */
    private suspend fun logHistoricalSuccess(
        damConfig: DamConfig,
        startDate: String,
        endDate: String,
        dataSource: String
    ) {
        runCatching {
            debugLogRepository.addEntry(
                SUCCESS_LOG_TITLE,
                historicalSearchDetails(dataSource, damConfig, startDate, endDate)
            )
        }
    }

    /**
     * 過去データ検索の失敗デバッグログを記録します（best-effort）。
     *
     * 記録対象はMLITフェッチ/パース失敗と、sudmonitorカスケード成功後の保存失敗のみ。
     * データソースは常に "MLIT" です。
     *
     * @param damConfig 対象ダムの静的設定（[DamConfig]）
     * @param startDate 検索開始日（フォーマット: YYYYMMDD）
     * @param endDate 検索終了日（フォーマット: YYYYMMDD）
     * @param error 失敗の原因となった例外
     */
    private suspend fun logHistoricalFailure(
        damConfig: DamConfig,
        startDate: String,
        endDate: String,
        error: Throwable
    ) {
        runCatching {
            debugLogRepository.addEntry(
                FAILURE_LOG_TITLE,
                historicalSearchDetails(MLIT_DATA_SOURCE_LABEL, damConfig, startDate, endDate, error.message)
            )
        }
    }

    /**
     * デバッグログの詳細文字列を組み立てます。
     *
     * 例: `Data Source: sudmonitor.kusugami-lab.net, Dam name: Sameura Dam, Period(Start): 2026-07-01T00:00:00+09:00, Period(End): 2026-07-31T00:00:00+09:00`
     *
     * @param dataSource データソース表示名
     * @param damConfig 対象ダムの静的設定（[DamConfig]）
     * @param startDate 検索開始日（フォーマット: YYYYMMDD）
     * @param endDate 検索終了日（フォーマット: YYYYMMDD）
     * @param reason 失敗理由（成功時はnull）
     * @return デバッグログの詳細文字列
     */
    private fun historicalSearchDetails(
        dataSource: String,
        damConfig: DamConfig,
        startDate: String,
        endDate: String,
        reason: String? = null
    ): String {
        val periodStart = formatPeriodDate(startDate)
        val periodEnd = formatPeriodDate(endDate)
        val base = "Data Source: $dataSource, Dam name: ${damConfig.nameEn}, " +
            "Period(Start): $periodStart, Period(End): $periodEnd"
        return if (reason == null) base else "$base, Reason: $reason"
    }

    /**
     * YYYYMMDD形式の日付をデバッグログ用のISO-8601（JST）形式へ変換します。パースできない場合は元の文字列を返します。
     */
    private fun formatPeriodDate(date: String): String {
        val millis = TimeUtils.parseJstMillis(date, DATE_ONLY_FORMAT) ?: return date
        return TimeUtils.formatToJst(millis, PERIOD_DATE_TIME_FORMAT)
    }

    /**
     * 該当する期間をカバーするローカルアセットファイルからデータを読み込み、期間に合致する観測データをマージ・重複排除して返します。
     *
     * @param damConfig 対象ダムの静的設定（[DamConfig]）
     * @param startDate 検索開始日 (フォーマット: YYYYMMDD)
     * @param endDate 検索終了日 (フォーマット: YYYYMMDD)
     * @return 成功時はマージ後のメタデータと過去観測データのペア、失敗時は例外
     */
    private suspend fun loadFromLocalFiles(
        damConfig: DamConfig,
        startDate: String,
        endDate: String
    ): Either<Throwable, Pair<HistoricalSearchMeta, List<DamHistoricalData>>> = catchNonCancellation {
        val requiredStartDate = TimeUtils.parseJstMillis(startDate, DATE_ONLY_FORMAT)
            ?: throw Exception(context.getString(R.string.historical_search_error_parse_start_date))
        val requiredEndDate = TimeUtils.parseJstMillis(endDate, DATE_ONLY_FORMAT)
            ?: throw Exception(context.getString(R.string.historical_search_error_parse_end_date))

        
        val coveringEntries = findCoveringEntries(damConfig.id, startDate, endDate)

        if (coveringEntries.isEmpty()) throw Exception(context.getString(R.string.historical_search_error_local_file_not_found))

        var mergedMeta: HistoricalSearchMeta? = null
        val mergedDataList = mutableListOf<DamHistoricalData>()

        for (entry in coveringEntries) {
            val datBytes = context.assets.open(entry.filePath).use { it.readBytes() }
            fileParser.parseHistoricalDatToDataList(datBytes, damConfig.id, startDate, endDate)
                .fold(
                    ifLeft = { throw it },
                    ifRight = { (meta, dataList) ->
                        if (mergedMeta == null) mergedMeta = meta
                        
                        val filteredData = dataList.filter { item ->
                            val datePart = item.time.substringBefore(" ")
                            val itemDate = TimeUtils.parseJstMillis(datePart, DAT_DATE_FORMAT)
                                ?: return@filter false
                            itemDate in requiredStartDate..requiredEndDate
                        }
                        mergedDataList.addAll(filteredData)
                    }
                )
        }

        val finalMeta = mergedMeta ?: throw Exception(context.getString(R.string.historical_search_error_meta_fetch_failed))

        
        val deduplicatedData = mergedDataList
            .distinctBy { it.time }
            .sortedBy { item ->
                TimeUtils.parseJstMillisAllow24Hour(item.time, TIME_FORMAT) ?: Long.MAX_VALUE
            }

        finalMeta to deduplicatedData
    }

    
    /**
     * 検索結果メタデータと観測履歴データ一覧を、同一のデータベーストランザクション内でRoomデータベースに保存します。
     *
     * 1. 入力データの存在チェックおよび最大件数・重複レコードのトランザクション内再チェックを行います。
     * 2. 表示順（ソートオーダー）を自動的にずらし（最新の検索結果が最上位になるようインクリメント）、新規メタデータをインサートします。
     * 3. 紐づく観測履歴データ一覧を一括インサートします。
     *
     * 保存用時刻はtransaction前に検証します。MLIT仕様上 `24:00` は有効ですが、`24:01` や `25:00` は
     * 不正データとしてLeft化し、部分insertを発生させません。
     *
     * @param meta 永続化対象の検索履歴メタデータ
     * @param dataList 紐づく過去観測データのリスト
     * @return 成功時は自動生成されたIDを含む永続化済みの[HistoricalSearchMeta]、失敗時は例外
     */
    private suspend fun storeHistoricalData(
        meta: HistoricalSearchMeta,
        dataList: List<DamHistoricalData>
    ): Either<Throwable, HistoricalSearchMeta> = catchNonCancellationSuspend {
        if (dataList.isEmpty()) {
            throw Exception(context.getString(R.string.historical_search_error_no_data))
        }

        
        val percentages = dataList.mapNotNull { it.storagePercentage }
        val firstItem = dataList.firstOrNull()
        val lastItem = dataList.lastOrNull()

        
        val entityTemps = dataList.map { item ->
            val timeMillis = TimeUtils.parseJstMillisAllow24Hour(item.time, TIME_FORMAT)
                ?: throw IllegalArgumentException("Invalid historical observation time: ${item.time}")
            Pair(item, timeMillis)
        }

        
        val metaEntity = HistoricalSearchMetaEntity(
            sortOrder = 0,
            observationStationId = meta.observationStationId,
            observationStationName = meta.observationStationName,
            riverSystemName = meta.riverSystemName,
            riverName = meta.riverName,
            damConfigId = meta.damConfigId,
            searchBgnDate = meta.searchBgnDate,
            searchEndDate = meta.searchEndDate,
            fetchedAt = meta.fetchedAt,
            dataStartTimeStr = firstItem?.time,
            dataEndTimeStr = lastItem?.time,
            dataStartStoragePct = firstItem?.storagePercentage,
            dataEndStoragePct = lastItem?.storagePercentage,
            dataMinStoragePct = if (percentages.isNotEmpty()) percentages.min() else null,
            dataMaxStoragePct = if (percentages.isNotEmpty()) percentages.max() else null
        )

        val insertedMetaId = transactionRunner.withTransaction {
            val maxCount = HistoricalSearchRepository.MAX_STORED_META_COUNT
            if (historicalSearchDao.countMeta() >= maxCount) {
                throw Exception(context.resources.getQuantityString(
                    R.plurals.historical_search_error_max_count,
                    maxCount, maxCount
                ))
            }
            if (historicalSearchDao.countDuplicates(meta.damConfigId, meta.searchBgnDate, meta.searchEndDate) > 0) {
                throw IllegalStateException(context.getString(R.string.historical_search_duplicate))
            }
            
            historicalSearchDao.shiftAllSortOrderUp()
            val metaId = historicalSearchDao.insertMeta(metaEntity)
            
            val entities = entityTemps.map { (item, timeMillis) ->
                item.toHistoricalEntity(timeMillis, metaId)
            }
            historicalSearchDao.insertDataList(entities)
            metaId
        }

        HistoricalSearchMeta(
            id = insertedMetaId,
            observationStationId = meta.observationStationId,
            observationStationName = meta.observationStationName,
            riverSystemName = meta.riverSystemName,
            riverName = meta.riverName,
            damConfigId = meta.damConfigId,
            searchBgnDate = meta.searchBgnDate,
            searchEndDate = meta.searchEndDate,
            fetchedAt = meta.fetchedAt,
            dataStartTimeStr = firstItem?.time,
            dataEndTimeStr = lastItem?.time,
            dataStartStoragePct = firstItem?.storagePercentage,
            dataEndStoragePct = lastItem?.storagePercentage,
            dataMinStoragePct = if (percentages.isNotEmpty()) percentages.min() else null,
            dataMaxStoragePct = if (percentages.isNotEmpty()) percentages.max() else null
        )
    }

    
    override suspend fun getMetaList(): List<HistoricalSearchMeta> =
        historicalSearchDao.getMetaList().map { it.toDomain() }

    
    override suspend fun getAllMetaList(): List<HistoricalSearchMeta> =
        historicalSearchDao.getAllMeta().map { it.toDomain() }

    
    override suspend fun getStoredMetaCount(): Int =
        historicalSearchDao.countMeta()

    
    override suspend fun getMetaById(metaId: Long): HistoricalSearchMeta? =
        historicalSearchDao.getMetaById(metaId)?.toDomain()

    
    override suspend fun getAllDataByMetaId(metaId: Long): List<DamHistoricalData> =
        historicalSearchDao.queryAllDataByMetaId(metaId).map { it.toDomain() }

    
    override suspend fun getDataByMetaIdAndTimeRange(
        metaId: Long,
        fromMillis: Long,
        toMillis: Long
    ): List<DamHistoricalData> =
        historicalSearchDao.queryDataByMetaIdAndTimeRange(metaId, fromMillis, toMillis)
            .map { it.toDomain() }

    
    override suspend fun getNewestTimeMillisByMetaId(metaId: Long): Long? =
        historicalSearchDao.queryNewestTimeMillisByMetaId(metaId)

    
    override suspend fun getOldestTimeMillisByMetaId(metaId: Long): Long? =
        historicalSearchDao.queryOldestTimeMillisByMetaId(metaId)

    
    override suspend fun deleteHistoricalData(metaId: Long) = transactionRunner.withTransaction {
        historicalSearchDao.deleteDataByMetaId(metaId)
        historicalSearchDao.deleteMetaById(metaId)
    }

    
    override suspend fun reorderHistoricalMeta(orderedIds: List<Long>) = transactionRunner.withTransaction {
        orderedIds.forEachIndexed { index, id ->
            historicalSearchDao.updateSortOrder(id, index)
        }
    }

    
    override suspend fun checkDuplicate(damConfigId: String, startDate: String, endDate: String): Boolean =
        historicalSearchDao.countDuplicates(damConfigId, startDate, endDate) > 0

    
    override suspend fun deleteAllHistoricalData() {
        historicalSearchDao.deleteAllData()
        historicalSearchDao.deleteAllMeta()
    }

    override suspend fun setPinned(metaId: Long, isPinned: Boolean) {
        historicalSearchDao.updatePinnedStatus(metaId, isPinned)
    }

    override suspend fun countPinnedMeta(): Int =
        historicalSearchDao.countPinnedMeta()

    /** `.dat` URL抽出の診断情報を内部errorに保持し、公開境界では既存メッセージへ変換する。 */
    private fun parseHistoricalDatUrl(htmlBytes: ByteArray): Either<HistoricalSearchError, String> =
        fileParser.parseHtmlForDatUrlResult(htmlBytes)
            .mapLeft { HistoricalSearchError.DatUrlNotFound(it) }

    private fun HistoricalSearchError.toThrowable(): Throwable =
        when (this) {
            is HistoricalSearchError.DatUrlNotFound ->
                Exception(context.getString(R.string.historical_search_error_dat_url_not_found_html))
            is HistoricalSearchError.SudmonitorDailyHistoryLoaded ->
                Exception(context.getString(R.string.historical_search_error_sudmonitor_daily_loaded))
        }
}
