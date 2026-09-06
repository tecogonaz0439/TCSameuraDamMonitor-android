// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import net.tecogonaz.tcsameuradammonitor.util.catchNonCancellationSuspend
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

internal const val MAX_RESPONSE_BYTES = 5L * 1024L * 1024L

internal data class DamNetworkTimeoutConfig(
    val requestTimeoutMillis: Long,
    val connectTimeoutMillis: Long,
    val socketTimeoutMillis: Long
)

internal val DEFAULT_DAM_NETWORK_TIMEOUT_CONFIG = DamNetworkTimeoutConfig(
    requestTimeoutMillis = 30_000L,
    connectTimeoutMillis = 15_000L,
    socketTimeoutMillis = 30_000L
)

internal fun createMlitHttpClient(
    timeoutConfig: DamNetworkTimeoutConfig = DEFAULT_DAM_NETWORK_TIMEOUT_CONFIG
): HttpClient = HttpClient(CIO) {
    expectSuccess = true
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = timeoutConfig.requestTimeoutMillis
        connectTimeoutMillis = timeoutConfig.connectTimeoutMillis
        socketTimeoutMillis = timeoutConfig.socketTimeoutMillis
    }
}


data class MlitEndpointConfig(
    val allowedSchemes: Set<String>,
    val allowedHosts: Set<String>,
    private val mlitBaseUrlOverride: String? = null
) {
    fun requireAllowed(url: String) {
        val uri = URI(url)
        require(uri.scheme?.lowercase() in allowedSchemes) {
            "Only ${allowedSchemes.joinToString("/").uppercase()} URLs are allowed."
        }
        require(uri.host?.lowercase() in allowedHosts) {
            "Only ${allowedHosts.joinToString()} URLs are allowed."
        }
    }

    fun resolveFetchUrl(url: String): String {
        requireAllowed(url)
        val override = mlitBaseUrlOverride ?: return url
        val uri = URI(url)
        if (uri.host?.lowercase() != MLIT_DAM_HOST) return url
        val base = URI(override.trimEnd('/') + "/")
        val path = uri.rawPath.orEmpty().removePrefix("/")
        val resolved = base.resolve(path)
        return URI(
            resolved.scheme,
            resolved.authority,
            resolved.path,
            uri.rawQuery,
            uri.rawFragment
        ).toString()
    }

    companion object {
        private const val MLIT_DAM_HOST = "www1.river.go.jp"

        fun production(): MlitEndpointConfig = MlitEndpointConfig(
            allowedSchemes = setOf("https"),
            allowedHosts = setOf(MLIT_DAM_HOST)
        )

        fun localhostHttp(): MlitEndpointConfig = MlitEndpointConfig(
            allowedSchemes = setOf("http"),
            allowedHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")
        )

        fun localhostMlitProxy(baseUrl: String): MlitEndpointConfig = MlitEndpointConfig(
            allowedSchemes = setOf("https", "http"),
            allowedHosts = setOf(MLIT_DAM_HOST, "localhost", "127.0.0.1", "10.0.2.2"),
            mlitBaseUrlOverride = baseUrl
        )
    }
}


/**
 * 国土交通省（MLIT）のWEBサーバー（川の防災情報 / 水文水質データベース）と直接HTTP/HTTPS通信を行うデータソースクラス。
 *
 * セキュリティのためのホワイトリストベースのURL検証（[MlitEndpointConfig]に基づく）、
 * メモリ枯乱・DDoSを防止するためのレスポンスサイズ制限（最大5MBまで）、
 * およびKtor HttpClientを使用したバイナリストリームダウンロードを実装しています。
 */
@Singleton
class DamNetworkDataSource(
    private val endpointConfig: MlitEndpointConfig,
    httpClient: HttpClient? = null
) {
    @Inject
    constructor(endpointConfig: MlitEndpointConfig) : this(endpointConfig, null)

    private val client = httpClient ?: createMlitHttpClient()

    /**
     * 指定されたURLからデータをバイト配列としてダウンロードします。
     *
     * 1. [endpointConfig]を使用してURLの検証とテスト用リダイレクト解決（ポート書き換え等）を事前に行います。
     * 2. KtorクライアントでHTTP GETリクエストを送信し、ヘッダーに専用User-Agentを付与します。
     * 3. データのContent-Lengthを検査し、最大許容量[MAX_RESPONSE_BYTES]を超える場合は即座に例外を投げます。
     * 4. チャンクごとにパケットを制限付きでストリーム処理し、合計容量が制限を超えた場合も処理をアボートします。
     *
     * @param url 取得対象のURL (例: https://www1.river.go.jp/...)
     * @return 成功時はファイルの全バイト配列、失敗時は例外を包んだ[Either]
     */
    suspend fun fetchBytes(url: String): Either<Throwable, ByteArray> = catchNonCancellationSuspend {
        val fetchUrl = endpointConfig.resolveFetchUrl(url)
        val response: HttpResponse = client.get(fetchUrl) {
            header("User-Agent", MLIT_USER_AGENT)
        }
        response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { contentLength ->
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw IOException("Response is too large: $contentLength bytes.")
            }
        }
        response.readBytesWithLimit()
    }

    
    private suspend fun HttpResponse.readBytesWithLimit(): ByteArray {
        val channel = bodyAsChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytes = 0L

        while (!channel.isClosedForRead) {
            val readBytes = channel.readAvailable(buffer, 0, buffer.size)
            if (readBytes == -1) break
            if (readBytes == 0) continue

            totalBytes += readBytes
            if (totalBytes > MAX_RESPONSE_BYTES) {
                throw IOException("Response is too large: $totalBytes bytes.")
            }
            output.write(buffer, 0, readBytes)
        }
        return output.toByteArray()
    }

    private companion object {
        private const val BUFFER_SIZE = 8 * 1024
        
        private const val MLIT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Mobile Safari/537.36"
    }
}
