// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.data.source.local.AssetBytesReader
import net.tecogonaz.tcsameuradammonitor.data.source.local.HistoricalComparisonAssetStore
import net.tecogonaz.tcsameuradammonitor.data.source.local.HistoricalDatFileTable
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.data.source.remote.MlitEndpointConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalComparisonMetric
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryFetchResult
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger

/**
 * 過去比較グラフ用リポジトリ [HistoricalComparisonRepositoryImpl] のJVMユニットテストクラス。
 *
 * 実テーブル [HistoricalDatFileTable]（2002-06以降）と、ファイルパスから月（YYYYMM）を
 * 取り出して合成DAT bytesを返すfake [AssetBytesReader] を用いて、非Sameura拒否、毎時時刻軸への
 * 逆写像、メトリック切替、欠測、非毎時行の除外、年跨ぎの24:00行、索引外の空、部分失敗の全体Left、
 * 並列数制限、うるう年の写像を検証する。
 *
 * 合成DAT行は12列（日付,時刻,雨量,雨量attr,貯水量,貯水量attr,流入量,流入量attr,
 * 放流量,放流量attr,貯水率,貯水率attr）で、attr空=正常値。`DamFileParser` は
 * `row.size > COL_STORAGE_PERCENTAGE`（11列以上）を要求するため、貯水率は必ず第11列に置く。
 * 合成行は毎日01:00〜23:00のみ生成し24:00行を生成しない（00:00は注入行でのみ再現する）ことで、
 * 「00:00行が無い」状態を決定的に作り出せる。
 */
class HistoricalComparisonRepositoryImplTest {

    private val fileParser = DamFileParser(datEndpointConfig = MlitEndpointConfig.production())

    /**
     * 非Sameura拒否: 早明浦以外のダムIDはassetsを一切読まずにLeftを返す。
     *
     * ダムIDチェックが読み込みより先に行われるため、readerの読込回数は0のまま。
     */
    @Test
    fun loadComparison_nonSameura_rejectsWithoutReadingAnyAsset() = runTest {
        val reader = FakeAssetBytesReader()
        val repo = repository(reader, jstMillis(2026, 6, 27, 13))

        val result = repo.loadComparison(
            damId = "1234567890123",
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2026, 6, 28, 0),
            windowEndMillis = jstMillis(2026, 6, 28, 2)
        )

        assertTrue(result.isLeft())
        assertEquals(0, reader.readCount.get())
    }

    /**
     * 毎時series構築: STORAGE_RATEで2024年の該当3時刻が軸の3点に対応する。
     *
     * window=2026-06-28T00:00..02:00 JST。2024-06-27の24:00行（→2024-06-28T00:00）と
     * 2024-06-28の01:00/02:00行を注入し、series(2024).values == [50, 51, 52]、
     * axis3点、availablePastYears==2002..2025、currentYear==2026を確認する。
     */
    @Test
    fun loadComparison_sameura_storageRateSeriesAlignedToHourlyAxis() = runTest {
        val reader = FakeAssetBytesReader()
        reader.addRows(
            "202406",
            datRow("2024/6/27", "24:00", rate = 50),
            datRow("2024/6/28", "01:00", rate = 51),
            datRow("2024/6/28", "02:00", rate = 52)
        )
        val repo = repository(reader, jstMillis(2026, 6, 27, 13))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2026, 6, 28, 0),
            windowEndMillis = jstMillis(2026, 6, 28, 2)
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        assertEquals(2026, data.currentYear)
        assertEquals((2002..2025).toList(), data.availablePastYears)
        assertEquals(
            listOf(
                jstMillis(2026, 6, 28, 0),
                jstMillis(2026, 6, 28, 1),
                jstMillis(2026, 6, 28, 2)
            ),
            data.hourlyAxisMillis
        )
        assertEquals(24, data.series.size)
        assertEquals(listOf(50f, 51f, 52f), data.series.first { it.year == 2024 }.values)
    }

    /**
     * メトリック切替: STORAGE_VOLUMEでは貯水量の値がseriesに載る。
     *
     * 同一window・同一行注入で貯水量 [200000, 201000, 202000] が返ることを確認する。
     */
    @Test
    fun loadComparison_sameura_storageVolumeMetricSelectsVolumeValues() = runTest {
        val reader = FakeAssetBytesReader()
        reader.addRows(
            "202406",
            datRow("2024/6/27", "24:00", volume = 200000),
            datRow("2024/6/28", "01:00", volume = 201000),
            datRow("2024/6/28", "02:00", volume = 202000)
        )
        val repo = repository(reader, jstMillis(2026, 6, 27, 13))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_VOLUME,
            windowStartMillis = jstMillis(2026, 6, 28, 0),
            windowEndMillis = jstMillis(2026, 6, 28, 2)
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        assertEquals(listOf(200000f, 201000f, 202000f), data.series.first { it.year == 2024 }.values)
    }

    /**
     * 品質欠測: attr="-"の行は対象indexだけnullになる。
     *
     * 01:00の行だけattr="-"にし、series(2024).values == [50, null, 52] を確認する。
     */
    @Test
    fun loadComparison_sameura_missingQualityMapsOnlyNullAtIndex() = runTest {
        val reader = FakeAssetBytesReader()
        reader.addRows(
            "202406",
            datRow("2024/6/27", "24:00", rate = 50),
            datRow("2024/6/28", "01:00", rate = 51, missing = true),
            datRow("2024/6/28", "02:00", rate = 52)
        )
        val repo = repository(reader, jstMillis(2026, 6, 27, 13))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2026, 6, 28, 0),
            windowEndMillis = jstMillis(2026, 6, 28, 2)
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        assertEquals(listOf(50f, null, 52f), data.series.first { it.year == 2024 }.values)
    }

    /**
     * 非毎時行の除外: 00:10の行は軸の00:00位置に写像されない。
     *
     * 00:00行が無い場合、00:10行が存在してもseries(2024)の00:00位置はnullのまま
     * （exact matchのみ軸へ載る）ことを確認する。
     */
    @Test
    fun loadComparison_sameura_nonHourlyRowNotMappedToAxis() = runTest {
        val reader = FakeAssetBytesReader()
        reader.addRows(
            "202406",
            datRow("2024/6/28", "00:10", rate = 55),
            datRow("2024/6/28", "01:00", rate = 51),
            datRow("2024/6/28", "02:00", rate = 52)
        )
        val repo = repository(reader, jstMillis(2026, 6, 27, 13))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2026, 6, 28, 0),
            windowEndMillis = jstMillis(2026, 6, 28, 2)
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        assertEquals(listOf(null, 51f, 52f), data.series.first { it.year == 2024 }.values)
    }

    /**
     * 年跨ぎ: 12月末〜1月初のwindowで、前年12月の24:00行と当年1月の行が軸へ載る。
     *
     * window=2026-01-01T00:00..2026-01-02T00:00（25点）。2002-12月ファイルの
     * "2002/12/31,24:00"（→2003-01-01T00:00）と2003-01月ファイルの00:00/01:00/02:00/24:00行
     * （24:00→2003-01-02T00:00）を注入し、year=2003のvaluesのaxis[0],[1],[2],[24]が
     * 注入値になることを確認する。00:00は前年ファイルの24:00行由来が優先される。
     */
    @Test
    fun loadComparison_sameura_crossYearWindowMapsDecAndJanFiles() = runTest {
        val reader = FakeAssetBytesReader()
        reader.addRows("200212", datRow("2002/12/31", "24:00", rate = 150))
        reader.addRows(
            "200301",
            datRow("2003/1/1", "00:00", rate = 149),
            datRow("2003/1/1", "01:00", rate = 151),
            datRow("2003/1/1", "02:00", rate = 152),
            datRow("2003/1/1", "24:00", rate = 153)
        )
        val repo = repository(reader, jstMillis(2026, 6, 27, 13))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2026, 1, 1, 0),
            windowEndMillis = jstMillis(2026, 1, 2, 0)
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val series2003 = data.series.first { it.year == 2003 }
        assertEquals(25, series2003.values.size)
        assertEquals(150f, series2003.values[0])
        assertEquals(151f, series2003.values[1])
        assertEquals(152f, series2003.values[2])
        assertEquals(153f, series2003.values[24])
    }

    /**
     * 索引外は正常な空: 2002年1月相当のwindowでseries(2002)は全nullのままRight成功する。
     *
     * 実テーブルは2002-06開始のため2002-01-15〜16の範囲はassets読込対象が無く、
     * 部分グラフを捨てずに空の時系列として返ることを確認する。
     */
    @Test
    fun loadComparison_sameura_beforeFirstYearReturnsAllNullSeries() = runTest {
        val reader = FakeAssetBytesReader()
        val repo = repository(reader, jstMillis(2026, 6, 27, 13))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2026, 1, 15, 0),
            windowEndMillis = jstMillis(2026, 1, 16, 0)
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val series2002 = data.series.first { it.year == 2002 }
        assertEquals(25, series2002.values.size)
        assertTrue(series2002.values.all { it == null })
    }

    /**
     * 部分失敗の全体Left: 一部の月の読込が失敗すると全体がLeftになる。
     *
     * failPathsに2003-06を追加し、他年（2002〜2025のうち2003以外）が正常に読めても
     * 部分グラフを作らず全体Leftで返ることを確認する。
     */
    @Test
    fun loadComparison_sameura_partialMonthFailureReturnsLeft() = runTest {
        val reader = FakeAssetBytesReader()
        reader.failPaths += "history/1368080700010_200306010100_200306302400.dat"
        val repo = repository(reader, jstMillis(2026, 6, 27, 13))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2026, 6, 28, 0),
            windowEndMillis = jstMillis(2026, 6, 28, 2)
        )

        assertTrue(result.isLeft())
        assertEquals(24, reader.readCount.get())
    }

    /**
     * 並列制限: 24年分の時系列構築でも同時読込の最大数は4以下。
     *
     * delayMillis=20で読込を遅延させ、repositoryの年別セマフォ
     * （MAX_CONCURRENT_YEARS=4）が機能することを確認する。
     */
    @Test
    fun loadComparison_sameura_concurrentYearsBoundedByMaxConcurrentYears() = runTest {
        val reader = FakeAssetBytesReader(delayMillis = 20)
        val repo = repository(reader, jstMillis(2026, 6, 27, 13))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2026, 6, 28, 0),
            windowEndMillis = jstMillis(2026, 6, 28, 2)
        )

        assertTrue(result.isRight())
        assertTrue(
            "同時読込の最大数=${reader.maxActiveSeen}が4を超えています",
            reader.maxActiveSeen <= 4
        )
    }

    /**
     * うるう年: 2/29のwindowで、うるう年のみが写像され値が載る。
     *
     * clock=JST 2024-02-29T12:00のためavailablePastYearsは2002..2023。2008年（うるう年）の
     * 2/29 12:00/13:00行→series(2008)に値あり。2023年（非うるう年）は写像が成立せず
     * 2月ファイルに行を置いてもseries(2023)は全nullのまま。
     */
    @Test
    fun loadComparison_sameura_leapDayMapsLeapYearsOnly() = runTest {
        val reader = FakeAssetBytesReader()
        reader.addRows(
            "200802",
            datRow("2008/2/29", "12:00", rate = 200),
            datRow("2008/2/29", "13:00", rate = 201)
        )
        reader.addRows("202302", datRow("2023/2/28", "13:00", rate = 99))
        val repo = repository(reader, jstMillis(2024, 2, 29, 12))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2024, 2, 29, 12),
            windowEndMillis = jstMillis(2024, 2, 29, 13)
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        assertEquals(2024, data.currentYear)
        assertEquals((2002..2023).toList(), data.availablePastYears)
        assertEquals(listOf(200f, 201f), data.series.first { it.year == 2008 }.values)
        assertEquals(listOf(null, null), data.series.first { it.year == 2023 }.values)
    }

    /**
     * mainYear指定: 通常の過去データ表示（表示対象データの年=2018）では、
     * 主系列年が2018になり、比較対象年は2002〜JST現在年(2026)から2018を除いた24年
     * （JST現在年の系列を含む）になる。
     *
     * window=2018-06-28T00:00..02:00 JST・clock=2026-06-27T13:00 JST。
     * 軸は主系列年(2018)の時刻で、各比較対象年は2018へ逆写像されて軸へ載る。
     */
    @Test
    fun loadComparison_sameura_mainYearMode_currentYearBecomesMainYear() = runTest {
        val reader = FakeAssetBytesReader()
        val repo = repository(reader, jstMillis(2026, 6, 27, 13), fakeSudmonitorRepository())

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2018, 6, 28, 0),
            windowEndMillis = jstMillis(2018, 6, 28, 2),
            mainYear = 2018
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        assertEquals(2018, data.currentYear)
        assertEquals((2002..2026).filterNot { it == 2018 }, data.availablePastYears)
        assertEquals(data.availablePastYears.size, data.series.size)
        assertTrue(data.series.any { it.year == 2026 })
        assertTrue(data.series.none { it.year == 2018 })
        assertEquals(
            listOf(
                jstMillis(2018, 6, 28, 0),
                jstMillis(2018, 6, 28, 1),
                jstMillis(2018, 6, 28, 2)
            ),
            data.hourlyAxisMillis
        )
    }

    /**
     * 合成系列: JST現在年(2026)の比較系列は、バンドル値の上に日次過去データの
     * 非null値を上書きする。
     *
     * window=2018-06-28T00:00..02:00 JST。日次行は2026-06-28の00:00/01:00のみで、
     * 02:00は日次に無いためバンドル値(合成DATの2026-06月値=88)のまま。
     */
    @Test
    fun loadComparison_sameura_mainYearMode_dailyRowsOverrideBundledClockYearValues() = runTest {
        val reader = FakeAssetBytesReader()
        val dailyRows = listOf(
            dailyRow("2026/6/28 00:00", rate = 70f),
            dailyRow("2026/6/28 01:00", rate = 71f)
        )
        val repo = repository(reader, jstMillis(2026, 6, 27, 13), fakeSudmonitorRepository(dailyRows))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2018, 6, 28, 0),
            windowEndMillis = jstMillis(2018, 6, 28, 2),
            mainYear = 2018
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val series2026 = data.series.first { it.year == 2026 }
        assertEquals(70f, series2026.values[0])
        assertEquals(71f, series2026.values[1])
        assertEquals(88f, series2026.values[2])
    }

    /**
     * カバレッジ外null: バンドルにも日次過去データにも無い時刻はnullのまま
     * （ライン途切れ）になる。バンドルfixtureの対象時刻を明示的に欠落させ、月次
     * datファイルの追加でこのテストの意味が変わらないようにする。
     *
     * window=2018-08-28T00:00..02:00 JST（2026-08へ写像）。日次行は00:00/01:00のみで、
     * 合成バンドルfixtureも02:00を欠落させるため、02:00はnullのまま。
     */
    @Test
    fun loadComparison_sameura_mainYearMode_hoursBeyondCoverageAreNull() = runTest {
        val reader = FakeAssetBytesReader()
        reader.omitGeneratedRows("202608", "2026/8/28 02:00")
        val dailyRows = listOf(
            dailyRow("2026/8/28 00:00", rate = 80f),
            dailyRow("2026/8/28 01:00", rate = 81f)
        )
        val repo = repository(reader, jstMillis(2026, 8, 27, 13), fakeSudmonitorRepository(dailyRows))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2018, 8, 28, 0),
            windowEndMillis = jstMillis(2018, 8, 28, 2),
            mainYear = 2018
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val series2026 = data.series.first { it.year == 2026 }
        assertEquals(listOf(80f, 81f, null), series2026.values)
    }

    /**
     * 24:00表記: 日次過去データの24:00行は翌日00:00として軸へ写像される。
     *
     * window=2018-06-28T23:00..2018-06-29T00:00 JST。日次行"2026/6/28 24:00"は
     * 2026-06-29T00:00→2018-06-29T00:00(axis[1])へ写像される。
     */
    @Test
    fun loadComparison_sameura_mainYearMode_dailyRowsWith24HourTimeMapToNextDay() = runTest {
        val reader = FakeAssetBytesReader()
        val dailyRows = listOf(
            dailyRow("2026/6/28 23:00", rate = 88f),
            dailyRow("2026/6/28 24:00", rate = 77f)
        )
        val repo = repository(reader, jstMillis(2026, 6, 27, 13), fakeSudmonitorRepository(dailyRows))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2018, 6, 28, 23),
            windowEndMillis = jstMillis(2018, 6, 29, 0),
            mainYear = 2018
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val series2026 = data.series.first { it.year == 2026 }
        assertEquals(listOf(88f, 77f), series2026.values)
    }

    /**
     * mainYear=null(リアルタイム・日次表示): 従来挙動のまま。日次過去データが
     * あってもJST現在年の系列は作られず、主系列年=JST現在年で比較対象は2002..2025。
     */
    @Test
    fun loadComparison_sameura_nullMainYear_realtimeStyleUnchanged() = runTest {
        val reader = FakeAssetBytesReader()
        reader.addRows(
            "202406",
            datRow("2024/6/27", "24:00", rate = 50),
            datRow("2024/6/28", "01:00", rate = 51),
            datRow("2024/6/28", "02:00", rate = 52)
        )
        val dailyRows = listOf(
            dailyRow("2026/6/28 00:00", rate = 90f),
            dailyRow("2026/6/28 01:00", rate = 91f)
        )
        val repo = repository(reader, jstMillis(2026, 6, 27, 13), fakeSudmonitorRepository(dailyRows))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2026, 6, 28, 0),
            windowEndMillis = jstMillis(2026, 6, 28, 2)
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        assertEquals(2026, data.currentYear)
        assertEquals((2002..2025).toList(), data.availablePastYears)
        assertEquals(24, data.series.size)
        assertTrue(data.series.none { it.year == 2026 })
        assertEquals(listOf(50f, 51f, 52f), data.series.first { it.year == 2024 }.values)
    }

    /**
     * sudmonitorリポジトリ未注入: 合成対象(JST現在年)の系列はバンドル値のみで構築され、
     * クラッシュしない。
     *
     * window=2018-06-28T01:00..02:00 JST（バンドル2026-06月が両時刻をカバー）。
     */
    @Test
    fun loadComparison_sameura_mainYearMode_nullDailyRepository_usesBundledOnly() = runTest {
        val reader = FakeAssetBytesReader()
        val repo = repository(reader, jstMillis(2026, 6, 27, 13))

        val result = repo.loadComparison(
            damId = AppSettings.DEFAULT_DAM_ID,
            metric = HistoricalComparisonMetric.STORAGE_RATE,
            windowStartMillis = jstMillis(2018, 6, 28, 1),
            windowEndMillis = jstMillis(2018, 6, 28, 2),
            mainYear = 2018
        )

        val data = result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val series2026 = data.series.first { it.year == 2026 }
        assertEquals(listOf(88f, 88f), series2026.values)
    }

    /** fakeリーダと実パーサを使うリポジトリを組み立てる。 */
    private fun repository(
        reader: FakeAssetBytesReader,
        clockMillis: Long,
        sudmonitorHistoryRepository: SudmonitorHistoryRepository? = null
    ): HistoricalComparisonRepositoryImpl =
        HistoricalComparisonRepositoryImpl(
            assetStore = HistoricalComparisonAssetStore(reader, fileParser),
            clock = { clockMillis },
            sudmonitorHistoryRepository = sudmonitorHistoryRepository
        )

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
     * 文字列処理で取り出し、その月の合成DAT bytes（毎日01:00〜23:00・12列・UTF-8 BOM付き）を返す。
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

        /** キー: YYYYMM。指定月の合成行の先頭に追加する生行（同一epochでは注入行が優先される） */
        val prependRows = mutableMapOf<String, List<String>>()

        /** キー: YYYYMM。合成行から明示的に欠落させる日時（テストfixture用） */
        private val omittedGeneratedRows = mutableMapOf<String, MutableSet<String>>()

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
                for (hour in 1..23) {
                    val dateTime = "$year/$monthValue/$day %02d:00".format(hour)
                    if (dateTime !in omittedGeneratedRows[month].orEmpty()) {
                        rows += datRow(
                            "$year/$monthValue/$day",
                            "%02d:00".format(hour),
                            rate = rate,
                            volume = volume
                        )
                    }
                }
            }
            return ("\uFEFF" + rows.joinToString("\n")).toByteArray(Charsets.UTF_8)
        }

        /** 指定月ファイルの先頭に追加する生行リストを登録する。 */
        fun addRows(month: String, vararg rows: String) {
            prependRows[month] = (prependRows[month] ?: emptyList()) + rows
        }

        /** 指定月の自動生成行から日時を欠落させる。 */
        fun omitGeneratedRows(month: String, vararg dateTimes: String) {
            omittedGeneratedRows.getOrPut(month) { mutableSetOf() }.addAll(dateTimes)
        }
    }
}

/**
 * 12列の合成DAT行を組み立てる。
 *
 * @param date 日付（"2024/6/28"）
 * @param time 時刻（"01:00" / "24:00" 等）
 * @param rate 貯水率。nullなら空セル
 * @param volume 貯水量。nullなら空セル
 * @param missing trueなら貯水量・貯水率のattrを"-"にして欠測を再現
 */
private fun datRow(
    date: String,
    time: String,
    rate: Int? = null,
    volume: Int? = null,
    missing: Boolean = false
): String {
    val attr = if (missing) "-" else ""
    val volumeCell = volume?.toString() ?: ""
    val rateCell = rate?.toString() ?: ""
    return listOf(
        date,
        time,
        "0.1",
        "",
        volumeCell,
        attr,
        "9",
        "",
        "60",
        "",
        rateCell,
        attr
    ).joinToString(",")
}

/**
 * 読込済み日次過去データ（sudmonitor）のfake行を組み立てる。
 *
 * @param time 観測日時文字列（"2026/6/28 00:00" / "24:00" 表記も許容）
 * @param rate 貯水率。nullなら欠測
 * @param volume 貯水量。nullなら欠測
 */
private fun dailyRow(time: String, rate: Float? = null, volume: Float? = null): DamHistoricalData =
    DamHistoricalData(
        time = time,
        catchmentAverageRainfall = null,
        storagePercentage = rate,
        storageVolume = volume,
        inflow = null,
        outflow = null
    )

/**
 * 指定した観測行を返す [SudmonitorHistoryRepository] のfake実装。
 *
 * 読出し以外のメソッドは使わないため、簡易な固定実装を返す。
 */
private fun fakeSudmonitorRepository(rows: List<DamHistoricalData> = emptyList()): SudmonitorHistoryRepository =
    object : SudmonitorHistoryRepository {
        override suspend fun fetchAndStore(
            damId: String,
            damConfig: DamConfig,
            trigger: SudmonitorHistoryTrigger
        ): arrow.core.Either<Throwable, SudmonitorHistoryFetchResult> =
            arrow.core.Either.Right(SudmonitorHistoryFetchResult.NotStored)

        override suspend fun findByDamId(damId: String): SudmonitorHistory? = null

        override suspend fun manualRefreshAvailableAt(damId: String): Long? = null

        override suspend fun autoFetch(interval: AutoUpdateInterval, now: Long, damId: String) = Unit

        override suspend fun getAllObservations(damId: String): List<DamHistoricalData> = rows

        override suspend fun getObservationsByTimeRange(
            damId: String,
            from: Long,
            to: Long
        ): List<DamHistoricalData> = emptyList()
    }
