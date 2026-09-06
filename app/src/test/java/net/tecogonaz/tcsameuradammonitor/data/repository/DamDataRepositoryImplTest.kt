// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import android.content.Context
import arrow.core.Either
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.data.source.local.DebugDatSourceReader
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.toDomain
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.toEntity
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamNetworkDataSource
import net.tecogonaz.tcsameuradammonitor.data.source.remote.MlitEndpointConfig
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorEndpointConfig
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorNetworkDataSource
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfigProvider
import net.tecogonaz.tcsameuradammonitor.domain.model.DamLoadStatus
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugDatSelectionMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.testutil.FakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.shouldBeLeft
import okio.Buffer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * ダムデータ取得リポジトリ実装 [DamDataRepositoryImpl] のユニットテストクラス。
 * ネットワーク（MockWebServer）経由での HTML/DAT ファイル取得、パース、Room データベース（[DamDao]）への保存、
 * 擬似通信エラー状態のシミュレーション、デバッグモード（アセットバンドルファイルや SAF 経由で取得したファイル）での動作、
 * 複数コルーチンから並行して更新をかけた際の多重リクエスト防止（Mutex 制御やキャッシュ利用）などが
 * 正しく機能することを検証します。
 */
class DamDataRepositoryImplTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var sudmonitorServer: MockWebServer
    private lateinit var dao: InMemoryDamDao
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var context: Context
    private lateinit var debugDatSourceReader: FakeDebugDatSourceReader
    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sudmonitorServer = MockWebServer()
        sudmonitorServer.start()
        dao = InMemoryDamDao()
        settingsRepository = FakeSettingsRepository(
            AppSettings(targetDamId = TEST_DAM_ID, realtimeDataSource = RealtimeDataSource.MLIT_DIRECT)
        )
        context = mockk()
        every { context.getString(any()) } answers { "string-${firstArg<Int>()}" }
        every { context.cacheDir } answers { tempFolder.root }
        debugDatSourceReader = FakeDebugDatSourceReader(context)
    }

    @After
    fun tearDown() {
        server.shutdown()
        sudmonitorServer.shutdown()
    }

    @Test
    fun fetchLatestData_success_fetchesHtmlDatParsesAndStoresData() = runBlocking {
        val datBytes = realtimeDatBytes()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""<html><a href="/current.dat">dat</a></html>""")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(datBytes)))
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isRight())
        val storedEntity = dao.current
        assertNotNull(storedEntity)
        val stored = storedEntity?.toDomain()
        assertEquals("1368080700010", stored?.observationStationId)
        assertEquals("2026/05/15 23:40", stored?.updatedAt)
        assertEquals(DamLoadStatus.SUCCESS, repository.loadStatus.value)
        assertEquals(false, repository.isLastLoadNetworkError.value)
        assertTrue(repository.getLastFetchTimeMillis() > 0L)
        assertArrayEquals(datBytes, repository.getLastRawDatBytes())
        assertArrayEquals(datBytes, repository.getLastRealtimeRawDatBytes())
        assertEquals("/index.html", server.takeRequest().path)
        assertEquals("/current.dat", server.takeRequest().path)
    }

    @Test
    fun cachedDataForDifferentTargetDam_isNotPublishedOrReportedAsCached() = runTest {
        dao.insert(realtimeDatEntity())
        val repository = createRepository(this)

        assertNotNull(repository.damDataFlow.first())
        assertTrue(repository.hasCachedData())

        settingsRepository.updateSettings {
            it.copy(targetDamId = "1368010125140")
        }

        assertNull(repository.damDataFlow.first())
        assertEquals(false, repository.hasCachedData())
    }

    @Test
    fun fetchLatestData_htmlWithOnlyForeignDatLink_returnsDatUrlErrorWithoutDatFetch() = runBlocking {
        every { context.getString(R.string.error_dat_url_not_found) } returns "dat url not found"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""<html><a href="https://example.com/current.dat">dat</a></html>""")
        )
        val repository = createRepository(this)

        val error = repository.fetchLatestData().shouldBeLeft()

        assertEquals("dat url not found", error.message)
        assertEquals(0, dao.insertCount)
        assertEquals(1, server.requestCount)
        assertEquals("/index.html", server.takeRequest().path)
    }

    @Test
    fun fetchLatestData_loadingFailureSimulation_returnsLeftWithoutInserting() = runTest {
        every { context.getString(R.string.settings_debug_simulate_error_msg) } returns "simulated failure"
        settingsRepository = FakeSettingsRepository(
            AppSettings(
                targetDamId = TEST_DAM_ID,
                debugModeEnabled = true,
                debugSimulateMode = DebugSimulateMode.LOADING_FAILURE
            )
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { assertEquals("simulated failure", it.message) },
            ifRight = { throw AssertionError("Expected failure but got $it") }
        )
        assertEquals(0, dao.insertCount)
    }

    @Test
    fun fetchLatestData_debugModeWithBundledAsset_readsAssetStoresRawBytesAndSkipsNetwork() = runBlocking {
        val datBytes = realtimeDatBytes()
        debugDatSourceReader.bundledBytes[DebugDatSelectionMode.BUNDLED_FILE_NAME] = datBytes
        settingsRepository = FakeSettingsRepository(
            AppSettings(
                targetDamId = TEST_DAM_ID,
                debugModeEnabled = true
            )
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isRight())
        assertEquals(0, server.requestCount)
        assertArrayEquals(datBytes, repository.getLastRawDatBytes())
        assertEquals(1, dao.insertCount)
        assertEquals("2026/05/15 23:40", dao.current?.toDomain()?.updatedAt)
        assertEquals(DamLoadStatus.SUCCESS, repository.loadStatus.value)
    }

    @Test
    fun fetchLatestData_debugModeWithSafUri_readsDebugSourceStoresRawBytesAndSkipsNetwork() = runBlocking {
        val datBytes = realtimeDatBytes()
        val uriString = "content://test/realtime.dat"
        debugDatSourceReader.safBytes[uriString] = datBytes
        settingsRepository = FakeSettingsRepository(
            AppSettings(
                targetDamId = TEST_DAM_ID,
                debugModeEnabled = true,
                debugRealtimeDatFileMode = DebugDatSelectionMode.USER_SELECTED,
                debugRealtimeDatFileUri = uriString
            )
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isRight())
        assertEquals(0, server.requestCount)
        assertArrayEquals(datBytes, repository.getLastRawDatBytes())
        assertEquals(1, dao.insertCount)
        assertEquals("1368080700010", dao.current?.observationStationId)
        assertEquals(DamLoadStatus.SUCCESS, repository.loadStatus.value)
    }

    @Test
    fun fetchLatestData_debugModeWithSafUriReadFailure_returnsLeftWithoutInserting() = runBlocking {
        val uriString = "content://test/missing.dat"
        every { context.getString(R.string.error_debug_file_read) } returns "debug read failed"
        settingsRepository = FakeSettingsRepository(
            AppSettings(
                targetDamId = TEST_DAM_ID,
                debugModeEnabled = true,
                debugRealtimeDatFileMode = DebugDatSelectionMode.USER_SELECTED,
                debugRealtimeDatFileUri = uriString
            )
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { assertEquals("debug read failed", it.message) },
            ifRight = { throw AssertionError("Expected failure but got $it") }
        )
        assertEquals(0, dao.insertCount)
        assertEquals(null, repository.getLastRawDatBytes())
    }

    @Test
    fun fetchLatestData_debugModeWithSafUriCancellation_rethrowsCancellation() = runBlocking {
        val uriString = "content://test/cancel.dat"
        val cancellation = CancellationException("cancelled")
        debugDatSourceReader.safFailures[uriString] = cancellation
        settingsRepository = FakeSettingsRepository(
            AppSettings(
                targetDamId = TEST_DAM_ID,
                debugModeEnabled = true,
                debugRealtimeDatFileMode = DebugDatSelectionMode.USER_SELECTED,
                debugRealtimeDatFileUri = uriString
            )
        )
        val repository = createRepository(this)

        try {
            repository.fetchLatestData()
            throw AssertionError("Expected CancellationException.")
        } catch (error: CancellationException) {
            assertEquals(cancellation, error)
        }

        assertEquals(0, dao.insertCount)
    }

    @Test
    fun fetchLatestData_daoInsertFailure_returnsLeftWithoutSuccessStatus() = runBlocking {
        val datBytes = realtimeDatBytes()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""<html><a href="/current.dat">dat</a></html>""")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(datBytes)))
        dao.insertFailure = IllegalStateException("insert failed")
        val repository = createRepository(this)

        val error = repository.fetchLatestData().shouldBeLeft()

        assertEquals("insert failed", error.message)
        assertEquals(DamLoadStatus.INITIAL, repository.loadStatus.value)
        assertEquals(1, dao.insertCount)
    }

    @Test
    fun fetchLatestData_withExistingDataReplacesOldData() = runBlocking {
        
        dao.insert(realtimeDatEntity())
        assertEquals(1, dao.insertCount)

        val datBytes = realtimeDatBytes()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""<html><a href="/current.dat">dat</a></html>""")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(datBytes)))
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isRight())
        
        assertEquals(2, dao.insertCount)
        assertEquals("2026/05/15 23:40", dao.current?.toDomain()?.updatedAt)
        assertEquals(DamLoadStatus.SUCCESS, repository.loadStatus.value)
    }

    @Test
    fun fetchLatestData_debugModeWithEndMillis_ignoresRowsAfterEndMillis() = runBlocking {
        val datBytes = realtimeDatBytes()
        debugDatSourceReader.bundledBytes[DebugDatSelectionMode.BUNDLED_FILE_NAME] = datBytes
        settingsRepository = FakeSettingsRepository(
            AppSettings(
                targetDamId = TEST_DAM_ID,
                debugModeEnabled = true,
                debugRealtimeDataPeriodAutoAdvanceEnabled = false,
                debugRealtimeDataEndMillis = net.tecogonaz.tcsameuradammonitor.util.TimeUtils.parseJstMillis(
                    "2026/05/15 23:10",
                    "yyyy/MM/dd HH:mm"
                )
            )
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isRight())
        assertEquals("2026/05/15 23:10", dao.current?.toDomain()?.updatedAt)
        assertEquals(4, dao.current?.toDomain()?.historicalData?.size)
    }

    @Test
    fun fetchLatestData_debugModeWithAutoAdvance_advancesEndMillisToNextRowAfterCurrentFetch() = runBlocking {
        val datBytes = realtimeDatBytes()
        debugDatSourceReader.bundledBytes[DebugDatSelectionMode.BUNDLED_FILE_NAME] = datBytes
        settingsRepository = FakeSettingsRepository(
            AppSettings(
                targetDamId = TEST_DAM_ID,
                debugModeEnabled = true,
                debugRealtimeDataEndMillis = net.tecogonaz.tcsameuradammonitor.util.TimeUtils.parseJstMillis(
                    "2026/05/15 23:10",
                    "yyyy/MM/dd HH:mm"
                )
            )
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isRight())
        assertEquals("2026/05/15 23:10", dao.current?.toDomain()?.updatedAt)
        assertEquals(4, dao.current?.toDomain()?.historicalData?.size)
        assertEquals(
            net.tecogonaz.tcsameuradammonitor.util.TimeUtils.parseJstMillis(
                "2026/05/15 23:20",
                "yyyy/MM/dd HH:mm"
            ),
            settingsRepository.current.debugRealtimeDataEndMillis
        )
    }

    @Test
    fun fetchLatestData_debugModeWithAutoAdvanceAtFullRange_doesNotChangeEndMillis() = runBlocking {
        val datBytes = realtimeDatBytes()
        debugDatSourceReader.bundledBytes[DebugDatSelectionMode.BUNDLED_FILE_NAME] = datBytes
        val fullRangeEnd = net.tecogonaz.tcsameuradammonitor.util.TimeUtils.parseJstMillis(
            "2026/05/15 23:40",
            "yyyy/MM/dd HH:mm"
        )
        settingsRepository = FakeSettingsRepository(
            AppSettings(
                targetDamId = TEST_DAM_ID,
                debugModeEnabled = true,
                debugRealtimeDataEndMillis = fullRangeEnd
            )
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isRight())
        assertEquals("2026/05/15 23:40", dao.current?.toDomain()?.updatedAt)
        assertEquals(fullRangeEnd, settingsRepository.current.debugRealtimeDataEndMillis)
    }

    @Test
    fun fetchLatestData_concurrentCalls_runNetworkFetchesSerially() = runTest {
        val activeFetches = AtomicInteger(0)
        val maxActiveFetches = AtomicInteger(0)
        val datBytes = realtimeDatBytes()
        val networkDataSource = mockk<DamNetworkDataSource>()
        coEvery { networkDataSource.fetchBytes(any()) } coAnswers {
            val active = activeFetches.incrementAndGet()
            maxActiveFetches.updateAndGet { current -> maxOf(current, active) }
            delay(100L)
            activeFetches.decrementAndGet()

            val url = invocation.args[0] as String
            if (url.endsWith("/index.html")) {
                Either.Right("""<html><a href="/current.dat">dat</a></html>""".toByteArray())
            } else {
                Either.Right(datBytes)
            }
        }
        val repository = createRepository(this, networkDataSource)

        val results = awaitAll(
            async { repository.fetchLatestData() },
            async { repository.fetchLatestData() }
        )

        assertTrue(results.all { it.isRight() })
        assertEquals(1, maxActiveFetches.get())
        assertEquals(2, dao.insertCount)
        assertEquals(DamLoadStatus.SUCCESS, repository.loadStatus.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun clearData_deletesCacheAndResetsStatus() = runTest {
        val repository = createRepository(this)
        dao.insert(realtimeDatEntity())
        repository.setLoadStatus(DamLoadStatus.NETWORK_UNAVAILABLE)

        repository.clearData()
        advanceUntilIdle()

        assertEquals(null, dao.current)
        assertEquals(0L, repository.getLastFetchTimeMillis())
        assertEquals(DamLoadStatus.INITIAL, repository.loadStatus.value)
        assertEquals(false, repository.isLastLoadNetworkError.value)
    }

    @Test
    fun fetchLatestData_savesDatFileToCache() = runBlocking {
        val datBytes = realtimeDatBytes()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""<html><a href="/current.dat">dat</a></html>""")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(datBytes)))
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isRight())
        
        // キャッシュファイルが存在し、内容が正しいことを確認
        val cacheDatFile = File(tempFolder.root, "last_realtime_raw.dat")
        val cacheUrlFile = File(tempFolder.root, "last_realtime_url.txt")
        assertTrue(cacheDatFile.exists())
        assertTrue(cacheUrlFile.exists())
        assertArrayEquals(datBytes, cacheDatFile.readBytes())
        assertEquals(server.url("/current.dat").toString(), cacheUrlFile.readText())
    }

    @Test
    fun init_restoresDatFileFromCache() = runBlocking {
        val datBytes = realtimeDatBytes()
        val datUrl = "http://localhost/current.dat"
        
        // キャッシュファイルを事前に作成
        val cacheDatFile = File(tempFolder.root, "last_realtime_raw.dat")
        val cacheUrlFile = File(tempFolder.root, "last_realtime_url.txt")
        cacheDatFile.writeBytes(datBytes)
        cacheUrlFile.writeText(datUrl)

        val repository = createRepository(this)
        delay(200) // init内の非同期な復元処理を待つ

        assertArrayEquals(datBytes, repository.getLastRealtimeRawDatBytes())
        assertEquals("current.dat", repository.getLastDatFileName())
    }

    @Test
    fun clearData_deletesCacheFiles() = runBlocking {
        val repository = createRepository(this)
        
        // キャッシュファイルを事前に作成
        val cacheDatFile = File(tempFolder.root, "last_realtime_raw.dat")
        val cacheUrlFile = File(tempFolder.root, "last_realtime_url.txt")
        cacheDatFile.writeBytes(realtimeDatBytes())
        cacheUrlFile.writeText("http://localhost/current.dat")

        repository.clearData()

        assertEquals(false, cacheDatFile.exists())
        assertEquals(false, cacheUrlFile.exists())
    }

    @Test
    fun fetchLatestData_sudmonitorSuccess_singleGetParsesStoresAndPersistsOriginFetchedAt() = runBlocking {
        val datBytes = realtimeDatBytes()
        sudmonitorServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", TEST_DAM_ID)
                .setHeader("X-TCS-Fetched-At", "2026-07-31T00:00:00Z")
                .setBody(Buffer().write(datBytes))
        )
        settingsRepository = FakeSettingsRepository(
            AppSettings(targetDamId = TEST_DAM_ID, realtimeDataSource = RealtimeDataSource.SUDMONITOR)
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isRight())
        assertEquals(0, server.requestCount)
        assertEquals(1, sudmonitorServer.requestCount)
        assertEquals("/v1/realtime/$TEST_DAM_ID/latest.dat", sudmonitorServer.takeRequest().path)
        assertEquals("2026/05/15 23:40", dao.current?.toDomain()?.updatedAt)
        assertEquals(DamLoadStatus.SUCCESS, repository.loadStatus.value)
        assertEquals(false, repository.isLastLoadNetworkError.value)
        assertArrayEquals(datBytes, repository.getLastRawDatBytes())
        assertArrayEquals(datBytes, repository.getLastRealtimeRawDatBytes())
        assertEquals(
            Instant.parse("2026-07-31T00:00:00Z").toEpochMilli(),
            settingsRepository.current.originFetchedAtMillis
        )
    }

    @Test
    fun fetchLatestData_sudmonitorWithoutHeaders_successToleratedAndOriginNotPersisted() = runBlocking {
        val datBytes = realtimeDatBytes()
        sudmonitorServer.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(datBytes)))
        settingsRepository = FakeSettingsRepository(
            AppSettings(targetDamId = TEST_DAM_ID, realtimeDataSource = RealtimeDataSource.SUDMONITOR)
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isRight())
        assertEquals(0, server.requestCount)
        assertEquals(DamLoadStatus.SUCCESS, repository.loadStatus.value)
        assertEquals(null, settingsRepository.current.originFetchedAtMillis)
    }

    @Test
    fun fetchLatestData_sudmonitorDamIdMismatch_rejectedKeepsDataWithoutMlit() = runBlocking {
        dao.insert(realtimeDatEntity())
        sudmonitorServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", "9999999999999")
                .setBody(Buffer().write(realtimeDatBytes()))
        )
        settingsRepository = FakeSettingsRepository(
            AppSettings(targetDamId = TEST_DAM_ID, realtimeDataSource = RealtimeDataSource.SUDMONITOR)
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isLeft())
        assertEquals(0, server.requestCount)
        assertEquals(1, dao.insertCount)
        assertNotNull(dao.current)
    }

    @Test
    fun fetchLatestData_sudmonitorNon200_failureWithoutMlitFallback() = runBlocking {
        sudmonitorServer.enqueue(
            MockResponse().setResponseCode(503).setBody("<html>Error 1015 Rate limited</html>")
        )
        settingsRepository = FakeSettingsRepository(
            AppSettings(targetDamId = TEST_DAM_ID, realtimeDataSource = RealtimeDataSource.SUDMONITOR)
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isLeft())
        assertEquals(0, server.requestCount)
        assertEquals(1, sudmonitorServer.requestCount)
        assertEquals(0, dao.insertCount)
        assertEquals(DamLoadStatus.INITIAL, repository.loadStatus.value)
    }

    @Test
    fun fetchLatestData_sudmonitorTransportError_failureWithoutMlitFallback() = runBlocking {
        sudmonitorServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        settingsRepository = FakeSettingsRepository(
            AppSettings(targetDamId = TEST_DAM_ID, realtimeDataSource = RealtimeDataSource.SUDMONITOR)
        )
        val repository = createRepository(this)

        val result = repository.fetchLatestData()

        assertTrue(result.isLeft())
        assertEquals(0, server.requestCount)
        assertEquals(0, dao.insertCount)
    }

    private fun createRepository(
        scope: CoroutineScope,
        networkDataSource: DamNetworkDataSource = DamNetworkDataSource(MlitEndpointConfig.localhostHttp()),
        sudmonitorNetworkDataSource: SudmonitorNetworkDataSource = SudmonitorNetworkDataSource(
            SudmonitorEndpointConfig.localhostHttp(sudmonitorServer.url("/").toString())
        )
    ): DamDataRepositoryImpl =
        DamDataRepositoryImpl(
            networkDataSource = networkDataSource,
            sudmonitorNetworkDataSource = sudmonitorNetworkDataSource,
            parser = DamFileParser(
                datBaseUrl = server.url("/").toString(),
                datEndpointConfig = MlitEndpointConfig.localhostHttp()
            ),
            settingsRepository = settingsRepository,
            damDao = dao,
            applicationScope = scope,
            context = context,
            damConfigProvider = object : DamConfigProvider {
                override fun get(targetDamId: String): DamConfig = testDamConfig()
            },
            debugDatSourceReader = debugDatSourceReader
        )

    private fun testDamConfig(): DamConfig =
        DamConfig(
            id = TEST_DAM_ID,
            nameJa = "早明浦ダム",
            nameEn = "Sameura Dam",
            dataUrl = server.url("/index.html").toString()
        )

    private fun realtimeDatEntity(): DamEntity =
        DamFileParser(datEndpointConfig = MlitEndpointConfig.production()).parseDatCsv(realtimeDatBytes(), TEST_DAM_ID, "早明浦ダム").fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { data ->
                data.toEntity(123L)
            }
        )

    private class InMemoryDamDao : DamDao {
        private val state = MutableStateFlow<DamEntity?>(null)
        var insertCount = 0
            private set
        var insertFailure: Throwable? = null

        val current: DamEntity?
            get() = state.value

        override fun getDamDataFlow(): Flow<DamEntity?> = state

        override suspend fun getDamData(): DamEntity? = state.value

        override suspend fun getAllDamData(): List<DamEntity> = listOfNotNull(state.value)

        override suspend fun insert(damData: DamEntity) {
            insertCount += 1
            insertFailure?.let { throw it }
            state.value = damData
        }

        override suspend fun insertAll(damData: List<DamEntity>) {
            damData.forEach { insert(it) }
        }

        override suspend fun deleteAll() {
            state.value = null
        }
    }

    private class FakeDebugDatSourceReader(context: Context) : DebugDatSourceReader(context) {
        val bundledBytes = mutableMapOf<String, ByteArray>()
        val safBytes = mutableMapOf<String, ByteArray>()
        val safFailures = mutableMapOf<String, Throwable>()

        override fun readBundled(fileName: String): ByteArray =
            bundledBytes[fileName] ?: error("Unexpected bundled debug dat read: $fileName")

        override fun readSaf(uriString: String, maxSizeBytes: Int, tooLargeMessage: String, readErrorMessage: String): ByteArray =
            safFailures[uriString]?.let { throw it } ?:
            safBytes[uriString] ?: throw Exception(readErrorMessage)
    }

    private companion object {
        private const val TEST_DAM_ID = "1368080700010"

        private fun realtimeDatBytes(): ByteArray =
            (
                listOf(
                    "水系名,吉野川",
                    "河川名,吉野川",
                    "観測所名,早明浦ダム",
                    "観測所記号,1368080700010",
                    "# 日付,時刻,流域平均雨量,属性,貯水量,属性,流入量,属性,放流量,属性,貯水率,属性"
                ) + (0..6).map { index ->
                    val minute = 40 + index * 10
                    val hour = 22 + minute / 60
                    val mm = minute % 60
                    "2026/05/15,%02d:%02d,0,,100000,,10.0,,9.0,,%.2f,"
                        .format(hour, mm, 79.0f + index * 0.1f)
                }
            ).joinToString("\n").toByteArray(Charset.forName("Shift_JIS"))
    }
}
