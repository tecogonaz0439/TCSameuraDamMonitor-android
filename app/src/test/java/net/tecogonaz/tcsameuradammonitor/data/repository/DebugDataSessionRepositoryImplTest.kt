// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import android.content.Context
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DatabaseTransactionRunner
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryObservationEntity
import net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRecovery
import net.tecogonaz.tcsameuradammonitor.testutil.FakeSettingsRepository
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class DebugDataSessionRepositoryImplTest {
    private lateinit var root: File
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var damDao: MemoryDamDao
    private lateinit var historyDao: MemoryHistoryDao
    private lateinit var settings: FakeSettingsRepository
    private lateinit var damRepository: DamDataRepository
    private lateinit var repository: DebugDataSessionRepositoryImpl

    @Before
    fun setUp() {
        root = File("build/test-debug-data-session/${UUID.randomUUID()}")
        filesDir = File(root, "files").apply { mkdirs() }
        cacheDir = File(root, "cache").apply { mkdirs() }
        val context = mockk<Context>()
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
        damDao = MemoryDamDao()
        historyDao = MemoryHistoryDao()
        settings = FakeSettingsRepository(AppSettings())
        damRepository = mockk(relaxed = true)
        repository = DebugDataSessionRepositoryImpl(
            damDao = damDao,
            historyDao = historyDao,
            transactionRunner = ImmediateTransactionRunner,
            settingsRepository = settings,
            damDataRepository = damRepository,
            operationMutex = DebugDataOperationMutex(),
            context = context
        )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun enterThenExit_restoresAllRoomRowsAndRawFiles() = runTest {
        val originalDam = damEntity("dam-a", 100L)
        val originalHistory = historyEntity("dam-a", File(cacheDir, "sudmonitor_history_dam-a.dat"))
        val originalObservation = observationEntity("dam-a", 0, 1000L)
        damDao.rows += originalDam
        historyDao.histories += originalHistory
        historyDao.observations += originalObservation
        val realtimeBytes = byteArrayOf(1, 2, 3)
        val currentBytes = byteArrayOf(7, 8, 9)
        val historyBytes = byteArrayOf(4, 5, 6)
        File(cacheDir, "last_realtime_raw.dat").writeBytes(realtimeBytes)
        File(cacheDir, "last_realtime_url.txt").writeText("https://example.test/original.dat")
        File(cacheDir, "last_current_raw.dat").writeBytes(currentBytes)
        File(cacheDir, "last_current_url.txt").writeText("https://example.test/current.dat")
        File(originalHistory.rawDatPath!!).writeBytes(historyBytes)

        repository.enterDebugMode().getOrThrow()
        assertTrue(settings.current.debugModeEnabled)
        assertTrue(File(filesDir, "debug-data-backup/active/manifest.json").isFile)

        damDao.rows.clear()
        damDao.rows += damEntity("debug", 999L)
        historyDao.histories.clear()
        historyDao.observations.clear()
        File(cacheDir, "last_realtime_raw.dat").writeBytes(byteArrayOf(9))
        File(cacheDir, "last_current_raw.dat").writeBytes(byteArrayOf(10))
        File(cacheDir, "sudmonitor_history_debug.dat").writeBytes(byteArrayOf(8))

        repository.exitDebugMode().getOrThrow()

        assertFalse(settings.current.debugModeEnabled)
        assertEquals(listOf(originalDam), damDao.rows)
        assertEquals(listOf(originalHistory), historyDao.histories)
        assertEquals(listOf(originalObservation), historyDao.observations)
        assertArrayEquals(realtimeBytes, File(cacheDir, "last_realtime_raw.dat").readBytes())
        assertArrayEquals(currentBytes, File(cacheDir, "last_current_raw.dat").readBytes())
        assertArrayEquals(historyBytes, File(originalHistory.rawDatPath).readBytes())
        assertFalse(File(cacheDir, "sudmonitor_history_debug.dat").exists())
        assertFalse(File(filesDir, "debug-data-backup/active").exists())
        coVerify(exactly = 1) { damRepository.reloadCachedStateFromStorage() }
    }

    @Test
    fun emptySnapshot_exitRemovesDataCreatedDuringDebug() = runTest {
        assertTrue(repository.enterDebugMode().isSuccess)
        damDao.rows += damEntity("debug", 999L)
        historyDao.histories += historyEntity("debug", File(cacheDir, "sudmonitor_history_debug.dat"))
        historyDao.observations += observationEntity("debug", 0, 999L)
        File(cacheDir, "last_realtime_raw.dat").writeBytes(byteArrayOf(9))
        File(cacheDir, "sudmonitor_history_debug.dat").writeBytes(byteArrayOf(8))

        assertTrue(repository.exitDebugMode().isSuccess)

        assertTrue(damDao.rows.isEmpty())
        assertTrue(historyDao.histories.isEmpty())
        assertTrue(historyDao.observations.isEmpty())
        assertFalse(File(cacheDir, "last_realtime_raw.dat").exists())
        assertFalse(File(cacheDir, "sudmonitor_history_debug.dat").exists())
    }

    @Test
    fun startupWithDebugOffAndBackup_restoresNormalData() = runTest {
        val original = damEntity("normal", 100L)
        damDao.rows += original
        assertTrue(repository.enterDebugMode().isSuccess)
        settings.updateSettings { it.copy(debugModeEnabled = false) }
        damDao.rows.clear()
        damDao.rows += damEntity("debug", 999L)

        val recovered = repository.recoverSessionOnStartup()

        assertEquals(DebugDataSessionRecovery.NORMAL_DATA_RESTORED, recovered.getOrNull())
        assertEquals(listOf(original), damDao.rows)
        assertFalse(File(filesDir, "debug-data-backup/active").exists())
    }

    @Test
    fun startupWithDebugOn_keepsOriginalBackup() = runTest {
        damDao.rows += damEntity("normal", 100L)
        assertTrue(repository.enterDebugMode().isSuccess)
        damDao.rows.clear()
        damDao.rows += damEntity("debug", 999L)

        val recovered = repository.recoverSessionOnStartup()

        assertEquals(DebugDataSessionRecovery.DEBUG_SESSION_CONTINUED, recovered.getOrNull())
        assertEquals("debug", damDao.rows.single().observationStationId)
        assertTrue(File(filesDir, "debug-data-backup/active").isDirectory)
    }

    private fun damEntity(id: String, fetchedAt: Long) = DamEntity(
        observationStationId = id,
        observationStationName = id,
        riverSystemName = "river-system",
        riverName = "river",
        updatedAt = "2026/08/17 12:00",
        catchmentAverageRainfall = 1f,
        storageVolume = 2f,
        storageVolumeTrend = "FLAT",
        inflow = 3f,
        outflow = 4f,
        storagePercentage = 5f,
        storagePercentageTrend = "FLAT",
        storagePercentageTime = null,
        storagePercentageDayChange = null,
        storagePercentageDayChangeTrend = "FLAT",
        storagePercentageWeekChange = null,
        storagePercentageWeekChangeTrend = "FLAT",
        storageVolumeForMessage = 2f,
        historicalDataJson = "[]",
        lastFetchTimeMillis = fetchedAt
    )

    private fun historyEntity(id: String, rawFile: File) = SudmonitorHistoryEntity(
        damId = id,
        periodStartEpochMs = 1L,
        periodEndEpochMs = 2L,
        status = "SUCCESS",
        rowCount = 1,
        firstStorageRatePct = 50f,
        lastStorageRatePct = 50f,
        minStorageRatePct = 50f,
        maxStorageRatePct = 50f,
        fetchedAtEpochMs = 3L,
        nextUpdateAtEpochMs = 4L,
        rawDatPath = rawFile.absolutePath,
        updatedAtEpochMs = 5L
    )

    private fun observationEntity(id: String, row: Int, time: Long) = SudmonitorHistoryObservationEntity(
        damId = id,
        rowNo = row,
        timeText = "2026/08/17 01:00",
        timeEpochMs = time,
        rainfallHourlyMm = 1f,
        storageVolume1000m3 = 2f,
        inflowM3s = 3f,
        outflowM3s = 4f,
        storageRatePct = 5f
    )

    private class MemoryDamDao : DamDao {
        val rows = mutableListOf<DamEntity>()
        private val flow = MutableStateFlow<DamEntity?>(null)
        override fun getDamDataFlow(): Flow<DamEntity?> = flow
        override suspend fun getDamData(): DamEntity? = rows.maxByOrNull { it.lastFetchTimeMillis }
        override suspend fun getAllDamData(): List<DamEntity> = rows.sortedBy { it.observationStationId }
        override suspend fun insert(damData: DamEntity) {
            rows.removeAll { it.observationStationId == damData.observationStationId }
            rows += damData
            flow.value = getDamData()
        }
        override suspend fun insertAll(damData: List<DamEntity>) = damData.forEach { insert(it) }
        override suspend fun deleteAll() {
            rows.clear()
            flow.value = null
        }
    }

    private class MemoryHistoryDao : SudmonitorHistoryDao {
        val histories = mutableListOf<SudmonitorHistoryEntity>()
        val observations = mutableListOf<SudmonitorHistoryObservationEntity>()
        private val revision = MutableStateFlow(0)
        override suspend fun upsertHistory(entity: SudmonitorHistoryEntity) {
            histories.removeAll { it.damId == entity.damId }
            histories += entity
            bumpRevision()
        }
        override suspend fun insertObservations(list: List<SudmonitorHistoryObservationEntity>) {
            observations += list
        }
        override suspend fun deleteObservations(damId: String) {
            observations.removeAll { it.damId == damId }
        }
        override suspend fun findByDamId(damId: String) = histories.find { it.damId == damId }
        override fun findByDamIdFlow(damId: String): Flow<SudmonitorHistoryEntity?> =
            revision.map { histories.find { row -> row.damId == damId } }
        override suspend fun getAllHistory() = histories.sortedBy { it.damId }
        override suspend fun getAllObservations() = observations.sortedWith(compareBy({ it.damId }, { it.rowNo }))
        override suspend fun deleteAllObservations() {
            observations.clear()
        }
        override suspend fun deleteAllHistory() {
            histories.clear()
            bumpRevision()
        }
        override suspend fun getAllObservations(damId: String) = observations.filter { it.damId == damId }
        override suspend fun queryObservationsByDamIdAndTimeRange(damId: String, from: Long, to: Long) =
            observations.filter { it.damId == damId && it.timeEpochMs in from..to }

        private fun bumpRevision() {
            revision.value += 1
        }
    }

    private object ImmediateTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
    }
}
