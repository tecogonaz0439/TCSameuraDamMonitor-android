// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.source.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
/**
 * インメモリ Room データベースを用いた、デバッグログのデータアクセスオブジェクト [DebugLogDao] の Instrumentation テストクラス。
 * デバッグログが ID の降順（IDが大きいほど新しい）で Flow から正常取得できること、
 * ログの挿入時に自動クリーンアップ（[DebugLogDao.MAX_ENTRIES] を超過した古いログが自動で削除されること）が機能し
 * 最新の規定件数のみが保持されること、およびログ全削除処理が正しく実行されることを検証します。
 */
class DebugLogDaoAndroidTest {
    private lateinit var database: DamDatabase
    private lateinit var dao: DebugLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.debugLogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getAllEntriesFlow_returnsEntriesByIdDesc() = runTest {
        dao.insertAndCleanup(entry(message = "first", timestampMillis = 300L))
        dao.insertAndCleanup(entry(message = "second", timestampMillis = 200L))
        dao.insertAndCleanup(entry(message = "third", timestampMillis = 100L))

        val entries = dao.getAllEntriesFlow().first()

        assertEquals(listOf("third", "second", "first"), entries.map { it.message })
    }

    @Test
    fun insertAndCleanup_keepsNewestMaxEntries() = runTest {
        repeat(DebugLogDao.MAX_ENTRIES + 2) { index ->
            dao.insertAndCleanup(entry(message = "entry-$index", timestampMillis = index.toLong()))
        }

        val entries = dao.getAllEntriesFlow().first()

        assertEquals(DebugLogDao.MAX_ENTRIES, entries.size)
        assertEquals("entry-${DebugLogDao.MAX_ENTRIES + 1}", entries.first().message)
        assertEquals("entry-2", entries.last().message)
        assertFalse(entries.any { it.message == "entry-0" || it.message == "entry-1" })
    }

    @Test
    fun deleteAll_removesAllEntries() = runTest {
        dao.insertAndCleanup(entry(message = "first", timestampMillis = 100L))
        dao.insertAndCleanup(entry(message = "second", timestampMillis = 200L))

        dao.deleteAll()

        assertEquals(emptyList<DebugLogEntity>(), dao.getAllEntriesFlow().first())
    }

    private fun entry(message: String, timestampMillis: Long): DebugLogEntity =
        DebugLogEntity(
            timestampMillis = timestampMillis,
            message = message,
            details = "details:$message"
        )
}
