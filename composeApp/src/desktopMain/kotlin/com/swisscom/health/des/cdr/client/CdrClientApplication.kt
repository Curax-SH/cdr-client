package com.swisscom.health.des.cdr.client

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.swisscom.health.des.cdr.client.config.CdrClientConfig
import com.swisscom.health.des.cdr.client.installer.App
import com.swisscom.health.des.cdr.client.installer.terminal
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.io.File

fun main(args: Array<String>) {
    if (checkSpringProperties()) {
        startSpringBootApp(args)
    } else if (isUiAvailable()) {
        startUI()
    } else {
        startTerminalApp()
    }
}

fun checkSpringProperties(): Boolean {
    val activeProfile = System.getProperty("spring.profiles.active")
    val additionalConfigLocations = System.getProperty("spring.config.additional-location")
    val installDir = System.getProperty("compose.application.installation.dir")
    println("installDir: '$installDir'")
    println("compose resources dir: '${System.getProperty("compose.application.resources.dir")}'")
    println("user.dir: '${System.getProperty("user.dir")}'")
    // TODO: get secret and/or client-id from environment variables

    if (activeProfile != null && activeProfile.split(",").any { it == "customer" }) {
        additionalConfigLocations ?: run {
            println("Error: spring.config.additional-location is not set")
            return false
        }

        var nonExistentFiles = true
        additionalConfigLocations.split(",").forEach {
            val configFile = File(it)
            if (configFile.exists()) {
                nonExistentFiles = false
            }
        }

        if (nonExistentFiles) {
            println("Error: No configuration file does not exist at any given location: '$additionalConfigLocations'")
            return false
        }
    } else {
        return false
    }

    return true
}

fun startSpringBootApp(args: Array<String>) {
    runApplication<CdrClientApplication>(*args)
}

fun isUiAvailable(): Boolean {
    return System.getenv("DISPLAY") != null
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
