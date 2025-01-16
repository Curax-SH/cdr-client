package com.swisscom.health.des.cdr.client

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.swisscom.health.des.cdr.client.config.CdrClientConfig
import com.swisscom.health.des.cdr.client.installer.App
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.io.File


fun main(args: Array<String>) {
    if (checkSpringProperties()) {
        startSpringBootApp(args)
    } else {
        startUI()
    }
}

fun checkSpringProperties(): Boolean {
    val activeProfile = System.getProperty("spring.profiles.active")
    val additionalConfigLocation = System.getProperty("spring.config.additional-location")

    if (activeProfile != null && activeProfile.split(",").any { it == "customer" }) {
        if (additionalConfigLocation.isNullOrEmpty()) {
            println("Error: spring.config.additional-location is not set")
            return false
        }

        val configFile = File(additionalConfigLocation)
        if (!configFile.exists()) {
            println("Error: Configuration file does not exist at $additionalConfigLocation")
            return false
        }
    } else {
        return false
    }

    return true
}

fun startUI() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "CDR-Client Setup",
    ) {
        App()
    }
}

fun startSpringBootApp(args: Array<String>) {
    runApplication<CdrClientApplication>(*args)
}

/**
 * Spring Boot entry point
 */
@SpringBootApplication
@EnableConfigurationProperties(CdrClientConfig::class)
@EnableScheduling
class CdrClientApplication
