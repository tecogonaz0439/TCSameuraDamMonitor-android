// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import arrow.core.Either
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.DamHistoricalData
import net.tecogonaz.tcsameuradammonitor.domain.model.HistoricalSearchMeta
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import java.net.URI
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.CancellationException
import javax.inject.Inject
import net.tecogonaz.tcsameuradammonitor.util.CsvEncodingUtils
import net.tecogonaz.tcsameuradammonitor.util.catchNonCancellation

/**
 * HTMLから `.dat` URLを抽出する内部診断用エラー。
 *
 * 既存の公開APIは nullable を返すため、この型はRepository内の分岐やテストで
 * 「候補なし」と「候補はあったが全て不許可」を区別するためだけに使う。
 */
internal sealed interface DatUrlParseError {
    data object NoDatLink : DatUrlParseError
    data class NoAllowedDatLink(
        val rejections: List<DatUrlCandidateRejection>
    ) : DatUrlParseError
}

/** `.dat` URL候補を採用しなかった理由。最初の不許可候補で止めず、次候補の走査は継続する。 */
internal sealed interface DatUrlCandidateRejection {
    data class MalformedHref(val href: String) : DatUrlCandidateRejection
    data class DisallowedUri(val href: String, val reason: String?) : DatUrlCandidateRejection
    data class NonDatPath(val href: String) : DatUrlCandidateRejection
}

/**
 * DAT parserの内部診断用エラー。
 *
 * public parser APIは既存どおり `Either<Throwable, T>` を返し、この型はadapterで
 * 既存メッセージのThrowableへ変換する。
 */
internal sealed interface DamParseError {
    data object NoRealtimeDataRows : DamParseError
    data object NoHistoricalDataRows : DamParseError
    data class Unexpected(val cause: Throwable) : DamParseError
}

private data class DatMetadata(
    val riverSystemName: String,
    val riverName: String,
    val stationName: String,
    val stationId: String
)

/**
 * 国土交通省（MLIT）から返されるHTMLおよび.dat観測データファイルをパース・解析するためのファイルパーサクラス。
 *
 * EUC-JPのHTMLからShift_JIS/UTF-8形式の.datデータファイルURLを抽出し、
 * 表データ（CSV風データ列）から各種数値（雨量、貯水量、流入量、放流量、貯水率等）を抽出し、
 * 欠測補完ルールに基づいて、ドメイン層のデータ構造へ変換します。
 */
class DamFileParser(
    private val datBaseUrl: String,
    private val datEndpointConfig: MlitEndpointConfig
) {
    /**
     * デフォルトのMLITベースURLを使用してパーサを初期化するセカンダリコンストラクタ。
     */
    @Inject
    constructor(datEndpointConfig: MlitEndpointConfig) : this(DAM_DATA_BASE_URL, datEndpointConfig)

    companion object {
        /** 流域平均雨量データのCSVカラムインデックス */
        const val COL_RAINFALL = 2
        
        /** 流域平均雨量属性（欠測等を示す記号）のCSVカラムインデックス */
        const val COL_RAINFALL_ATTR = 3
        
        /** 貯水量データのCSVカラムインデックス */
        const val COL_STORAGE_VOLUME = 4
        
        /** 貯水量属性のCSVカラムインデックス */
        const val COL_STORAGE_VOLUME_ATTR = 5
        
        /** 流入量データのCSVカラムインデックス */
        const val COL_INFLOW = 6
        
        /** 流入量属性のCSVカラムインデックス */
        const val COL_INFLOW_ATTR = 7
        
        /** 放流量データのCSVカラムインデックス */
        const val COL_OUTFLOW = 8
        
        /** 放流量属性のCSVカラムインデックス */
        const val COL_OUTFLOW_ATTR = 9
        
        /** 貯水率データのCSVカラムインデックス */
        const val COL_STORAGE_PERCENTAGE = 10
        
        /** 貯水率属性のCSVカラムインデックス */
        const val COL_STORAGE_PERCENTAGE_ATTR = 11
        
        /** 1時間あたりの観測行数（10分おき観測のため6行） */
        const val ROWS_PER_HOUR = 6
        
        /** 1日（24時間）あたりの観測行数（144行） */
        const val ROWS_PER_DAY = 144
        
        /** トレンド（上昇・下降・横ばい）判定の横ばい閾値 */
        const val TREND_EPSILON = 0.0001f
        
        private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
        
        private const val DAM_DATA_BASE_URL = "https://www1.river.go.jp"
        
        private const val JST_TIMEZONE_ID = "Asia/Tokyo"
    }

    /**
     * 国土交通省のデータ表示画面（EUC-JPでエンコードされたHTML）をパースし、
     * 静的観測データファイル（.dat）のダウンロードURLを正規化して抽出します。
     *
     * URLの抽出にあたっては、SSLセキュリティの観点から `http://` 接続を `https://` へ自動変換・正規化します。
     *
     * @param htmlBytes HTMLページの生バイトデータ
     * @return 抽出された正規化済みの.datファイルダウンロードURL（見つからない場合はnull）
     */
    fun parseHtmlForDatUrl(htmlBytes: ByteArray): String? =
        parseHtmlForDatUrlResult(htmlBytes).fold(
            ifLeft = { null },
            ifRight = { it }
        )

    /**
     * `.dat` URL抽出の診断用API。
     *
     * Repository公開契約とUI表示文言を変えないため、呼び出し側は必要に応じて既存の
     * localized `Throwable` や nullable へadapter変換する。
     */
    internal fun parseHtmlForDatUrlResult(htmlBytes: ByteArray): Either<DatUrlParseError, String> {
        val htmlEucJp = String(htmlBytes, Charset.forName("EUC-JP"))
        val regex = """href\s*=\s*['"]([^'"]+\.dat(?:\?[^'"]*)?)['"]""".toRegex(RegexOption.IGNORE_CASE)
        val matches = regex.findAll(htmlEucJp).toList()
        if (matches.isEmpty()) return Either.Left(DatUrlParseError.NoDatLink)

        val rejections = mutableListOf<DatUrlCandidateRejection>()
        for (matchResult in matches) {
            val href = matchResult.groups[1]?.value.orEmpty()
            val resolved = resolveDatHref(href)
            if (resolved == null) {
                rejections += DatUrlCandidateRejection.MalformedHref(href)
                continue
            }
            if (!resolved.path.lowercase(Locale.US).endsWith(".dat")) {
                rejections += DatUrlCandidateRejection.NonDatPath(href)
                continue
            }
            val allowResult = runCatching { datEndpointConfig.requireAllowed(resolved.toString()) }
            if (allowResult.isSuccess) return Either.Right(resolved.toString())
            rejections += DatUrlCandidateRejection.DisallowedUri(
                href = href,
                reason = allowResult.exceptionOrNull()?.message
            )
        }

        return Either.Left(DatUrlParseError.NoAllowedDatLink(rejections))
    }

    private fun resolveDatHref(href: String): URI? =
        runCatching {
            if (href.startsWith("://")) return@runCatching null
            if (href.startsWith("http://") || href.startsWith("https://")) {
                val normalizedHref = if (href.startsWith("http://") && "http" !in datEndpointConfig.allowedSchemes) {
                    href.replaceFirst("http://", "https://")
                } else {
                    href
                }
                URI(normalizedHref)
            } else {
                URI(datBaseUrl).resolve(href)
            }
        }.getOrNull()

    /**
     * 国土交通省のリアルタイム.dat（Shift_JIS形式、またはデバッグ用BOM付UTF-8形式）ファイルの中身（CSV風）をパースし、
     * 現在状態（最新行）および24時間のグラフ表示用履歴リスト（[DamHistoricalData]）を包含するドメインモデル[DamData]へ変換します。
     *
     * - 空文字や空白以外（例: `-`, `$`, `*` 等）は属性カラムを参照して「欠測値」として適切にフィルタリング・遡り処理を行います。
     * - 現在状態表示において、最新行が欠測・異常値の場合は、正常値が格納されている過去行まで遡って数値を補完採用します。
     * - 貯水率メッセージ分類用に、観測行を新しい順に遡って最後に正常な（欠測でない）貯水量を [DamData.storageVolumeForMessage] へ設定します。
     * - 各種トレンド（上昇・下降・横ばい）は最新2時間（12行）の変化率および前日・前週比の差分などを閾値[TREND_EPSILON]に基づいて判定します。
     * - MLIT時刻の `24:00` は翌日0時として扱い、`24:01` や `25:00` は無効行として扱います。
     *
     * @param csvBytes .datファイルの生バイトデータ
     * @param stationId 静的定義された観測所ID
     * @param stationName 静的定義された観測所名（例: "早明浦ダム"）
     * @param dataStartMillis 読み込み対象期間の開始タイムスタンプ（ミリ秒、オプション）
     * @param dataEndMillis 読み込み対象期間の終了タイムスタンプ（ミリ秒、オプション）
     * @return 成功時は構築された[DamData]ドメインモデル、失敗時は例外を包んだ[Either]
     */
    fun parseDatCsv(
        csvBytes: ByteArray,
        stationId: String,
        stationName: String,
        dataStartMillis: Long? = null,
        dataEndMillis: Long? = null
    ): Either<Throwable, DamData> =
        parseDatCsvResult(
            csvBytes = csvBytes,
            stationId = stationId,
            stationName = stationName,
            dataStartMillis = dataStartMillis,
            dataEndMillis = dataEndMillis
        ).mapLeft { error ->
            when (error) {
                DamParseError.NoRealtimeDataRows -> Exception("No data rows found in dat file.")
                DamParseError.NoHistoricalDataRows -> Exception("過去データdatファイルにデータ行が見つかりませんでした。")
                is DamParseError.Unexpected -> error.cause
            }
        }

    /** リアルタイムDAT parserの内部typed API。public APIは既存Throwable契約へ変換する。 */
    internal fun parseDatCsvResult(
        csvBytes: ByteArray,
        stationId: String,
        stationName: String,
        dataStartMillis: Long? = null,
        dataEndMillis: Long? = null
    ): Either<DamParseError, DamData> = try {
            val csvText = CsvEncodingUtils.decodeWithAutoEncoding(csvBytes)
            val rawLines = csvText.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val metadata = parseDatMetadata(
                rawLines = rawLines,
                fallbackStationName = stationName,
                fallbackStationId = stationId
            )

            val dataLines = realtimeDataLines(rawLines)
                .filter { line ->
                    val row = line.split(",")
                    val millis = parseRealtimeDataRowMillis(row) ?: return@filter false
                    (dataStartMillis == null || millis >= dataStartMillis) &&
                        (dataEndMillis == null || millis <= dataEndMillis)
                }

            if (dataLines.isEmpty()) {
                return Either.Left(DamParseError.NoRealtimeDataRows)
            }

            val reversedLines = dataLines.reversed()

            fun getFloat(row: List<String>, index: Int): Float? {
                if (index >= row.size) return null
                return row[index].trim().toFloatOrNull()
            }

            fun isAttributeNormal(row: List<String>, dataColIndex: Int): Boolean {
                val attrColIndex = dataColIndex + 1
                if (attrColIndex >= row.size) return true 
                val attr = row[attrColIndex]
                
                return attr == " " || attr == ""
            }

            val latestRowData = reversedLines.first().split(",")
            val updatedAt = "${latestRowData[0]} ${latestRowData[1]}"

            
            val isClosed = !isAttributeNormal(latestRowData, COL_RAINFALL) &&
                           !isAttributeNormal(latestRowData, COL_STORAGE_VOLUME) &&
                           !isAttributeNormal(latestRowData, COL_INFLOW) &&
                           !isAttributeNormal(latestRowData, COL_OUTFLOW) &&
                           !isAttributeNormal(latestRowData, COL_STORAGE_PERCENTAGE)

            fun getTrend(index: Int, latestValidRowIndex: Int?, offsetRows: Int = 1): Trend {
                if (latestValidRowIndex == null) return Trend.UNKNOWN
                val latestVal = getFloat(reversedLines[latestValidRowIndex].split(","), index) ?: return Trend.UNKNOWN
                
                val prevRelativeIndex = reversedLines.drop(latestValidRowIndex + offsetRows).indexOfFirst { 
                    val r = it.split(",")
                    isAttributeNormal(r, index) && getFloat(r, index) != null 
                }
                if (prevRelativeIndex == -1) return Trend.UNKNOWN
                
                val absolutePrevRowIndex = latestValidRowIndex + offsetRows + prevRelativeIndex
                val prevVal = getFloat(reversedLines[absolutePrevRowIndex].split(","), index) ?: return Trend.UNKNOWN
                
                return when {
                    latestVal > prevVal -> Trend.UP
                    latestVal < prevVal -> Trend.DOWN
                    else -> Trend.FLAT
                }
            }

            fun getTimestamp(rowIndex: Int?): String? {
                if (rowIndex == null || rowIndex == -1) return null
                val row = reversedLines[rowIndex].split(",")
                return "${row[0]} ${row[1]}"
            }

            
            
            fun resolveLatestInfo(index: Int): Pair<Float?, Int?> {
                if (isClosed) return null to null
                val latestRow = reversedLines.firstOrNull()?.split(",") ?: return null to null
                if (!isAttributeNormal(latestRow, index)) return null to null
                return getFloat(latestRow, index) to 0
            }

            fun getHourStartMillis(row: List<String>): Long? =
                parseRealtimeDataRowMillis(row)?.let { (it / MILLIS_PER_HOUR) * MILLIS_PER_HOUR }

            val latestHourStartMillis = getHourStartMillis(latestRowData)

            
            
            
            fun resolveStoragePercentageInfo(): Pair<Float?, Int?> {
                if (isClosed) return null to null
                val validRowIndex = reversedLines.indexOfFirst {
                    val r = it.split(",")
                    getHourStartMillis(r) == latestHourStartMillis &&
                        isAttributeNormal(r, COL_STORAGE_PERCENTAGE) && getFloat(r, COL_STORAGE_PERCENTAGE) != null
                }
                if (validRowIndex == -1) return null to null
                return getFloat(reversedLines[validRowIndex].split(","), COL_STORAGE_PERCENTAGE) to validRowIndex
            }

            val rainInfo = resolveLatestInfo(COL_RAINFALL)
            val catchmentRainfall = rainInfo.first
            val rainfallTrend = getTrend(COL_RAINFALL, rainInfo.second, 1)

            val svInfo = resolveLatestInfo(COL_STORAGE_VOLUME)
            val storageVolume = svInfo.first
            val storageVolumeTrend = getTrend(COL_STORAGE_VOLUME, svInfo.second, 1)

            
            val storageVolumeForMessage = reversedLines.firstNotNullOfOrNull { line ->
                val row = line.split(",")
                if (isAttributeNormal(row, COL_STORAGE_VOLUME)) getFloat(row, COL_STORAGE_VOLUME) else null
            }
            
            val inInfo = resolveLatestInfo(COL_INFLOW)
            val inflow = inInfo.first
            
            val outInfo = resolveLatestInfo(COL_OUTFLOW)
            val outflow = outInfo.first

            val spInfo = resolveStoragePercentageInfo()
            val storagePercentage = spInfo.first
            val storagePercentageTrend = getTrend(COL_STORAGE_PERCENTAGE, spInfo.second, ROWS_PER_HOUR)
            val storagePercentageTime = getTimestamp(spInfo.second)

            fun resolveChange(index: Int, latestValidRowIndex: Int?, offset: Int? = null): Pair<Float?, Trend> {
                if (latestValidRowIndex == null) return null to Trend.UNKNOWN
                val latestVal = getFloat(reversedLines[latestValidRowIndex].split(","), index) ?: return null to Trend.UNKNOWN
                
                val compareRowIndex = if (offset != null) {
                    val target = latestValidRowIndex + offset
                    if (target >= reversedLines.size) {
                        reversedLines.indices.reversed().firstOrNull { 
                            val r = reversedLines[it].split(",")
                            isAttributeNormal(r, index) && getFloat(r, index) != null 
                        }
                    } else {
                        val found = reversedLines.drop(target).indexOfFirst { 
                            val r = it.split(",")
                            isAttributeNormal(r, index) && getFloat(r, index) != null 
                        }
                        if (found != -1) target + found else null
                    }
                } else {
                    reversedLines.indices.reversed().firstOrNull { 
                        val r = reversedLines[it].split(",")
                        isAttributeNormal(r, index) && getFloat(r, index) != null 
                    }
                }

                if (compareRowIndex == null || compareRowIndex == latestValidRowIndex) return null to Trend.UNKNOWN
                
                val compareVal = getFloat(reversedLines[compareRowIndex].split(","), index) ?: return null to Trend.UNKNOWN
                val diff = latestVal - compareVal
                
                val trend = when {
                    diff > TREND_EPSILON -> Trend.UP
                    diff < -TREND_EPSILON -> Trend.DOWN
                    else -> Trend.FLAT
                }
                
                return diff to trend
            }

            val dayChangeInfo = resolveChange(COL_STORAGE_PERCENTAGE, spInfo.second, ROWS_PER_DAY)
            val weekChangeInfo = resolveChange(COL_STORAGE_PERCENTAGE, spInfo.second, null)

            
            val historicalDataList = mutableListOf<DamHistoricalData>()
            for (line in dataLines) {
                val row = line.split(",")
                if (row.size <= COL_STORAGE_PERCENTAGE) continue
                val time = "${row[0]} ${row[1]}"
                historicalDataList.add(
                    DamHistoricalData(
                        time = time,
                        catchmentAverageRainfall = if (isAttributeNormal(row, COL_RAINFALL)) getFloat(row, COL_RAINFALL) else null,
                        storagePercentage = if (isAttributeNormal(row, COL_STORAGE_PERCENTAGE)) getFloat(row, COL_STORAGE_PERCENTAGE) else null,
                        storageVolume = if (isAttributeNormal(row, COL_STORAGE_VOLUME)) getFloat(row, COL_STORAGE_VOLUME) else null,
                        inflow = if (isAttributeNormal(row, COL_INFLOW)) getFloat(row, COL_INFLOW) else null,
                        outflow = if (isAttributeNormal(row, COL_OUTFLOW)) getFloat(row, COL_OUTFLOW) else null
                    )
                )
            }

            Either.Right(DamData(
                observationStationId = metadata.stationId,
                observationStationName = metadata.stationName,
                riverSystemName = metadata.riverSystemName,
                riverName = metadata.riverName,
                updatedAt = updatedAt,
                catchmentAverageRainfall = catchmentRainfall,
                storageVolume = storageVolume,
                storageVolumeTrend = storageVolumeTrend,
                inflow = inflow,
                outflow = outflow,
                storagePercentage = storagePercentage,
                storagePercentageTrend = storagePercentageTrend,
                storagePercentageTime = storagePercentageTime,
                storagePercentageDayChange = dayChangeInfo.first,
                storagePercentageDayChangeTrend = dayChangeInfo.second,
                storagePercentageWeekChange = weekChangeInfo.first,
                storagePercentageWeekChangeTrend = weekChangeInfo.second,
                storageVolumeForMessage = storageVolumeForMessage,
                historicalData = historicalDataList
            ))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Either.Left(DamParseError.Unexpected(e))
    }

    private fun parseRealtimeDataRowMillis(row: List<String>): Long? {
        val date = row.getOrNull(0)?.trim().orEmpty()
        val time = row.getOrNull(1)?.trim().orEmpty()
        return net.tecogonaz.tcsameuradammonitor.util.TimeUtils.parseJstMillisAllow24Hour(
            "$date $time",
            "yyyy/MM/dd HH:mm"
        )
    }

    private fun realtimeDataLines(rawLines: List<String>): List<String> =
        rawLines.filter { !it.startsWith("#") && it.split(",").size > 5 }

    fun parseRealtimeDataTimes(csvBytes: ByteArray): Either<Throwable, List<Long>> = catchNonCancellation {
        val csvText = CsvEncodingUtils.decodeWithAutoEncoding(csvBytes)
        realtimeDataLines(
            csvText.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
        )
            .mapNotNull { line -> parseRealtimeDataRowMillis(line.split(",")) }
            .distinct()
            .sorted()
    }

    
    /**
     * 過去データDATを既存公開契約の `Either<Throwable, ...>` として返します。
     *
     * 内部では [parseHistoricalDatToDataListResult] でtyped errorを保持し、ここで既存の
     * Throwableメッセージへ変換する。
     */
    fun parseHistoricalDatToDataList(
        csvBytes: ByteArray,
        damConfigId: String,
        searchBgnDate: String,
        searchEndDate: String
    ): Either<Throwable, Pair<HistoricalSearchMeta, List<DamHistoricalData>>> =
        parseHistoricalDatToDataListResult(
            csvBytes = csvBytes,
            damConfigId = damConfigId,
            searchBgnDate = searchBgnDate,
            searchEndDate = searchEndDate
        ).mapLeft { error ->
            when (error) {
                DamParseError.NoRealtimeDataRows ->
                    Exception("No data rows found in dat file.")
                DamParseError.NoHistoricalDataRows ->
                    Exception("過去データdatファイルにデータ行が見つかりませんでした。")
                is DamParseError.Unexpected -> error.cause
            }
        }

    /**
     * 過去データDAT parserの内部typed API。
     *
     * MLITの履歴時刻は `yyyy/M/d HH:mm` で、`24:00` は有効、`24:01` / `25:00` は無効行として除外する。
     */
    internal fun parseHistoricalDatToDataListResult(
        csvBytes: ByteArray,
        damConfigId: String,
        searchBgnDate: String,
        searchEndDate: String
    ): Either<DamParseError, Pair<HistoricalSearchMeta, List<DamHistoricalData>>> = try {
        val csvText = CsvEncodingUtils.decodeWithAutoEncoding(csvBytes)

        val rawLines = csvText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val metadata = parseDatMetadata(rawLines)

        val dataLines = rawLines.filter { !it.startsWith("#") && it.split(",").size > COL_STORAGE_PERCENTAGE }

        val historicalDataList = dataLines.mapNotNull { line ->
            parseHistoricalDataRow(line.split(","))
        }

        if (historicalDataList.isEmpty()) {
            return Either.Left(DamParseError.NoHistoricalDataRows)
        }

        val meta = HistoricalSearchMeta(
            id = 0,
            observationStationId = metadata.stationId,
            observationStationName = metadata.stationName,
            riverSystemName = metadata.riverSystemName,
            riverName = metadata.riverName,
            damConfigId = damConfigId,
            searchBgnDate = searchBgnDate,
            searchEndDate = searchEndDate,
            fetchedAt = System.currentTimeMillis(),
            dataStartTimeStr = null,
            dataEndTimeStr = null,
            dataStartStoragePct = null,
            dataEndStoragePct = null,
            dataMinStoragePct = null,
            dataMaxStoragePct = null
        )

        Either.Right(meta to historicalDataList)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Either.Left(DamParseError.Unexpected(e))
    }

    private fun parseDatMetadata(
        rawLines: List<String>,
        fallbackStationName: String = "",
        fallbackStationId: String = ""
    ): DatMetadata {
        var parsedRiverSystem = ""
        var parsedRiverName = ""
        var parsedStationName = fallbackStationName
        var parsedStationId = fallbackStationId

        for (line in rawLines) {
            if (!line.contains(",")) continue
            val parts = line.split(",", limit = 2)
            when (parts[0]) {
                "水系名" -> parsedRiverSystem = parts[1]
                "河川名" -> parsedRiverName = parts[1]
                "観測所名" -> parsedStationName = parts[1]
                "観測所記号" -> parsedStationId = parts[1]
            }
        }

        return DatMetadata(
            riverSystemName = parsedRiverSystem,
            riverName = parsedRiverName,
            stationName = parsedStationName,
            stationId = parsedStationId
        )
    }

    private fun parseHistoricalDataRow(row: List<String>): DamHistoricalData? {
        if (row.size <= COL_STORAGE_PERCENTAGE) return null
        val time = "${row[0].trim()} ${row[1].trim()}"
        if (!isHistoricalDataTimeValid(time)) return null
        return DamHistoricalData(
            time = time,
            catchmentAverageRainfall = historicalFloatOrNull(row, COL_RAINFALL),
            storagePercentage = historicalFloatOrNull(row, COL_STORAGE_PERCENTAGE),
            storageVolume = historicalFloatOrNull(row, COL_STORAGE_VOLUME),
            inflow = historicalFloatOrNull(row, COL_INFLOW),
            outflow = historicalFloatOrNull(row, COL_OUTFLOW)
        )
    }

    private fun historicalFloatOrNull(row: List<String>, dataColIndex: Int): Float? {
        if (!isHistoricalAttributeNormal(row, dataColIndex)) return null
        return row.getOrNull(dataColIndex)?.trim()?.toFloatOrNull()
    }

    private fun isHistoricalDataTimeValid(time: String): Boolean =
        net.tecogonaz.tcsameuradammonitor.util.TimeUtils.parseJstMillisAllow24Hour(
            time,
            "yyyy/M/d HH:mm"
        ) != null

    private fun isHistoricalAttributeNormal(row: List<String>, dataColIndex: Int): Boolean {
        val attr = row.getOrNull(dataColIndex + 1)?.trim()
        return attr.isNullOrEmpty()
    }
}
