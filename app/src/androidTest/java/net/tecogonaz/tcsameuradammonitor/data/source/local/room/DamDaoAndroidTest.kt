// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.testutil.androidTestDamData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
/**
 * インメモリの SQLite Room データベースを用いた、リアルタイムダムデータのデータアクセスオブジェクト [DamDao] の Instrumentation テストクラス。
 * ダムデータの挿入・抽出処理の確認、同一観測所ID指定時の Replace 動作、
 * 異なる観測所IDが登録された場合に最新取得時間（lastFetchTimeMillis）のレコードが正しく最上位取得される仕様の確認、
 * Flow を介したリアルタイムな新規登録の検知、およびデータ全削除（deleteAll）機能が正しく動作することを検証します。
 */
class DamDaoAndroidTest {
    private lateinit var database: DamDatabase
    private lateinit var dao: DamDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.damDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndQuery_returnsInsertedDamData() = runTest {
        val entity = androidTestDamData(storagePercentage = 80.0f).toEntity(fetchTimeMillis = 123L)

        dao.insert(entity)

        val stored = dao.getDamData() ?: throw AssertionError("Expected stored dam data.")
        assertEquals("1368080700010", stored.observationStationId)
        assertNotNull(stored.storagePercentage)
        assertEquals(80.0f, stored.storagePercentage!!, 0.001f)
        assertEquals(123L, stored.lastFetchTimeMillis)
    }

    @Test
    fun insertWithSameStation_replacesExistingRow() = runTest {
        dao.insert(androidTestDamData(storagePercentage = 80.0f).toEntity(fetchTimeMillis = 100L))

        dao.insert(androidTestDamData(storagePercentage = 79.5f).toEntity(fetchTimeMillis = 200L))

        val stored = dao.getDamData() ?: throw AssertionError("Expected stored dam data.")
        assertNotNull(stored.storagePercentage)
        assertEquals(79.5f, stored.storagePercentage!!, 0.001f)
        assertEquals(200L, stored.lastFetchTimeMillis)
    }

    @Test
    fun getDamData_returnsRowWithHighestLastFetchTimeAcrossStations() = runTest {
        dao.insert(
            androidTestDamData(storagePercentage = 70.0f)
                .toEntity(fetchTimeMillis = 300L)
                .copy(observationStationId = "older-station")
        )
        dao.insert(
            androidTestDamData(storagePercentage = 85.0f)
                .toEntity(fetchTimeMillis = 500L)
                .copy(observationStationId = "newer-station")
        )

        val stored = dao.getDamData() ?: throw AssertionError("Expected stored dam data.")

        assertEquals("newer-station", stored.observationStationId)
        assertNotNull(stored.storagePercentage)
        assertEquals(85.0f, stored.storagePercentage!!, 0.001f)
        assertEquals(500L, stored.lastFetchTimeMillis)
    }

    @Test
    fun deleteAll_removesCachedDamData() = runTest {
        dao.insert(androidTestDamData().toEntity(fetchTimeMillis = 123L))

        dao.deleteAll()

        assertNull(dao.getDamData())
    }

    @Test
    fun getDamDataFlow_emitsInsertedNewestRow() = runTest {
        assertNull(dao.getDamDataFlow().first())
        val emitted = async { dao.getDamDataFlow().filterNotNull().first() }

        dao.insert(androidTestDamData(storagePercentage = 80.0f).toEntity(fetchTimeMillis = 100L))
        val stored = emitted.await()

        assertEquals(80.0f, stored.storagePercentage!!, 0.001f)
        assertEquals(100L, stored.lastFetchTimeMillis)
    }
}
