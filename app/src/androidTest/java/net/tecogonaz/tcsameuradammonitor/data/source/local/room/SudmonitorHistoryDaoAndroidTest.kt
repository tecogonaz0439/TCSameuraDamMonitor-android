// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.domain.model.SudmonitorHistory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * インメモリ Room データベースを用いた sudmonitor 日次過去データの DAO
 * （[SudmonitorHistoryDao]）の Instrumentation テストクラス。
 *
 * 保存行の upsert（REPLACE）によるダムごと1件の上書き、観測明細の一括置換
 * （先 DELETE 後 INSERT）、期間クエリ、全削除、ダム間の分離を検証します。
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SudmonitorHistoryDaoAndroidTest {
    private lateinit var database: DamDatabase
    private lateinit var dao: SudmonitorHistoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.sudmonitorHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertHistoryAndInsertObservations_roundTripAscendingOrder() = runTest {
        dao.upsertHistory(historyRow(damId = DAM_A, rowCount = 2))
        dao.insertObservations(
            listOf(
                observation(damId = DAM_A, rowNo = 0, time = "2026/08/01 02:00", millis = 2_000L),
                observation(damId = DAM_A, rowNo = 1, time = "2026/08/01 01:00", millis = 1_000L)
            )
        )

        val row = dao.findByDamId(DAM_A)
        assertEquals(DAM_A, row!!.damId)
        assertEquals(2, row.rowCount)
        assertEquals(SudmonitorHistory.STATUS_SUCCESS, row.status)
        // 観測明細は時刻昇順で返る
        assertEquals(listOf("2026/08/01 01:00", "2026/08/01 02:00"), dao.getAllObservations(DAM_A).map { it.timeText })
    }

    @Test
    fun upsertHistory_sameDamId_replacesRowAndObservations() = runTest {
        dao.upsertHistory(historyRow(damId = DAM_A, rowCount = 1))
        dao.insertObservations(listOf(observation(damId = DAM_A, rowNo = 0, time = "2026/08/01 01:00", millis = 1_000L)))

        // 再取得（D3: ダムごと1件の上書き）。観測明細は先に DELETE してから一括 INSERT する
        dao.deleteObservations(DAM_A)
        dao.upsertHistory(historyRow(damId = DAM_A, rowCount = 2, firstPct = 82f))
        dao.insertObservations(
            listOf(
                observation(damId = DAM_A, rowNo = 0, time = "2026/08/02 01:00", millis = 3_000L, pct = 82f),
                observation(damId = DAM_A, rowNo = 1, time = "2026/08/02 02:00", millis = 4_000L, pct = 83f)
            )
        )

        val row = dao.findByDamId(DAM_A)
        assertEquals(2, row!!.rowCount)
        assertEquals(82f, row.firstStorageRatePct ?: -1f, 0.001f)
        assertEquals(
            listOf("2026/08/02 01:00", "2026/08/02 02:00"),
            dao.getAllObservations(DAM_A).map { it.timeText }
        )
    }

    @Test
    fun observations_keptSeparatePerDam() = runTest {
        dao.upsertHistory(historyRow(damId = DAM_A))
        dao.upsertHistory(historyRow(damId = DAM_B))
        dao.insertObservations(listOf(observation(damId = DAM_A, rowNo = 0, time = "2026/08/01 01:00", millis = 1_000L)))
        dao.insertObservations(listOf(observation(damId = DAM_B, rowNo = 0, time = "2026/08/01 02:00", millis = 2_000L)))

        assertEquals(1, dao.getAllObservations(DAM_A).size)
        assertEquals(1, dao.getAllObservations(DAM_B).size)
        assertTrue(dao.findByDamId(DAM_A) != null)
        assertTrue(dao.findByDamId(DAM_B) != null)
    }

    @Test
    fun queryObservationsByDamIdAndTimeRange_filtersInclusiveAscending() = runTest {
        dao.upsertHistory(historyRow(damId = DAM_A))
        dao.insertObservations(
            listOf(
                observation(damId = DAM_A, rowNo = 0, time = "2026/08/01 01:00", millis = 1_000L),
                observation(damId = DAM_A, rowNo = 1, time = "2026/08/01 02:00", millis = 2_000L),
                observation(damId = DAM_A, rowNo = 2, time = "2026/08/01 03:00", millis = 3_000L)
            )
        )

        val ranged = dao.queryObservationsByDamIdAndTimeRange(DAM_A, from = 1_500L, to = 3_000L)

        assertEquals(listOf("2026/08/01 02:00", "2026/08/01 03:00"), ranged.map { it.timeText })
        // 他ダムの観測明細は含まれない
        assertEquals(
            emptyList<SudmonitorHistoryObservationEntity>(),
            dao.queryObservationsByDamIdAndTimeRange(DAM_B, from = 0L, to = 9_000L)
        )
    }

    @Test
    fun deleteAll_removesRowsAndObservations() = runTest {
        dao.upsertHistory(historyRow(damId = DAM_A))
        dao.insertObservations(listOf(observation(damId = DAM_A, rowNo = 0, time = "2026/08/01 01:00", millis = 1_000L)))

        dao.deleteAll()

        assertNull(dao.findByDamId(DAM_A))
        assertEquals(emptyList<SudmonitorHistoryObservationEntity>(), dao.getAllObservations(DAM_A))
    }

    @Test
    fun findByDamIdFlow_unstored_emitsNull() = runTest {
        // 未保存のダムは null をemitする
        assertNull(dao.findByDamIdFlow(DAM_A).first())
    }

    @Test
    fun findByDamIdFlow_upsertHistory_emitsStoredRow() = runTest {
        val emitted = async { dao.findByDamIdFlow(DAM_A).first { it != null } }

        dao.upsertHistory(historyRow(damId = DAM_A, rowCount = 2))
        val row = emitted.await()

        assertEquals(DAM_A, row!!.damId)
        assertEquals(2, row.rowCount)
        assertEquals(SudmonitorHistory.STATUS_SUCCESS, row.status)
    }

    @Test
    fun findByDamIdFlow_observationReplacement_emitsUpdatedRow() = runTest {
        dao.upsertHistory(historyRow(damId = DAM_A, rowCount = 1))
        dao.insertObservations(
            listOf(observation(damId = DAM_A, rowNo = 0, time = "2026/08/01 01:00", millis = 1_000L))
        )

        // 観測行置換（deleteObservations + upsertHistory + insertObservations）後に更新内容がemitされる
        val updated = async { dao.findByDamIdFlow(DAM_A).first { it != null && it.rowCount == 2 } }
        dao.deleteObservations(DAM_A)
        dao.upsertHistory(historyRow(damId = DAM_A, rowCount = 2, firstPct = 82f))
        dao.insertObservations(
            listOf(
                observation(damId = DAM_A, rowNo = 0, time = "2026/08/02 01:00", millis = 3_000L, pct = 82f),
                observation(damId = DAM_A, rowNo = 1, time = "2026/08/02 02:00", millis = 4_000L, pct = 83f)
            )
        )
        val row = updated.await()

        assertEquals(2, row!!.rowCount)
        assertEquals(82f, row.firstStorageRatePct ?: -1f, 0.001f)
    }

    @Test
    fun findByDamIdFlow_deleteAll_emitsNull() = runTest {
        dao.upsertHistory(historyRow(damId = DAM_A))

        val cleared = async { dao.findByDamIdFlow(DAM_A).first { it == null } }
        dao.deleteAll()
        assertNull(cleared.await())
    }

    private fun historyRow(
        damId: String,
        rowCount: Int = 1,
        firstPct: Float? = 80f
    ): SudmonitorHistoryEntity =
        SudmonitorHistoryEntity(
            damId = damId,
            periodStartEpochMs = 1_000L,
            periodEndEpochMs = 2_000L,
            status = SudmonitorHistory.STATUS_SUCCESS,
            rowCount = rowCount,
            firstStorageRatePct = firstPct,
            lastStorageRatePct = 81f,
            minStorageRatePct = 79f,
            maxStorageRatePct = 82f,
            fetchedAtEpochMs = 1_000L,
            nextUpdateAtEpochMs = 2_000L,
            rawDatPath = null,
            updatedAtEpochMs = 1_000L
        )

    private fun observation(
        damId: String,
        rowNo: Int,
        time: String,
        millis: Long,
        pct: Float = 80f
    ): SudmonitorHistoryObservationEntity =
        SudmonitorHistoryObservationEntity(
            damId = damId,
            rowNo = rowNo,
            timeText = time,
            timeEpochMs = millis,
            rainfallHourlyMm = 0f,
            storageVolume1000m3 = 72_000f,
            inflowM3s = 10f,
            outflowM3s = 9f,
            storageRatePct = pct
        )

    private companion object {
        private const val DAM_A = "1368080700010"
        private const val DAM_B = "9999999999999"
    }
}
