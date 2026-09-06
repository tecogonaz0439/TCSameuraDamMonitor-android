// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
/**
 * インメモリ Room データベースを用いた過去データ検索メタデータ・点データのデータアクセスオブジェクト [HistoricalSearchDao] の Instrumentation テストクラス。
 * 過去データメタデータと点リストの挿入・時間指定抽出、メタデータ削除時に紐づく点リストが自動でカスケード削除（Cascade Delete）されることの検証、
 * 同一ダム・期間指定時のユニーク制約違反（SQLiteConstraintException）、および表示順（sortOrder）のシフト
 * （最新データを一番上に割り当てる処理）や並び替えの更新処理が正確に動作することを検証します。
 */
class HistoricalSearchDaoAndroidTest {
    private lateinit var database: DamDatabase
    private lateinit var dao: HistoricalSearchDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.historicalSearchDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertMetaAndData_queriesByMetaIdAndTimeRange() = runTest {
        val metaId = dao.insertMeta(meta(searchBgnDate = "20260501", searchEndDate = "20260502"))
        dao.insertDataList(
            listOf(
                data(metaId, timeStr = "2026/05/01 00:00", timeMillis = 1000L, storagePercentage = 80.0f),
                data(metaId, timeStr = "2026/05/01 01:00", timeMillis = 2000L, storagePercentage = 81.0f),
                data(metaId, timeStr = "2026/05/01 02:00", timeMillis = 3000L, storagePercentage = 82.0f)
            )
        )

        val range = dao.queryDataByMetaIdAndTimeRange(metaId, fromMillis = 1500L, toMillis = 3000L)

        assertEquals(listOf("2026/05/01 01:00", "2026/05/01 02:00"), range.map { it.timeStr })
        assertEquals(1000L, dao.queryOldestTimeMillisByMetaId(metaId))
        assertEquals(3000L, dao.queryNewestTimeMillisByMetaId(metaId))
    }

    @Test
    fun deleteMeta_cascadesHistoricalData() = runTest {
        val metaId = dao.insertMeta(meta(searchBgnDate = "20260501", searchEndDate = "20260502"))
        dao.insertDataList(listOf(data(metaId, timeStr = "2026/05/01 00:00", timeMillis = 1000L)))

        dao.deleteMetaById(metaId)

        assertNull(dao.getMetaById(metaId))
        assertEquals(emptyList<HistoricalDamDataEntity>(), dao.queryAllDataByMetaId(metaId))
    }

    @Test
    fun duplicateDamAndDateRange_violatesUniqueConstraint() = runTest {
        dao.insertMeta(meta(searchBgnDate = "20260501", searchEndDate = "20260502"))

        try {
            dao.insertMeta(meta(searchBgnDate = "20260501", searchEndDate = "20260502"))
            fail("Expected SQLiteConstraintException")
        } catch (_: SQLiteConstraintException) {
            assertEquals(1, dao.countDuplicates(TEST_DAM_ID, "20260501", "20260502"))
        }
    }

    @Test
    fun shiftAndReorderSortOrder_returnsMetaInDisplayOrder() = runTest {
        val firstId = dao.insertMeta(meta(sortOrder = 0, searchBgnDate = "20260501", searchEndDate = "20260501"))
        dao.shiftAllSortOrderUp()
        val secondId = dao.insertMeta(meta(sortOrder = 0, searchBgnDate = "20260502", searchEndDate = "20260502"))

        assertEquals(listOf(secondId, firstId), dao.getAllMeta().map { it.id })

        dao.updateSortOrder(firstId, 0)
        dao.updateSortOrder(secondId, 1)

        assertEquals(listOf(firstId, secondId), dao.getAllMeta().map { it.id })
    }

    private fun meta(
        sortOrder: Int = 0,
        searchBgnDate: String,
        searchEndDate: String
    ): HistoricalSearchMetaEntity =
        HistoricalSearchMetaEntity(
            sortOrder = sortOrder,
            observationStationId = TEST_DAM_ID,
            observationStationName = "早明浦ダム",
            riverSystemName = "吉野川",
            riverName = "吉野川",
            damConfigId = TEST_DAM_ID,
            searchBgnDate = searchBgnDate,
            searchEndDate = searchEndDate,
            fetchedAt = 100L,
            dataStartTimeStr = null,
            dataEndTimeStr = null,
            dataStartStoragePct = null,
            dataEndStoragePct = null,
            dataMinStoragePct = null,
            dataMaxStoragePct = null
        )

    private fun data(
        searchMetaId: Long,
        timeStr: String,
        timeMillis: Long,
        storagePercentage: Float? = 80.0f
    ): HistoricalDamDataEntity =
        HistoricalDamDataEntity(
            searchMetaId = searchMetaId,
            timeStr = timeStr,
            timeMillis = timeMillis,
            catchmentAverageRainfall = 0.0f,
            storageVolume = 100000f,
            inflow = 10.0f,
            outflow = 9.0f,
            storagePercentage = storagePercentage
        )

    private companion object {
        private const val TEST_DAM_ID = "1368080700010"
    }
}
