package com.swisscom.health.des.cdr.client.handler

import com.swisscom.health.des.cdr.client.common.DTOs
import com.swisscom.health.des.cdr.client.config.CdrClientConfig
import com.swisscom.health.des.cdr.client.handler.CdrApiClient.Companion.TEMP_FILE_EXTENSION
import com.swisscom.health.des.cdr.client.scheduling.BaseUploadScheduler.Companion.EXTENSION_XML
import io.github.oshai.kotlinlogging.KotlinLogging
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

    /**
     * Checks all error directories and the temporary directory for problematic files.
     * Returns the current file monitoring status.
     */
    suspend fun checkFileStatus(): DTOs.FileMonitoringStatusResponse {
        logger.debug { "Starting file monitoring check" }

        val errorFileCount = countErrorFiles()
        val oldTempFileCount = countOldTempFiles()

        val status = DTOs.FileMonitoringStatusResponse(
            errorFileCount = errorFileCount,
            oldTempFileCount = oldTempFileCount
        )

        logger.debug { "File monitoring check completed: $status" }
        return status
    }

    /**
     * Counts all files in error directories across all connectors.
     */
    private fun countErrorFiles(): Int {
        var totalCount = 0

        config.customer.forEach { connector ->
            runCatching {
                val errorFolder = connector.getEffectiveSourceErrorFolder()
                if (errorFolder.exists() && errorFolder.isDirectory()) {
                    val count = Files.walk(errorFolder)
                        .asSequence()
                        .count { it.isRegularFile() && it.extension.lowercase() == EXTENSION_XML }
                    totalCount += count
                    if (count > 0) {
                        logger.debug { "Found $count error file(s) in '${errorFolder}' for connector '${connector.connectorId}'" }
                    }
                }
            }.onFailure { t: Throwable ->
                logger.warn { "Failed to check error folder for connector '${connector.connectorId}': ${t.message}" }
            }
        }

        return totalCount
    }

    /**
     * Counts files older than the configured threshold in the temporary download directory.
     */
    private fun countOldTempFiles(): Int {
        var count = 0

        runCatching {
            val tempFolder = config.localFolder.path
            if (tempFolder.exists() && tempFolder.isDirectory()) {
                val threshold = Instant.now().minus(config.oldFileThreshold)

                count = Files.walk(tempFolder)
                    .asSequence()
                    .filter { it.isRegularFile() && it.extension.lowercase() == TEMP_FILE_EXTENSION }
                    .count { file ->
                        val lastModified = Files.getLastModifiedTime(file).toInstant()
                        lastModified.isBefore(threshold)
                    }

                if (count > 0) {
                    logger.debug { "Found $count old file(s) (older than '${config.oldFileThreshold}') in temp directory '$tempFolder'" }
                }
            }
        }.onFailure { t: Throwable ->
            logger.warn { "Failed to check temporary folder: ${t.message}" }
        }

        return count
    }
}

