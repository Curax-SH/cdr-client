package com.swisscom.health.des.cdr.client.handler

import com.swisscom.health.des.cdr.client.scheduling.BaseUploadScheduler.Companion.DEFAULT_INITIAL_DELAY_MILLIS
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Periodically checks the file system for monitoring purposes.
 */
@Service
@Profile("!noFileMonitoringScheduler")
internal class FileMonitoringScheduler(private val fileMonitoringService: FileMonitoringService) {

    @Scheduled(initialDelay = DEFAULT_INITIAL_DELAY_MILLIS, fixedDelayString = $$"${client.schedule-delay}")
    suspend fun checkFileSystem(): Unit = fileMonitoringService.checkFileStatus()

}
