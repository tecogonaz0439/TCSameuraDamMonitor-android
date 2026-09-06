// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.SUDMONITOR_BASE_URL
import net.tecogonaz.tcsameuradammonitor.domain.model.SUDMONITOR_SUPPORTED_DAM_IDS
import net.tecogonaz.tcsameuradammonitor.util.catchNonCancellationSuspend
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * sudmonitor の履歴ファイル（月次・日次）を取得するクライアント。
 *
 * 月次ファイル（`{damId}_{YYYYMMDDHHMM}_{YYYYMMDDHHMM}.dat`）と日次ファイル（`latest.dat`）を
 * sudmonitor から取得し、バイト列と応答ヘッダー（X-TCS-Dam-Id / X-TCS-History-*）を返す。
 * 404 は「取得対象外（ファイルが存在しない）」を意味するため [Right] の null として扱い、
 * その結果は TTL 付きのプロセス内キャッシュ（負の結果キャッシュ）に保存される。
 */
data class SudmonitorHistoricalResult(
    val bytes: ByteArray,
    val damIdHeader: String?,
    val since: String?,
    val until: String?,
    val nextUpdateAtHeader: String? = null,
    val fetchedAtHeader: String? = null
)

@Singleton
class SudmonitorHistoricalClient(
    private val endpointConfig: SudmonitorEndpointConfig,
    httpClient: HttpClient? = null,
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Inject
    constructor(endpointConfig: SudmonitorEndpointConfig) : this(endpointConfig, null)

    private val client = httpClient ?: createMlitHttpClient()
    private val cacheMutex = Mutex()
    private val negativeCache = LinkedHashMap<CacheKey, CacheEntry>()

    /**
     * 指定されたダムの月次履歴ファイルを取得する。
     *
     * [SUDMONITOR_SUPPORTED_DAM_IDS] に含まれない damId はネットワークアクセスせず
     * [Right] の null を返す。404（ファイル無し）も [Right] の null となり、
     * その結果は [CACHE_TTL_MILLIS] の間プロセス内キャッシュされる。
     *
     * @param damId 対象ダムの観測所ID
     * @param month 取得対象月
     * @return 成功時はファイル内容とヘッダー情報、ファイルが無い場合は null
     */
    suspend fun fetchMonthly(damId: String, month: YearMonth): Either<Throwable, SudmonitorHistoricalResult?> =
        catchNonCancellationSuspend {
            if (damId !in SUDMONITOR_SUPPORTED_DAM_IDS) return@catchNonCancellationSuspend null
            val key = CacheKey(damId, CacheKind.MONTHLY, month.toString())
            if (isCachedNegative(key)) return@catchNonCancellationSuspend null
            val url = "$SUDMONITOR_BASE_URL/v1/history/$damId/${monthlyFileName(damId, month)}"
            fetchFile(url, damId, key)
        }

    /**
     * 指定されたダムの日次履歴ファイル（latest.dat）を取得する。
     *
     * [SUDMONITOR_SUPPORTED_DAM_IDS] に含まれない damId はネットワークアクセスせず
     * [Right] の null を返す。404（ファイル無し）も [Right] の null となり、
     * その結果は [CACHE_TTL_MILLIS] の間プロセス内キャッシュされる。
     *
     * @param damId 対象ダムの観測所ID
     * @return 成功時はファイル内容とヘッダー情報、ファイルが無い場合は null
     */
    suspend fun fetchLatest(damId: String): Either<Throwable, SudmonitorHistoricalResult?> =
        catchNonCancellationSuspend {
            if (damId !in SUDMONITOR_SUPPORTED_DAM_IDS) return@catchNonCancellationSuspend null
            val key = CacheKey(damId, CacheKind.LATEST, null)
            if (isCachedNegative(key)) return@catchNonCancellationSuspend null
            val url = "$SUDMONITOR_BASE_URL/v1/history/$damId/latest.dat"
            fetchFile(url, damId, key)
        }

    /**
     * 履歴ファイルを1回のGETで取得する。HTTP 200 のみ成功とし、404 は null（取得対象外）として
     * 負の結果キャッシュに記録する。その他の非200（3xx / 5xx 等）は例外（Left）として扱う。
     * 非2xx は [createMlitHttpClient] の `expectSuccess=true` により Ktor の [ResponseException] として
     * スローされるため、その例外のステータスで404を判定する。
     */
    private suspend fun fetchFile(
        url: String,
        requestedDamId: String,
        key: CacheKey
    ): SudmonitorHistoricalResult? {
        val fetchUrl = endpointConfig.resolveFetchUrl(url)
        val response: HttpResponse = try {
            client.get(fetchUrl) {
                header("User-Agent", SUDMONITOR_USER_AGENT)
            }
        } catch (error: ResponseException) {
            if (error.response.status == HttpStatusCode.NotFound) {
                putNegative(key)
                return null
            }
            throw error
        }
        if (response.status != HttpStatusCode.OK) {
            throw IOException("Unexpected HTTP status ${response.status.value} for $url.")
        }
        val damIdHeader = response.headers[HEADER_DAM_ID]
        if (damIdHeader != null && damIdHeader != requestedDamId) {
            throw IOException("X-TCS-Dam-Id mismatch: expected $requestedDamId but was $damIdHeader.")
        }
        response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { contentLength ->
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw IOException("Response is too large: $contentLength bytes.")
            }
        }
        val bytes = response.readBytesWithLimit()
        val sinceHeader = response.headers[HEADER_HISTORY_START] ?: response.headers[HEADER_HISTORY_SINCE]
        val untilHeader = response.headers[HEADER_HISTORY_END] ?: response.headers[HEADER_HISTORY_UNTIL]
        return SudmonitorHistoricalResult(
            bytes = bytes,
            damIdHeader = damIdHeader,
            since = sinceHeader,
            until = untilHeader,
            nextUpdateAtHeader = response.headers[HEADER_NEXT_UPDATE_AT],
            fetchedAtHeader = response.headers[HEADER_FETCHED_AT]
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

    /** TTL が有効な負の結果キャッシュのヒット判定。 */
    private suspend fun isCachedNegative(key: CacheKey): Boolean = cacheMutex.withLock {
        val entry = negativeCache[key] ?: return@withLock false
        if (clock() - entry.cachedAtMillis > CACHE_TTL_MILLIS) {
            negativeCache.remove(key)
            return@withLock false
        }
        true
    }

    /** 負の結果キャッシュに記録し、サイズ超過時は古い順に evict する。 */
    private suspend fun putNegative(key: CacheKey) {
        cacheMutex.withLock {
            negativeCache[key] = CacheEntry(cachedAtMillis = clock())
            while (negativeCache.size > MAX_CACHE_SIZE) {
                val oldest = negativeCache.keys.firstOrNull() ?: break
                negativeCache.remove(oldest)
            }
        }
    }

    companion object {
        /**
         * 月次履歴ファイル名を決定的に生成する。
         * 開始は当月1日01:00、終了は末日24:00（2400表記）とする。
         * 例: 2026-07 → `1368080700010_202607010100_202607312400.dat`（うるう年2月は `202402292400.dat` になる）。
         */
        fun monthlyFileName(damId: String, month: YearMonth): String {
            val start = month.atDay(1).atTime(1, 0)
            val endDay = month.atEndOfMonth()
            return "${damId}_${start.format(DATE_TIME_FORMATTER)}_${endDay.format(DAY_FORMATTER)}2400.dat"
        }

        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        private val DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
        private const val BUFFER_SIZE = 8 * 1024
        private const val CACHE_TTL_MILLIS = 5L * 60L * 1000L
        private const val MAX_CACHE_SIZE = 128
        private const val HEADER_DAM_ID = "X-TCS-Dam-Id"
        private const val HEADER_HISTORY_START = "X-TCS-History-Start"
        private const val HEADER_HISTORY_END = "X-TCS-History-End"
        private const val HEADER_HISTORY_SINCE = "X-TCS-History-Since"
        private const val HEADER_HISTORY_UNTIL = "X-TCS-History-Until"
        private const val HEADER_NEXT_UPDATE_AT = "X-TCS-Next-Update-At"
        private const val HEADER_FETCHED_AT = "X-TCS-Fetched-At"
        private val SUDMONITOR_USER_AGENT =
            "TCSameuraDamMonitor-Android/${BuildConfig.VERSION_NAME}"
    }

    private enum class CacheKind { MONTHLY, LATEST }

    private data class CacheKey(
        val damId: String,
        val kind: CacheKind,
        val month: String?
    )

    private data class CacheEntry(
        val cachedAtMillis: Long
    )
}
