// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local

import arrow.core.Either
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamParseError
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonPoint
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import java.util.concurrent.CancellationException

/**
 * バンドルassets内のdatファイルから、過去比較グラフ用の観測データを読み込むストア。
 *
 * 早明浦ダムの月次datファイルを読み取り、[HistoricalComparisonPoint]へ射影して返す。
 * 通信・DBには一切アクセスせず、読み込んだ月分データはLRUキャッシュとin-flight重複排除で
 * 管理する。Web版（HistoricalComparisonAssetStore）の移植であり、並列ファイル読込は
 * [MAX_CONCURRENT_MONTHS]で制限される。
 *
 * @property assetBytesReader assets内のファイルをバイト列として読み出すリーダ
 * @property fileParser datファイルのパーサ
 * @property maxCachedFiles キャッシュする月分ファイルの最大件数（超過時は最も古いものを破棄）
 */
class HistoricalComparisonAssetStore(
    private val assetBytesReader: AssetBytesReader,
    private val fileParser: DamFileParser,
    internal val maxCachedFiles: Int = DEFAULT_MAX_CACHED_FILES
) {
    companion object {
        /** キャッシュ可能な月分データのデフォルト最大件数 */
        const val DEFAULT_MAX_CACHED_FILES = 128

        /** ファイル読込+parseの最大並列数 */
        const val MAX_CONCURRENT_MONTHS = 4

        /** バンドル過去データの開始年 */
        const val FIRST_YEAR = 2002

        /** 1日（24時間）のミリ秒数 */
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }

    /** 月分データのLRUキャッシュ。キーは[HistoricalDatFileTable.Entry.filePath] */
    private val cache = LinkedHashMap<String, List<HistoricalComparisonPoint>>(0, 0.75f, true)
    private val cacheMutex = Mutex()

    /** in-flight読込の重複排除テーブル。キーは[HistoricalDatFileTable.Entry.filePath] */
    private val loading =
        mutableMapOf<String, CompletableDeferred<Either<Throwable, List<HistoricalComparisonPoint>>>>()
    private val loadingMutex = Mutex()

    /**
     * 指定された期間（両端含む）の観測データを取得します。
     *
     * 対象期間を含む月次datファイルを選択して読み込み、期間外の観測を切り詰めた上で
     * 時刻の昇順・重複除去して返します。対象ファイルが無い場合は正常な空リストを返します。
     *
     * @param startMillis 期間開始エポックミリ秒（含む）
     * @param endMillis 期間終了エポックミリ秒（含む）
     * @return 成功時は期間内の観測データリスト、失敗時はエラーを包んだ[Either]
     */
    suspend fun loadRange(startMillis: Long, endMillis: Long): Either<Throwable, List<HistoricalComparisonPoint>> {
        if (endMillis < startMillis) return Either.Right(emptyList())
        val entries = selectEntries(startMillis, endMillis)
        if (entries.isEmpty()) return Either.Right(emptyList())
        val semaphore = Semaphore(MAX_CONCURRENT_MONTHS)
        return coroutineScope {
            val results = entries.map { entry ->
                async { loadEntry(entry, semaphore) }
            }.awaitAll()
            var firstLeft: Either<Throwable, List<HistoricalComparisonPoint>>? = null
            val points = mutableListOf<HistoricalComparisonPoint>()
            for (result in results) {
                when (result) {
                    is Either.Left -> {
                        if (firstLeft == null) firstLeft = result
                    }
                    is Either.Right -> points += result.value
                }
            }
            firstLeft ?: Either.Right(
                points
                    .filter { it.epochMillis in startMillis..endMillis }
                    .distinctBy { it.epochMillis }
                    .sortedBy { it.epochMillis }
            )
        }
    }

    /**
     * 対象期間と重複する月次エントリを開始日の昇順で選択します。
     *
     * 各月の開始日00:00の観測は前月ファイルのraw 24:00行に含まれるため、
     * 選択範囲は開始側を1日前へ広げる。
     */
    private fun selectEntries(startMillis: Long, endMillis: Long): List<HistoricalDatFileTable.Entry> {
        val windowStart = startMillis - MILLIS_PER_DAY
        return HistoricalDatFileTable.entries
            .mapNotNull { entry ->
                val startDay = TimeUtils.parseJstMillis(entry.startDatetime.take(8), "yyyyMMdd")
                val endDay = TimeUtils.parseJstMillis(entry.endDatetime.take(8), "yyyyMMdd")
                if (entry.stationId != AppSettings.DEFAULT_DAM_ID || startDay == null || endDay == null) {
                    null
                } else {
                    Triple(entry, startDay, endDay)
                }
            }
            .filter { (_, startDay, endDay) -> startDay <= endMillis && endDay >= windowStart }
            .sortedBy { (_, startDay, _) -> startDay }
            .map { (entry, _, _) -> entry }
    }

    /**
     * 1エントリ分のデータをキャッシュ・in-flight重複排除付きで読み込みます。
     *
     * キャッシュhit時はそのまま返し、miss時は同一ファイルの読込中ジョブがあればそれを待つ。
     * 新規ジョブを登録した呼び出し側のみが実際の読込を行い、完了時にキャッシュ登録と
     * Deferredのcompleteを行う。キャンセル時はloadingから除去して[CancellationException]を再スローする。
     */
    private suspend fun loadEntry(
        entry: HistoricalDatFileTable.Entry,
        semaphore: Semaphore
    ): Either<Throwable, List<HistoricalComparisonPoint>> {
        cacheMutex.withLock {
            cache[entry.filePath]?.let { return Either.Right(it) }
        }
        val (deferred, isRegistrar) = loadingMutex.withLock {
            val existing = loading[entry.filePath]
            if (existing != null) {
                existing to false
            } else {
                val created = CompletableDeferred<Either<Throwable, List<HistoricalComparisonPoint>>>()
                loading[entry.filePath] = created
                created to true
            }
        }
        if (!isRegistrar) return deferred.await()
        return try {
            val result = withContext(Dispatchers.IO) {
                semaphore.withPermit { loadAndParse(entry) }
            }
            loadingMutex.withLock { loading.remove(entry.filePath) }
            if (result is Either.Right) {
                cacheMutex.withLock {
                    cache[entry.filePath] = result.value
                    evictOverflow()
                }
            }
            deferred.complete(result)
            result
        } catch (e: CancellationException) {
            loadingMutex.withLock { loading.remove(entry.filePath) }
            deferred.completeExceptionally(e)
            throw e
        }
    }

    /**
     * datファイルの読込とparseを行い、[HistoricalComparisonPoint]のリストへ射影します。
     *
     * 失敗（assets欠落・parse失敗・時刻不正）は成功可否を分けず、そのまま[Either.Left]として伝播する。
     * 24:00表記の正規化はここで一度だけ行う。値null（欠測・異常）はそのまま保持する。
     */
    private fun loadAndParse(entry: HistoricalDatFileTable.Entry): Either<Throwable, List<HistoricalComparisonPoint>> =
        try {
            parseEntry(entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Either.Left(e)
        }

    private fun parseEntry(entry: HistoricalDatFileTable.Entry): Either<Throwable, List<HistoricalComparisonPoint>> {
        val bytes = assetBytesReader.readBytes(entry.filePath)
        return fileParser.parseHistoricalDatToDataListResult(
            csvBytes = bytes,
            damConfigId = AppSettings.DEFAULT_DAM_ID,
            searchBgnDate = entry.startDatetime.take(8),
            searchEndDate = entry.endDatetime.take(8)
        ).mapLeft { error ->
            when (error) {
                DamParseError.NoRealtimeDataRows -> Exception("No data rows found in dat file.")
                DamParseError.NoHistoricalDataRows -> Exception("過去データdatファイルにデータ行が見つかりませんでした。")
                is DamParseError.Unexpected -> error.cause
            }
        }.map { (_, items) ->
            items.map { item ->
                HistoricalComparisonPoint(
                    epochMillis = requireNotNull(
                        TimeUtils.parseJstMillisAllow24Hour(item.time, "yyyy/M/d HH:mm"),
                        { "datファイルの時刻をパースできませんでした: ${item.time}" }
                    ),
                    storageRate = item.storagePercentage,
                    storageVolume = item.storageVolume
                )
            }
        }
    }

    /** LRUキャッシュが最大件数を超えた場合、最古のエントリから破棄します。 */
    private fun evictOverflow() {
        while (cache.size > maxCachedFiles) {
            val iterator = cache.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }
}

/**
 * バンドルassets内のファイルをバイト列として読み出すリーダ。
 */
fun interface AssetBytesReader {
    /**
     * assets内のファイルを読み出します。
     *
     * @param filePath assetsディレクトリからの相対パス
     * @return ファイルの全バイト列
     */
    fun readBytes(filePath: String): ByteArray
}
