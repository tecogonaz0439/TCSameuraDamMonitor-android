// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.SUDMONITOR_HOST
import net.tecogonaz.tcsameuradammonitor.util.catchNonCancellationSuspend
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class SudmonitorEndpointConfig(
    val allowedSchemes: Set<String>,
    val allowedHosts: Set<String>,
    private val baseUrlOverride: String? = null
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
        val override = baseUrlOverride ?: return url
        val uri = URI(url)
        if (uri.host?.lowercase() != SUDMONITOR_HOST) return url
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
        fun production(): SudmonitorEndpointConfig = SudmonitorEndpointConfig(
            allowedSchemes = setOf("https"),
            allowedHosts = setOf(SUDMONITOR_HOST)
        )

        fun localhostHttp(baseUrl: String): SudmonitorEndpointConfig = SudmonitorEndpointConfig(
            allowedSchemes = setOf("https", "http"),
            allowedHosts = setOf(SUDMONITOR_HOST, "localhost", "127.0.0.1", "10.0.2.2"),
            baseUrlOverride = baseUrl
        )
    }
}

data class SudmonitorDatResponse(
    val bytes: ByteArray,
    val damIdHeader: String?,
    val fetchedAtHeader: String?,
    val nextUpdateAtHeader: String? = null
)

@Singleton
class SudmonitorNetworkDataSource(
    private val endpointConfig: SudmonitorEndpointConfig,
    httpClient: HttpClient? = null
) {
    @Inject
    constructor(endpointConfig: SudmonitorEndpointConfig) : this(endpointConfig, null)

    private val client = httpClient ?: createMlitHttpClient()

    suspend fun fetchDat(url: String): Either<Throwable, SudmonitorDatResponse> = catchNonCancellationSuspend {
        val fetchUrl = endpointConfig.resolveFetchUrl(url)
        val response: HttpResponse = client.get(fetchUrl) {
            header("User-Agent", SUDMONITOR_USER_AGENT)
        }
        response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { contentLength ->
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw IOException("Response is too large: $contentLength bytes.")
            }
        }
        val bytes = response.readBytesWithLimit()
        SudmonitorDatResponse(
            bytes = bytes,
            damIdHeader = response.headers[HEADER_DAM_ID],
            fetchedAtHeader = response.headers[HEADER_FETCHED_AT],
            nextUpdateAtHeader = response.headers[HEADER_NEXT_UPDATE_AT]
        )
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
        private const val HEADER_DAM_ID = "X-TCS-Dam-Id"
        private const val HEADER_FETCHED_AT = "X-TCS-Fetched-At"
        private const val HEADER_NEXT_UPDATE_AT = "X-TCS-Next-Update-At"
        private val SUDMONITOR_USER_AGENT =
            "TCSameuraDamMonitor-Android/${BuildConfig.VERSION_NAME}"
    }
}
