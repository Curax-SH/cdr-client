package com.swisscom.health.des.cdr.client

import com.swisscom.health.des.cdr.client.config.CdrClientConfig
import org.springframework.boot.actuate.autoconfigure.scheduling.ScheduledTasksObservabilityAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.nio.file.Path
import kotlin.io.path.isRegularFile

private const val SPRING_BOOT_ADDITIONAL_CONFIG_FILE_LOCATION_PROPERTY = "spring.config.additional-location"
private const val SPRING_BOOT_LOGBACK_CONFIG_LOCATION_PROPERTY = "logging.config"
private const val LOGBACK_CONFIGURATION_FILE_PROPERTY = "logback.configurationFile"

/**
 * Spring Boot entry point
 */
@SpringBootApplication(exclude = [ScheduledTasksObservabilityAutoConfiguration::class])
@EnableConfigurationProperties(CdrClientConfig::class)
@EnableScheduling
internal class CdrClientApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    initConfig()
    upgradeConfig()
    runApplication<CdrClientApplication>(*args)
}

/**
 * Creates application and logging configuration files for new installations (or if either configuration
 * has been deleted, for whatever reason).
 */
private fun initConfig() {
    System.getProperty(SPRING_BOOT_ADDITIONAL_CONFIG_FILE_LOCATION_PROPERTY)
        ?.takeIf { it.isNotBlank() }
        ?.let { configLocation: String -> Path.of(configLocation) }
        ?.let { configPath -> ConfigInit.initSpringBootConfig(configPath) }
        ?.let { absoluteConfigPath ->
            // update property with the absolute path to the SpringBoot configuration file
            System.setProperty(SPRING_BOOT_ADDITIONAL_CONFIG_FILE_LOCATION_PROPERTY, absoluteConfigPath.toString())
            Unit
        }
        ?: println(
            "No SpringBoot configuration file location configured via system property '$SPRING_BOOT_ADDITIONAL_CONFIG_FILE_LOCATION_PROPERTY', " +
                    "skipping initialization of SpringBoot configuration"
        )

    System.getProperty(SPRING_BOOT_LOGBACK_CONFIG_LOCATION_PROPERTY)
        ?.takeIf { it.isNotBlank() }
        ?.let { configLocation: String -> Path.of(configLocation) }
        ?.let { configPath -> ConfigInit.initLogbackConfig(configPath) }
        ?.let { absoluteConfigPath ->
            // update property with the absolute path to the logback configuration file
            System.setProperty(SPRING_BOOT_LOGBACK_CONFIG_LOCATION_PROPERTY, absoluteConfigPath.toString())
            // overwrite the logback-ui config that is set because of the conveyor.conf settings
            System.setProperty(LOGBACK_CONFIGURATION_FILE_PROPERTY, absoluteConfigPath.toString())
            Unit
        }
        ?: println(
            "No Logback configuration file location configured via system property '$SPRING_BOOT_LOGBACK_CONFIG_LOCATION_PROPERTY', " +
                    "skipping initialization of Logback configuration"
        )
}

/**
 * Upgrades existing configuration files to the latest version, adding new configuration items with
 * default values where necessary.
 */
private fun upgradeConfig() =
    System.getProperty(SPRING_BOOT_ADDITIONAL_CONFIG_FILE_LOCATION_PROPERTY)
        ?.let { configLocation: String -> configLocation.takeIf { it.isNotBlank() } }
        ?.let { configLocation: String -> Path.of(configLocation) }
        ?.let { configLocation: Path -> configLocation.takeIf { it.isRegularFile() } }
        ?.let { configLocation: Path ->
            when (val upgradeResult = ConfigUpgrade.applyPendingUpgradeSteps(configLocation)) {
                is UpgradeResult.AlreadyAtLatestVersion -> println("Configuration was already at the latest version, no upgrade was performed")
                is UpgradeResult.Success -> println("Configuration successfully upgraded to version '${upgradeResult.version}'")
                is UpgradeResult.Failure -> {
                    println("Failed to upgrade to version '${upgradeResult.version}'")
                    error("Failed to upgrade to version '${upgradeResult.version}'") // causes the JVM to exit
                }
            }
        }
        ?: println(
            "No SpringBoot configuration file location configured via system property '$SPRING_BOOT_ADDITIONAL_CONFIG_FILE_LOCATION_PROPERTY' " +
                    "or no file exists at configured location, skipping upgrade of SpringBoot configuration"
        )
