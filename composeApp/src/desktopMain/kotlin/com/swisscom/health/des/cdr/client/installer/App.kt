package com.swisscom.health.des.cdr.client.installer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cdr_client.composeapp.generated.resources.Res
import com.swisscom.health.des.cdr.client.getInstallDir
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File
import java.io.OutputStream
import java.io.PrintStream

const val CONFIG_FILE = "application-customer.properties"
const val CONF_TENANT_ID = "tenant-id"
const val CONF_CONNECTOR_ID = "connector-id"
const val CONF_CLIENT_ID = "client-id"
const val CONF_CLIENT_SECRET = "client-secret"

private val logger = KotlinLogging.logger {}

val logMessages = mutableStateOf("")

class CustomOutputStream : OutputStream() {
    override fun write(b: Int) {
        logMessages.value += b.toChar()
    }
}

fun setupCustomLogger() {
    val customOutputStream = CustomOutputStream()
    val printStream = PrintStream(customOutputStream)
    System.setOut(printStream)
    System.setErr(printStream)
}

@Composable
@Preview
fun App() {
    setupCustomLogger()
    MaterialTheme {
        var clientId by remember { mutableStateOf("") }
        var clientSecret by remember { mutableStateOf("") }
        var connectorId by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var showContent by remember { mutableStateOf(false) }
        var tenantId by remember { mutableStateOf("") }
        var createServiceChecked by remember { mutableStateOf(false) }

        val scrollState = rememberScrollState()

        // TODO: no scrollbar visible
        Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = createServiceChecked,
                        onCheckedChange = { createServiceChecked = it }
                    )
                    Text("Create Service (needs to run as Administrator, Windows only)")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val (success, msg) = updateConfigFile(
                        tenantId = tenantId,
                        connectorId = connectorId,
                        clientId = clientId,
                        clientSecret = clientSecret
                    )
                    message = msg
                    showContent = true
                    if (success && createServiceChecked) createService(connectorId)
                }) {
                    Text("Submit")
                }
                AnimatedVisibility(showContent) {
                    Text(message, style = MaterialTheme.typography.body1, modifier = Modifier.padding(16.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = TextFieldValue(logMessages.value),
                    onValueChange = {},
                    label = { Text("Log Messages") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    readOnly = true
                )
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

    val (_, message) = updateConfigFile(
        tenantId = tenantId,
        connectorId = connectorId,
        clientId = clientId,
        clientSecret = clientSecret
    )
    println(message)
}

@OptIn(ExperimentalResourceApi::class)
fun updateConfigFile(
    tenantId: String,
    connectorId: String,
    clientId: String,
    clientSecret: String
): Pair<Boolean, String> {
    val configFile = File(getInstallDir() + File.separator + CONFIG_FILE)
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
            clientSecret = trimmedClientSecret,
        )
        logger.info { "update config file: '$configFile'" }
        configFile.writeText(updatedContent)
    } else {
        runBlocking {
            launch(Dispatchers.IO) {
                val bytes = Res.readBytes("files/application-customer.properties")
                val content = String(bytes)
                val newContent = replaceAndAddText(
                    content = content,
                    tenantId = trimmedTenantId,
                    connectorId = trimmedConnectorId,
                    clientId = trimmedClientId,
                    clientSecret = trimmedClientSecret,
                )
                logger.info { "write new config file to '$configFile'" }
                configFile.writeText(newContent, Charsets.UTF_8)
            }.join()
        }
    }
    return true to "Configuration updated successfully\nPlease restart the application"
}

fun replaceText(
    content: String,
    tenantId: String,
    connectorId: String,
    clientId: String,
    clientSecret: String,
): String {
    val folderPath = getInstallDir().replace("\\", "/")
    return content
        // TODO: should the local-folder only be set in the replaceAndAddText function?
        .replace(Regex("local-folder=.*"), "local-folder=$folderPath/download/inflight")
        .replace(Regex("$CONF_TENANT_ID=.*"), "$CONF_TENANT_ID=$tenantId")
        .replace(Regex("$CONF_CONNECTOR_ID=.*"), "$CONF_CONNECTOR_ID=$connectorId")
        .replace(Regex("$CONF_CLIENT_ID=.*"), "$CONF_CLIENT_ID=$clientId")
        .replace(Regex("$CONF_CLIENT_SECRET=.*"), "$CONF_CLIENT_SECRET=$clientSecret")
}

fun replaceAndAddText(
    content: String,
    tenantId: String,
    connectorId: String,
    clientId: String,
    clientSecret: String,
): String {
    val folderPath = getInstallDir().replace("\\", "/")
    return replaceText(
        content = content,
        tenantId = tenantId,
        connectorId = connectorId,
        clientId = clientId,
        clientSecret = clientSecret
    )
        .plus(
            createConnector(
                connectorId = connectorId,
                folderPath = folderPath,
                isProduction = true,
                entryNumber = 0
            )
        )
        .plus(
            createConnector(
                connectorId = connectorId,
                folderPath = folderPath,
                isProduction = false,
                entryNumber = 1
            )
        )
}

fun createConnector(connectorId: String, folderPath: String, isProduction: Boolean, entryNumber: Int): String {
    val download = "$folderPath/download"
    val downloadProduction = "$download/$connectorId"
    val downloadTest = "$download/test/$connectorId"
    val upload = "$folderPath/upload"
    val uploadProduction = "$upload/$connectorId"
    val uploadTest = "$upload/test/$connectorId"
    StringBuilder().apply {
        append("client.customer[$entryNumber].connector-id=$connectorId\n")
        append("client.customer[$entryNumber].content-type=application/forumdatenaustausch+xml;charset=UTF-8\n")
        append("client.customer[$entryNumber].target-folder=${if (isProduction) downloadProduction else downloadTest}\n")
        append("client.customer[$entryNumber].source-folder=${if (isProduction) uploadProduction else uploadTest}\n")
        append("client.customer[$entryNumber].mode=${if (isProduction) "production" else "test"}\n")
        return toString()
    }
}

fun createService(connectorId: String) {
    val osName = System.getProperty("os.name").lowercase()
    when {
        osName.contains("win") -> {
            logger.info { "Running on Windows" }
            executeScCommand(connectorId)
        }

        osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> {
            logger.info { "Running on Linux/Unix - please create a daemon service for this application" }
        }

        osName.contains("mac") -> {
            logger.info { "Running on Mac OS - please create a daemon service for this application" }
        }

        else -> {
            logger.info { "Unknown operating system" }
        }
    }
}

fun executeScCommand(connectorId: String) {
    val processBuilder = ProcessBuilder(
        "sc.exe",
        "create",
        "swisscom-cdr-client-$connectorId",
        "binPath=\"${getInstallDir()}${File.separator}cdr-client.exe\"",
        "start=auto"
    )
    processBuilder.inheritIO()
    logger.info { "Create service command: ${processBuilder.command()}" }
    val process = processBuilder.start()
    when (val exitValue = process.waitFor()) {
        0 -> {
            logger.info { "Service created successfully" }
        }

        5 -> {
            logger.info {
                "Failed to create service. Access denied. Did you run the Application as Administrator? " +
                        "Exit value: $exitValue"
            }
        }

        else -> {
            logger.info { "Failed to create service. Exit value: $exitValue" }
        }
    }
}
