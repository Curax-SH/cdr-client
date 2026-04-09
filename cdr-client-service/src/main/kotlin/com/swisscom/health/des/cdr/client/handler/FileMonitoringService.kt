package com.swisscom.health.des.cdr.client.handler

import com.swisscom.health.des.cdr.client.common.DTOs
import com.swisscom.health.des.cdr.client.config.CdrClientConfig
import com.swisscom.health.des.cdr.client.handler.CdrApiClient.Companion.TEMP_FILE_EXTENSION
import com.swisscom.health.des.cdr.client.scheduling.BaseUploadScheduler.Companion.EXTENSION_XML
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger {}

/**
 * Service to monitor file system status including:
 * - Files in error directories
 * - Old files in temporary directories
 */
@Service
internal class FileMonitoringService(
    private val config: CdrClientConfig,
) {
    private val mutex = Mutex()
    private val _monitoringStatus = MutableStateFlow(
        DTOs.FileMonitoringStatusResponse(
            errorFileCount = 0,
            oldTempFileCount = 0
        )
    )
    val monitoringStatus: StateFlow<DTOs.FileMonitoringStatusResponse> = _monitoringStatus.asStateFlow()

    /**
     * Checks all error directories and the temporary directory for problematic files.
     * Returns the current file monitoring status.
     * This method is thread-safe and can be called from multiple sources.
     */
    suspend fun checkFileStatus() {
        mutex.withLock {
            logger.debug { "Starting file monitoring check" }

            val errorFileCount = countErrorFiles()
            val oldTempFileCount = countOldTempFiles()

            val newStatus = DTOs.FileMonitoringStatusResponse(
                errorFileCount = errorFileCount,
                oldTempFileCount = oldTempFileCount
            )

            _monitoringStatus.value = newStatus
            logger.debug { "File monitoring check completed: $newStatus" }
        }
    }

    /**
     * Counts all files in error directories across all connectors.
     */
    private fun countErrorFiles(): Int =
        config.customer.map { connector ->
            runCatching {
                val errorFolder = connector.getEffectiveSourceErrorFolder()
                if (errorFolder.exists() && errorFolder.isDirectory()) {
                    val count = Files.walk(errorFolder)
                        .asSequence()
                        .count { it.isRegularFile() && it.extension.lowercase() == EXTENSION_XML }
                    if (count > 0) {
                        logger.debug { "Found $count error file(s) in '${errorFolder}' for connector '${connector.connectorId}'" }
                    }
                    count
                } else {
                    0
                }
            }.getOrElse { t: Throwable ->
                logger.warn { "Failed to check error folder for connector '${connector.connectorId}': ${t.message}" }
                0
            }
        }.sumOf { it }

    /**
     * Counts files older than the configured threshold in the temporary download directory.
     */
    private fun countOldTempFiles(): Int {
        return runCatching {
            val tempFolder = config.localFolder.path
            val count = if (tempFolder.exists() && tempFolder.isDirectory()) {
                val threshold = Instant.now().minus(config.oldFileThreshold)

                Files.walk(tempFolder)
                    .asSequence()
                    .filter { it.isRegularFile() && it.extension.lowercase() == TEMP_FILE_EXTENSION }
                    .count { file ->
                        val lastModified = Files.getLastModifiedTime(file).toInstant()
                        lastModified.isBefore(threshold)
                    }
            } else {
                0
            }
            if (count > 0) {
                logger.debug { "Found $count old file(s) (older than '${config.oldFileThreshold}') in temp directory '$tempFolder'" }
            }
            count
        }.getOrElse { t ->
            logger.warn { "Failed to check temporary folder: ${t.message}" }
            0
        }
    }
}


