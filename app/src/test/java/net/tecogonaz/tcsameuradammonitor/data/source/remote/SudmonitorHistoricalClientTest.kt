// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import arrow.core.Either
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

/**
 * sudmonitor 履歴ファイル取得クライアント [SudmonitorHistoricalClient] のユニットテストクラス。
 * MockWebServer と [SudmonitorEndpointConfig.localhostHttp] による localhost 書き換え、
 * 固定クロック注入を使い、月次・日次の取得、ヘッダーパース、エラーハンドリング、
 * 負の結果キャッシュ（TTL / 最大キー数 / evict）、UA ヘッダー、非対応ダムIDのスキップを検証する。
 */
class SudmonitorHistoricalClientTest {
    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private var currentTimeMillis = 0L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = createMlitHttpClient()
        currentTimeMillis = 1_000L
    }

    @After
    fun tearDown() {
        httpClient.close()
        server.shutdown()
    }

    private fun createClient() = SudmonitorHistoricalClient(
        endpointConfig = SudmonitorEndpointConfig.localhostHttp(server.url("/").toString()),
        httpClient = httpClient,
        clock = { currentTimeMillis }
    )

    @Test
    fun monthlyFileName_derivesDeterministicNames() {
        assertEquals(
            "${TEST_DAM_ID}_202607010100_202607312400.dat",
            SudmonitorHistoricalClient.monthlyFileName(TEST_DAM_ID, YearMonth.of(2026, 7))
        )
        assertEquals(
            "${TEST_DAM_ID}_202402010100_202402292400.dat",
            SudmonitorHistoricalClient.monthlyFileName(TEST_DAM_ID, YearMonth.of(2024, 2))
        )
        assertEquals(
            "${TEST_DAM_ID}_202302010100_202302282400.dat",
            SudmonitorHistoricalClient.monthlyFileName(TEST_DAM_ID, YearMonth.of(2023, 2))
        )
    }

    @Test
    fun fetchMonthly_singleGetWithExpectedPathAndParsedStartEndHeaders() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", TEST_DAM_ID)
                .setHeader("X-TCS-History-Start", "2026-07-01T00:00:00+09:00")
                .setHeader("X-TCS-History-End", "2026-07-31T23:59:00+09:00")
                .setBody(Buffer().write(SAMPLE_DAT_BYTES))
        )
        val client = createClient()

        val result = client.fetchMonthly(TEST_DAM_ID, YearMonth.of(2026, 7))

        assertTrue(result.isRight())
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals(
            "/v1/history/$TEST_DAM_ID/${TEST_DAM_ID}_202607010100_202607312400.dat",
            request.path
        )
        val body = result.getOrNull()
        assertNotNull(body)
        assertArrayEquals(SAMPLE_DAT_BYTES, body!!.bytes)
        assertEquals(TEST_DAM_ID, body.damIdHeader)
        assertEquals("2026-07-01T00:00:00+09:00", body.since)
        assertEquals("2026-07-31T23:59:00+09:00", body.until)
    }

    @Test
    fun fetchLatest_singleGetWithLatestPathAndParsedSinceUntilHeaders() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", TEST_DAM_ID)
                .setHeader("X-TCS-History-Since", "2026-08-07T00:00:00+09:00")
                .setHeader("X-TCS-History-Until", "2026-08-07T21:00:00+09:00")
                .setBody(Buffer().write(SAMPLE_DAT_BYTES))
        )
        val client = createClient()

        val result = client.fetchLatest(TEST_DAM_ID)

        assertTrue(result.isRight())
        assertEquals(1, server.requestCount)
        assertEquals("/v1/history/$TEST_DAM_ID/latest.dat", server.takeRequest().path)
        val body = result.getOrNull()
        assertNotNull(body)
        assertEquals("2026-08-07T00:00:00+09:00", body!!.since)
        assertEquals("2026-08-07T21:00:00+09:00", body.until)
    }

    @Test
    fun fetchLatest_parsesNextUpdateAtHeader() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", TEST_DAM_ID)
                .setHeader("X-TCS-Next-Update-At", "2026-08-08T00:13:00Z")
                .setBody(Buffer().write(SAMPLE_DAT_BYTES))
        )
        val client = createClient()

        val result = client.fetchLatest(TEST_DAM_ID)

        assertTrue(result.isRight())
        assertEquals("2026-08-08T00:13:00Z", result.getOrNull()?.nextUpdateAtHeader)
    }

    @Test
    fun fetchLatest_missingNextUpdateAtHeader_returnsNull() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", TEST_DAM_ID)
                .setBody(Buffer().write(SAMPLE_DAT_BYTES))
        )
        val client = createClient()

        val result = client.fetchLatest(TEST_DAM_ID)

        assertTrue(result.isRight())
        assertNull(result.getOrNull()?.nextUpdateAtHeader)
    }

    @Test
    fun fetchLatest_invalidNextUpdateAtHeader_passesThroughRawValue() = runBlocking {
        // クライアントは生文字列を透過させるだけであり、寛容な parse（不正値→null）は
        // リポジトリ層（SudmonitorHistoryRepositoryImpl）で実施される契約のため、
        // ここでは不正値がそのまま結果に含まれることを確認する。
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", TEST_DAM_ID)
                .setHeader("X-TCS-Next-Update-At", "not-an-instant")
                .setBody(Buffer().write(SAMPLE_DAT_BYTES))
        )
        val client = createClient()

        val result = client.fetchLatest(TEST_DAM_ID)

        assertTrue(result.isRight())
        assertEquals("not-an-instant", result.getOrNull()?.nextUpdateAtHeader)
    }

    @Test
    fun fetchMonthly_notFound_returnsRightNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val client = createClient()

        val result = client.fetchMonthly(TEST_DAM_ID, YearMonth.of(2026, 7))

        assertTrue(result.isRight())
        assertNull(result.getOrNull())
        assertNotNull(server.takeRequest())
    }

    @Test
    fun fetchLatest_serverError_returnsLeft() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(503).setBody("<html>Error 1015 Rate limited</html>")
        )
        val client = createClient()

        val result = client.fetchLatest(TEST_DAM_ID)

        assertTrue(result.isLeft())
        assertLeftMessageContains(result, "503")
    }

    @Test
    fun fetchMonthly_damIdHeaderMismatch_returnsLeft() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", "9999999999999")
                .setBody(Buffer().write(SAMPLE_DAT_BYTES))
        )
        val client = createClient()

        val result = client.fetchMonthly(TEST_DAM_ID, YearMonth.of(2026, 7))

        assertTrue(result.isLeft())
        assertLeftMessageContains(result, "X-TCS-Dam-Id mismatch")
    }

    @Test
    fun fetchMonthly_withoutDamIdHeader_succeeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(SAMPLE_DAT_BYTES)))
        val client = createClient()

        val result = client.fetchMonthly(TEST_DAM_ID, YearMonth.of(2026, 7))

        assertTrue(result.isRight())
        assertNull(result.getOrNull()?.damIdHeader)
    }

    @Test
    fun fetchMonthly_redirect_returnsLeftWithoutFollowing() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/v1/history/$TEST_DAM_ID/redirected.dat")
        )
        val client = createClient()

        val result = client.fetchMonthly(TEST_DAM_ID, YearMonth.of(2026, 7))

        assertTrue(result.isLeft())
        assertEquals(1, server.requestCount)
        assertLeftMessageContains(result, "302")
    }

    @Test
    fun fetchMonthly_responseOverLimit_returnsLeftIOException() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("x".repeat((MAX_RESPONSE_BYTES + 1).toInt()))
        )
        val client = createClient()

        val result = client.fetchMonthly(TEST_DAM_ID, YearMonth.of(2026, 7))

        assertTrue(result.isLeft())
        assertTrue(result.swap().getOrNull() is java.io.IOException)
        assertLeftMessageContains(result, "Response is too large")
    }

    @Test
    fun fetchMonthly_sendsAndroidUserAgent() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(SAMPLE_DAT_BYTES)))
        val client = createClient()

        val result = client.fetchMonthly(TEST_DAM_ID, YearMonth.of(2026, 7))

        assertTrue(result.isRight())
        assertEquals(
            "TCSameuraDamMonitor-Android/${BuildConfig.VERSION_NAME}",
            server.takeRequest().getHeader("User-Agent")
        )
    }

    @Test
    fun negativeCache_hitWithinTtl_returnsNullWithoutNetworkAccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val client = createClient()

        val first = client.fetchMonthly(TEST_DAM_ID, YearMonth.of(2026, 7))
        assertTrue(first.isRight())
        assertNull(first.getOrNull())
        assertNotNull(server.takeRequest())

        val second = client.fetchMonthly(TEST_DAM_ID, YearMonth.of(2026, 7))
        assertTrue(second.isRight())
        assertNull(second.getOrNull())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun negativeCache_expiredAfterTtl_refetchesNetwork() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        val client = createClient()

        val first = client.fetchLatest(TEST_DAM_ID)
        assertNull(first.getOrNull())
        assertNotNull(server.takeRequest())

        currentTimeMillis += TTL_MILLIS + 1L
        val second = client.fetchLatest(TEST_DAM_ID)
        assertNull(second.getOrNull())
        assertNotNull(server.takeRequest())
    }

    @Test
    fun negativeCache_exceedingMaxSize_evictsOldest() = runBlocking {
        val client = createClient()
        val startMonth = YearMonth.of(2020, 1)

        for (index in 0 until MAX_CACHE_KEYS + 1) {
            server.enqueue(MockResponse().setResponseCode(404))
            val month = startMonth.plusMonths(index.toLong())
            val result = client.fetchMonthly(TEST_DAM_ID, month)
            assertNull(result.getOrNull())
            assertNotNull(server.takeRequest())
        }

        // 2番目のキー（2020-02）はキャッシュに残っているためアクセスされない
        val secondResult = client.fetchMonthly(TEST_DAM_ID, startMonth.plusMonths(1))
        assertNull(secondResult.getOrNull())
        assertEquals(MAX_CACHE_KEYS + 1, server.requestCount)

        // 最古キー（2020-01）は evict されているため再アクセスされる
        server.enqueue(MockResponse().setResponseCode(404))
        val oldestResult = client.fetchMonthly(TEST_DAM_ID, startMonth)
        assertNull(oldestResult.getOrNull())
        assertNotNull(server.takeRequest())

        // 最新キー（2020-01+128）はキャッシュに残っているためアクセスされない
        val newestResult = client.fetchMonthly(TEST_DAM_ID, startMonth.plusMonths(MAX_CACHE_KEYS.toLong()))
        assertNull(newestResult.getOrNull())
        assertEquals(MAX_CACHE_KEYS + 2, server.requestCount)
    }

    @Test
    fun unsupportedDamId_returnsRightNullWithoutNetworkAccess() = runBlocking {
        val client = createClient()

        val monthly = client.fetchMonthly(UNSUPPORTED_DAM_ID, YearMonth.of(2026, 7))
        val latest = client.fetchLatest(UNSUPPORTED_DAM_ID)

        assertTrue(monthly.isRight())
        assertNull(monthly.getOrNull())
        assertTrue(latest.isRight())
        assertNull(latest.getOrNull())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun fetchMonthly_realFixtureServesSameBytes() = runBlocking {
        val fixtureBytes = loadFixtureBytes()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-TCS-Dam-Id", TEST_DAM_ID)
                .setHeader("X-TCS-History-Start", "2026-07-01T00:00:00+09:00")
                .setHeader("X-TCS-History-End", "2026-07-31T23:59:00+09:00")
                .setBody(Buffer().write(fixtureBytes))
        )
        val client = createClient()

        val result = client.fetchMonthly(TEST_DAM_ID, YearMonth.of(2026, 7))

        assertTrue(result.isRight())
        assertArrayEquals(fixtureBytes, result.getOrNull()?.bytes)
    }

    private fun loadFixtureBytes(): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(FIXTURE_RESOURCE_PATH)) {
            "Fixture $FIXTURE_RESOURCE_PATH not found."
        }.use { it.readBytes() }

    private fun assertLeftMessageContains(
        result: Either<Throwable, SudmonitorHistoricalResult?>,
        expectedMessage: String
    ) {
        result.fold(
            ifLeft = { error ->
                assertTrue(
                    "Expected error message to contain <$expectedMessage>, but was <${error.message}>.",
                    error.message?.contains(expectedMessage) == true
                )
            },
            ifRight = {
                throw AssertionError("Expected failure, but got success.")
            }
        )
    }

    private companion object {
        private const val TEST_DAM_ID = "1368080700010"
        private const val UNSUPPORTED_DAM_ID = "9999999999999"
        private const val TTL_MILLIS = 5L * 60L * 1000L
        private const val MAX_CACHE_KEYS = 128
        private const val FIXTURE_RESOURCE_PATH = "/sudmonitor/202607_monthly.dat"
        private val SAMPLE_DAT_BYTES =
            ("202607010100,0.00\n" + "202607010200,0.00\n").toByteArray(Charsets.UTF_8)
    }
}
