package com.swisscom.health.des.cdr.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.swisscom.health.des.cdr.client.common.DTOs
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.Res
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.label_file_monitoring_error_files
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.label_file_monitoring_old_temp_files
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.label_file_monitoring_warnings
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FileMonitoringWarningBanner(
    modifier: Modifier = Modifier,
    fileMonitoringStatus: DTOs.FileMonitoringStatusResponse
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3CD),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            androidx.compose.material3.Text(
                text = "⚠️ ${stringResource(Res.string.label_file_monitoring_warnings)}",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = Color(0xFF856404)
            )

            if (fileMonitoringStatus.hasErrorFiles) {
                androidx.compose.material3.Text(
                    text = "• ${stringResource(Res.string.label_file_monitoring_error_files, fileMonitoringStatus.errorFileCount)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF856404),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (fileMonitoringStatus.hasOldTempFiles) {
                androidx.compose.material3.Text(
                    text = "• ${stringResource(Res.string.label_file_monitoring_old_temp_files, fileMonitoringStatus.oldTempFileCount)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF856404),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

