// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import net.tecogonaz.tcsameuradammonitor.data.source.remote.MlitEndpointConfig
import net.tecogonaz.tcsameuradammonitor.domain.model.DamData
import net.tecogonaz.tcsameuradammonitor.domain.model.Trend
import net.tecogonaz.tcsameuradammonitor.testutil.shouldBeLeft
import net.tecogonaz.tcsameuradammonitor.testutil.shouldBeRight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

/**
 * リアルタイムダムデータ（HTML/DATファイル）の解析ロジックを行う [DamFileParser] のユニットテストクラス。
 * 観測所ページ HTML からの `.dat` ファイルダウンロード URL の正規化および抽出ロジック
 * （セキュリティのための他ドメイン排除、ケースインセンシティブ、拡張子の厳格化など）、
 * DATファイル（CSV）内のメタデータ解析、最新行の抽出とトレンド（上昇、下落、横ばい）の計算ロジック、
 * 欠損値や属性記号（異常値/メンテナンスを示す記号）の処理、過去履歴データリストの構築機能などを網羅的にテストします。
 */
class DamFileParserTest {
    private val parser = DamFileParser(datEndpointConfig = MlitEndpointConfig.production())

    @Test
    fun parseHtmlForDatUrl_resolvesRelativeDatUrlForProductionHost() {
        val html = """<html><body><a href="/data/sample.dat">dat</a></body></html>"""
            .toByteArray(Charset.forName("EUC-JP"))

        assertEquals(
            "https://www1.river.go.jp/data/sample.dat",
            parser.parseHtmlForDatUrl(html)
        )
    }

    @Test
    fun parseHtmlForDatUrl_normalizesProductionHttpDatUrlToHttps() {
        val html = """<a href="http://www1.river.go.jp/data/sample.dat">dat</a>"""
            .toByteArray(Charset.forName("EUC-JP"))

        assertEquals(
            "https://www1.river.go.jp/data/sample.dat",
            parser.parseHtmlForDatUrl(html)
        )
    }

    @Test
    fun parseHtmlForDatUrl_rejectsForeignHost() {
        val foreignHost = """<a href="https://example.com/data/sample.dat">dat</a>"""
            .toByteArray(Charset.forName("EUC-JP"))

        assertNull(parser.parseHtmlForDatUrl(foreignHost))
    }

    @Test
    fun parseHtmlForDatUrl_rejectsNonDatExtension() {
        val nonDat = """<a href="https://www1.river.go.jp/data/sample.txt">txt</a>"""
            .toByteArray(Charset.forName("EUC-JP"))

        assertNull(parser.parseHtmlForDatUrl(nonDat))
    }

    @Test
    fun parseHtmlForDatUrl_acceptsHrefCaseQuoteWhitespaceAndQueryVariants() {
        val html = """
            <html>
              <A HREF = '/data/first.dat?KIND=1&ID=1368080700010'>dat</A>
              <a href="/data/second.dat">dat</a>
            </html>
        """.trimIndent().toByteArray(Charset.forName("EUC-JP"))

        assertEquals(
            "https://www1.river.go.jp/data/first.dat?KIND=1&ID=1368080700010",
            parser.parseHtmlForDatUrl(html)
        )
    }

    @Test
    fun parseHtmlForDatUrl_skipsForeignHostAndUsesNextAllowedDatLink() {
        val html = """
            <a href="https://example.com/data/foreign.dat">foreign</a>
            <a href="/data/allowed.dat">allowed</a>
        """.trimIndent().toByteArray(Charset.forName("EUC-JP"))

        assertEquals(
            "https://www1.river.go.jp/data/allowed.dat",
            parser.parseHtmlForDatUrl(html)
        )
    }

    @Test
    fun parseHtmlForDatUrl_allowsLocalhostHttpWhenConfiguredForMockWebServer() {
        val parser = DamFileParser(
            datBaseUrl = "http://localhost:8080/base/",
            datEndpointConfig = MlitEndpointConfig.localhostHttp()
        )
        val html = """<a href="current.dat">dat</a>""".toByteArray(Charset.forName("EUC-JP"))

        assertEquals(
            "http://localhost:8080/base/current.dat",
            parser.parseHtmlForDatUrl(html)
        )
    }

    
    @Test
    fun parseHtmlForDatUrl_malformedAbsoluteHrefWithSpace_returnsNull() {
        val html = """<a href="http://invalid host/path.dat">dat</a>"""
            .toByteArray(Charset.forName("EUC-JP"))

        assertNull(parser.parseHtmlForDatUrl(html))
    }

    
    @Test
    fun parseHtmlForDatUrl_malformedHrefMissingProtocol_returnsNull() {
        val html = """<a href="://missing-protocol/path.dat">dat</a>"""
            .toByteArray(Charset.forName("EUC-JP"))

        assertNull(parser.parseHtmlForDatUrl(html))
    }

    
    @Test
    fun parseHtmlForDatUrl_resolvesProtocolRelativeHref() {
        val html = """<a href="//www1.river.go.jp/data/sample.dat">dat</a>"""
            .toByteArray(Charset.forName("EUC-JP"))

        assertEquals(
            "https://www1.river.go.jp/data/sample.dat",
            parser.parseHtmlForDatUrl(html)
        )
    }

    
    @Test
    fun parseHtmlForDatUrl_emptyHref_returnsNull() {
        val html = """<a href="">dat</a>""".toByteArray(Charset.forName("EUC-JP"))

        assertNull(parser.parseHtmlForDatUrl(html))
    }

    
    @Test
    fun parseHtmlForDatUrl_malformedHrefWithBraces_returnsNull() {
        val html = """<a href="http://test/path{1}.dat">dat</a>"""
            .toByteArray(Charset.forName("EUC-JP"))

        assertNull(parser.parseHtmlForDatUrl(html))
    }

    
    @Test
    fun parseHtmlForDatUrl_validProductionHrefStillResolves() {
        val html = """<a href="https://www1.river.go.jp/data/sample.dat">dat</a>"""
            .toByteArray(Charset.forName("EUC-JP"))

        assertEquals(
            "https://www1.river.go.jp/data/sample.dat",
            parser.parseHtmlForDatUrl(html)
        )
    }

    @Test
    fun parseHtmlForDatUrlResult_returnsNoDatLinkForNonDatCandidates() {
        val html = """<a href="https://www1.river.go.jp/data/sample.txt">txt</a>"""
            .toByteArray(Charset.forName("EUC-JP"))

        val error = parser.parseHtmlForDatUrlResult(html).shouldBeLeft()

        assertEquals(DatUrlParseError.NoDatLink, error)
    }

    @Test
    fun parseHtmlForDatUrlResult_keepsRejectionReasonsAndUsesNextAllowedCandidate() {
        val html = """
            <a href="https://example.com/data/foreign.dat">foreign</a>
            <a href="://missing-protocol/path.dat">broken</a>
            <a href="/data/allowed.dat">allowed</a>
        """.trimIndent().toByteArray(Charset.forName("EUC-JP"))

        val url = parser.parseHtmlForDatUrlResult(html).shouldBeRight()

        assertEquals("https://www1.river.go.jp/data/allowed.dat", url)
    }

    @Test
    fun parseHtmlForDatUrlResult_returnsAllCandidateFailures() {
        val html = """
            <a href="https://example.com/data/foreign.dat">foreign</a>
            <a href="://missing-protocol/path.dat">broken</a>
        """.trimIndent().toByteArray(Charset.forName("EUC-JP"))

        val error = parser.parseHtmlForDatUrlResult(html).shouldBeLeft()

        assertTrue(error is DatUrlParseError.NoAllowedDatLink)
        val rejections = (error as DatUrlParseError.NoAllowedDatLink).rejections
        assertEquals(2, rejections.size)
        assertTrue(rejections[0] is DatUrlCandidateRejection.DisallowedUri)
        assertTrue(rejections[1] is DatUrlCandidateRejection.MalformedHref)
    }

    @Test
    fun parseDatCsv_usesMetadataAndLatestValues() {
        val data = parseRealtimeData(
            listOf(
                row("2026/05/15", "22:40", storagePercentage = "79.00"),
                row("2026/05/15", "22:50", storagePercentage = "79.10"),
                row("2026/05/15", "23:00", storagePercentage = "79.20"),
                row("2026/05/15", "23:10", storagePercentage = "79.30"),
                row("2026/05/15", "23:20", storagePercentage = "79.40"),
                row("2026/05/15", "23:30", storagePercentage = "79.50"),
                row("2026/05/15", "23:40", storagePercentage = "80.00")
            )
        )

        assertEquals("1368080700010", data.observationStationId)
        assertEquals("早明浦ダム", data.observationStationName)
        assertEquals("吉野川", data.riverSystemName)
        assertEquals("吉野川", data.riverName)
        assertEquals("2026/05/15 23:40", data.updatedAt)
        assertFloatEquals(80.0f, data.storagePercentage)
        assertEquals("2026/05/15 23:40", data.storagePercentageTime)
        assertEquals(7, data.historicalData.size)
    }

    @Test
    fun parseDatCsv_ignoresTruncatedRealtimeRows() {
        val data = parseRealtimeData(
            listOf(
                "2026/05/15,23:30,0.0,",
                row("2026/05/15", "23:40", storagePercentage = "80.00")
            )
        )

        assertEquals("2026/05/15 23:40", data.updatedAt)
        assertEquals(1, data.historicalData.size)
        assertFloatEquals(80.0f, data.storagePercentage)
    }

    @Test
    fun parseDatCsv_keeps24HourRowAsNextDayMidnightObservation() {
        val data = parseRealtimeData(
            listOf(
                row("2026/05/15", "23:50", storagePercentage = "79.90"),
                row("2026/05/15", "24:00", storagePercentage = "80.00"),
                row("2026/05/16", "00:10", storagePercentage = "80.10")
            )
        )

        assertEquals(3, data.historicalData.size)
        assertEquals("2026/05/15 24:00", data.historicalData[1].time)
        assertFloatEquals(80.0f, data.historicalData[1].storagePercentage)
    }

    @Test
    fun parseDatCsv_fallsBackTo24HourStoragePercentageForNextDayZeroHourRows() {
        val data = parseRealtimeData(
            listOf(
                row("2026/05/15", "23:50", storagePercentage = "79.90"),
                row("2026/05/15", "24:00", storagePercentage = "80.00"),
                row("2026/05/16", "00:10", storagePercentage = "0", storagePercentageAttr = "-"),
                row("2026/05/16", "00:50", storagePercentage = "0", storagePercentageAttr = "-")
            )
        )

        assertEquals("2026/05/16 00:50", data.updatedAt)
        assertFloatEquals(80.0f, data.storagePercentage)
        assertEquals("2026/05/15 24:00", data.storagePercentageTime)
    }

    @Test
    fun parseRealtimeDataTimes_parses24HourRowAsNextDayMidnight() {
        val result = parser.parseRealtimeDataTimes(
            realtimeDat(
                listOf(
                    row("2026/05/15", "23:50"),
                    row("2026/05/15", "24:00"),
                    row("2026/05/16", "00:10")
                )
            )
        ).fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { it }
        )

        assertEquals(3, result.size)
        assertEquals(
            net.tecogonaz.tcsameuradammonitor.util.TimeUtils.parseJstMillis("2026/05/16 00:00", "yyyy/MM/dd HH:mm"),
            result[1]
        )
    }

    @Test
    fun parseDatCsv_calculatesUpStoragePercentageTrendFromOneHourOffset() {
        val data = parseRealtimeData(
            listOf(
                row("2026/05/15", "22:40", storagePercentage = "79.00"),
                row("2026/05/15", "22:50", storagePercentage = "79.10"),
                row("2026/05/15", "23:00", storagePercentage = "79.20"),
                row("2026/05/15", "23:10", storagePercentage = "79.30"),
                row("2026/05/15", "23:20", storagePercentage = "79.40"),
                row("2026/05/15", "23:30", storagePercentage = "79.50"),
                row("2026/05/15", "23:40", storagePercentage = "80.00")
            )
        )

        assertEquals(Trend.UP, data.storagePercentageTrend)
    }

    @Test
    fun parseDatCsv_fallsBackToPreviousNormalStoragePercentageWithinSameHour() {
        
        val data = parseRealtimeData(
            listOf(
                row("2026/05/15", "23:10", storagePercentage = "79.50"),
                row("2026/05/15", "23:40", storagePercentage = "0", storagePercentageAttr = "-")
            )
        )

        assertEquals("2026/05/15 23:40", data.updatedAt)
        assertFloatEquals(79.5f, data.storagePercentage)
        assertEquals("2026/05/15 23:10", data.storagePercentageTime)
    }

    @Test
    fun parseDatCsv_doesNotFallbackAcrossHourBoundary() {
        
        val data = parseRealtimeData(
            listOf(
                row("2026/05/15", "22:40", storagePercentage = "79.50"),
                row("2026/05/15", "23:40", storagePercentage = "0", storagePercentageAttr = "-")
            )
        )

        assertEquals("2026/05/15 23:40", data.updatedAt)
        assertNull(data.storagePercentage)
        assertNull(data.storagePercentageTime)
    }

    @Test
    fun parseDatCsv_doesNotFallbackToPreviousDaySameHour() {
        
        val data = parseRealtimeData(
            listOf(
                row("2026/05/29", "17:50", storagePercentage = "79.50"),
                row("2026/05/30", "17:40", storagePercentage = "0", storagePercentageAttr = "-")
            )
        )

        assertEquals("2026/05/30 17:40", data.updatedAt)
        assertNull(data.storagePercentage)
        assertNull(data.storagePercentageTime)
    }

    @Test
    fun parseDatCsv_keepsLatestOnlyFieldsMissingWhenLatestAttributesAreAbnormal() {
        val data = parseRealtimeData(
            listOf(
                row(
                    date = "2026/05/15",
                    time = "22:40",
                    rainfall = "1.2",
                    storageVolume = "123000",
                    inflow = "20.0",
                    outflow = "15.0",
                    storagePercentage = "79.50"
                ),
                row(
                    date = "2026/05/15",
                    time = "23:40",
                    rainfall = "0",
                    rainfallAttr = "-",
                    storageVolume = "0",
                    storageVolumeAttr = "-",
                    inflow = "0",
                    inflowAttr = "-",
                    outflow = "0",
                    outflowAttr = "-",
                    storagePercentage = "79.60"
                )
            )
        )

        assertNull(data.catchmentAverageRainfall)
        assertNull(data.storageVolume)
        assertNull(data.inflow)
        assertNull(data.outflow)
        assertFloatEquals(79.6f, data.storagePercentage)
    }

    @Test
    fun parseDatCsv_reportsNoDataRows() {
        val result = parser.parseDatCsv(
            csvBytes = metadataOnlyDat(),
            stationId = "1368080700010",
            stationName = "早明浦ダム"
        )

        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(
                    "Expected no-data error message, but was <${error.message}>.",
                    error.message?.contains("No data rows") == true
                )
            },
            ifRight = { throw AssertionError("Expected failure, but got success.") }
        )
    }

    @Test
    fun parseDatCsvResult_returnsTypedNoRealtimeRowsErrorForMetadataOnlyFile() {
        val error = parser.parseDatCsvResult(
            csvBytes = metadataOnlyDat(),
            stationId = "1368080700010",
            stationName = "早明浦ダム"
        ).shouldBeLeft()

        assertEquals(DamParseError.NoRealtimeDataRows, error)
    }

    @Test
    fun parseDatCsv_adapterKeepsThrowableMessageForNoRealtimeRows() {
        val error = parser.parseDatCsv(
            csvBytes = metadataOnlyDat(),
            stationId = "1368080700010",
            stationName = "早明浦ダム"
        ).shouldBeLeft()

        assertEquals("No data rows found in dat file.", error.message)
    }

    @Test
    fun parseDatCsv_usesStationMetadataFromDatFile() {
        val bytes = realtimeDatText(
            metadata = listOf(
                "水系名,那賀川",
                "河川名,那賀川",
                "観測所名,長安口ダム",
                "観測所記号,1368080100010"
            ),
            dataRows = listOf(row("2026/05/15", "23:40", storagePercentage = "70.50"))
        ).toByteArray(Charset.forName("Shift_JIS"))

        val data = parser.parseDatCsv(
            csvBytes = bytes,
            stationId = "fallback-id",
            stationName = "fallback-name"
        ).fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { it }
        )

        assertEquals("1368080100010", data.observationStationId)
        assertEquals("長安口ダム", data.observationStationName)
        assertEquals("那賀川", data.riverSystemName)
        assertEquals("那賀川", data.riverName)
    }

    @Test
    fun parseDatCsv_decodesShiftJisNonAsciiMetadata() {
        val bytes = realtimeDatText(
            metadata = listOf(
                "水系名,四万十川",
                "河川名,中筋川",
                "観測所名,中筋川ダム",
                "観測所記号,1398080300010"
            ),
            dataRows = listOf(row("2026/05/15", "23:40", storagePercentage = "70.50"))
        ).toByteArray(Charset.forName("Shift_JIS"))

        val data = parser.parseDatCsv(
            csvBytes = bytes,
            stationId = "fallback-id",
            stationName = "fallback-name"
        ).fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { it }
        )

        assertEquals("1398080300010", data.observationStationId)
        assertEquals("中筋川ダム", data.observationStationName)
        assertEquals("四万十川", data.riverSystemName)
        assertEquals("中筋川", data.riverName)
        assertFloatEquals(70.5f, data.storagePercentage)
    }

    @Test
    fun parseDatCsv_skipsInterspersedCommentLines() {
        val data = parseRealtimeData(
            realtimeDatText(
                listOf(
                    row("2026/05/15", "23:30", storagePercentage = "79.90"),
                    "# comment between data rows should be ignored",
                    row("2026/05/15", "23:40", storagePercentage = "80.10")
                )
            ).toByteArray(Charset.forName("Shift_JIS"))
        )

        assertEquals("2026/05/15 23:40", data.updatedAt)
        assertFloatEquals(80.1f, data.storagePercentage)
        assertEquals(2, data.historicalData.size)
    }

    @Test
    fun parseDatCsv_acceptsUtf8Bom() {
        val text = realtimeDatText(
            listOf(
                row("2026/05/15", "23:40", storagePercentage = "81.25")
            )
        )
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)

        val data = parseRealtimeData(bytes)

        assertEquals("早明浦ダム", data.observationStationName)
        assertFloatEquals(81.25f, data.storagePercentage)
    }

    @Test
    fun parseDatCsv_utf8BomWithBrokenBody_returnsFailure() {
        val brokenUtf8Dat = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            byteArrayOf(0x80.toByte(), 0x81.toByte(), 0x82.toByte(), 0x0A)

        val result = parser.parseDatCsv(
            csvBytes = brokenUtf8Dat,
            stationId = "1368080700010",
            stationName = "早明浦ダム"
        )

        assertTrue(result.isLeft())
        result.fold(
            ifLeft = { error ->
                assertTrue(
                    "Expected broken UTF-8 dat to fail as no data rows, but was <${error.message}>.",
                    error.message?.contains("No data rows") == true
                )
            },
            ifRight = { throw AssertionError("Expected failure, but got success.") }
        )
    }

    @Test
    fun parseDatCsv_calculatesDownStoragePercentageTrendFromOneHourOffset() {
        val data = parseRealtimeData(
            listOf(
                row("2026/05/15", "22:40", storagePercentage = "80.00"),
                row("2026/05/15", "22:50", storagePercentage = "79.90"),
                row("2026/05/15", "23:00", storagePercentage = "79.80"),
                row("2026/05/15", "23:10", storagePercentage = "79.70"),
                row("2026/05/15", "23:20", storagePercentage = "79.60"),
                row("2026/05/15", "23:30", storagePercentage = "79.50"),
                row("2026/05/15", "23:40", storagePercentage = "79.00")
            )
        )

        assertEquals(Trend.DOWN, data.storagePercentageTrend)
    }

    @Test
    fun parseDatCsv_calculatesFlatStoragePercentageTrendFromOneHourOffset() {
        val data = parseRealtimeData(
            listOf(
                row("2026/05/15", "22:40", storagePercentage = "80.00"),
                row("2026/05/15", "22:50", storagePercentage = "80.20"),
                row("2026/05/15", "23:00", storagePercentage = "80.30"),
                row("2026/05/15", "23:10", storagePercentage = "80.20"),
                row("2026/05/15", "23:20", storagePercentage = "80.10"),
                row("2026/05/15", "23:30", storagePercentage = "80.05"),
                row("2026/05/15", "23:40", storagePercentage = "80.00")
            )
        )

        assertEquals(Trend.FLAT, data.storagePercentageTrend)
    }

    @Test
    fun parseDatCsv_reportsUnknownStoragePercentageTrendWhenOffsetRowIsMissing() {
        val data = parseRealtimeData(
            listOf(row("2026/05/15", "23:40", storagePercentage = "80.00"))
        )

        assertEquals(Trend.UNKNOWN, data.storagePercentageTrend)
    }

    @Test
    fun parseDatCsv_reportsUnknownStoragePercentageTrendWhenFallbackRowHasNoOffsetRow() {
        val data = parseRealtimeData(
            listOf(
                row("2026/05/15", "22:40", storagePercentage = "79.50"),
                row("2026/05/15", "23:40", storagePercentage = "0", storagePercentageAttr = "-")
            )
        )

        assertEquals(Trend.UNKNOWN, data.storagePercentageTrend)
    }

    @Test
    fun parseDatCsv_storagePercentageDayChangeWithinEpsilonIsFlat() {
        val data = parseRealtimeData(
            listOf(
                row("2026/05/15", "22:40", storagePercentage = "80.00000"),
                row("2026/05/15", "23:40", storagePercentage = "80.00005")
            )
        )

        assertEquals(Trend.FLAT, data.storagePercentageDayChangeTrend)
    }

    @Test
    fun parseDatCsv_storagePercentageDayChangeUsesOneDayOffsetWhenAvailable() {
        val previousDayRows = (0 until 144).map { index ->
            row("2026/05/14", "%02d:%02d".format(index / 6, (index % 6) * 10), storagePercentage = "79.00")
        }
        val data = parseRealtimeData(
            previousDayRows + row("2026/05/15", "00:00", storagePercentage = "80.50")
        )

        assertFloatEquals(1.5f, data.storagePercentageDayChange)
        assertEquals(Trend.UP, data.storagePercentageDayChangeTrend)
    }

    @Test
    fun parseDatCsv_storagePercentageWeekChangeUsesOldestValidRow() {
        val data = parseRealtimeData(
            listOf(
                row("2026/05/08", "23:40", storagePercentage = "78.50"),
                row("2026/05/15", "23:40", storagePercentage = "80.00")
            )
        )

        assertFloatEquals(1.5f, data.storagePercentageWeekChange)
        assertEquals(Trend.UP, data.storagePercentageWeekChangeTrend)
    }

    @Test
    fun parseDatCsv_closedLatestRowDoesNotFallbackToOlderStoragePercentage() {
        val data = parseRealtimeData(
            listOf(
                row("2026/05/15", "22:40", storagePercentage = "79.50"),
                row(
                    date = "2026/05/15",
                    time = "23:40",
                    rainfall = "0",
                    rainfallAttr = "-",
                    storageVolume = "0",
                    storageVolumeAttr = "-",
                    inflow = "0",
                    inflowAttr = "-",
                    outflow = "0",
                    outflowAttr = "-",
                    storagePercentage = "0",
                    storagePercentageAttr = "-"
                )
            )
        )

        assertNull(data.storagePercentage)
        assertNull(data.storagePercentageTime)
        assertEquals(Trend.UNKNOWN, data.storagePercentageTrend)
    }

    private fun parseRealtimeData(dataRows: List<String>): DamData =
        parseRealtimeData(realtimeDat(dataRows))

    private fun parseRealtimeData(csvBytes: ByteArray): DamData =
        parser.parseDatCsv(
            csvBytes = csvBytes,
            stationId = "1368080700010",
            stationName = "早明浦ダム"
        ).fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { it }
        )

    private fun assertFloatEquals(expected: Float, actual: Float?) {
        if (actual == null) {
            throw AssertionError("Expected <$expected> but was null.")
        }
        assertEquals(expected, actual, FLOAT_DELTA)
    }

    private fun realtimeDat(dataRows: List<String>): ByteArray =
        realtimeDatText(dataRows).toByteArray(Charset.forName("Shift_JIS"))

    private fun realtimeDatText(
        dataRows: List<String>,
        metadata: List<String> = listOf(
            "水系名,吉野川",
            "河川名,吉野川",
            "観測所名,早明浦ダム",
            "観測所記号,1368080700010"
        )
    ): String =
        (
            metadata + "# 日付,時刻,流域平均雨量,属性,貯水量,属性,流入量,属性,放流量,属性,貯水率,属性" + dataRows
        ).joinToString("\n")

    private fun metadataOnlyDat(): ByteArray =
        listOf(
            "水系名,吉野川",
            "河川名,吉野川",
            "観測所名,早明浦ダム",
            "観測所記号,1368080700010"
        ).joinToString("\n").toByteArray(Charset.forName("Shift_JIS"))

    private fun row(
        date: String,
        time: String,
        rainfall: String = "0",
        rainfallAttr: String = "",
        storageVolume: String = "100000",
        storageVolumeAttr: String = "",
        inflow: String = "10.0",
        inflowAttr: String = "",
        outflow: String = "9.0",
        outflowAttr: String = "",
        storagePercentage: String = "80.00",
        storagePercentageAttr: String = ""
    ): String =
        listOf(
            date,
            time,
            rainfall,
            rainfallAttr,
            storageVolume,
            storageVolumeAttr,
            inflow,
            inflowAttr,
            outflow,
            outflowAttr,
            storagePercentage,
            storagePercentageAttr
        ).joinToString(",")

    private companion object {
        private const val FLOAT_DELTA = 0.001f
    }
}
