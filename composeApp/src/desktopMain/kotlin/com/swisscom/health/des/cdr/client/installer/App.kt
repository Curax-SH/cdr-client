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
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

@Composable
@Preview
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        var clientId by remember { mutableStateOf("") }
        var clientSecret by remember { mutableStateOf("") }
        var configPath by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Configuration Setup", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = configPath,
                onValueChange = { configPath = it },
                label = { Text("Config Path (application-customer.yaml)") },
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
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val success = updateConfigFile(configPath, clientId = clientId, clientSecret = clientSecret)
                message = if (success) "Configuration updated successfully" else "Failed to update configuration"
                showContent = true
            }) {
                Text("Submit")
            }
            AnimatedVisibility(showContent) {
                Text(message, style = MaterialTheme.typography.body1, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

fun updateConfigFile(configPath: String, clientId: String, clientSecret: String): Boolean {
    val configFile = File(configPath)

    if (configFile.exists()) {
        // Update the existing file
        val content = configFile.readText()
        val updatedContent = content.replace(Regex("client-id:.*"), "client-id: $clientId")
            .replace(Regex("client-secret:.*"), "client-secret: $clientSecret")
        configFile.writeText(updatedContent)
    } else {
        val defaultFilePath = "default-application-customer.yaml"
        val defaultFileUrl = object {}.javaClass.classLoader.getResource(defaultFilePath)
        println("defaultFileUrl: $defaultFileUrl")

        val defaultFileContent = defaultFileUrl?.let {
            InputStreamReader(it.openStream()).readText()
        }
        // Use the default file content if it exists
        val newContent =
            defaultFileContent
                ?.replace(Regex("client-id:.*"), "client-id: $clientId")
                ?.replace(Regex("client-secret:.*"), "client-secret: $clientSecret")
                ?: // Create a new file with the provided clientId and clientSecret
                """
                client-id: $clientId
                client-secret: $clientSecret
                """.trimIndent()
        Files.write(Paths.get(configPath), newContent.toByteArray(), StandardOpenOption.CREATE)
    }

    return true


}
