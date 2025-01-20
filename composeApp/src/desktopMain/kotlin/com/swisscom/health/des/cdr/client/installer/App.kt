package com.swisscom.health.des.cdr.client.installer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cdr_client.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

const val CONFIG_PATH = "application-customer.properties"
const val CONF_TENANT_ID = "tenant-id"
const val CONF_CONNECTOR_ID = "connector-id"
const val CONF_CLIENT_ID = "client-id"
const val CONF_CLIENT_SECRET = "client-secret"

@Composable
@Preview
fun App() {
    MaterialTheme {
        var clientId by remember { mutableStateOf("") }
        var clientSecret by remember { mutableStateOf("") }
        var connectorId by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var showContent by remember { mutableStateOf(false) }
        var tenantId by remember { mutableStateOf("") }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Configuration Setup", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = tenantId,
                onValueChange = { tenantId = it },
                label = { Text("Tenant-ID") },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text("Client-ID") },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            OutlinedTextField(
                value = clientSecret,
                onValueChange = { clientSecret = it },
                label = { Text("Client-Secret") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = connectorId,
                onValueChange = { connectorId = it },
                label = { Text("Connector-ID") },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val success = updateConfigFile(CONFIG_PATH, tenantId = tenantId, connectorId = connectorId, clientId = clientId, clientSecret = clientSecret)
                message = if (success) "Configuration updated successfully" else "Failed to update configuration"
                showContent = true
                if (success) createService(connectorId)
            }) {
                Text("Submit")
            }
            AnimatedVisibility(showContent) {
                Text(message, style = MaterialTheme.typography.body1, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

fun terminal() {
    println("Configuration Setup")

    print("Enter Tenant-ID: ")
    val tenantId = readlnOrNull() ?: ""

    print("Enter Client-ID: ")
    val clientId = readlnOrNull() ?: ""

    print("Enter Client-Secret: ")
    val clientSecret = readlnOrNull() ?: ""

    print("Enter Connector-ID: ")
    val connectorId = readlnOrNull() ?: ""

    val success = updateConfigFile(CONFIG_PATH, tenantId = tenantId, connectorId = connectorId, clientId = clientId, clientSecret = clientSecret)
    if (success) {
        println("Configuration updated successfully")
    } else {
        println("Failed to update configuration")
    }
}

@OptIn(ExperimentalResourceApi::class)
fun updateConfigFile(configPath: String, tenantId: String, connectorId: String, clientId: String, clientSecret: String): Boolean {

    val configFile = File(configPath)
    val trimmedTenantId = tenantId.trim()
    val trimmedConnectorId = connectorId.trim()
    val trimmedClientId = clientId.trim()
    val trimmedClientSecret = clientSecret.trim()

    if (configFile.exists()) {
        // Update the existing file
        val content = configFile.readText()
        val updatedContent = replaceText(
            content = content,
            tenantId = trimmedTenantId,
            connectorId = trimmedConnectorId,
            clientId = trimmedClientId,
            clientSecret = trimmedClientSecret
        )
        configFile.writeText(updatedContent)
    } else {
        runBlocking {
            launch(Dispatchers.IO) {
                val bytes = Res.readBytes("files/application-customer.properties")
                val content = String(bytes)
                val newContent = replaceText(
                    content = content,
                    tenantId = trimmedTenantId,
                    connectorId = trimmedConnectorId,
                    clientId = trimmedClientId,
                    clientSecret = trimmedClientSecret
                )
                Files.write(Paths.get(configPath), newContent.toByteArray(), StandardOpenOption.CREATE)

            }.join()
        }
    }
    return true
}

fun replaceText(content: String, tenantId: String, connectorId: String, clientId: String, clientSecret: String): String {
    return content
        .replace(Regex("$CONF_TENANT_ID=.*"), "$CONF_TENANT_ID=$tenantId")
        .replace(Regex("$CONF_CONNECTOR_ID=.*"), "$CONF_CONNECTOR_ID=$connectorId")
        .replace(Regex("$CONF_CLIENT_ID=.*"), "$CONF_CLIENT_ID=$clientId")
        .replace(Regex("$CONF_CLIENT_SECRET=.*"), "$CONF_CLIENT_SECRET=$clientSecret")
}

fun createService(connectorId: String) {
    val osName = System.getProperty("os.name").lowercase()
    println("osName: $osName")
    when {
        osName.contains("win") -> {
            println("Running on Windows")
            executeScCommand(connectorId)
        }

        osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> {
            println("Running on Linux/Unix - please create a daemon service for this application")
            // Add Linux/Unix specific code here
        }

        osName.contains("mac") -> {
            println("Running on Mac OS - please create a daemon service for this application")
            // Add Mac OS specific code here
        }

        else -> {
            println("Unknown operating system")
        }
    }
}

fun executeScCommand(connectorId: String) {
    // Use ProcessBuilder or similar to run shell commands
    val processBuilder = ProcessBuilder("sc.exe", "create", "swisscom-cdr-client-$connectorId", "binPath=\"C:\\path\\to\\your\\application.exe\"", "start=auto")
    processBuilder.inheritIO()
    val process = processBuilder.start()
    process.waitFor()
}
