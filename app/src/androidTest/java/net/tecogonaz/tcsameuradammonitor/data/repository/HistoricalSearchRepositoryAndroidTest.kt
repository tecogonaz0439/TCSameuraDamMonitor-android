// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamDatabase
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.RoomDatabaseTransactionRunner
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamFileParser
import net.tecogonaz.tcsameuradammonitor.data.source.remote.DamNetworkDataSource
import net.tecogonaz.tcsameuradammonitor.data.source.remote.MlitEndpointConfig
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorEndpointConfig
import net.tecogonaz.tcsameuradammonitor.data.source.remote.SudmonitorHistoricalClient
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.model.RealtimeDataSource
import net.tecogonaz.tcsameuradammonitor.testutil.FakeNetworkAvailability
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.LocalHttpServer
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.Charset

@RunWith(AndroidJUnit4::class)
@MediumTest
/**
 * インメモリ Room データベースを用いた過去データ検索リポジトリ [HistoricalSearchRepositoryImpl] の Instrumentation テストクラス。
 * アセット内の過去データ（3月末から4月頭の月またぎ期間）のパース、並び替え、重複除去、 Room データベースへの永続化、
 * およびローカルに起動したモックTCPサーバー（LocalhostProxy）を介した MLIT 過去検索 Web API（`DspDamData.exe`）の
 * HTTP 応答シミュレーションを行い、返却された CSV バイナリが正確にパース・保存・カウント上限制限されることを検証します。
 */
class HistoricalSearchRepositoryAndroidTest {
    private lateinit var context: Context
    private lateinit var database: DamDatabase
    private lateinit var repository: HistoricalSearchRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, DamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val endpointConfig = MlitEndpointConfig.localhostHttp()
        repository = HistoricalSearchRepositoryImpl(
            networkDataSource = DamNetworkDataSource(endpointConfig),
            fileParser = DamFileParser(endpointConfig),
            transactionRunner = RoomDatabaseTransactionRunner(database),
            historicalSearchDao = database.historicalSearchDao(),
            sudmonitorHistoryDao = database.sudmonitorHistoryDao(),
            networkAvailability = FakeNetworkAvailability(),
            sudmonitorHistoricalClient = SudmonitorHistoricalClient(SudmonitorEndpointConfig.production()),
            settingsRepository = HiltFakeSettingsRepository(
                AppSettings(historicalDataSource = RealtimeDataSource.SUDMONITOR)
            ),
            debugLogRepository = HiltFakeDebugLogRepository(),
            context = context
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fetchAndStore_localAssetsCoverCrossMonth_mergesDedupesSortsAndStores() = runTest {
        val result = repository.fetchAndStore(DamConfig.DEFAULT, "20260331", "20260401")

        val meta = result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { it }
        )
        val storedMeta = repository.getMetaById(meta.id)
        val data = repository.getAllDataByMetaId(meta.id)
        val millisList = data.map { jstMillisLenient(it.time) }

        assertTrue(meta.id > 0L)
        assertNotNull(storedMeta)
        assertEquals("20260331", meta.searchBgnDate)
        assertEquals("20260401", meta.searchEndDate)
        assertTrue(data.isNotEmpty())
        assertEquals(data.map { it.time }.distinct().size, data.size)
        assertEquals(millisList.sorted(), millisList)
        assertTrue(data.any { it.time.startsWith("2026/3/31") })
        assertTrue(data.any { it.time.startsWith("2026/4/1") })
        assertEquals(data.first().time, meta.dataStartTimeStr)
        assertEquals(data.last().time, meta.dataEndTimeStr)
        assertEquals(data.mapNotNull { it.storagePercentage }.minOrNull(), meta.dataMinStoragePct)
        assertEquals(data.mapNotNull { it.storagePercentage }.maxOrNull(), meta.dataMaxStoragePct)
        assertEquals(listOf(meta), repository.getMetaList())
        assertEquals(1, repository.getStoredMetaCount())
    }

    @Test
    fun fetchAndStore_networkHistoricalSearch_storesMlitUrlResponseThroughLocalhostProxy() = runBlocking {
        LocalHttpServer("historical-repository-local-http-server").use { server ->
            server.start()
            val endpointConfig = MlitEndpointConfig.localhostMlitProxy(server.url("/"))
            val networkRepository = HistoricalSearchRepositoryImpl(
                networkDataSource = DamNetworkDataSource(endpointConfig),
                fileParser = DamFileParser("https://www1.river.go.jp/", endpointConfig),
                transactionRunner = RoomDatabaseTransactionRunner(database),
                historicalSearchDao = database.historicalSearchDao(),
                sudmonitorHistoryDao = database.sudmonitorHistoryDao(),
                networkAvailability = FakeNetworkAvailability(),
                sudmonitorHistoricalClient = SudmonitorHistoricalClient(SudmonitorEndpointConfig.production()),
                settingsRepository = HiltFakeSettingsRepository(
                    AppSettings(historicalDataSource = RealtimeDataSource.SUDMONITOR)
                ),
                debugLogRepository = HiltFakeDebugLogRepository(),
                context = context
            )
            server.enqueue(200, """<html><a href="/history.dat">download</a></html>""".toByteArray())
            server.enqueue(200, networkHistoricalDatBytes())

            val result = networkRepository.fetchAndStore(networkOnlySupportedDam(), "20260501", "20260501")

            val meta = result.fold(
                ifLeft = { throw AssertionError(it) },
                ifRight = { it }
            )
            val data = networkRepository.getAllDataByMetaId(meta.id)

            assertTrue(meta.id > 0L)
            assertEquals("20260501", meta.searchBgnDate)
            assertEquals("20260501", meta.searchEndDate)
            assertEquals("9999999999999", meta.damConfigId)
            assertEquals(2, data.size)
            assertEquals(listOf("2026/5/1 01:00", "2026/5/1 02:00"), data.map { it.time })
            assertEquals("2026/5/1 01:00", meta.dataStartTimeStr)
            assertEquals("2026/5/1 02:00", meta.dataEndTimeStr)
            assertEquals(80.0f, meta.dataStartStoragePct!!, 0.001f)
            assertEquals(81.0f, meta.dataEndStoragePct!!, 0.001f)
            assertEquals(80.0f, meta.dataMinStoragePct!!, 0.001f)
            assertEquals(81.0f, meta.dataMaxStoragePct!!, 0.001f)
            assertEquals(listOf(meta), networkRepository.getMetaList())
        }
    }

    @Test
    fun fetchAndStore_sudmonitorCrossMonth_coversWithMonthlyAndLatest() = runBlocking {
        LocalHttpServer("historical-sudmonitor-http-server").use { server ->
            server.start()
            val sudmonitorRepository = HistoricalSearchRepositoryImpl(
                networkDataSource = DamNetworkDataSource(MlitEndpointConfig.localhostHttp()),
                fileParser = DamFileParser(MlitEndpointConfig.localhostHttp()),
                transactionRunner = RoomDatabaseTransactionRunner(database),
                historicalSearchDao = database.historicalSearchDao(),
                sudmonitorHistoryDao = database.sudmonitorHistoryDao(),
                networkAvailability = FakeNetworkAvailability(),
                sudmonitorHistoricalClient = SudmonitorHistoricalClient(
                    SudmonitorEndpointConfig.localhostHttp(server.url("/"))
                ),
                settingsRepository = HiltFakeSettingsRepository(
                    AppSettings(historicalDataSource = RealtimeDataSource.SUDMONITOR)
                ),
                debugLogRepository = HiltFakeDebugLogRepository(),
                context = context
            )
            // 月次2026-07: 7/31(24:00)までをカバー
            server.enqueue(
                200,
                sudmonitorDatBytes(
                    listOf(
                        sudmonitorRow(date = "2026/7/31", time = "01:00", storagePercentage = "68.9"),
                        sudmonitorRow(date = "2026/7/31", time = "02:00", storagePercentage = "68.8")
                    )
                ),
                mapOf(
                    "X-TCS-Dam-Id" to "1368080700010",
                    "X-TCS-History-Start" to "2026-07-01T00:00:00+09:00",
                    "X-TCS-History-End" to "2026-08-01T00:00:00+09:00"
                )
            )
            // 月次2026-08: 404
            server.enqueue(404, byteArrayOf())
            // latest: 7/31〜8/1をカバー（untilは8/1 24:00=翌日00:00表記）
            server.enqueue(
                200,
                sudmonitorDatBytes(
                    listOf(
                        sudmonitorRow(date = "2026/7/31", time = "01:00", storagePercentage = "55.0"),
                        sudmonitorRow(date = "2026/7/31", time = "02:00", storagePercentage = "54.0"),
                        sudmonitorRow(date = "2026/8/1", time = "01:00", storagePercentage = "68.5"),
                        sudmonitorRow(date = "2026/8/1", time = "02:00", storagePercentage = "68.3")
                    )
                ),
                mapOf(
                    "X-TCS-Dam-Id" to "1368080700010",
                    "X-TCS-History-Since" to "2026-07-31T01:00:00+09:00",
                    "X-TCS-History-Until" to "2026-08-02T00:00:00+09:00"
                )
            )

            val result = sudmonitorRepository.fetchAndStore(DamConfig.DEFAULT, "20260731", "20260801")

            val meta = result.fold(
                ifLeft = { throw AssertionError(it) },
                ifRight = { it }
            )
            val storedMeta = sudmonitorRepository.getMetaById(meta.id)
            val data = sudmonitorRepository.getAllDataByMetaId(meta.id)
            val millisList = data.map { jstMillisLenient(it.time) }

            assertTrue(meta.id > 0L)
            assertNotNull(storedMeta)
            assertEquals("20260731", meta.searchBgnDate)
            assertEquals("20260801", meta.searchEndDate)
            // 重複排除: 7/31は月次（高優先度）の行が残り、8/1はlatestの行が残る
            assertEquals(
                listOf("2026/7/31 01:00", "2026/7/31 02:00", "2026/8/1 01:00", "2026/8/1 02:00"),
                data.map { it.time }
            )
            assertEquals(listOf(68.9f, 68.8f, 68.5f, 68.3f), data.map { it.storagePercentage })
            assertEquals(data.map { it.time }.distinct().size, data.size)
            assertEquals(millisList.sorted(), millisList)
            assertEquals(data.first().time, meta.dataStartTimeStr)
            assertEquals(data.last().time, meta.dataEndTimeStr)
            assertEquals(listOf(meta), sudmonitorRepository.getMetaList())
        }
    }

    @Test
    fun fetchAndStore_sudmonitorMonthly_coversJuly2026() = runBlocking {
        LocalHttpServer("historical-sudmonitor-http-server").use { server ->
            server.start()
            val sudmonitorRepository = HistoricalSearchRepositoryImpl(
                networkDataSource = DamNetworkDataSource(MlitEndpointConfig.localhostHttp()),
                fileParser = DamFileParser(MlitEndpointConfig.localhostHttp()),
                transactionRunner = RoomDatabaseTransactionRunner(database),
                historicalSearchDao = database.historicalSearchDao(),
                sudmonitorHistoryDao = database.sudmonitorHistoryDao(),
                networkAvailability = FakeNetworkAvailability(),
                sudmonitorHistoricalClient = SudmonitorHistoricalClient(
                    SudmonitorEndpointConfig.localhostHttp(server.url("/"))
                ),
                settingsRepository = HiltFakeSettingsRepository(
                    AppSettings(historicalDataSource = RealtimeDataSource.SUDMONITOR)
                ),
                debugLogRepository = HiltFakeDebugLogRepository(),
                context = context
            )
            // バンドルは2026-06までなので2026-07はバンドル外
            server.enqueue(
                200,
                sudmonitorDatBytes(
                    listOf(
                        sudmonitorRow(date = "2026/7/1", time = "01:00", storagePercentage = "80.0"),
                        sudmonitorRow(date = "2026/7/31", time = "24:00", storagePercentage = "81.0")
                    )
                ),
                mapOf(
                    "X-TCS-Dam-Id" to "1368080700010",
                    "X-TCS-History-Start" to "2026-07-01T00:00:00+09:00",
                    "X-TCS-History-End" to "2026-08-01T00:00:00+09:00"
                )
            )

            val result = sudmonitorRepository.fetchAndStore(DamConfig.DEFAULT, "20260701", "20260731")

            val meta = result.fold(
                ifLeft = { throw AssertionError(it) },
                ifRight = { it }
            )
            val data = sudmonitorRepository.getAllDataByMetaId(meta.id)

            assertTrue(meta.id > 0L)
            assertEquals("20260701", meta.searchBgnDate)
            assertEquals("20260731", meta.searchEndDate)
            assertEquals(listOf("2026/7/1 01:00", "2026/7/31 24:00"), data.map { it.time })
            assertEquals(listOf(80.0f, 81.0f), data.map { it.storagePercentage })
            assertEquals("2026/7/1 01:00", meta.dataStartTimeStr)
            assertEquals("2026/7/31 24:00", meta.dataEndTimeStr)
            assertEquals(listOf(meta), sudmonitorRepository.getMetaList())
        }
    }

    private fun jstMillisLenient(time: String): Long =
        TimeUtils.parseJstMillisAllow24Hour(time, "yyyy/MM/dd HH:mm") ?: error("Invalid time: $time")

    private fun networkHistoricalDatBytes(): ByteArray =
        listOf(
            "任意期間ダム諸量検索結果",
            "水系名,吉野川",
            "河川名,吉野川",
            "観測所名,早明浦ダム",
            "観測所記号,1368080700010",
            "#年月日,時刻,流域平均雨量,雨量属性,貯水量,貯水量属性,流入量,流入量属性,放流量,放流量属性,貯水率,貯水率属性",
            "2026/5/1,01:00,0,,100000,,10.0,,9.0,,80.0,",
            "2026/5/1,02:00,0,,100100,,10.5,,9.5,,81.0,"
        ).joinToString("\n").toByteArray(Charset.forName("Shift_JIS"))

    /** sudmonitorモック用の小さな合成.dat（ヘッダ6行+データ行）をShift_JISで生成する。 */
    private fun sudmonitorDatBytes(dataRows: List<String>): ByteArray =
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

    private fun sudmonitorRow(
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

    private fun networkOnlySupportedDam(): DamConfig =
        DamConfig(
            id = "9999999999999",
            nameJa = "テストダム",
            nameEn = "Test Dam",
            dataUrl = "https://www1.river.go.jp/cgi-bin/DspDamData.exe?KIND=1&ID=9999999999999"
        )

}
