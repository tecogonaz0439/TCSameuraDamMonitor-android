// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DamEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.DatabaseTransactionRunner
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryDao
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryEntity
import net.tecogonaz.tcsameuradammonitor.data.source.local.room.SudmonitorHistoryObservationEntity
import net.tecogonaz.tcsameuradammonitor.domain.repository.DamDataRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRecovery
import net.tecogonaz.tcsameuradammonitor.domain.repository.DebugDataSessionRepository
import net.tecogonaz.tcsameuradammonitor.domain.repository.SettingsRepository
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugDatSelectionMode
import net.tecogonaz.tcsameuradammonitor.domain.model.DebugSimulateMode
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** [DebugDataSessionRepository]のアプリ内部ファイル・Room実装です。 */
@Singleton
class DebugDataSessionRepositoryImpl @Inject constructor(
    private val damDao: DamDao,
    private val historyDao: SudmonitorHistoryDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val settingsRepository: SettingsRepository,
    private val damDataRepository: DamDataRepository,
    private val operationMutex: DebugDataOperationMutex,
    @ApplicationContext context: Context
) : DebugDataSessionRepository {
    private val filesDir = context.filesDir.absoluteFile
    private val cacheDir = context.cacheDir.absoluteFile
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        prettyPrint = true
    }

    override suspend fun enterDebugMode(): Result<Unit> = resultOf {
        operationMutex.withLock {
            cleanupStaging()
            val settings = settingsRepository.appSettingsFlow.first()
            if (settings.debugModeEnabled) {
                requireValidBackup()
                return@withLock
            }
            if (activeDir.exists()) {
                restoreLocked()
                deleteRecursivelyChecked(activeDir)
            }
            createBackupLocked()
            settingsRepository.updateSettings { it.copy(debugModeEnabled = true) }
        }
    }

    override suspend fun exitDebugMode(): Result<Unit> = resultOf {
        operationMutex.withLock {
            cleanupStaging()
            val settings = settingsRepository.appSettingsFlow.first()
            if (!activeDir.exists()) {
                check(!settings.debugModeEnabled) { "Debug data backup is missing." }
                return@withLock
            }
            restoreLocked()
            damDataRepository.reloadCachedStateFromStorage()
            settingsRepository.updateSettings { it.withDebugSessionDisabled() }
            deleteRecursivelyChecked(activeDir)
        }
    }

    override suspend fun recoverSessionOnStartup(): Result<DebugDataSessionRecovery> = resultOf {
        operationMutex.withLock {
            cleanupStaging()
            val debugEnabled = settingsRepository.appSettingsFlow.first().debugModeEnabled
            if (!activeDir.exists()) {
                check(!debugEnabled) { "Debug data backup is missing." }
                return@withLock DebugDataSessionRecovery.NOTHING_TO_DO
            }
            requireValidBackup()
            if (debugEnabled) {
                DebugDataSessionRecovery.DEBUG_SESSION_CONTINUED
            } else {
                // OFFが永続化された直後などにプロセスが停止しても、復元を再実行して整合させる。
                restoreLocked()
                damDataRepository.reloadCachedStateFromStorage()
                deleteRecursivelyChecked(activeDir)
                DebugDataSessionRecovery.NORMAL_DATA_RESTORED
            }
        }
    }

    private suspend fun createBackupLocked() {
        val snapshot = transactionRunner.withTransaction {
            DatabaseSnapshot(
                damData = damDao.getAllDamData(),
                history = historyDao.getAllHistory(),
                observations = historyDao.getAllObservations()
            )
        }
        withContext(Dispatchers.IO) {
            check(!activeDir.exists()) { "Debug data backup already exists." }
            deleteRecursivelyChecked(stagingDir)
            check(stagingDir.mkdirs()) { "Cannot create debug data staging directory." }
            try {
                val rawFiles = collectRawFiles(snapshot.history).mapIndexed { index, source ->
                    val storedName = "raw-${index.toString().padStart(4, '0')}.bin"
                    val target = File(stagingDir, storedName)
                    source.copyTo(target, overwrite = false)
                    RawFileBackup(
                        cacheRelativePath = source.relativeTo(cacheDir).invariantSeparatorsPath,
                        storedName = storedName,
                        byteCount = target.length(),
                        sha256 = sha256(target)
                    )
                }
                val manifest = BackupManifest(
                    version = MANIFEST_VERSION,
                    database = snapshot,
                    rawFiles = rawFiles
                )
                File(stagingDir, MANIFEST_FILE).writeText(json.encodeToString(manifest))
                validateBackup(stagingDir)
                check(stagingDir.renameTo(activeDir)) { "Cannot activate debug data backup." }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                deleteRecursivelyChecked(stagingDir)
                throw error
            }
        }
    }

    private fun net.tecogonaz.tcsameuradammonitor.domain.model.AppSettings.withDebugSessionDisabled() = copy(
        debugModeEnabled = false,
        debugRealtimeDatFileMode = DebugDatSelectionMode.BUNDLED,
        debugRealtimeDatFileUri = null,
        debugHistoricalDailyDatFileMode = DebugDatSelectionMode.BUNDLED,
        debugHistoricalDailyDatFileUri = null,
        debugRealtimeDataStartMillis = null,
        debugRealtimeDataEndMillis = null,
        debugRealtimeDataPeriodAutoAdvanceEnabled = true,
        debugSimulateMode = DebugSimulateMode.NONE
    )

    private suspend fun restoreLocked() {
        val manifest = withContext(Dispatchers.IO) { validateBackup(activeDir) }
        val prepared = withContext(Dispatchers.IO) {
            manifest.rawFiles.map { raw ->
                val target = resolveCacheFile(raw.cacheRelativePath)
                target.parentFile?.mkdirs()
                val temporary = File(target.parentFile, ".${target.name}.debug-restore")
                File(activeDir, raw.storedName).copyTo(temporary, overwrite = true)
                check(temporary.length() == raw.byteCount && sha256(temporary) == raw.sha256) {
                    "Debug raw data backup failed integrity verification."
                }
                PreparedRawFile(target, temporary)
            }
        }
        try {
            transactionRunner.withTransaction {
                historyDao.deleteAll()
                damDao.deleteAll()
                if (manifest.database.damData.isNotEmpty()) {
                    damDao.insertAll(manifest.database.damData)
                }
                manifest.database.history.forEach { historyDao.upsertHistory(it) }
                if (manifest.database.observations.isNotEmpty()) {
                    historyDao.insertObservations(manifest.database.observations)
                }
            }
            withContext(Dispatchers.IO) {
                collectManagedCurrentRawFiles().forEach { current ->
                    if (prepared.none { it.target.canonicalFile == current.canonicalFile }) {
                        check(!current.exists() || current.delete()) { "Cannot remove Debug session raw data." }
                    }
                }
                prepared.forEach { item ->
                    check(!item.target.exists() || item.target.delete()) { "Cannot replace restored raw data." }
                    check(item.temporary.renameTo(item.target)) { "Cannot commit restored raw data." }
                }
            }
        } finally {
            withContext(Dispatchers.IO) { prepared.forEach { it.temporary.delete() } }
        }
    }

    private fun collectRawFiles(history: List<SudmonitorHistoryEntity>): List<File> {
        val candidates = buildList {
            cacheDir.listFiles().orEmpty()
                .filter(::isManagedRealtimeFile)
                .forEach(::add)
            history.mapNotNull { it.rawDatPath }
                .map(::File)
                .filter { isInsideCache(it) }
                .forEach(::add)
        }
        return candidates.filter { it.isFile }.distinctBy { it.canonicalPath }.sortedBy { it.name }
    }

    private fun collectManagedCurrentRawFiles(): List<File> = buildList {
        cacheDir.listFiles().orEmpty().filter(::isManagedRealtimeFile).forEach(::add)
        historyDaoPathsFromBackupSafe().forEach(::add)
        cacheDir.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith(HISTORY_RAW_PREFIX) && it.name.endsWith(".dat") }
            .forEach(::add)
    }.distinctBy { it.absolutePath }

    private fun historyDaoPathsFromBackupSafe(): List<File> = runCatching {
        validateBackup(activeDir).database.history.mapNotNull { it.rawDatPath?.let(::File) }.filter(::isInsideCache)
    }.getOrDefault(emptyList())

    private fun isManagedRealtimeFile(file: File): Boolean = file.isFile && (
            file.name.startsWith("last_realtime_") ||
            file.name.startsWith("last_current_") ||
            file.name.startsWith("last_displayed_")
        )

    private fun validateBackup(directory: File): BackupManifest {
        check(directory.isDirectory) { "Debug data backup directory is missing." }
        val manifest = json.decodeFromString<BackupManifest>(File(directory, MANIFEST_FILE).readText())
        check(manifest.version == MANIFEST_VERSION) { "Unsupported debug data backup version." }
        manifest.rawFiles.forEach { raw ->
            check(raw.storedName.matches(Regex("raw-[0-9]{4}\\.bin"))) { "Invalid backup raw file name." }
            resolveCacheFile(raw.cacheRelativePath)
            val stored = File(directory, raw.storedName)
            check(stored.isFile && stored.length() == raw.byteCount && sha256(stored) == raw.sha256) {
                "Debug raw data backup is incomplete."
            }
        }
        return manifest
    }

    private fun requireValidBackup() {
        validateBackup(activeDir)
    }

    private fun resolveCacheFile(relativePath: String): File {
        check(relativePath.isNotBlank() && !File(relativePath).isAbsolute) { "Invalid cache path in backup." }
        val resolved = File(cacheDir, relativePath).canonicalFile
        check(isInsideCache(resolved)) { "Backup cache path escapes the app cache directory." }
        return resolved
    }

    private fun isInsideCache(file: File): Boolean {
        val root = cacheDir.canonicalFile.toPath()
        return file.canonicalFile.toPath().startsWith(root)
    }

    private fun cleanupStaging() = deleteRecursivelyChecked(stagingDir)

    private fun deleteRecursivelyChecked(directory: File) {
        if (directory.exists()) {
            check(directory.deleteRecursively()) { "Cannot remove debug data backup directory." }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private val rootDir get() = File(filesDir, ROOT_DIRECTORY)
    private val activeDir get() = File(rootDir, ACTIVE_DIRECTORY)
    private val stagingDir get() = File(rootDir, STAGING_DIRECTORY)

    @Serializable
    private data class BackupManifest(
        val version: Int,
        val database: DatabaseSnapshot,
        val rawFiles: List<RawFileBackup>
    )

    @Serializable
    private data class DatabaseSnapshot(
        val damData: List<DamEntity>,
        val history: List<SudmonitorHistoryEntity>,
        val observations: List<SudmonitorHistoryObservationEntity>
    )

    @Serializable
    private data class RawFileBackup(
        val cacheRelativePath: String,
        val storedName: String,
        val byteCount: Long,
        val sha256: String
    )

    private data class PreparedRawFile(val target: File, val temporary: File)

    private companion object {
        const val MANIFEST_VERSION = 1
        const val ROOT_DIRECTORY = "debug-data-backup"
        const val ACTIVE_DIRECTORY = "active"
        const val STAGING_DIRECTORY = ".staging"
        const val MANIFEST_FILE = "manifest.json"
        const val HISTORY_RAW_PREFIX = "sudmonitor_history_"
    }
}
