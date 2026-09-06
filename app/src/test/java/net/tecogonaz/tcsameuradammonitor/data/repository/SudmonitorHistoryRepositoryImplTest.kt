// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import android.content.Context
import android.util.Log
import arrow.core.Either
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.DebugDatSourceReader
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryObservationEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DatabaseTransactionRunner
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.data.source.remote.MlitEndpointConfig
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorEndpointConfig
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorHistoricalClient
import net.tecogonaz.tcsameuradammonitor.data.source.remote.createMlitHttpClient
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugDatSelectionMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryFetchResult
import net.tecogonaz.tcsameuradammonitor.domain.repository.SudmonitorHistoryTrigger
import net.tecogonaz.tcsameuradammonitor.testutil.FakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.FakeSudmonitorHistoryDao
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * sudmonitor 日次過去データリポジトリ [SudmonitorHistoryRepositoryImpl] のユニットテストクラス。
 *
 * MockWebServer + 実 [SudmonitorHistoricalClient] / [DamFileParser] と、テスト用の偽 DAO
 * （[FakeSudmonitorHistoryDao]）・トランザクションランナー・設定リポジトリを組み合わせて、
 * 取得→保存（行 + 観測行・統計・次回更新時刻）、404（未蓄積）スキップ、5xx・タイムアウト失敗、
 * `X-TCS-Next-Update-At` 欠落時の翌日 00:13 JST フォールバック、ダムごと上書き（D3）、
 * `autoFetch` のクールダウン判定（1時間/12時間 スキップ・1日/7日 実行・行未保存時は即実行・
 * ゲート無効時は何もしない）を検証します。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SudmonitorHistoryRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File
    private var currentTimeMillis = 0L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        cacheDir = File("../../work/sudmonitor-history-test-${UUID.randomUUID()}").canonicalFile
        check(cacheDir.mkdirs())
        currentTimeMillis = Instant.parse("2026-08-01T10:00:00Z").toEpochMilli()
    }

    @After
    fun tearDown() {
        runCatching { unmockkStatic(Log::class) }
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    /**
     * android.util.Log の静的メソッドをモックします（既存 [HistoricalSearchRepositoryImplTest] と同じ方式）。
     * `autoFetch` はクールダウンスキップ・失敗時に Log.d / Log.w を呼び出すため、該当テストで事前に呼び出すこと。
     */
    private fun mockAndroidLog() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    // ---- fetchAndStore ----

    @Test
    fun fetchAndStore_success_upsertsRowAndObservationsWithStatsAndNextUpdate() = runTest {
        server.enqueue(
            successResponse(
                rows = listOf(
                    row("2026/8/1", "01:00", "80.0"),
                    row("2026/8/1", "02:00", "81.0"),
                    row("2026/8/1", "03:00", "79.5")
                ),
                since = "2026-08-01T00:00:00+09:00",
                until = "2026-08-01T23:00:00+09:00",
                nextUpdateAt = "2026-08-02T00:13:00Z"
            )
        )
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(realClient(), dao = dao)

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.INITIAL)

        assertTrue(result.isRight())
        val fetchResult = result.getOrNull()
        assertTrue(fetchResult is SudmonitorHistoryFetchResult.Success)
        val history = (fetchResult as SudmonitorHistoryFetchResult.Success).history
        assertEquals(TEST_DAM_ID, history.damId)
        assertEquals(jstDayStartMillis(2026, 8, 1), history.periodStartEpochMs)
        assertEquals(jstDayStartMillis(2026, 8, 1), history.periodEndEpochMs)
        assertEquals(SudmonitorHistory.STATUS_SUCCESS, history.status)
        assertEquals(3, history.rowCount)
        assertEquals(80.0f, history.firstStorageRatePct ?: -1f, 0.001f)
        assertEquals(79.5f, history.lastStorageRatePct ?: -1f, 0.001f)
        assertEquals(79.5f, history.minStorageRatePct ?: -1f, 0.001f)
        assertEquals(81.0f, history.maxStorageRatePct ?: -1f, 0.001f)
        assertEquals(currentTimeMillis, history.fetchedAtEpochMs)
        assertEquals(Instant.parse("2026-08-02T00:13:00Z").toEpochMilli(), history.nextUpdateAtEpochMs)
        assertNotNull(history.rawDatPath)

        // 保存は「観測行 DELETE → 行 upsert → 観測行 INSERT」の順で1回ずつ実施される
        assertEquals(1, dao.deleteObservationsCalls)
        assertEquals(1, dao.upsertCalls)
        assertEquals(1, dao.insertObservationsCalls)
        val inserted = dao.insertedObservationLists.single()
        assertEquals(listOf(0, 1, 2), inserted.map { it.rowNo })
        assertEquals(listOf("2026/8/1 01:00", "2026/8/1 02:00", "2026/8/1 03:00"), inserted.map { it.timeText })
        assertEquals(listOf(80.0f, 81.0f, 79.5f), inserted.map { it.storageRatePct })
        assertNotNull(dao.historyRow)
        assertEquals(TEST_DAM_ID, dao.historyRow!!.damId)
    }

    @Test
    fun fetchAndStore_notFound_returnsNotStoredWithoutSaving() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(realClient(), dao = dao)

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.MANUAL)

        assertTrue(result.isRight())
        assertEquals(SudmonitorHistoryFetchResult.NotStored, result.getOrNull())
        assertEquals(0, dao.upsertCalls)
        assertEquals(0, dao.insertObservationsCalls)
        assertNull(dao.historyRow)
    }

    @Test
    fun fetchAndStore_gateDisabled_returnsNotStoredWithoutNetworkAccess() = runTest {
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(
            realClient(),
            dao = dao,
            settings = AppSettings(historicalDataSource = RealtimeDataSource.MLIT_DIRECT)
        )

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.MANUAL)

        assertTrue(result.isRight())
        assertEquals(SudmonitorHistoryFetchResult.NotStored, result.getOrNull())
        assertEquals(0, server.requestCount)
        assertEquals(0, dao.upsertCalls)
    }

    @Test
    fun fetchAndStore_serverError_returnsLeft() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(realClient(), dao = dao)

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.MANUAL)

        assertTrue(result.isLeft())
        assertTrue(result.swap().getOrNull()?.message?.contains("503") == true)
        assertEquals(0, dao.upsertCalls)
    }

    @Test
    fun fetchAndStore_timeout_returnsLeft() = runTest {
        val dao = FakeSudmonitorHistoryDao()
        val client = mockk<SudmonitorHistoricalClient>()
        coEvery { client.fetchLatest(TEST_DAM_ID) } returns
            Either.Left(SocketTimeoutException("Read timed out"))
        val repository = createRepository(client, dao = dao)

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.MANUAL)

        assertTrue(result.isLeft())
        assertTrue(result.swap().getOrNull() is SocketTimeoutException)
        assertEquals(0, dao.upsertCalls)
        assertNull(dao.historyRow)
    }

    @Test
    fun fetchAndStore_missingNextUpdateAtHeader_fallsBackToNextDay0013Jst() = runTest {
        server.enqueue(
            successResponse(
                rows = listOf(row("2026/8/1", "01:00", "80.0")),
                since = "2026-08-01T00:00:00+09:00",
                until = "2026-08-01T23:00:00+09:00",
                fetchedAt = "2026-08-01T05:00:00Z"
            )
        )
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(realClient(), dao = dao)

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.INITIAL)

        assertTrue(result.isRight())
        val history = (result.getOrNull() as SudmonitorHistoryFetchResult.Success).history
        // X-TCS-Fetched-At(2026-08-01) の翌日 00:13 JST へフォールバックする
        assertEquals(
            LocalDate.of(2026, 8, 2).atTime(0, 13).atZone(TimeUtils.JST_ZONE).toInstant().toEpochMilli(),
            history.nextUpdateAtEpochMs
        )
    }

    @Test
    fun fetchAndStore_invalidNextUpdateAtHeader_fallsBackUsingClock() = runTest {
        server.enqueue(
            successResponse(
                rows = listOf(row("2026/8/1", "01:00", "80.0")),
                since = "2026-08-01T00:00:00+09:00",
                until = "2026-08-01T23:00:00+09:00",
                nextUpdateAt = "not-an-instant"
            )
        )
        val dao = FakeSudmonitorHistoryDao()
        // X-TCS-Fetched-At も欠落しているため、フォールバックの基準日は clock（2026-08-01）になる
        val repository = createRepository(realClient(), dao = dao)

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.INITIAL)

        assertTrue(result.isRight())
        val history = (result.getOrNull() as SudmonitorHistoryFetchResult.Success).history
        assertEquals(
            LocalDate.of(2026, 8, 2).atTime(0, 13).atZone(TimeUtils.JST_ZONE).toInstant().toEpochMilli(),
            history.nextUpdateAtEpochMs
        )
    }

    @Test
    fun fetchAndStore_coverageHeadersMissing_skipsParsingAndStoresEmptyObservations() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", TEST_DAM_ID)
                .setBody(Buffer().write(datBytes(listOf(row("2026/8/1", "01:00", "80.0")))))
        )
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(realClient(), dao = dao)

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.INITIAL)

        assertTrue(result.isRight())
        val history = (result.getOrNull() as SudmonitorHistoryFetchResult.Success).history
        assertNull(history.periodStartEpochMs)
        assertNull(history.periodEndEpochMs)
        assertEquals(0, history.rowCount)
        assertNull(history.firstStorageRatePct)
        assertEquals(1, dao.upsertCalls)
        assertEquals(0, dao.insertObservationsCalls)
    }

    @Test
    fun fetchAndStore_secondFetch_overwritesRowAndObservationsForSameDam() = runTest {
        // 1回目: 2行
        server.enqueue(
            successResponse(
                rows = listOf(row("2026/8/1", "01:00", "80.0"), row("2026/8/1", "02:00", "81.0")),
                since = "2026-08-01T00:00:00+09:00",
                until = "2026-08-01T23:00:00+09:00"
            )
        )
        // 2回目: 1行（日次更新で行数が変わる）
        server.enqueue(
            successResponse(
                rows = listOf(row("2026/8/2", "01:00", "82.0")),
                since = "2026-08-01T00:00:00+09:00",
                until = "2026-08-02T23:00:00+09:00"
            )
        )
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(realClient(), dao = dao)

        val first = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.INITIAL)
        val second = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.MANUAL)

        assertTrue(first.isRight())
        assertTrue(second.isRight())
        // D3: ダムごと1件の上書き（観測明細は毎回 DELETE → INSERT の一括置換）
        assertEquals(2, dao.deleteObservationsCalls)
        assertEquals(2, dao.upsertCalls)
        assertEquals(2, dao.insertObservationsCalls)
        assertEquals(2, dao.insertedObservationLists[0].size)
        assertEquals(listOf("2026/8/2 01:00"), dao.insertedObservationLists[1].map { it.timeText })
        assertEquals(listOf("2026/8/2 01:00"), dao.observations.map { it.timeText })
        assertEquals(82.0f, dao.historyRow!!.firstStorageRatePct ?: -1f, 0.001f)
    }

    @Test
    fun fetchAndStore_debugBundled_storesAllRowsWithoutNetworkAndUsesFullObservedPeriod() = runTest {
        val bytes = completeDailyDatBytes()
        val reader = mockk<DebugDatSourceReader>()
        every { reader.readBundled("historical_daily_sameura.dat") } returns bytes
        val client = mockk<SudmonitorHistoricalClient>()
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(
            client = client,
            dao = dao,
            settings = debugSettings(DebugDatSelectionMode.BUNDLED),
            debugDatSourceReader = reader
        )

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.MANUAL)

        assertTrue(result.isRight())
        val history = (result.getOrNull() as SudmonitorHistoryFetchResult.Success).history
        assertEquals(744, history.rowCount)
        assertEquals(0L, history.nextUpdateAtEpochMs)
        assertEquals(TimeUtils.parseJstMillisAllow24Hour("2026/5/9 01:00", "yyyy/M/d HH:mm"), history.periodStartEpochMs)
        assertEquals(TimeUtils.parseJstMillisAllow24Hour("2026/6/8 24:00", "yyyy/M/d HH:mm"), history.periodEndEpochMs)
        assertEquals(744, dao.observations.size)
        assertEquals("2026/5/9 01:00", dao.observations.first().timeText)
        assertEquals("2026/6/8 24:00", dao.observations.last().timeText)
        assertNotNull(history.rawDatPath)
    }

    @Test
    fun fetchAndStore_debugLatest_reusesCurrentRawAndBypassesCooldown() = runTest {
        val bytes = completeDailyDatBytes()
        val raw = File(cacheDir, "existing.dat").also { it.writeBytes(bytes) }
        val dao = FakeSudmonitorHistoryDao().also {
            it.reset(historyRow = historyEntity(nextUpdateAtEpochMs = currentTimeMillis + 60_000L).copy(rawDatPath = raw.path))
        }
        val repository = createRepository(
            client = mockk(),
            dao = dao,
            settings = debugSettings(DebugDatSelectionMode.LATEST)
        )

        assertNull(repository.manualRefreshAvailableAt(TEST_DAM_ID))
        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.MANUAL)

        assertTrue(result.isRight())
        assertEquals(744, dao.observations.size)
    }

    @Test
    fun fetchAndStore_debugUserSelected_readsSafWithoutNetwork() = runTest {
        val reader = mockk<DebugDatSourceReader>()
        every {
            reader.readSaf("content://debug/daily.dat", any(), any(), any())
        } returns completeDailyDatBytes()
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(
            client = mockk(),
            dao = dao,
            settings = debugSettings(DebugDatSelectionMode.USER_SELECTED).copy(
                debugHistoricalDailyDatFileUri = "content://debug/daily.dat"
            ),
            debugDatSourceReader = reader
        )

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.MANUAL)

        assertTrue(result.isRight())
        assertEquals(744, dao.observations.size)
    }

    @Test
    fun fetchAndStore_debugRejectsStationMismatchWithoutReplacingCurrentData() = runTest {
        val original = historyEntity()
        val dao = FakeSudmonitorHistoryDao().also { it.reset(historyRow = original) }
        val reader = mockk<DebugDatSourceReader>()
        every { reader.readBundled(any()) } returns completeDailyDatBytes(stationId = "9999999999999")
        val repository = createRepository(
            client = mockk(),
            dao = dao,
            settings = debugSettings(DebugDatSelectionMode.BUNDLED),
            debugDatSourceReader = reader
        )

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.MANUAL)

        assertTrue(result.isLeft())
        assertEquals(original, dao.historyRow)
        assertEquals(0, dao.deleteObservationsCalls)
    }

    @Test
    fun fetchAndStore_debugLoadingFailure_doesNotReadSourceOrCallNetwork() = runTest {
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(
            client = mockk(),
            dao = dao,
            settings = debugSettings(DebugDatSelectionMode.BUNDLED).copy(
                debugSimulateMode = DebugSimulateMode.LOADING_FAILURE
            ),
            debugDatSourceReader = mockk()
        )

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.MANUAL)

        assertTrue(result.isLeft())
        assertEquals(0, dao.upsertCalls)
    }

    @Test
    fun fetchAndStore_debugNetworkUnavailable_doesNotReadSourceOrCallNetwork() = runTest {
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(
            client = mockk(),
            dao = dao,
            settings = debugSettings(DebugDatSelectionMode.BUNDLED).copy(
                debugSimulateMode = DebugSimulateMode.NETWORK_UNAVAILABLE
            ),
            debugDatSourceReader = mockk()
        )

        val result = repository.fetchAndStore(TEST_DAM_ID, supportedDam(), SudmonitorHistoryTrigger.AUTO)

        assertTrue(result.isLeft())
        assertTrue(result.swap().getOrNull() is IOException)
        assertEquals(0, dao.upsertCalls)
    }

    // ---- 読出し ----

    @Test
    fun findByDamId_returnsDomainModel() = runTest {
        val entity = historyEntity(nextUpdateAtEpochMs = 123L)
        val dao = FakeSudmonitorHistoryDao().also { it.reset(historyRow = entity) }
        val repository = createRepository(realClient(), dao = dao)

        val history = repository.findByDamId(TEST_DAM_ID)

        assertNotNull(history)
        assertEquals(123L, history!!.nextUpdateAtEpochMs)
        assertNull(repository.findByDamId("other"))
    }

    @Test
    fun manualRefreshAvailableAt_returnsNextUpdateAtFromRow() = runTest {
        val entity = historyEntity(nextUpdateAtEpochMs = 456L)
        val dao = FakeSudmonitorHistoryDao().also { it.reset(historyRow = entity) }
        val repository = createRepository(realClient(), dao = dao)

        assertEquals(456L, repository.manualRefreshAvailableAt(TEST_DAM_ID))
        assertNull(repository.manualRefreshAvailableAt("other"))
    }

    @Test
    fun getAllObservations_mapsToDomainAscending() = runTest {
        val dao = FakeSudmonitorHistoryDao().also {
            it.reset(
                historyRow = historyEntity(),
                observations = listOf(
                    observationEntity(time = "2026/8/1 03:00", storagePercentage = 79.5f, rowNo = 2),
                    observationEntity(time = "2026/8/1 01:00", storagePercentage = 80.0f, rowNo = 0),
                    observationEntity(time = "2026/8/1 02:00", storagePercentage = 81.0f, rowNo = 1)
                )
            )
        }
        val repository = createRepository(realClient(), dao = dao)

        val all = repository.getAllObservations(TEST_DAM_ID)
        // DAO が昇順を保証する契約のため、リポジトリは受け取った順をそのまま返す
        assertEquals(listOf("2026/8/1 03:00", "2026/8/1 01:00", "2026/8/1 02:00"), all.map { it.time })

        val ranged = repository.getObservationsByTimeRange(
            TEST_DAM_ID,
            TimeUtils.parseJstMillis("2026/08/01 01:00", "yyyy/MM/dd HH:mm")!!,
            TimeUtils.parseJstMillis("2026/08/01 02:00", "yyyy/MM/dd HH:mm")!!
        )
        assertEquals(listOf("2026/8/1 01:00", "2026/8/1 02:00"), ranged.map { it.time })
    }

    @Test
    fun currentRawDatApi_readsOnlyTheRawReferencedByCurrentRow() = runTest {
        val raw = File(cacheDir, "held-daily.dat").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val dao = FakeSudmonitorHistoryDao().also {
            it.reset(historyRow = historyEntity().copy(rawDatPath = raw.absolutePath))
        }
        val repository = createRepository(realClient(), dao = dao)

        assertTrue(repository.hasCurrentRawDat(TEST_DAM_ID))
        assertEquals(listOf<Byte>(1, 2, 3), repository.getCurrentRawDatBytes(TEST_DAM_ID)?.toList())
        assertEquals("held-daily.dat", repository.getCurrentRawDatFileName(TEST_DAM_ID))
        assertNull(repository.getCurrentRawDatBytes("other"))
    }

    // ---- historyFlow ----

    @Test
    fun historyFlow_unstored_emitsNull() = runTest {
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(realClient(), dao = dao)

        assertNull(repository.historyFlow(TEST_DAM_ID).first())
    }

    @Test
    fun historyFlow_mapsEntityToDomain() = runTest {
        val dao = FakeSudmonitorHistoryDao().also {
            it.reset(historyRow = historyEntity(nextUpdateAtEpochMs = 123L))
        }
        val repository = createRepository(realClient(), dao = dao)

        val history = repository.historyFlow(TEST_DAM_ID).first()

        assertNotNull(history)
        assertEquals(TEST_DAM_ID, history!!.damId)
        assertEquals(123L, history.nextUpdateAtEpochMs)
        assertEquals(SudmonitorHistory.STATUS_SUCCESS, history.status)
    }

    @Test
    fun historyFlow_otherDam_emitsNull() = runTest {
        val dao = FakeSudmonitorHistoryDao().also { it.reset(historyRow = historyEntity()) }
        val repository = createRepository(realClient(), dao = dao)

        assertNull(repository.historyFlow("other-dam").first())
    }

    @Test
    fun historyFlow_reEmitsMappedRowOnUpdateAndNullOnClear() = runTest {
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(realClient(), dao = dao)
        val emissions = mutableListOf<SudmonitorHistory?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.historyFlow(TEST_DAM_ID).collect { emissions += it }
        }
        advanceUntilIdle()
        assertEquals(listOf<SudmonitorHistory?>(null), emissions)

        dao.historyRow = historyEntity(nextUpdateAtEpochMs = 123L)
        advanceUntilIdle()
        dao.historyRow = null
        advanceUntilIdle()
        job.cancel()

        assertEquals(3, emissions.size)
        assertNull(emissions[0])
        assertEquals(123L, emissions[1]?.nextUpdateAtEpochMs)
        assertNull(emissions[2])
    }

    // ---- autoFetch（D7 のクールダウン判定） ----

    @Test
    fun autoFetch_oneHour_skipsWhileCooldownActive() = runTest {
        mockAndroidLog()
        server.enqueue(successResponse(rows = listOf(row("2026/8/1", "01:00", "80.0"))))
        val dao = FakeSudmonitorHistoryDao().also {
            it.reset(historyRow = historyEntity(nextUpdateAtEpochMs = currentTimeMillis + 60_000L))
        }
        val repository = createRepository(realClient(), dao = dao)

        repository.autoFetch(AutoUpdateInterval.ONE_HOUR, currentTimeMillis, TEST_DAM_ID)

        assertEquals(0, server.requestCount)
        assertEquals(0, dao.upsertCalls)
    }

    @Test
    fun autoFetch_twelveHours_skipsWhileCooldownActive() = runTest {
        mockAndroidLog()
        val dao = FakeSudmonitorHistoryDao().also {
            it.reset(historyRow = historyEntity(nextUpdateAtEpochMs = currentTimeMillis + 60_000L))
        }
        val repository = createRepository(realClient(), dao = dao)

        repository.autoFetch(AutoUpdateInterval.TWELVE_HOURS, currentTimeMillis, TEST_DAM_ID)

        assertEquals(0, server.requestCount)
    }

    @Test
    fun autoFetch_oneHour_expiredCooldown_runs() = runTest {
        server.enqueue(successResponse(rows = listOf(row("2026/8/1", "01:00", "80.0"))))
        val dao = FakeSudmonitorHistoryDao().also {
            it.reset(historyRow = historyEntity(nextUpdateAtEpochMs = currentTimeMillis - 1L))
        }
        val repository = createRepository(realClient(), dao = dao)

        repository.autoFetch(AutoUpdateInterval.ONE_HOUR, currentTimeMillis, TEST_DAM_ID)

        assertEquals(1, server.requestCount)
        assertEquals(1, dao.upsertCalls)
    }

    @Test
    fun autoFetch_oneDay_runsEveryTime() = runTest {
        server.enqueue(successResponse(rows = listOf(row("2026/8/1", "01:00", "80.0"))))
        val dao = FakeSudmonitorHistoryDao().also {
            it.reset(historyRow = historyEntity(nextUpdateAtEpochMs = currentTimeMillis + 60_000L))
        }
        val repository = createRepository(realClient(), dao = dao)

        repository.autoFetch(AutoUpdateInterval.ONE_DAY, currentTimeMillis, TEST_DAM_ID)

        assertEquals(1, server.requestCount)
        assertEquals(1, dao.upsertCalls)
    }

    @Test
    fun autoFetch_oneWeek_runsEveryTime() = runTest {
        server.enqueue(successResponse(rows = listOf(row("2026/8/1", "01:00", "80.0"))))
        val dao = FakeSudmonitorHistoryDao().also {
            it.reset(historyRow = historyEntity(nextUpdateAtEpochMs = currentTimeMillis + 60_000L))
        }
        val repository = createRepository(realClient(), dao = dao)

        repository.autoFetch(AutoUpdateInterval.ONE_WEEK, currentTimeMillis, TEST_DAM_ID)

        assertEquals(1, server.requestCount)
        assertEquals(1, dao.upsertCalls)
    }

    @Test
    fun autoFetch_rowNotStored_runsWithoutCooldownCheck() = runTest {
        server.enqueue(successResponse(rows = listOf(row("2026/8/1", "01:00", "80.0"))))
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(realClient(), dao = dao)

        repository.autoFetch(AutoUpdateInterval.ONE_HOUR, currentTimeMillis, TEST_DAM_ID)

        assertEquals(1, server.requestCount)
        assertEquals(1, dao.upsertCalls)
    }

    @Test
    fun autoFetch_gateDisabled_doesNothing() = runTest {
        val dao = FakeSudmonitorHistoryDao()
        val repository = createRepository(
            realClient(),
            dao = dao,
            settings = AppSettings(historicalDataSource = RealtimeDataSource.MLIT_DIRECT)
        )

        repository.autoFetch(AutoUpdateInterval.ONE_DAY, currentTimeMillis, TEST_DAM_ID)

        assertEquals(0, server.requestCount)
        assertEquals(0, dao.upsertCalls)
    }

    @Test
    fun autoFetch_failure_isSwallowed() = runTest {
        mockAndroidLog()
        val dao = FakeSudmonitorHistoryDao()
        val client = mockk<SudmonitorHistoricalClient>()
        coEvery { client.fetchLatest(TEST_DAM_ID) } returns
            Either.Left(SocketTimeoutException("Read timed out"))
        val repository = createRepository(client, dao = dao)

        // autoFetch は失敗を握りつぶし（ログのみ）、例外を投げない
        repository.autoFetch(AutoUpdateInterval.ONE_DAY, currentTimeMillis, TEST_DAM_ID)

        assertEquals(0, dao.upsertCalls)
        assertNull(dao.historyRow)
    }

    // ---- ヘルパー ----

    private fun realClient(): SudmonitorHistoricalClient =
        SudmonitorHistoricalClient(
            endpointConfig = SudmonitorEndpointConfig.localhostHttp(server.url("/").toString()),
            httpClient = createMlitHttpClient(),
            clock = { currentTimeMillis }
        )

    private fun createRepository(
        client: SudmonitorHistoricalClient,
        dao: SudmonitorHistoryDao,
        settings: AppSettings = AppSettings(historicalDataSource = RealtimeDataSource.SUDMONITOR),
        clock: () -> Long = { currentTimeMillis },
        debugDatSourceReader: DebugDatSourceReader = mockk()
    ): SudmonitorHistoryRepositoryImpl {
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        return SudmonitorHistoryRepositoryImpl(
            sudmonitorHistoricalClient = client,
            parser = DamFileParser(datEndpointConfig = MlitEndpointConfig.production()),
            transactionRunner = ImmediateTransactionRunner(),
            sudmonitorHistoryDao = dao,
            settingsRepository = FakeSettingsRepository(settings),
            debugDatSourceReader = debugDatSourceReader,
            context = context,
            clock = clock
        )
    }

    private fun successResponse(
        rows: List<String>,
        since: String = "2026-08-01T00:00:00+09:00",
        until: String = "2026-08-01T23:00:00+09:00",
        nextUpdateAt: String? = null,
        fetchedAt: String? = null
    ): MockResponse {
        val response = MockResponse()
            .setResponseCode(200)
            .setHeader("X-TCS-Dam-Id", TEST_DAM_ID)
            .setHeader("X-TCS-History-Since", since)
            .setHeader("X-TCS-History-Until", until)
            .setBody(Buffer().write(datBytes(rows)))
        nextUpdateAt?.let { response.setHeader("X-TCS-Next-Update-At", it) }
        fetchedAt?.let { response.setHeader("X-TCS-Fetched-At", it) }
        return response
    }

    private fun datBytes(dataRows: List<String>): ByteArray =
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

    private fun completeDailyDatBytes(stationId: String = TEST_DAM_ID): ByteArray {
        val rows = buildList {
            var day = LocalDate.of(2026, 5, 9)
            repeat(31) {
                for (hour in 1..24) {
                    add(row("${day.year}/${day.monthValue}/${day.dayOfMonth}", "%02d:00".format(hour), "80.0"))
                }
                day = day.plusDays(1)
            }
        }
        return (
            listOf(
                "任意期間ダム諸量検索結果",
                "水系名,吉野川",
                "河川名,吉野川",
                "観測所名,早明浦ダム",
                "観測所記号,$stationId",
                "#年月日,時刻,流域平均雨量,雨量属性,貯水量,貯水量属性,流入量,流入量属性,放流量,放流量属性,貯水率,貯水率属性"
            ) + rows
        ).joinToString("\n").toByteArray(Charset.forName("Shift_JIS"))
    }

    private fun debugSettings(mode: DebugDatSelectionMode): AppSettings = AppSettings(
        historicalDataSource = RealtimeDataSource.SUDMONITOR,
        debugModeEnabled = true,
        debugHistoricalDailyDatFileMode = mode
    )

    private fun row(date: String, time: String, storagePercentage: String): String =
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

    private fun historyEntity(nextUpdateAtEpochMs: Long = 1L): SudmonitorHistoryEntity =
        SudmonitorHistoryEntity(
            damId = TEST_DAM_ID,
            periodStartEpochMs = jstDayStartMillis(2026, 8, 1),
            periodEndEpochMs = jstDayStartMillis(2026, 8, 1),
            status = SudmonitorHistory.STATUS_SUCCESS,
            rowCount = 0,
            firstStorageRatePct = null,
            lastStorageRatePct = null,
            minStorageRatePct = null,
            maxStorageRatePct = null,
            fetchedAtEpochMs = 1L,
            nextUpdateAtEpochMs = nextUpdateAtEpochMs,
            rawDatPath = null,
            updatedAtEpochMs = 1L
        )

    private fun observationEntity(
        time: String,
        storagePercentage: Float,
        rowNo: Int
    ): SudmonitorHistoryObservationEntity =
        SudmonitorHistoryObservationEntity(
            damId = TEST_DAM_ID,
            rowNo = rowNo,
            timeText = time,
            timeEpochMs = TimeUtils.parseJstMillisAllow24Hour(time, "yyyy/MM/dd HH:mm") ?: 0L,
            rainfallHourlyMm = 0f,
            storageVolume1000m3 = 72000f,
            inflowM3s = 10f,
            outflowM3s = 9f,
            storageRatePct = storagePercentage
        )

    private fun supportedDam(): DamConfig =
        DamConfig(
            id = TEST_DAM_ID,
            dataUrl = "https://www1.river.go.jp/cgi-bin/DspDamData.exe?KIND=1&ID=1368080700010"
        )

    private fun jstDayStartMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(TimeUtils.JST_ZONE).toInstant().toEpochMilli()

    private class ImmediateTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
    }

    private companion object {
        private const val TEST_DAM_ID = "1368080700010"
    }
}
