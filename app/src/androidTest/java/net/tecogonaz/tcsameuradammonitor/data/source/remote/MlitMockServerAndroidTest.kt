// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import android.content.Context
import arrow.core.Either
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import net.tecogonaz.tcsameuradammonitor.MainActivity
import net.tecogonaz.tcsameuradammonitor.di.NetworkModule
import net.tecogonaz.tcsameuradammonitor.di.WorkerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.tecogonaz.tcsameuradammonitor.data.repository.DamDataRepositoryImpl
import net.tecogonaz.tcsameuradammonitor.data.source.local.DebugDatSourceReader
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamDatabase
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.model.AutoUpdateInterval
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfigProvider

import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDamWorkManagerGateway
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeDebugLogRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeHistoricalComparisonRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeHistoricalSearchRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeNetworkAvailability
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSettingsRepository
import net.tecogonaz.tcsameuradammonitor.testutil.HiltFakeSudmonitorHistoryRepository
import net.tecogonaz.tcsameuradammonitor.testutil.LocalHttpServer
import net.tecogonaz.tcsameuradammonitor.ui.main.TestTags
import net.tecogonaz.tcsameuradammonitor.ui.main.MainViewModel
import net.tecogonaz.tcsameuradammonitor.domain.usecase.QueryAvailableAppsUseCase
import net.tecogonaz.tcsameuradammonitor.util.AppNotificationManager
import net.tecogonaz.tcsameuradammonitor.util.TimeUtils
import net.tecogonaz.tcsameuradammonitor.worker.DamWorkManagerGateway
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.Charset
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(NetworkModule::class, WorkerModule::class)
@RunWith(AndroidJUnit4::class)
@MediumTest
/**
 * ローカルに起動したモック HTTP サーバー（LocalHttpServer）を用いて、国土交通省のダムデータ配信サーバー（MLIT）の応答を
 * エミュレートし、アプリアクティビティ（[MainActivity]）との間でエンドツーエンド（E2E）での通信結合テストを行う Instrumentation テストクラス。
 * バックグラウンドスレッドで起動した Socket ベースの独自 HTTP モックサーバーを利用して、HTML および DAT ファイルの配信、
 * アプリでのパース、Room データベースへの登録、およびメイン画面（Compose）上での「80.00%」等の観測データの
 * 正確な表示更新プロセスを統合的に検証します。
 */
class MlitMockServerAndroidTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @BindValue
    @JvmField
    var boundMlitEndpointConfig: MlitEndpointConfig = MlitEndpointConfig.localhostHttp()

    @BindValue
    @JvmField
    var boundSudmonitorEndpointConfig: SudmonitorEndpointConfig =
        SudmonitorEndpointConfig.production()

    @BindValue
    @JvmField
    val boundDamWorkManagerGateway: DamWorkManagerGateway = HiltFakeDamWorkManagerGateway()

    @Inject
    lateinit var hiltDamDataRepository: DamDataRepository

    @Inject
    lateinit var hiltSettingsRepository: SettingsRepository

    private lateinit var server: LocalHttpServer

    @Before
    fun setUp() {
        server = LocalHttpServer("mlit-local-http-server").also { it.start() }
        boundMlitEndpointConfig = MlitEndpointConfig.localhostMlitProxy(server.url("/"))
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun localHttpServer_pipeline_returnsParsedDamDataOverLocalhostHttp() = runBlocking {
        val baseUrl = server.url("/")
        val dataSource = DamNetworkDataSource(MlitEndpointConfig.localhostHttp())
        val parser = DamFileParser(baseUrl, MlitEndpointConfig.localhostHttp())
        server.enqueue(200, """<a href="latest.dat">latest</a>""".toByteArray())
        server.enqueue(200, datBytes())

        val htmlBytes = dataSource.fetchBytes(server.url("/index.html"))
            .fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val datUrl = parser.parseHtmlForDatUrl(htmlBytes)
        val datBytes = dataSource.fetchBytes(datUrl ?: error("dat url missing"))
            .fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val parsed = parser.parseDatCsv(datBytes, "1368080700010", "早明浦ダム")

        assertTrue(parsed.isRight())
        parsed.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = {
                assertNotNull(it.storagePercentage)
                assertEquals(81.25f, it.storagePercentage!!, 0.001f)
                assertEquals("2026/05/15 23:40", it.updatedAt)
            }
        )
    }

    @Test
    fun localHttpServer_invalidHtml_returnsNoDatUrl() = runBlocking {
        val dataSource = DamNetworkDataSource(MlitEndpointConfig.localhostHttp())
        val parser = DamFileParser(
            datBaseUrl = server.url("/"),
            datEndpointConfig = MlitEndpointConfig.localhostHttp()
        )
        server.enqueue(200, """<html>No dat link</html>""".toByteArray())

        val htmlBytes = dataSource.fetchBytes(server.url("/index.html"))
            .fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })

        assertNull(parser.parseHtmlForDatUrl(htmlBytes))
    }

    @Test
    fun localHttpServer_unparseableDat_returnsFailure() = runBlocking {
        val dataSource = DamNetworkDataSource(MlitEndpointConfig.localhostHttp())
        val parser = DamFileParser(
            datBaseUrl = server.url("/"),
            datEndpointConfig = MlitEndpointConfig.localhostHttp()
        )
        server.enqueue(200, """<a href="latest.dat">latest</a>""".toByteArray())
        server.enqueue(200, "not,a,dam,dat".toByteArray())

        val htmlBytes = dataSource.fetchBytes(server.url("/index.html"))
            .fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val datUrl = parser.parseHtmlForDatUrl(htmlBytes) ?: error("dat url missing")
        val bytes = dataSource.fetchBytes(datUrl)
            .fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val parsed = parser.parseDatCsv(bytes, "1368080700010", "早明浦ダム")

        assertTrue(parsed.isLeft())
        assertLeftMessageContains(parsed, "No data rows")
    }

    @Test
    fun localHttpServer_utf8BomDat_returnsParsedDamData() = runBlocking {
        val dataSource = DamNetworkDataSource(MlitEndpointConfig.localhostHttp())
        val parser = DamFileParser(
            datBaseUrl = server.url("/"),
            datEndpointConfig = MlitEndpointConfig.localhostHttp()
        )
        val bomDatBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            String(datBytes(), Charset.forName("Shift_JIS")).toByteArray(Charsets.UTF_8)
        server.enqueue(200, """<a href="latest.dat">latest</a>""".toByteArray())
        server.enqueue(200, bomDatBytes)

        val htmlBytes = dataSource.fetchBytes(server.url("/index.html"))
            .fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val datUrl = parser.parseHtmlForDatUrl(htmlBytes) ?: error("dat url missing")
        val bytes = dataSource.fetchBytes(datUrl)
            .fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
        val parsed = parser.parseDatCsv(bytes, "1368080700010", "早明浦ダム")

        assertTrue(parsed.isRight())
        parsed.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = {
                assertNotNull(it.storagePercentage)
                assertEquals(81.25f, it.storagePercentage!!, 0.001f)
            }
        )
    }

    @Test
    fun localHttpServer_serverError_returnsFailure() = runBlocking {
        val dataSource = DamNetworkDataSource(MlitEndpointConfig.localhostHttp())
        server.enqueue(500, "ng".toByteArray())

        val result = dataSource.fetchBytes(server.url("/index.html"))

        assertTrue(result.isLeft())
        assertLeftMessageContains(result, "500")
    }

    @Test
    fun localHttpServer_repositoryAndViewModelPipeline_returnsParsedUiState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, DamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val endpointConfig = MlitEndpointConfig.localhostMlitProxy(server.url("/"))
            val repository = DamDataRepositoryImpl(
                networkDataSource = DamNetworkDataSource(endpointConfig),
                sudmonitorNetworkDataSource = SudmonitorNetworkDataSource(
                    SudmonitorEndpointConfig.production()
                ),
                parser = DamFileParser("https://www1.river.go.jp/", endpointConfig),
                settingsRepository = HiltFakeSettingsRepository(AppSettings(initialAutoUpdateDialogShown = true)),
                damDao = database.damDao(),
                applicationScope = applicationScope,
                context = context,
                damConfigProvider = object : DamConfigProvider {
                    override fun get(targetDamId: String): DamConfig =
                        DamConfig.DEFAULT.copy(dataUrl = "https://www1.river.go.jp/index.html")
                },
                debugDatSourceReader = DebugDatSourceReader(context)
            )
            server.enqueue(200, """<a href="/latest.dat">latest</a>""".toByteArray())
            server.enqueue(200, datBytes())

            val fetched = repository.fetchLatestData()
                .fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })
            val viewModel = MainViewModel(
                context = context,
                settingsRepository = HiltFakeSettingsRepository(AppSettings(initialAutoUpdateDialogShown = true)),
                damDataRepository = repository,
                debugLogRepository = HiltFakeDebugLogRepository(),
                historicalSearchRepository = HiltFakeHistoricalSearchRepository(),
                historicalComparisonRepository = HiltFakeHistoricalComparisonRepository(),
                appNotificationManager = AppNotificationManager(context),
                queryAvailableAppsUseCase = QueryAvailableAppsUseCase(context),
                damWorkManagerGateway = HiltFakeDamWorkManagerGateway(),
                networkAvailability = HiltFakeNetworkAvailability(),
                sudmonitorHistoryRepository = HiltFakeSudmonitorHistoryRepository()
            )

            viewModel.uiState.first { it.damData != null }

            assertEquals(81.25f, fetched.storagePercentage!!, 0.001f)
            assertEquals("2026/05/15 23:40", viewModel.uiState.value.damData?.updatedAt)
            assertEquals(81.25f, viewModel.uiState.value.damData?.storagePercentage!!, 0.001f)
        } finally {
            applicationScope.cancel()
            database.close()
        }
    }

    @Test
    fun hiltGraphEndpointOverride_repositoryFetchFeedsActivityViewModelState() = runBlocking {
        hiltSettingsRepository.updateSettings {
            AppSettings(
                initialAutoUpdateDialogShown = true,
                autoUpdateInterval = AutoUpdateInterval.ONE_WEEK,
                autoUpdateCustomTimingMillisWeekly = jstMillis("2099/01/05 05:15"),
                autoUpdateCustomTimingMillisDaily = jstMillis("2099/01/01 05:15"),
                autoUpdateCustomTimingMillis12Hours = jstMillis("2099/01/01 05:15"),
                autoUpdateCustomTimingMillisHourly = jstMillis("2099/01/01 05:15")
            )
        }
        hiltDamDataRepository.clearData()
        server.enqueue(200, """<a href="/latest.dat">latest</a>""".toByteArray())
        server.enqueue(200, datBytes())

        val fetched = hiltDamDataRepository.fetchLatestData()
            .fold(ifLeft = { throw AssertionError(it) }, ifRight = { it })

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForMainRoot()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("81.25%", substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            assertTrue(
                composeRule.onAllNodesWithText("81.25%", substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            )
        }
        assertEquals(81.25f, fetched.storagePercentage!!, 0.001f)
    }

    private fun assertLeftMessageContains(
        result: Either<Throwable, *>,
        expectedMessage: String
    ) {
        result.fold(
            ifLeft = { error ->
                assertTrue(
                    "Expected error message to contain <$expectedMessage>, but was <${error.message}>.",
                    error.message?.contains(expectedMessage) == true
                )
            },
            ifRight = { throw AssertionError("Expected failure, but got success.") }
        )
    }

    private fun datBytes(): ByteArray =
        listOf(
            "水系名,吉野川",
            "河川名,吉野川",
            "観測所名,早明浦ダム",
            "観測所記号,1368080700010",
            "# 日付,時刻,流域平均雨量,属性,貯水量,属性,流入量,属性,放流量,属性,貯水率,属性",
            "2026/05/15,23:40,0,,100000,,10.0,,9.0,,81.25,"
        ).joinToString("\n").toByteArray(Charset.forName("Shift_JIS"))

    private fun waitForMainRoot() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(TestTags.MAIN_ROOT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun jstMillis(value: String): Long =
        TimeUtils.parseJstMillis(value, "yyyy/MM/dd HH:mm") ?: error("Invalid date: $value")

}
