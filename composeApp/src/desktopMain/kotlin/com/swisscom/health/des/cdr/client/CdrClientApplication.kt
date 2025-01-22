package com.swisscom.health.des.cdr.client

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cdr_client.composeapp.generated.resources.Res
import com.swisscom.health.des.cdr.client.config.CdrClientConfig
import com.swisscom.health.des.cdr.client.installer.App
import com.swisscom.health.des.cdr.client.installer.terminal
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.scheduling.annotation.EnableScheduling
import java.awt.AWTException
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.io.File
import java.net.URI
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    if (checkSpringProperties()) {
        if (isUiAvailable()) {
            System.setProperty("java.awt.headless", "false") // for system tray only
        }
        startSpringBootApp(args)
    } else if (isUiAvailable()) {
        startUI()
    } else {
        startTerminalApp()
    }
}

fun checkSpringProperties(): Boolean {
    val activeProfile = activeProfiles()
    // TODO: get secret and/or client-id from environment variables

    if (activeProfile != null && activeProfile.split(",").any { it == "customer" }) {
        val systemAdditionalLocations = System.getProperty("spring.config.additional-location")
        logger.info { "AdditionalLocations when checking: '$systemAdditionalLocations'" }
        val additionalLocation = systemAdditionalLocations ?: (getInstallDir() + "/application-customer.properties")

        var nonExistentFiles = true
        additionalLocation.split(",").forEach {
            val configFile = File(it)
            if (configFile.exists()) {
                nonExistentFiles = false
            }
        }

        if (nonExistentFiles) {
            logger.info { "Error: No configuration file does not exist at any given location: '$additionalLocation'" }
            return false
        }
    } else if (isSkipInstallerProfile()) {
        logger.info { "local development or testing" }
    } else {
        error("Error: spring.profiles.active is not set or does not contain 'customer'")
    }

    return true
}

fun getInstallDir(): String {
    val jarPath = {}::class.java.protectionDomain.codeSource.location.path
    val osName = System.getProperty("os.name").lowercase()
    val installDir = if (osName.contains("win")) {
        File(jarPath).parentFile.parent // Windows: 'cdr-client\app\xyz.jar'
    } else {
        File(jarPath).parentFile.parentFile.parent // Unix: 'cdr-client/lib/app/xyz.jar'
    }
    logger.debug { "Install dir: $installDir" }
    return installDir
}

fun activeProfiles(): String? {
    return System.getProperty("spring.profiles.active")
}

fun startSpringBootApp(args: Array<String>) {
    val app = SpringApplication(CdrClientApplication::class.java)
    // TODO: if active profiles are not active, should we fail or set the 'customer' profile?
    if (activeProfiles() == null || !isSkipInstallerProfile()) {
        System.getProperty("spring.config.additional-location") ?: run {
            System.setProperty(
                "spring.config.additional-location",
                "${getInstallDir()}${File.separator}application-customer.properties"
            )
            //  app.addListeners(ConfigLocationListener())
        }
        System.getProperty("LOGGING_FILE_NAME") ?: run {
            System.setProperty(
                "LOGGING_FILE_NAME",
                "${getInstallDir()}${File.separator}logs${File.separator}cdr-client.log"
            )
        }
    }
    app.addListeners(ApplicationListener<ApplicationReadyEvent> {
        initSystemTray()
    })
    app.addListeners(ApplicationListener<ContextClosedEvent> {
        SystemTray.getSystemTray().trayIcons.forEach { SystemTray.getSystemTray().remove(it) }
    })
    app.run(*args)
}

@OptIn(ExperimentalResourceApi::class)
fun initSystemTray() {
    if (!SystemTray.isSupported()) {
        logger.warn { "System tray is not supported" }
        return
    }

    val tray = SystemTray.getSystemTray()
    val imageUri = Res.getUri("drawable/swisscom-logo-lifeform-180x180.png")
    val image = Toolkit.getDefaultToolkit().getImage(URI(imageUri).toURL())
    val trayIcon = TrayIcon(image, "Swisscom CDR Client")
    trayIcon.isImageAutoSize = true
    trayIcon.toolTip = "Swisscom CDR Client"

    val popup = PopupMenu()
    val exitItem = MenuItem("Exit")
    exitItem.addActionListener {
        tray.remove(trayIcon)
        exitProcess(0)
    }
    popup.add(exitItem)
    trayIcon.popupMenu = popup

    try {
        tray.add(trayIcon)
    } catch (e: AWTException) {
        logger.error { "TrayIcon could not be added." }
    }
}

fun isSkipInstallerProfile(): Boolean {
    val activeProfile = activeProfiles()
    return activeProfile != null && activeProfile.split(",").any { it == "dev" || it == "test" }
}

fun isUiAvailable(): Boolean {
    val osName = System.getProperty("os.name").lowercase()
    return if (osName.contains("win")) {
        true
    } else {
        val display = System.getenv("DISPLAY")
        !display.isNullOrEmpty()
    }
}

fun startUI() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "CDR-Client Setup",
    ) {
        App()
    }
}

fun startTerminalApp() {
    terminal()
}

/**
 * Spring Boot entry point
 */
@SpringBootApplication
@EnableConfigurationProperties(CdrClientConfig::class)
@EnableScheduling
class CdrClientApplication
