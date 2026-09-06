// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local

import arrow.core.Either
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.data.source.remote.MlitEndpointConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonPoint
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger

/**
 * バンドルassets内の過去datファイルを読み込む [HistoricalComparisonAssetStore] のJVMユニットテストクラス。
 *
 * 実テーブル [HistoricalDatFileTable] のエントリと合成DAT bytesを返すfake [AssetBytesReader] を用いて、
 * 前日24:00行の正規化、範囲切詰め、月跨ぎ重複排除、LRUキャッシュ、in-flight重複排除、
 * 失敗非キャッシュ、索引外の空応答、parse失敗のLeft伝播、並列読込制限を検証する。
 *
 * 合成DAT行は12列（日付,時刻,雨量,雨量attr,貯水量,貯水量attr,流入量,流入量attr,
 * 放流量,放流量attr,貯水率,貯水率attr）で、attr空=正常値。`DamFileParser` は
 * `row.size > COL_STORAGE_PERCENTAGE`（11列以上）を要求するため、貯水率は必ず第11列に置く。
 */
class HistoricalComparisonAssetStoreTest {

    private val fileParser = DamFileParser(datEndpointConfig = MlitEndpointConfig.production())

    /**
     * 前日24:00行の正規化: 2026-05月ファイルの "2026/5/31,24:00" 行が翌日2026-06-01T00:00の点になる。
     *
     * 開始日2026-06-01の前日拡張により5月ファイルも選択され、その24:00行は翌日00:00へ正規化される。
     * 注入行（貯水量50000）を5月ファイルの先頭に置くことで、同日時の合成24:00行より優先される。
     */
    @Test
    fun loadRange_previousDay2400_normalizedToNextDay0000() = runTest {
        val reader = FakeAssetBytesReader()
        reader.prependRows["202605"] = listOf("2026/5/31,24:00,0.1,,50000,,9,,60,,60,")
        val store = HistoricalComparisonAssetStore(reader, fileParser)
        val start = jstMillis(2026, 6, 1, 0, 0)
        val end = jstMillis(2026, 6, 1, 0, 0)

        val result = store.loadRange(start, end)

        val points = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        assertEquals(1, points.size)
        assertEquals(jstMillis(2026, 6, 1, 0, 0), points.single().epochMillis)
        assertEquals(50000f, points.single().storageVolume ?: -1f, 0.001f)
    }

    /**
     * 範囲切詰め: 6月ファイルが範囲外時刻を含んでも [start, end] 内のみ返る。
     *
     * 2026-06-10T00:00〜2026-06-11T00:00 の両端を含む25点（毎時00分）だけが返り、
     * 範囲外の6/1〜6/9・6/11以降の行は除外される。
     */
    @Test
    fun loadRange_clampsToRequestedRange() = runTest {
        val reader = FakeAssetBytesReader()
        val store = HistoricalComparisonAssetStore(reader, fileParser)
        val start = jstMillis(2026, 6, 10, 0, 0)
        val end = jstMillis(2026, 6, 11, 0, 0)

        val result = store.loadRange(start, end)

        val points = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        assertEquals(25, points.size)
        assertEquals(start, points.first().epochMillis)
        assertEquals(end, points.last().epochMillis)
        assertEquals(1, reader.readCount.get())
    }

    /**
     * 月跨ぎ重複排除: 5月末〜6月初の範囲で、同一epoch時刻は常に1点のみ返る。
     *
     * 5月ファイルの "5/31,24:00"（→6/1 00:00）と6月ファイルへ注入した "6/1,00:00" が
     * 同一epochで重複するが、distinctBy epochにより1点にまとまる。
     */
    @Test
    fun loadRange_crossMonthOverlap_deduplicatedByEpoch() = runTest {
        val reader = FakeAssetBytesReader()
        reader.prependRows["202606"] = listOf("2026/6/1,00:00,0.1,,90000,,9,,60,,70,")
        val store = HistoricalComparisonAssetStore(reader, fileParser)
        val start = jstMillis(2026, 5, 31, 0, 0)
        val end = jstMillis(2026, 6, 2, 0, 0)

        val result = store.loadRange(start, end)

        val points = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        assertEquals(49, points.size)
        assertEquals(start, points.first().epochMillis)
        assertEquals(end, points.last().epochMillis)
        assertEquals(points.size, points.map { it.epochMillis }.distinct().size)
        assertEquals(1, points.count { it.epochMillis == jstMillis(2026, 6, 1, 0, 0) })
        assertEquals(points.map { it.epochMillis }.sorted(), points.map { it.epochMillis })
    }

    /**
     * cache hit: 同じ範囲を2回読み込んでもreaderの読込回数は1回のまま。
     */
    @Test
    fun loadRange_sameRangeTwice_cachesSecondCall() = runTest {
        val reader = FakeAssetBytesReader()
        val store = HistoricalComparisonAssetStore(reader, fileParser)
        val start = jstMillis(2026, 6, 10, 0, 0)
        val end = jstMillis(2026, 6, 11, 0, 0)

        assertTrue(store.loadRange(start, end).isRight())
        assertTrue(store.loadRange(start, end).isRight())

        assertEquals(1, reader.readCount.get())
    }

    /**
     * 同時要求dedup: runTestでasync×2により同時に同じ範囲を要求しても読込回数は1回。
     * delayMillisによりin-flightウィンドウを確保し、同一ファイルの読込が1回に集約されることを確認する。
     */
    @Test
    fun loadRange_concurrentRequests_deduplicatesInFlightRead() = runTest {
        val reader = FakeAssetBytesReader(delayMillis = 30)
        val store = HistoricalComparisonAssetStore(reader, fileParser)
        val start = jstMillis(2026, 6, 10, 0, 0)
        val end = jstMillis(2026, 6, 11, 0, 0)

        val results = listOf(
            async { store.loadRange(start, end) },
            async { store.loadRange(start, end) }
        ).awaitAll()

        assertEquals(2, results.size)
        assertTrue(results.all { it.isRight() })
        assertEquals(1, reader.readCount.get())
    }

    /**
     * LRU evict: maxCachedFiles=2で3ヶ月連続読込後、最初の月はキャッシュから追い出され
     * readerが再呼び出しされる。2番目の月はキャッシュに残っておりcache hitとなる。
     */
    @Test
    fun loadRange_lruEviction_evictsLeastRecentlyUsed() = runTest {
        val reader = FakeAssetBytesReader()
        val store = HistoricalComparisonAssetStore(reader, fileParser, maxCachedFiles = 2)
        val april = jstMillis(2026, 4, 10, 0, 0) to jstMillis(2026, 4, 11, 0, 0)
        val may = jstMillis(2026, 5, 10, 0, 0) to jstMillis(2026, 5, 11, 0, 0)
        val june = jstMillis(2026, 6, 10, 0, 0) to jstMillis(2026, 6, 11, 0, 0)

        assertTrue(store.loadRange(april.first, april.second).isRight())
        assertTrue(store.loadRange(may.first, may.second).isRight())
        assertTrue(store.loadRange(june.first, june.second).isRight())
        assertEquals(3, reader.readCount.get())

        assertTrue(store.loadRange(may.first, may.second).isRight())
        assertEquals(3, reader.readCount.get())

        assertTrue(store.loadRange(april.first, april.second).isRight())
        assertEquals(4, reader.readCount.get())
    }

    /**
     * 失敗非cache: failPathsで1回目だけ読込失敗→Left。失敗解除後の2回目はRightで読込回数は2回。
     * 失敗結果はキャッシュされないことを確認する。
     */
    @Test
    fun loadRange_failure_notCachedAndRetriedOnNextCall() = runTest {
        val reader = FakeAssetBytesReader()
        val junePath = "history/1368080700010_202606010100_202606302400.dat"
        reader.failPaths += junePath
        val store = HistoricalComparisonAssetStore(reader, fileParser)
        val start = jstMillis(2026, 6, 10, 0, 0)
        val end = jstMillis(2026, 6, 11, 0, 0)

        assertTrue(store.loadRange(start, end).isLeft())
        reader.failPaths -= junePath

        assertTrue(store.loadRange(start, end).isRight())

        assertEquals(2, reader.readCount.get())
    }

    /**
     * 索引外は空: テーブル開始前（2002-01-15〜16）の範囲は空リストを返し、readerは呼ばれない。
     */
    @Test
    fun loadRange_outsideTable_returnsEmptyWithoutReading() = runTest {
        val reader = FakeAssetBytesReader()
        val store = HistoricalComparisonAssetStore(reader, fileParser)
        val start = jstMillis(2002, 1, 15, 0, 0)
        val end = jstMillis(2002, 1, 16, 0, 0)

        val result = store.loadRange(start, end)

        assertEquals(
            emptyList<HistoricalComparisonPoint>(),
            result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        )
        assertEquals(0, reader.readCount.get())
    }

    /**
     * parse失敗はLeft: 対象月のファイルが空ByteArray（データ行なし）の場合、
     * NoHistoricalDataRows相当のエラーがLeftとして伝播し、readerの読込は1回のみ。
     */
    @Test
    fun loadRange_parseFailure_returnsLeft() = runTest {
        val reader = FakeAssetBytesReader()
        reader.emptyPaths += "history/1368080700010_202403010100_202403312400.dat"
        val store = HistoricalComparisonAssetStore(reader, fileParser)
        val start = jstMillis(2024, 3, 10, 0, 0)
        val end = jstMillis(2024, 3, 11, 0, 0)

        val result = store.loadRange(start, end)

        assertTrue(result.isLeft())
        assertEquals(1, reader.readCount.get())
    }

    /**
     * bounded concurrency: 5ヶ月分（2024-04〜2024-08）のファイルを跨ぐ範囲を読み込み、
     * 同時に読み込まれた月の最大数がMAX_CONCURRENT_MONTHS（4）以下であることを確認する。
     */
    @Test
    fun loadRange_acrossFiveMonths_concurrencyBoundedByMaxConcurrentMonths() = runTest {
        val reader = FakeAssetBytesReader(delayMillis = 30)
        val store = HistoricalComparisonAssetStore(reader, fileParser)
        val start = jstMillis(2024, 4, 10, 0, 0)
        val end = jstMillis(2024, 8, 10, 0, 0)

        val result = store.loadRange(start, end)

        assertTrue(result.isRight())
        assertTrue(
            "同時読込の最大数=${reader.maxActiveSeen}が上限=${HistoricalComparisonAssetStore.MAX_CONCURRENT_MONTHS}を超えています",
            reader.maxActiveSeen <= HistoricalComparisonAssetStore.MAX_CONCURRENT_MONTHS
        )
    }

    /** JST基準のエポックミリ秒を組み立てる。 */
    private fun jstMillis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(TimeUtils.JST_ZONE)
            .toInstant()
            .toEpochMilli()

    /**
     * assets内ファイルを模したfakeリーダ。
     *
     * ファイルパス（例: "history/1368080700010_202406010100_202406302400.dat"）から月（YYYYMM）を
     * 文字列処理で取り出し、その月の合成DAT bytes（毎時00分・12列・UTF-8 BOM付き）を返す。
     * 貯水率・貯水量は月番号から決定的に設定する。
     *
     * @property delayMillis 0より大きい場合、読込時にそのミリ秒だけスリープする
     */
    private class FakeAssetBytesReader(
        private val delayMillis: Long = 0L
    ) : AssetBytesReader {

        /** 読込回数 */
        val readCount = AtomicInteger(0)

        /** このパスが含まれる場合 [FileNotFoundException] を投げる */
        val failPaths = mutableSetOf<String>()

        /** このパスが含まれる場合 空ByteArray を返す（parse失敗の再現用） */
        val emptyPaths = mutableSetOf<String>()

        /** キー: YYYYMM。指定月の合成行の先頭に追加する生行 */
        val prependRows = mutableMapOf<String, List<String>>()

        private val active = AtomicInteger(0)
        private val maxActive = AtomicInteger(0)

        /** 同時読込数の観測された最大値 */
        val maxActiveSeen: Int
            get() = maxActive.get()

        override fun readBytes(filePath: String): ByteArray {
            readCount.incrementAndGet()
            val entered = active.incrementAndGet()
            updateMaxActive(entered)
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis)
                if (filePath in failPaths) throw FileNotFoundException("fail path: $filePath")
                if (filePath in emptyPaths) return ByteArray(0)
                val month = filePath.substringAfter("_").substring(0, 6)
                return syntheticDatBytes(month)
            } finally {
                active.decrementAndGet()
            }
        }

        private fun updateMaxActive(value: Int) {
            while (true) {
                val current = maxActive.get()
                if (value <= current || maxActive.compareAndSet(current, value)) return
            }
        }

        private fun syntheticDatBytes(month: String): ByteArray {
            val year = month.substring(0, 4).toInt()
            val monthValue = month.substring(4, 6).toInt()
            val daysInMonth = YearMonth.of(year, monthValue).lengthOfMonth()
            val volume = 100000 + monthValue
            val rate = 50 + (year * 12 + monthValue) % 40
            val rows = mutableListOf<String>()
            prependRows[month]?.let { rows += it }
            for (day in 1..daysInMonth) {
                for (hour in 1..24) {
                    rows += row(date = "$year/$monthValue/$day", time = "%02d:00".format(hour), volume = volume, rate = rate)
                }
            }
            return ("\uFEFF" + rows.joinToString("\n")).toByteArray(Charsets.UTF_8)
        }

        private fun row(date: String, time: String, volume: Int, rate: Int): String =
            listOf(
                date,
                time,
                "0.1",
                "",
                volume.toString(),
                "",
                "9",
                "",
                "60",
                "",
                rate.toString(),
                ""
            ).joinToString(",")
    }
}
