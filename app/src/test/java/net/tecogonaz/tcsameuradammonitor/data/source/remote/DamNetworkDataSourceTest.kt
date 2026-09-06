// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import arrow.core.Either
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 国土交通省（MLIT）のサーバーからデータをバイナリとして安全に取得する [DamNetworkDataSource] のユニットテストクラス。
 * モックサーバー（MockWebServer）を用いた正常通信（バイナリデータの取得）、User-Agent ヘッダーの検証、
 * 未許可ホストや HTTP（本番環境では HTTPS のみ許可）によるリクエストの防止、
 * レスポンスサイズ超過（[MAX_RESPONSE_BYTES] 超過）時のエラーハンドリング（Content-Length ヘッダー有無両方）、
 * リダイレクトの拒否動作、およびローカルデバッグ用のプロキシ書き換えロジックが正常に機能することを検証します。
 */
class DamNetworkDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var dataSource: DamNetworkDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = createMlitHttpClient()
        dataSource = DamNetworkDataSource(MlitEndpointConfig.localhostHttp(), httpClient)
    }

    @After
    fun tearDown() {
        httpClient.close()
        server.shutdown()
    }

    @Test
    fun fetchBytes_returnsBody() = runBlocking {
        val body = "ok".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(String(body)))

        val result = dataSource.fetchBytes(server.url("/current.dat").toString())

        assertTrue(result.isRight())
        result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { assertArrayEquals(body, it) }
        )
    }

    @Test
    fun fetchBytes_sendsUserAgent() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val result = dataSource.fetchBytes(server.url("/current.dat").toString())

        assertTrue(result.isRight())
        assertEquals(EXPECTED_MLIT_USER_AGENT, server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun fetchBytes_rejectsUnconfiguredHostBeforeRequest() = runBlocking {
        val result = dataSource.fetchBytes("http://example.com/current.dat")

        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { assertTrue(it is IllegalArgumentException) },
            ifRight = { throw AssertionError("Expected failure, but got success.") }
        )
    }

    @Test
    fun fetchBytes_returnsFailureForServerError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("ng"))

        val result = dataSource.fetchBytes(server.url("/current.dat").toString())

        assertTrue(result.isLeft())
        assertLeftMessageContains(result, "500")
    }

    @Test
    fun fetchBytes_rejectsContentLengthOverLimit() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("x".repeat((MAX_RESPONSE_BYTES + 1).toInt()))
        )

        val result = dataSource.fetchBytes(server.url("/current.dat").toString())

        assertTrue(result.isLeft())
        assertLeftMessageContains(result, "Response is too large")
    }

    @Test
    fun fetchBytes_rejectsStreamingBodyOverLimitWithoutContentLength() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setChunkedBody("x".repeat((MAX_RESPONSE_BYTES + 1).toInt()), 8192)
        )

        val result = dataSource.fetchBytes(server.url("/current.dat").toString())

        assertTrue(result.isLeft())
        assertLeftMessageContains(result, "Response is too large")
    }

    @Test
    fun fetchBytes_rejectsRedirect() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/redirected.dat")
        )

        val result = dataSource.fetchBytes(server.url("/current.dat").toString())

        assertTrue(result.isLeft())
        assertEquals(1, server.requestCount)
        assertLeftMessageContains(result, "302")
    }

    @Test
    fun productionEndpointConfig_allowsOnlyMlitHttps() {
        val config = MlitEndpointConfig.production()

        config.requireAllowed("https://www1.river.go.jp/current.dat")
        assertRequireAllowedFailsWith(
            config = config,
            url = "http://www1.river.go.jp/current.dat",
            expectedMessage = "Only HTTPS URLs are allowed."
        )
        assertRequireAllowedFailsWith(
            config = config,
            url = "https://example.com/current.dat",
            expectedMessage = "Only www1.river.go.jp URLs are allowed."
        )
    }

    @Test
    fun localhostMlitProxy_rewritesOnlyMlitHostToLocalBaseUrl() {
        val config = MlitEndpointConfig.localhostMlitProxy("http://127.0.0.1:41234/base")

        assertEquals(
            "http://127.0.0.1:41234/base/cgi-bin/DspDamData.exe?ID=1368080700010",
            config.resolveFetchUrl("https://www1.river.go.jp/cgi-bin/DspDamData.exe?ID=1368080700010")
        )
        assertEquals(
            "http://127.0.0.1:41234/current.dat",
            config.resolveFetchUrl("http://127.0.0.1:41234/current.dat")
        )
    }

    private fun assertLeftMessageContains(
        result: Either<Throwable, ByteArray>,
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

    private fun assertRequireAllowedFailsWith(
        config: MlitEndpointConfig,
        url: String,
        expectedMessage: String
    ) {
        val error = runCatching { config.requireAllowed(url) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(
            "Expected error message to contain <$expectedMessage>, but was <${error?.message}>.",
            error?.message?.contains(expectedMessage) == true
        )
    }

    private companion object {
        private const val EXPECTED_MLIT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Mobile Safari/537.36"
    }
}
