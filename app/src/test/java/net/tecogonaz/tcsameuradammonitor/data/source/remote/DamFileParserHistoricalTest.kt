// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.remote

import net.tecogonaz.tcsameuradammonitor.data.source.remote.MlitEndpointConfig
import net.tecogonaz.tcsameuradammonitor.testutil.shouldBeLeft
import net.tecogonaz.tcsameuradammonitor.testutil.shouldBeRight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

/**
 * 過去データ（DATファイル）のパース処理を行う [DamFileParser] のユニットテストクラス。
 * MLITから取得した Shift_JIS エンコーディングの過去CSVデータのパース、ヘッダーメタデータ
 * （水系名、河川名、観測所名、観測所ID）の抽出、UTF-8 BOMの対応、日付と時間のパースおよび24:00の繰り上げ処理、
 * 属性記号（異常値など）に基づくデータのフィルタリングと `null` 化、無効な行のスキップ処理などが
 * 正確に機能することを検証します。
 */
class DamFileParserHistoricalTest {
    private val parser = DamFileParser(datEndpointConfig = MlitEndpointConfig.production())

    @Test
    fun parseHistoricalDatToDataList_parsesMetadataAndHourlyRows() {
        val rows = (1..24).map { hour ->
            historicalRow(
                date = "2026/5/1",
                time = "%02d:00".format(hour),
                rainfall = (hour / 10f).toString(),
                storageVolume = (72000 + hour).toString(),
                inflow = (10 + hour).toString(),
                outflow = (9 + hour).toString(),
                storagePercentage = (60 + hour / 10f).toString()
            )
        }

        val result = parser.parseHistoricalDatToDataList(
            csvBytes = historicalDat(rows),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        )

        assertTrue(result.isRight())
        result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { (meta, dataList) ->
                assertEquals("吉野川", meta.riverSystemName)
                assertEquals("吉野川", meta.riverName)
                assertEquals("早明浦ダム", meta.observationStationName)
                assertEquals("1368080700010", meta.observationStationId)
                assertEquals("1368080700010", meta.damConfigId)
                assertEquals("20260501", meta.searchBgnDate)
                assertEquals("20260501", meta.searchEndDate)
                assertEquals(24, dataList.size)
                assertEquals("2026/5/1 01:00", dataList.first().time)
                assertEquals("2026/5/1 24:00", dataList.last().time)
                assertNotNull(dataList.first().storagePercentage)
                assertEquals(60.1f, dataList.first().storagePercentage!!, 0.001f)
                assertNotNull(dataList.last().storageVolume)
                assertEquals(72024f, dataList.last().storageVolume!!, 0.001f)
            }
        )
    }

    @Test
    fun parseHistoricalDatToDataList_acceptsUtf8Bom() {
        val text = historicalDatText(
            listOf(
                historicalRow(
                    date = "2026/5/1",
                    time = "01:00",
                    storagePercentage = "61.2"
                )
            )
        )
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)

        val result = parser.parseHistoricalDatToDataList(bytes, "1368080700010", "20260501", "20260501")

        assertTrue(result.isRight())
        result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { (_, dataList) ->
                assertEquals(1, dataList.size)
                assertNotNull(dataList.single().storagePercentage)
                assertEquals(61.2f, dataList.single().storagePercentage!!, 0.001f)
            }
        )
    }

    @Test
    fun parseHistoricalDatToDataList_returnsLeftForMetadataOnlyFile() {
        val result = parser.parseHistoricalDatToDataList(
            csvBytes = historicalDat(emptyList()),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        )

        assertTrue(result.isLeft())
    }

    @Test
    fun parseHistoricalDatToDataListResult_returnsTypedNoRowsErrorForMetadataOnlyFile() {
        val error = parser.parseHistoricalDatToDataListResult(
            csvBytes = historicalDat(emptyList()),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        ).shouldBeLeft()

        assertEquals(DamParseError.NoHistoricalDataRows, error)
    }

    @Test
    fun parseHistoricalDatToDataListResult_keepsHistoricalMetadataAndRows() {
        val (meta, dataList) = parser.parseHistoricalDatToDataListResult(
            csvBytes = historicalDat(
                listOf(
                    historicalRow(date = "2026/5/1", time = "01:00", storagePercentage = "61.2")
                )
            ),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        ).shouldBeRight()

        assertEquals("吉野川", meta.riverSystemName)
        assertEquals("吉野川", meta.riverName)
        assertEquals("早明浦ダム", meta.observationStationName)
        assertEquals("1368080700010", meta.observationStationId)
        assertEquals(1, dataList.size)
        assertEquals("2026/5/1 01:00", dataList.single().time)
        assertEquals(61.2f, dataList.single().storagePercentage!!, 0.001f)
    }

    @Test
    fun parseHistoricalDatToDataList_returnsLeftForGarbageBytes() {
        val result = parser.parseHistoricalDatToDataList(
            csvBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        )

        assertTrue(result.isLeft())
    }

    @Test
    fun parseHistoricalDatToDataList_utf8BomWithBrokenBody_returnsLeft() {
        val brokenUtf8Dat = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            byteArrayOf(0x80.toByte(), 0x81.toByte(), 0x82.toByte(), 0x0A)

        val result = parser.parseHistoricalDatToDataList(
            csvBytes = brokenUtf8Dat,
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        )

        assertTrue(result.isLeft())
    }

    @Test
    fun parseHistoricalDatToDataList_withoutMetadata_keepsEmptyMetadataFields() {
        val text = listOf(
            "#年月日,時刻,流域平均雨量,雨量属性,貯水量,貯水量属性,流入量,流入量属性,放流量,放流量属性,貯水率,貯水率属性",
            historicalRow(date = "2026/5/1", time = "01:00", storagePercentage = "61.2")
        ).joinToString("\n")

        val result = parser.parseHistoricalDatToDataList(
            csvBytes = text.toByteArray(Charset.forName("Shift_JIS")),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        )

        assertTrue(result.isRight())
        result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { (meta, dataList) ->
                assertEquals("", meta.observationStationId)
                assertEquals("", meta.observationStationName)
                assertEquals("", meta.riverSystemName)
                assertEquals("", meta.riverName)
                assertEquals(1, dataList.size)
            }
        )
    }

    @Test
    fun parseHistoricalDatToDataList_withoutHeaderCommentLine_dropsHeaderLikeRowAndParsesDataRows() {
        val text = listOf(
            "水系名,吉野川",
            "河川名,吉野川",
            "観測所名,早明浦ダム",
            "観測所記号,1368080700010",
            "年月日,時刻,流域平均雨量,雨量属性,貯水量,貯水量属性,流入量,流入量属性,放流量,放流量属性,貯水率,貯水率属性",
            historicalRow(date = "2026/5/1", time = "01:00", storagePercentage = "61.2")
        ).joinToString("\n")

        val result = parser.parseHistoricalDatToDataList(
            csvBytes = text.toByteArray(Charset.forName("Shift_JIS")),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        )

        assertTrue(result.isRight())
        result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { (meta, dataList) ->
                assertEquals("早明浦ダム", meta.observationStationName)
                assertEquals(1, dataList.size)
                assertEquals("2026/5/1 01:00", dataList.single().time)
                assertEquals(61.2f, dataList.single().storagePercentage!!, 0.001f)
            }
        )
    }

    @Test
    fun parseHistoricalDatToDataList_abnormalAttributesProduceNullValuesPerField() {
        val result = parser.parseHistoricalDatToDataList(
            csvBytes = historicalDat(
                listOf(
                    historicalRow(
                        date = "2026/5/1",
                        time = "01:00",
                        rainfall = "1.0",
                        rainfallAttr = "-",
                        storageVolume = "72000",
                        storageVolumeAttr = "9999",
                        inflow = "10.0",
                        inflowAttr = "\$",
                        outflow = "9.0",
                        outflowAttr = "-",
                        storagePercentage = "61.2",
                        storagePercentageAttr = "-"
                    )
                )
            ),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        )

        assertTrue(result.isRight())
        result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { (_, dataList) ->
                val row = dataList.single()
                assertNull(row.catchmentAverageRainfall)
                assertNull(row.storageVolume)
                assertNull(row.inflow)
                assertNull(row.outflow)
                assertNull(row.storagePercentage)
            }
        )
    }

    @Test
    fun parseHistoricalDatToDataList_treatsBlankSpaceAndMissingAttributesAsNormal() {
        val missingStoragePercentageAttrRow = listOf(
            "2026/5/1",
            "03:00",
            "0.3",
            "",
            "72030",
            " ",
            "13.0",
            "",
            "12.0",
            " ",
            "63.0"
        ).joinToString(",")

        val result = parser.parseHistoricalDatToDataList(
            csvBytes = historicalDat(
                listOf(
                    historicalRow(
                        date = "2026/5/1",
                        time = "01:00",
                        rainfall = "0.1",
                        rainfallAttr = "",
                        storageVolume = "72010",
                        storageVolumeAttr = " ",
                        inflow = "11.0",
                        inflowAttr = "",
                        outflow = "10.0",
                        outflowAttr = " ",
                        storagePercentage = "61.0",
                        storagePercentageAttr = ""
                    ),
                    missingStoragePercentageAttrRow
                )
            ),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        )

        assertTrue(result.isRight())
        result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { (_, dataList) ->
                assertEquals(2, dataList.size)
                assertEquals(61.0f, dataList[0].storagePercentage!!, 0.001f)
                assertEquals(63.0f, dataList[1].storagePercentage!!, 0.001f)
                assertEquals(72030f, dataList[1].storageVolume!!, 0.001f)
            }
        )
    }

    @Test
    fun parseHistoricalDatToDataList_dropsMalformedTimeRowsAndRejectsAllInvalidRows() {
        val result = parser.parseHistoricalDatToDataList(
            csvBytes = historicalDat(
                listOf(
                    historicalRow(date = "2026/5/1", time = "", storagePercentage = "60.0"),
                    historicalRow(date = "2026/5/1", time = "xx:00", storagePercentage = "61.0"),
                    historicalRow(date = "2026/5/1", time = "24:10", storagePercentage = "62.0"),
                    historicalRow(date = "2026/5/1", time = "24:00", storagePercentage = "63.0")
                )
            ),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        )

        assertTrue(result.isRight())
        result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { (_, dataList) ->
                assertEquals(1, dataList.size)
                assertEquals("2026/5/1 24:00", dataList.single().time)
                assertEquals(63.0f, dataList.single().storagePercentage!!, 0.001f)
            }
        )

        val allInvalid = parser.parseHistoricalDatToDataList(
            csvBytes = historicalDat(
                listOf(
                    historicalRow(date = "2026/5/1", time = "", storagePercentage = "60.0"),
                    historicalRow(date = "2026/5/1", time = "24:10", storagePercentage = "62.0")
                )
            ),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        )
        assertTrue(allInvalid.isLeft())
    }

    @Test
    fun parseHistoricalDatToDataList_keepsUtf8BomOnlyAtFileStart() {
        val text = historicalDatText(
            listOf(
                historicalRow(date = "2026/5/1", time = "01:00", storagePercentage = "61.2"),
                historicalRow(date = "2026/5/1", time = "\uFEFF02:00", storagePercentage = "62.2")
            )
        )
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)

        val result = parser.parseHistoricalDatToDataList(bytes, "1368080700010", "20260501", "20260501")

        assertTrue(result.isRight())
        result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { (_, dataList) ->
                assertEquals(1, dataList.size)
                assertEquals("2026/5/1 01:00", dataList.single().time)
            }
        )
    }

    @Test
    fun parseHistoricalDatToDataList_parsesSingleValidRow() {
        val result = parser.parseHistoricalDatToDataList(
            csvBytes = historicalDat(
                listOf(
                    historicalRow(
                        date = "2026/5/1",
                        time = "01:00",
                        rainfall = "0.2",
                        storageVolume = "72010",
                        inflow = "12.0",
                        outflow = "11.0",
                        storagePercentage = "61.2"
                    )
                )
            ),
            damConfigId = "1368080700010",
            searchBgnDate = "20260501",
            searchEndDate = "20260501"
        )

        assertTrue(result.isRight())
        result.fold(
            ifLeft = { throw AssertionError(it) },
            ifRight = { (_, dataList) ->
                assertEquals(1, dataList.size)
                assertEquals("2026/5/1 01:00", dataList.single().time)
                assertNotNull(dataList.single().catchmentAverageRainfall)
                assertEquals(0.2f, dataList.single().catchmentAverageRainfall!!, 0.001f)
                assertNotNull(dataList.single().storagePercentage)
                assertEquals(61.2f, dataList.single().storagePercentage!!, 0.001f)
            }
        )
    }

    private fun historicalDat(dataRows: List<String>): ByteArray =
        historicalDatText(dataRows).toByteArray(Charset.forName("Shift_JIS"))

    private fun historicalDatText(dataRows: List<String>): String =
        (
            listOf(
                "任意期間ダム諸量検索結果",
                "水系名,吉野川",
                "河川名,吉野川",
                "観測所名,早明浦ダム",
                "観測所記号,1368080700010",
                "#年月日,時刻,流域平均雨量,雨量属性,貯水量,貯水量属性,流入量,流入量属性,放流量,放流量属性,貯水率,貯水率属性"
            ) + dataRows
        ).joinToString("\n")

    private fun historicalRow(
        date: String,
        time: String,
        rainfall: String = "0.0",
        rainfallAttr: String = "",
        storageVolume: String = "72000",
        storageVolumeAttr: String = "",
        inflow: String = "10.0",
        inflowAttr: String = "",
        outflow: String = "9.0",
        outflowAttr: String = "",
        storagePercentage: String = "60.0",
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
}
