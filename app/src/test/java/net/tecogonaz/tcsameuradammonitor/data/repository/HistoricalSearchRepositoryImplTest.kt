// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import arrow.core.Either
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.data.source.local.HistoricalDatFileTable
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DatabaseTransactionRunner
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.HistoricalDamDataEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.HistoricalSearchDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.HistoricalSearchMetaEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryObservationEntity
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamNetworkDataSource
import net.tecogonaz.tcsameuradammonitor.data.source.remote.MlitEndpointConfig
import net.tecogonaz.tcsameuradammonitor.data.source.remote.NetworkAvailability
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorEndpointConfig
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorHistoricalClient
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugLogRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.HistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.FakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.FakeNetworkAvailability
import net.tecogonaz.tcsameuradammonitor.testutil.FakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.shouldBeLeft
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.YearMonth

/**
 * 過去データ検索リポジトリ実装 [HistoricalSearchRepositoryImpl] のユニットテストクラス。
 * 過去データの取得要求、パース結果の Room データベースへの保存（トランザクション処理）、
 * 取得上限数（[HistoricalSearchRepository.MAX_STORED_META_COUNT]）に達した際の保存制限、
 * 重複範囲の検索ブロック、非対応ダムの検証などが正しく動作することを検証します。
 */
class HistoricalSearchRepositoryImplTest {
    private lateinit var sudmonitorServer: MockWebServer

    @Before
    fun setUp() {
        sudmonitorServer = MockWebServer()
        sudmonitorServer.start()
    }

    @After
    fun tearDown() {
        runCatching { unmockkStatic(Log::class) }
        runCatching { unmockkObject(HistoricalDatFileTable) }
        sudmonitorServer.shutdown()
    }

    @Test
    fun fetchAndStore_networkSuccess_fetchesHtmlAndDatThenStoresParsedRows() = runTest {
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        val networkDataSource = mockk<DamNetworkDataSource>()
        coEvery { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) } returns
            Either.Right("""<html><body><a href="/dat/dam-history.dat">dat</a></body></html>""".toByteArray(Charset.forName("EUC-JP")))
        coEvery { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") } returns
            Either.Right(
                historicalDat(
                    listOf(
                        historicalRow(date = "2026/5/1", time = "01:00", storagePercentage = "61.2"),
                        historicalRow(date = "2026/5/1", time = "02:00", storagePercentage = "62.3")
                    )
                )
            )
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            fileParser = DamFileParser(datEndpointConfig = MlitEndpointConfig.production())
        )

        val result = repository.fetchAndStore(networkOnlySupportedDam(), "20260501", "20260501")

        assertTrue(result.isRight())
        result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { meta ->
                assertEquals(42L, meta.id)
                assertEquals("9999999999999", meta.damConfigId)
                assertEquals("2026/5/1 01:00", meta.dataStartTimeStr)
                assertEquals("2026/5/1 02:00", meta.dataEndTimeStr)
                assertEquals(61.2f, meta.dataStartStoragePct ?: -1f, 0.001f)
                assertEquals(62.3f, meta.dataEndStoragePct ?: -1f, 0.001f)
            }
        )
        assertEquals(2, dao.duplicateChecks)
        assertEquals(2, dao.metaCountChecks)
        assertEquals(1, dao.shiftSortOrderCount)
        assertNotNull(dao.insertedMeta)
        assertEquals("9999999999999", dao.insertedMeta?.damConfigId)
        assertEquals(2, dao.insertedData.size)
        assertEquals(42L, dao.insertedData.single { it.timeStr == "2026/5/1 01:00" }.searchMetaId)
        coVerify(exactly = 1) { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) }
        coVerify(exactly = 1) { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") }
    }

    @Test
    fun fetchAndStore_htmlWithOnlyForeignDatLink_returnsDatUrlErrorWithoutDatFetchOrInsert() = runTest {
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        val networkDataSource = mockk<DamNetworkDataSource>()
        coEvery { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) } returns
            Either.Right("""<html><a href="https://example.com/dam-history.dat">dat</a></html>""".toByteArray(Charset.forName("EUC-JP")))
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            fileParser = DamFileParser(datEndpointConfig = MlitEndpointConfig.production())
        )

        val error = repository.fetchAndStore(networkOnlySupportedDam(), "20260501", "20260501").shouldBeLeft()

        assertEquals("dat url not found", error.message)
        assertEquals(0, dao.shiftSortOrderCount)
        assertEquals(null, dao.insertedMeta)
        assertEquals(0, dao.insertedData.size)
        coVerify(exactly = 1) { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) }
        coVerify(exactly = 0) { networkDataSource.fetchBytes("https://example.com/dam-history.dat") }
    }

    @Test
    fun fetchAndStore_unsupportedDam_returnsFailureBeforeStorageOrNetwork() = runTest {
        val dao = FakeHistoricalSearchDao()
        val context = contextWithHistoricalStrings()
        every { context.getString(R.string.historical_search_error_dam_not_supported) } returns
            "このダムは過去データ検索に対応していません。"
        val repository = createRepository(dao, context)
        val unsupportedDam = DamConfig(
            id = "unsupported",
            dataUrl = "https://example.com/not-mlit"
        )

        val result = repository.fetchAndStore(unsupportedDam, "20260501", "20260502")

        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { assertEquals("このダムは過去データ検索に対応していません。", it.message) },
            ifRight = { throw AssertionError("Expected failure but got $it") }
        )
        assertEquals(0, dao.duplicateChecks)
        assertEquals(0, dao.metaCountChecks)
    }

    @Test
    fun fetchAndStore_duplicateRange_returnsFailureBeforeNetwork() = runTest {
        val dao = FakeHistoricalSearchDao(duplicateCount = 1)
        val context = contextWithHistoricalStrings()
        every { context.getString(R.string.historical_search_error_duplicate) } returns
            "同じダム・期間の過去データが既に存在します。"
        val repository = createRepository(dao, context)

        val result = repository.fetchAndStore(supportedDam(), "20260501", "20260502")

        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { assertEquals("同じダム・期間の過去データが既に存在します。", it.message) },
            ifRight = { throw AssertionError("Expected failure but got $it") }
        )
        assertEquals(1, dao.duplicateChecks)
        assertEquals(0, dao.metaCountChecks)
    }

    @Test
    fun fetchAndStore_maxStoredMetaCount_returnsLocalizedFailureBeforeNetwork() = runTest {
        val dao = FakeHistoricalSearchDao(metaCount = HistoricalSearchRepository.MAX_STORED_META_COUNT)
        val context = contextWithHistoricalStrings()
        val resources = context.resources
        val maxCount = HistoricalSearchRepository.MAX_STORED_META_COUNT
        every {
            resources.getQuantityString(
                R.plurals.historical_search_error_max_count,
                maxCount, maxCount
            )
        } returns "保存上限です"
        val repository = createRepository(dao, context)

        val result = repository.fetchAndStore(supportedDam(), "20260501", "20260502")

        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { assertEquals("保存上限です", it.message) },
            ifRight = { throw AssertionError("Expected failure but got $it") }
        )
        assertEquals(1, dao.duplicateChecks)
        assertEquals(1, dao.metaCountChecks)
    }

    @Test
    fun fetchAndStore_invalidHistoricalTime_returnsFailureBeforeInsert() = runTest {
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        val networkDataSource = mockk<DamNetworkDataSource>()
        val fileParser = mockk<DamFileParser>()
        coEvery { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) } returns
            Either.Right("""<html><body><a href="/dat/dam-history.dat">dat</a></body></html>""".toByteArray(Charset.forName("EUC-JP")))
        every { fileParser.parseHtmlForDatUrlResult(any()) } returns
            Either.Right("https://www1.river.go.jp/dat/dam-history.dat")
        coEvery { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") } returns
            Either.Right("dat".toByteArray())
        every {
            fileParser.parseHistoricalDatToDataList(any(), "9999999999999", "20260501", "20260501")
        } returns Either.Right(
            historicalMeta("9999999999999", "20260501", "20260501") to listOf(
                DamHistoricalData(
                    time = "2026/5/1 24:01",
                    storagePercentage = 61.2f,
                    storageVolume = 72000f,
                    inflow = 10f,
                    outflow = 9f
                )
            )
        )
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            fileParser = fileParser
        )

        val error = repository.fetchAndStore(networkOnlySupportedDam(), "20260501", "20260501").shouldBeLeft()

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, dao.shiftSortOrderCount)
        assertEquals(null, dao.insertedMeta)
        assertEquals(0, dao.insertedData.size)
    }

    @Test
    fun fetchAndStore_sudmonitorUnavailable_fallsBackToMlitFullRangeAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        mockkStatic(Log::class)
        every { Log.w(any(), any(), any()) } returns 0
        sudmonitorServer.enqueue(MockResponse().setResponseCode(503).setBody("<html>Error 1015 Rate limited</html>"))
        sudmonitorServer.enqueue(MockResponse().setResponseCode(503).setBody("<html>Error 1015 Rate limited</html>"))
        val networkDataSource = mockk<DamNetworkDataSource>()
        coEvery { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) } returns
            Either.Right("""<html><body><a href="/dat/dam-history.dat">dat</a></body></html>""".toByteArray(Charset.forName("EUC-JP")))
        coEvery { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") } returns
            Either.Right(
                historicalDat(
                    listOf(
                        historicalRow(date = "2026/8/1", time = "01:00", storagePercentage = "61.2"),
                        historicalRow(date = "2026/8/1", time = "02:00", storagePercentage = "62.3")
                    )
                )
            )
        val debugLogRepository = FakeDebugLogRepository()
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            debugLogRepository = debugLogRepository
        )

        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260831")

        assertTrue(result.isRight())
        assertEquals(2, sudmonitorServer.requestCount)
        assertEquals(
            "/v1/history/1368080700010/${SudmonitorHistoricalClient.monthlyFileName("1368080700010", YearMonth.of(2026, 8))}",
            sudmonitorServer.takeRequest().path
        )
        assertEquals("/v1/history/1368080700010/latest.dat", sudmonitorServer.takeRequest().path)
        coVerify(exactly = 1) { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) }
        coVerify(exactly = 1) { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") }
        assertEquals(2, dao.insertedData.size)
        val (title, details) = debugLogRepository.addedEntries.single()
        assertEquals("Historical search succeeded.", title)
        assertTrue(details.contains("Data Source: MLIT"))
    }

    @Test
    fun fetchAndStore_sudmonitorMonthly404_fallsBackToMlitAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        sudmonitorServer.enqueue(MockResponse().setResponseCode(404))
        sudmonitorServer.enqueue(MockResponse().setResponseCode(404))
        val networkDataSource = mockk<DamNetworkDataSource>()
        coEvery { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) } returns
            Either.Right("""<html><body><a href="/dat/dam-history.dat">dat</a></body></html>""".toByteArray(Charset.forName("EUC-JP")))
        coEvery { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") } returns
            Either.Right(
                historicalDat(
                    listOf(historicalRow(date = "2026/8/1", time = "01:00", storagePercentage = "61.2"))
                )
            )
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource
        )

        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260831")

        assertTrue(result.isRight())
        assertEquals(2, sudmonitorServer.requestCount)
        coVerify(exactly = 1) { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) }
        coVerify(exactly = 1) { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") }
        assertEquals(1, dao.insertedData.size)
    }

    @Test
    fun fetchAndStore_sudmonitorMonthlyCovers_usesSudmonitorBytesWithoutMlitAccessAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        // production catalogの追加に影響されないようバンドルを空にし、2026-08 fixtureでカスケードを検証する
        val augustBytes = historicalDat(augustHistoricalRows())
        sudmonitorServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", "1368080700010")
                .setHeader("X-TCS-History-Start", "2026-08-01T01:00:00+09:00")
                .setHeader("X-TCS-History-End", "2026-09-01T00:00:00+09:00")
                .setBody(Buffer().write(augustBytes))
        )
        // MLITモックは未スタブ: アクセスするとMockK例外でテストが失敗する
        val networkDataSource = mockk<DamNetworkDataSource>()
        val debugLogRepository = FakeDebugLogRepository()
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            debugLogRepository = debugLogRepository
        )

        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260831")

        assertTrue(result.isRight())
        assertEquals(1, sudmonitorServer.requestCount)
        assertEquals(
            "/v1/history/1368080700010/${SudmonitorHistoricalClient.monthlyFileName("1368080700010", YearMonth.of(2026, 8))}",
            sudmonitorServer.takeRequest().path
        )
        assertEquals(42L, result.fold(ifLeft = { throw AssertionError(it) }, ifRight = { it.id }))
        assertEquals(744, dao.insertedData.size)
        assertEquals("2026/8/1 01:00", dao.insertedData.first().timeStr)
        assertEquals("2026/8/31 24:00", dao.insertedData.last().timeStr)
        assertEquals(744, dao.insertedData.map { it.timeStr }.distinct().size)
        assertEquals(dao.insertedData.map { it.timeMillis }.sorted(), dao.insertedData.map { it.timeMillis })
        val (title, details) = debugLogRepository.addedEntries.single()
        assertEquals("Historical search succeeded.", title)
        assertTrue(details.contains("Data Source: sudmonitor.kusugami-lab.net"))
        assertTrue(details.contains("Dam name: Sameura Dam"))
        assertTrue(details.contains("Period(Start): 2026-08-01T00:00:00+09:00"))
        assertTrue(details.contains("Period(End): 2026-08-31T00:00:00+09:00"))
    }

    @Test
    fun fetchAndStore_sudmonitorMonthlyPartialCoverage_latest404_fallsBackToMlitAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        sudmonitorServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-History-Start", "2026-08-01T00:00:00+09:00")
                .setHeader("X-TCS-History-End", "2026-08-10T23:00:00+09:00")
                .setBody(
                    Buffer().write(
                        historicalDat(
                            listOf(historicalRow(date = "2026/8/1", time = "01:00", storagePercentage = "61.2"))
                        )
                    )
                )
        )
        sudmonitorServer.enqueue(MockResponse().setResponseCode(404))
        val networkDataSource = mockk<DamNetworkDataSource>()
        coEvery { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) } returns
            Either.Right("""<html><body><a href="/dat/dam-history.dat">dat</a></body></html>""".toByteArray(Charset.forName("EUC-JP")))
        coEvery { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") } returns
            Either.Right(
                historicalDat(
                    listOf(historicalRow(date = "2026/8/1", time = "01:00", storagePercentage = "61.2"))
                )
            )
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource
        )

        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260831")

        assertTrue(result.isRight())
        assertEquals(2, sudmonitorServer.requestCount)
        coVerify(exactly = 1) { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) }
        coVerify(exactly = 1) { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") }
        assertEquals(1, dao.insertedData.size)
    }

    @Test
    fun fetchAndStore_sudmonitorLatestCoverage_coversCrossMonthRangeAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        sudmonitorServer.enqueue(MockResponse().setResponseCode(404)) // 2026-08月次
        sudmonitorServer.enqueue(MockResponse().setResponseCode(404)) // 2026-09月次
        sudmonitorServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-History-Since", "2026-08-31T01:00:00+09:00")
                .setHeader("X-TCS-History-Until", "2026-09-02T00:00:00+09:00")
                .setBody(
                    Buffer().write(
                        historicalDat(
                            listOf(
                                historicalRow(date = "2026/8/31", time = "01:00", storagePercentage = "68.9"),
                                historicalRow(date = "2026/8/31", time = "24:00", storagePercentage = "68.7"),
                                historicalRow(date = "2026/9/1", time = "01:00", storagePercentage = "68.5"),
                                historicalRow(date = "2026/9/1", time = "02:00", storagePercentage = "68.3")
                            )
                        )
                    )
                )
        )
        // MLITモックは未スタブ: アクセスするとMockK例外でテストが失敗する
        val networkDataSource = mockk<DamNetworkDataSource>()
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource
        )

        val result = repository.fetchAndStore(supportedDam(), "20260831", "20260901")

        assertTrue(result.isRight())
        assertEquals(3, sudmonitorServer.requestCount)
        assertEquals(
            listOf("2026/8/31 01:00", "2026/8/31 24:00", "2026/9/1 01:00", "2026/9/1 02:00"),
            dao.insertedData.map { it.timeStr }
        )
        assertEquals(listOf(68.9f, 68.7f, 68.5f, 68.3f), dao.insertedData.map { it.storagePercentage })
    }

    @Test
    fun fetchAndStore_sudmonitorMerge_dedupePrefersBundleRowsAugust2026() = runTest {
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        // 2026-07は実バンドルで全区間カバーされるため、バンドル優先検証は2026-08を含む合成エントリで行う
        mockkObject(HistoricalDatFileTable)
        every { HistoricalDatFileTable.entries } returns listOf(
            HistoricalDatFileTable.Entry(
                stationId = "1368080700010",
                startDatetime = "202607150100",
                endDatetime = "202608010100",
                filePath = "history/1368080700010_202607150100_202608010100.dat"
            )
        )
        val bundleBytes = historicalDat(
            listOf(
                historicalRow(date = "2026/7/31", time = "01:00", storagePercentage = "61.2"),
                historicalRow(date = "2026/8/1", time = "01:00", storagePercentage = "70.0")
            )
        )
        every { context.assets.open(any()) } returns ByteArrayInputStream(bundleBytes)
        sudmonitorServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-History-Start", "2026-08-01T01:00:00+09:00")
                .setHeader("X-TCS-History-End", "2026-09-01T00:00:00+09:00")
                .setBody(
                    Buffer().write(
                        historicalDat(
                            listOf(
                                historicalRow(date = "2026/8/1", time = "01:00", storagePercentage = "66.0"),
                                historicalRow(date = "2026/8/1", time = "02:00", storagePercentage = "67.0"),
                                historicalRow(date = "2026/8/2", time = "01:00", storagePercentage = "68.0")
                            )
                        )
                    )
                )
        )
        val networkDataSource = mockk<DamNetworkDataSource>()
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource
        )

        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260831")

        assertTrue(result.isRight())
        assertEquals(1, sudmonitorServer.requestCount)
        // 同一epochは高優先度ソース（バンドル > 月次）の行を採用
        assertEquals(
            listOf("2026/8/1 01:00", "2026/8/1 02:00", "2026/8/2 01:00"),
            dao.insertedData.map { it.timeStr }
        )
        assertEquals(listOf(70.0f, 67.0f, 68.0f), dao.insertedData.map { it.storagePercentage })
    }

    @Test
    fun fetchAndStore_mlitDirect_doesNotProbeSudmonitorAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        val networkDataSource = mockk<DamNetworkDataSource>()
        coEvery { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) } returns
            Either.Right("""<html><body><a href="/dat/dam-history.dat">dat</a></body></html>""".toByteArray(Charset.forName("EUC-JP")))
        coEvery { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") } returns
            Either.Right(
                historicalDat(
                    listOf(historicalRow(date = "2026/8/1", time = "01:00", storagePercentage = "61.2"))
                )
            )
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            settingsRepository = FakeSettingsRepository(
                AppSettings(historicalDataSource = RealtimeDataSource.MLIT_DIRECT)
            )
        )

        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260831")

        assertTrue(result.isRight())
        assertEquals(0, sudmonitorServer.requestCount)
        coVerify(exactly = 1) { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) }
        coVerify(exactly = 1) { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") }
    }

    @Test
    fun fetchAndStore_bundleCovered_successWithoutDebugLog() = runTest {
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        every { context.assets.open(any()) } returns ByteArrayInputStream(
            historicalDat(
                listOf(
                    historicalRow(date = "2026/6/1", time = "01:00", storagePercentage = "61.2"),
                    historicalRow(date = "2026/6/30", time = "23:00", storagePercentage = "62.3")
                )
            )
        )
        val debugLogRepository = FakeDebugLogRepository()
        val repository = createRepository(
            dao = dao,
            context = context,
            debugLogRepository = debugLogRepository
        )

        val result = repository.fetchAndStore(supportedDam(), "20260601", "20260630")

        assertTrue(result.isRight())
        assertEquals(2, dao.insertedData.size)
        assertEquals(0, sudmonitorServer.requestCount)
        assertTrue(debugLogRepository.addedEntries.isEmpty())
    }

    @Test
    fun fetchAndStore_bundleCoveredJuly2026_successWithoutDebugLog() = runTest {
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        // 2026-07バンドル復元後はバンドル全区間カバーで完結し、sudmonitor非プローブ・成功デバッグログなしを固定する
        every { context.assets.open(any()) } returns ByteArrayInputStream(
            historicalDat(
                listOf(
                    historicalRow(date = "2026/7/1", time = "01:00", storagePercentage = "61.2"),
                    historicalRow(date = "2026/7/31", time = "23:00", storagePercentage = "62.3")
                )
            )
        )
        val debugLogRepository = FakeDebugLogRepository()
        val repository = createRepository(
            dao = dao,
            context = context,
            debugLogRepository = debugLogRepository
        )

        val result = repository.fetchAndStore(supportedDam(), "20260701", "20260731")

        assertTrue(result.isRight())
        assertEquals(2, dao.insertedData.size)
        assertEquals(0, sudmonitorServer.requestCount)
        assertTrue(debugLogRepository.addedEntries.isEmpty())
    }

    @Test
    fun fetchAndStore_duplicate_failureWithoutDebugLog() = runTest {
        val dao = FakeHistoricalSearchDao(duplicateCount = 1)
        val context = contextWithHistoricalStrings()
        val debugLogRepository = FakeDebugLogRepository()
        val repository = createRepository(
            dao = dao,
            context = context,
            debugLogRepository = debugLogRepository
        )

        val result = repository.fetchAndStore(supportedDam(), "20260701", "20260731")

        assertTrue(result.isLeft())
        assertEquals(0, sudmonitorServer.requestCount)
        assertTrue(debugLogRepository.addedEntries.isEmpty())
    }

    @Test
    fun fetchAndStore_mlitFetchFailure_logsFailureWithReasonAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        sudmonitorServer.enqueue(MockResponse().setResponseCode(404))
        sudmonitorServer.enqueue(MockResponse().setResponseCode(404))
        val networkDataSource = mockk<DamNetworkDataSource>()
        coEvery { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) } returns
            Either.Left(IOException("network boom"))
        val debugLogRepository = FakeDebugLogRepository()
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            debugLogRepository = debugLogRepository
        )

        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260831")

        assertTrue(result.isLeft())
        assertEquals(0, dao.insertedData.size)
        val (title, details) = debugLogRepository.addedEntries.single()
        assertEquals("Historical search failed.", title)
        assertTrue(details.contains("Reason: network boom"))
        assertTrue(details.contains("Data Source: MLIT"))
        assertTrue(details.contains("Period(Start): 2026-08-01T00:00:00+09:00"))
        assertTrue(details.contains("Period(End): 2026-08-31T00:00:00+09:00"))
    }

    @Test
    fun fetchAndStore_networkUnavailable_failureWithoutLogsAndSudmonitorProbesAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        val debugLogRepository = FakeDebugLogRepository()
        val repository = createRepository(
            dao = dao,
            context = context,
            networkAvailability = FakeNetworkAvailability(available = false),
            debugLogRepository = debugLogRepository
        )

        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260831")

        assertTrue(result.isLeft())
        assertEquals("network unavailable", result.fold(ifLeft = { it.message }, ifRight = { null }))
        assertEquals(0, sudmonitorServer.requestCount)
        assertTrue(debugLogRepository.addedEntries.isEmpty())
    }

    @Test
    fun fetchAndStore_sudmonitorDailyLoaded_exactMatch_returnsLoadedErrorAugust2026() = runTest {
        val dao = FakeHistoricalSearchDao()
        val context = contextWithHistoricalStrings()
        val sudmonitorHistoryDao = FakeSudmonitorHistoryDao(
            historyRow = sudmonitorHistoryRow(
                periodStartEpochMs = jstDayStartMillis(2026, 8, 1),
                periodEndEpochMs = jstDayStartMillis(2026, 8, 31)
            )
        )
        val repository = createRepository(
            dao = dao,
            context = context,
            sudmonitorHistoryDao = sudmonitorHistoryDao
        )

        // 完全に同一期間（読込済み [20260801, 20260831]）は拒否（D2）
        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260831")

        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { assertEquals("already loaded as sudmonitor daily historical data", it.message) },
            ifRight = { throw AssertionError("Expected failure but got $it") }
        )
        assertEquals(1, dao.duplicateChecks)
        assertEquals(0, dao.metaCountChecks)
        assertEquals(1, sudmonitorHistoryDao.findByDamIdCalls)
        assertEquals(0, sudmonitorServer.requestCount)
    }

    @Test
    fun fetchAndStore_sudmonitorDailyLoaded_properSubset_servedFromLocalWithoutSudmonitorAccessAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        val sudmonitorHistoryDao = FakeSudmonitorHistoryDao(
            historyRow = sudmonitorHistoryRow(
                periodStartEpochMs = jstDayStartMillis(2026, 8, 1),
                periodEndEpochMs = jstDayStartMillis(2026, 8, 31)
            ),
            observations = listOf(
                observationEntity("2026/8/1 01:00", 68.9f),
                observationEntity("2026/8/1 02:00", 68.7f),
                observationEntity("2026/8/15 23:00", 68.5f),
                observationEntity("2026/8/15 24:00", 68.3f)
            )
        )
        val debugLogRepository = FakeDebugLogRepository()
        val repository = createRepository(
            dao = dao,
            context = context,
            sudmonitorHistoryDao = sudmonitorHistoryDao,
            debugLogRepository = debugLogRepository
        )

        // 真部分集合は拒否せず、ローカルカバレッジから提供（D8: fetchMonthly / fetchLatest 未呼び出し）
        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260815")

        assertTrue(result.isRight())
        assertEquals(0, sudmonitorServer.requestCount)
        assertEquals(
            listOf("2026/8/1 01:00", "2026/8/1 02:00", "2026/8/15 23:00", "2026/8/15 24:00"),
            dao.insertedData.map { it.timeStr }
        )
        assertEquals("20260801", dao.insertedMeta?.searchBgnDate)
        assertEquals("20260815", dao.insertedMeta?.searchEndDate)
        assertEquals(2, sudmonitorHistoryDao.findByDamIdCalls) // D2判定 + カバレッジ統合
        assertEquals(1, sudmonitorHistoryDao.getAllObservationsCalls)
        val (title, details) = debugLogRepository.addedEntries.single()
        assertEquals("Historical search succeeded.", title)
        assertTrue(details.contains("Data Source: sudmonitor.kusugami-lab.net"))
    }

    @Test
    fun fetchAndStore_sudmonitorDailyLoaded_superset_allowedWithCascadeAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        val sudmonitorHistoryDao = FakeSudmonitorHistoryDao(
            historyRow = sudmonitorHistoryRow(
                periodStartEpochMs = jstDayStartMillis(2026, 8, 1),
                periodEndEpochMs = jstDayStartMillis(2026, 8, 31)
            ),
            observations = listOf(
                observationEntity("2026/8/1 01:00", 68.9f),
                observationEntity("2026/8/31 24:00", 68.3f)
            )
        )
        // 2026-09月次: 9/1〜9/5（残ギャップ）をカバー
        sudmonitorServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", "1368080700010")
                .setHeader("X-TCS-History-Start", "2026-09-01T01:00:00+09:00")
                .setHeader("X-TCS-History-End", "2026-09-06T00:00:00+09:00")
                .setBody(
                    Buffer().write(
                        historicalDat(
                            listOf(
                                historicalRow(date = "2026/9/1", time = "01:00", storagePercentage = "61.2"),
                                historicalRow(date = "2026/9/5", time = "24:00", storagePercentage = "61.5")
                            )
                        )
                    )
                )
        )
        val networkDataSource = mockk<DamNetworkDataSource>()
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            sudmonitorHistoryDao = sudmonitorHistoryDao
        )

        // 上位集合（読込済み期間を含む検索）は拒否せず、ローカル + 残ギャップのカスケードで提供
        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260905")

        assertTrue(result.isRight())
        assertEquals(1, sudmonitorServer.requestCount)
        assertEquals(
            "/v1/history/1368080700010/${SudmonitorHistoricalClient.monthlyFileName("1368080700010", YearMonth.of(2026, 9))}",
            sudmonitorServer.takeRequest().path
        )
        assertEquals(
            listOf("2026/8/1 01:00", "2026/8/31 24:00", "2026/9/1 01:00", "2026/9/5 24:00"),
            dao.insertedData.map { it.timeStr }
        )
    }

    @Test
    fun fetchAndStore_sudmonitorDailyLoaded_partialOverlap_allowedWithCascadeAugust2026() = runTest {
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        // 検索期間をバンドルの影響から切り離すため、合成エントリを空にする
        mockkObject(HistoricalDatFileTable)
        every { HistoricalDatFileTable.entries } returns emptyList()
        val sudmonitorHistoryDao = FakeSudmonitorHistoryDao(
            historyRow = sudmonitorHistoryRow(
                periodStartEpochMs = jstDayStartMillis(2026, 8, 1),
                periodEndEpochMs = jstDayStartMillis(2026, 8, 31)
            ),
            observations = listOf(
                observationEntity("2026/8/10 01:00", 68.9f),
                observationEntity("2026/8/31 24:00", 68.3f)
            )
        )
        // 2026-09月次: 9/1〜9/10（残ギャップ）をカバー
        sudmonitorServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", "1368080700010")
                .setHeader("X-TCS-History-Start", "2026-09-01T01:00:00+09:00")
                .setHeader("X-TCS-History-End", "2026-09-11T00:00:00+09:00")
                .setBody(
                    Buffer().write(
                        historicalDat(
                            listOf(
                                historicalRow(date = "2026/9/1", time = "01:00", storagePercentage = "61.2"),
                                historicalRow(date = "2026/9/10", time = "24:00", storagePercentage = "61.5")
                            )
                        )
                    )
                )
        )
        val networkDataSource = mockk<DamNetworkDataSource>()
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            sudmonitorHistoryDao = sudmonitorHistoryDao
        )

        // 部分重複（読込済み [20260801, 20260831] と検索 [20260810, 20260910]）は拒否しない
        val result = repository.fetchAndStore(supportedDam(), "20260810", "20260910")

        assertTrue(result.isRight())
        assertEquals(1, sudmonitorServer.requestCount)
        assertEquals(
            listOf("2026/8/10 01:00", "2026/8/31 24:00", "2026/9/1 01:00", "2026/9/10 24:00"),
            dao.insertedData.map { it.timeStr }
        )
    }

    @Test
    fun fetchAndStore_sudmonitorDailyLoaded_rowMissing_keepsLegacyCascadeAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        val sudmonitorHistoryDao = FakeSudmonitorHistoryDao()
        sudmonitorServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", "1368080700010")
                .setHeader("X-TCS-History-Start", "2026-08-01T01:00:00+09:00")
                .setHeader("X-TCS-History-End", "2026-08-16T00:00:00+09:00")
                .setBody(
                    Buffer().write(
                        historicalDat(
                            listOf(
                                historicalRow(date = "2026/8/1", time = "01:00", storagePercentage = "61.2"),
                                historicalRow(date = "2026/8/15", time = "24:00", storagePercentage = "62.3")
                            )
                        )
                    )
                )
        )
        val networkDataSource = mockk<DamNetworkDataSource>()
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            sudmonitorHistoryDao = sudmonitorHistoryDao
        )

        // 行未保存: 拒否なし・ローカルカバレッジ統合なし（従来の月次プローブで補完）
        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260815")

        assertTrue(result.isRight())
        assertEquals(1, sudmonitorServer.requestCount)
        assertEquals(
            "/v1/history/1368080700010/${SudmonitorHistoricalClient.monthlyFileName("1368080700010", YearMonth.of(2026, 8))}",
            sudmonitorServer.takeRequest().path
        )
        assertEquals(
            listOf("2026/8/1 01:00", "2026/8/15 24:00"),
            dao.insertedData.map { it.timeStr }
        )
        assertEquals(2, sudmonitorHistoryDao.findByDamIdCalls) // D2判定 + カバレッジ統合（行なし）
        assertEquals(0, sudmonitorHistoryDao.getAllObservationsCalls)
    }

    @Test
    fun fetchAndStore_mlitDirect_noRejectionAndNoCoverageIntegrationAugust2026() = runTest {
        isolateFromBundledHistoricalData()
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        val sudmonitorHistoryDao = FakeSudmonitorHistoryDao(
            historyRow = sudmonitorHistoryRow(
                periodStartEpochMs = jstDayStartMillis(2026, 8, 1),
                periodEndEpochMs = jstDayStartMillis(2026, 8, 31)
            ),
            observations = listOf(observationEntity("2026/8/1 01:00", 68.9f))
        )
        val networkDataSource = mockk<DamNetworkDataSource>()
        coEvery { networkDataSource.fetchBytes(match { it.contains("DspDamData.exe") }) } returns
            Either.Right("""<html><body><a href="/dat/dam-history.dat">dat</a></html>""".toByteArray(Charset.forName("EUC-JP")))
        coEvery { networkDataSource.fetchBytes("https://www1.river.go.jp/dat/dam-history.dat") } returns
            Either.Right(
                historicalDat(
                    listOf(historicalRow(date = "2026/8/1", time = "01:00", storagePercentage = "61.2"))
                )
            )
        val debugLogRepository = FakeDebugLogRepository()
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            sudmonitorHistoryDao = sudmonitorHistoryDao,
            settingsRepository = FakeSettingsRepository(
                AppSettings(historicalDataSource = RealtimeDataSource.MLIT_DIRECT)
            ),
            debugLogRepository = debugLogRepository
        )

        // ゲート無効: 完全同一でも拒否せず、カバレッジ統合も行わない（MLIT直接取得）
        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260831")

        assertTrue(result.isRight())
        assertEquals(0, sudmonitorServer.requestCount)
        assertEquals(0, sudmonitorHistoryDao.findByDamIdCalls)
        assertEquals(0, sudmonitorHistoryDao.getAllObservationsCalls)
        val (title, details) = debugLogRepository.addedEntries.single()
        assertEquals("Historical search succeeded.", title)
        assertTrue(details.contains("Data Source: MLIT"))
    }

    @Test
    fun fetchAndStore_sudmonitorMerge_dedupePrefersBundleOverLocalAugust2026() = runTest {
        val dao = FakeHistoricalSearchDao(insertedMetaId = 42L)
        val context = contextWithHistoricalStrings()
        // 2026-08の合成バンドルエントリ（8/1〜8/2のみ。検索期間を覆い切らないためカスケードが実行される）と、
        // 読込済み日次過去データの観測行で重複を検証する
        mockkObject(HistoricalDatFileTable)
        every { HistoricalDatFileTable.entries } returns listOf(
            HistoricalDatFileTable.Entry(
                stationId = "1368080700010",
                startDatetime = "202608010100",
                endDatetime = "202608020100",
                filePath = "history/1368080700010_202608010100_202608020100.dat"
            )
        )
        val bundleBytes = historicalDat(
            listOf(
                historicalRow(date = "2026/8/1", time = "01:00", storagePercentage = "61.2"),
                historicalRow(date = "2026/8/1", time = "02:00", storagePercentage = "62.0")
            )
        )
        every { context.assets.open(any()) } returns ByteArrayInputStream(bundleBytes)
        val sudmonitorHistoryDao = FakeSudmonitorHistoryDao(
            historyRow = sudmonitorHistoryRow(
                periodStartEpochMs = jstDayStartMillis(2026, 8, 1),
                periodEndEpochMs = jstDayStartMillis(2026, 8, 31)
            ),
            observations = listOf(
                observationEntity("2026/8/1 01:00", 68.9f),
                observationEntity("2026/8/1 02:00", 68.7f),
                observationEntity("2026/8/2 01:00", 68.5f)
            )
        )
        val networkDataSource = mockk<DamNetworkDataSource>()
        val repository = createRepository(
            dao = dao,
            context = context,
            networkDataSource = networkDataSource,
            sudmonitorHistoryDao = sudmonitorHistoryDao
        )

        // 検索期間は読込済み期間の真部分集合（D2: 拒否されない）で、バンドル+ローカルが全区間をカバー
        val result = repository.fetchAndStore(supportedDam(), "20260801", "20260830")

        assertTrue(result.isRight())
        assertEquals(0, sudmonitorServer.requestCount)
        // 同一epochは高優先度ソース（バンドル ≧ ローカル）の行を採用
        assertEquals(
            listOf("2026/8/1 01:00", "2026/8/1 02:00", "2026/8/2 01:00"),
            dao.insertedData.map { it.timeStr }
        )
        assertEquals(listOf(61.2f, 62.0f, 68.5f), dao.insertedData.map { it.storagePercentage })
        assertEquals(1, sudmonitorHistoryDao.getAllObservationsCalls)
    }

    private fun createRepository(
        dao: HistoricalSearchDao,
        context: Context = contextWithHistoricalStrings(),
        networkDataSource: DamNetworkDataSource = DamNetworkDataSource(MlitEndpointConfig.localhostHttp()),
        fileParser: DamFileParser = DamFileParser(datEndpointConfig = MlitEndpointConfig.production()),
        transactionRunner: DatabaseTransactionRunner = ImmediateTransactionRunner(),
        sudmonitorClient: SudmonitorHistoricalClient = SudmonitorHistoricalClient(
            SudmonitorEndpointConfig.localhostHttp(sudmonitorServer.url("/").toString())
        ),
        settingsRepository: SettingsRepository = FakeSettingsRepository(
            AppSettings(historicalDataSource = RealtimeDataSource.SUDMONITOR)
        ),
        networkAvailability: NetworkAvailability = FakeNetworkAvailability(),
        sudmonitorHistoryDao: SudmonitorHistoryDao = FakeSudmonitorHistoryDao(),
        debugLogRepository: DebugLogRepository = FakeDebugLogRepository()
    ): HistoricalSearchRepositoryImpl =
        HistoricalSearchRepositoryImpl(
            networkDataSource = networkDataSource,
            fileParser = fileParser,
            transactionRunner = transactionRunner,
            historicalSearchDao = dao,
            sudmonitorHistoryDao = sudmonitorHistoryDao,
            networkAvailability = networkAvailability,
            sudmonitorHistoricalClient = sudmonitorClient,
            settingsRepository = settingsRepository,
            debugLogRepository = debugLogRepository,
            context = context
        )

    /** Network経路のテストを増え続けるproduction bundle catalogから隔離する。 */
    private fun isolateFromBundledHistoricalData() {
        mockkObject(HistoricalDatFileTable)
        every { HistoricalDatFileTable.entries } returns emptyList()
    }

    private fun supportedDam(): DamConfig =
        DamConfig(
            id = "1368080700010",
            dataUrl = "https://www1.river.go.jp/cgi-bin/DspDamData.exe?KIND=1&ID=1368080700010"
        )

    private fun networkOnlySupportedDam(): DamConfig =
        DamConfig(
            id = "9999999999999",
            nameJa = "テストダム",
            nameEn = "Test Dam",
            dataUrl = "https://www1.river.go.jp/cgi-bin/DspDamData.exe?KIND=1&ID=9999999999999"
        )

    private fun contextWithHistoricalStrings(): Context {
        val context = mockk<Context>()
        val resources = mockk<Resources>()
        val assets = mockk<AssetManager>()
        every { context.resources } returns resources
        every { context.assets } returns assets
        every { assets.open(any()) } throws AssertionError("Network-path unit tests must not read bundled assets.")
        every { context.getString(R.string.historical_search_error_dam_not_supported) } returns "unsupported dam"
        every { context.getString(R.string.historical_search_error_duplicate) } returns "duplicate"
        every { context.getString(R.string.historical_search_duplicate) } returns "duplicate"
        every { context.getString(R.string.historical_search_error_network_unavailable) } returns "network unavailable"
        every { context.getString(R.string.historical_search_error_dat_url_not_found_html) } returns "dat url not found"
        every { context.getString(R.string.historical_search_error_no_data) } returns "no data"
        every { context.getString(R.string.historical_search_error_sudmonitor_daily_loaded) } returns
            "already loaded as sudmonitor daily historical data"
        every {
            resources.getQuantityString(
                R.plurals.historical_search_error_max_count,
                HistoricalSearchRepository.MAX_STORED_META_COUNT,
                HistoricalSearchRepository.MAX_STORED_META_COUNT
            )
        } returns "max count"
        return context
    }

    private fun historicalDat(dataRows: List<String>): ByteArray =
        (
            listOf(
                "任意期間ダム諸量検索結果",
                "水系名,吉野川",
                "河川名,吉野川",
                "観測所名,早明浦ダム",
                "観測所記号,1368080700010",
                "#年月日,時刻,流域平均雨量,雨量属性,貯水量,貯水量属性,流入量,流入量属性,放流量,放流量属性,貯水率,貯水率属性"
            ) + dataRows
        ).joinToString("\n").toByteArray(Charset.forName("Shift_JIS"))

    private fun historicalRow(
        date: String,
        time: String,
        storagePercentage: String
    ): String =
        listOf(
            date,
            time,
            "0.0",
            "",
            "72000",
            "",
            "10.0",
            "",
            "9.0",
            "",
            storagePercentage,
            ""
        ).joinToString(",")

    /** 2026-08の合成行一覧（31日×24時間=744行）を返す。 */
    private fun augustHistoricalRows(): List<String> =
        buildList {
            for (day in 1..31) {
                for (hour in 1..24) {
                    add(historicalRow(date = "2026/8/$day", time = "%02d:00".format(hour), storagePercentage = "61.2"))
                }
            }
        }

    private fun historicalMeta(
        damConfigId: String,
        startDate: String,
        endDate: String
    ): HistoricalSearchMeta =
        HistoricalSearchMeta(
            id = 0L,
            observationStationId = "1368080700010",
            observationStationName = "早明浦ダム",
            riverSystemName = "吉野川",
            riverName = "吉野川",
            damConfigId = damConfigId,
            searchBgnDate = startDate,
            searchEndDate = endDate,
            fetchedAt = 1L,
            dataStartTimeStr = null,
            dataEndTimeStr = null,
            dataStartStoragePct = null,
            dataEndStoragePct = null,
            dataMinStoragePct = null,
            dataMaxStoragePct = null
        )

    private class FakeHistoricalSearchDao(
        private val duplicateCount: Int = 0,
        private val metaCount: Int = 0,
        private val insertedMetaId: Long = 1L
    ) : HistoricalSearchDao {
        var duplicateChecks = 0
            private set
        var metaCountChecks = 0
            private set
        var shiftSortOrderCount = 0
            private set
        var insertedMeta: HistoricalSearchMetaEntity? = null
            private set
        var insertedData: List<HistoricalDamDataEntity> = emptyList()
            private set

        override suspend fun countDuplicates(
            damConfigId: String,
            searchBgnDate: String,
            searchEndDate: String
        ): Int {
            duplicateChecks += 1
            return duplicateCount
        }

        override suspend fun countMeta(): Int {
            metaCountChecks += 1
            return metaCount
        }

        override suspend fun deleteDataByMetaId(metaId: Long) = Unit
        override suspend fun deleteMetaById(metaId: Long) = Unit
        override suspend fun shiftAllSortOrderUp() {
            shiftSortOrderCount += 1
        }

        override suspend fun insertMeta(meta: HistoricalSearchMetaEntity): Long {
            insertedMeta = meta
            return insertedMetaId
        }

        override suspend fun insertDataList(list: List<HistoricalDamDataEntity>) {
            insertedData = list
        }

        override suspend fun getMetaList(): List<HistoricalSearchMetaEntity> = emptyList()
        override suspend fun getAllMeta(): List<HistoricalSearchMetaEntity> = emptyList()
        override suspend fun updateSortOrder(metaId: Long, sortOrder: Int) = Unit
        override suspend fun getMetaById(metaId: Long): HistoricalSearchMetaEntity? = null

        override suspend fun deleteAllData() = Unit
        override suspend fun deleteAllMeta() = Unit
        override suspend fun queryDataByMetaIdAndTimeRange(
            metaId: Long,
            fromMillis: Long,
            toMillis: Long
        ): List<HistoricalDamDataEntity> = emptyList()
        override suspend fun queryOldestTimeMillisByMetaId(metaId: Long): Long? = null
        override suspend fun queryNewestTimeMillisByMetaId(metaId: Long): Long? = null
        override suspend fun queryAllDataByMetaId(metaId: Long): List<HistoricalDamDataEntity> = emptyList()
        override suspend fun updatePinnedStatus(metaId: Long, isPinned: Boolean) = Unit
        override suspend fun countPinnedMeta(): Int = 0
    }

    private fun jstDayStartMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(TimeUtils.JST_ZONE).toInstant().toEpochMilli()

    private fun sudmonitorHistoryRow(
        periodStartEpochMs: Long,
        periodEndEpochMs: Long
    ): SudmonitorHistoryEntity =
        SudmonitorHistoryEntity(
            damId = "1368080700010",
            periodStartEpochMs = periodStartEpochMs,
            periodEndEpochMs = periodEndEpochMs,
            status = "SUCCESS",
            rowCount = 0,
            firstStorageRatePct = null,
            lastStorageRatePct = null,
            minStorageRatePct = null,
            maxStorageRatePct = null,
            fetchedAtEpochMs = 1L,
            nextUpdateAtEpochMs = 1L,
            rawDatPath = null,
            updatedAtEpochMs = 1L
        )

    private fun observationEntity(
        time: String,
        storagePercentage: Float
    ): SudmonitorHistoryObservationEntity =
        SudmonitorHistoryObservationEntity(
            damId = "1368080700010",
            rowNo = 0,
            timeText = time,
            timeEpochMs = TimeUtils.parseJstMillisAllow24Hour(time, "yyyy/MM/dd HH:mm") ?: 0L,
            rainfallHourlyMm = 0f,
            storageVolume1000m3 = 72000f,
            inflowM3s = 10f,
            outflowM3s = 9f,
            storageRatePct = storagePercentage
        )

    private class FakeSudmonitorHistoryDao(
        var historyRow: SudmonitorHistoryEntity? = null,
        var observations: List<SudmonitorHistoryObservationEntity> = emptyList()
    ) : SudmonitorHistoryDao {
        var findByDamIdCalls = 0
            private set
        var getAllObservationsCalls = 0
            private set

        override suspend fun findByDamId(damId: String): SudmonitorHistoryEntity? {
            findByDamIdCalls += 1
            return historyRow
        }

        override fun findByDamIdFlow(damId: String): Flow<SudmonitorHistoryEntity?> =
            flow { emit(historyRow) }

        override suspend fun getAllHistory(): List<SudmonitorHistoryEntity> = listOfNotNull(historyRow)

        override suspend fun getAllObservations(): List<SudmonitorHistoryObservationEntity> = observations

        override suspend fun getAllObservations(damId: String): List<SudmonitorHistoryObservationEntity> {
            getAllObservationsCalls += 1
            return observations
        }

        override suspend fun upsertHistory(entity: SudmonitorHistoryEntity) = Unit
        override suspend fun insertObservations(list: List<SudmonitorHistoryObservationEntity>) = Unit
        override suspend fun deleteObservations(damId: String) = Unit

        override suspend fun queryObservationsByDamIdAndTimeRange(
            damId: String,
            from: Long,
            to: Long
        ): List<SudmonitorHistoryObservationEntity> = emptyList()

        override suspend fun deleteAllObservations() = Unit
        override suspend fun deleteAllHistory() = Unit
    }

    private class ImmediateTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
    }
}
