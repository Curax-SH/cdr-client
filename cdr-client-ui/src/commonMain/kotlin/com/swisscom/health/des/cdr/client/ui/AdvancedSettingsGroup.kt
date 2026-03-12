package com.swisscom.health.des.cdr.client.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swisscom.health.des.cdr.client.common.DTOs
import com.swisscom.health.des.cdr.client.common.DomainObjects
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.Res
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.label_advanced_settings
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.label_client_file_busy_strategy
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.label_client_file_busy_strategy_placeholder
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.label_client_idp_settings_client_secret_renewal
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.label_client_idp_settings_client_secret_renewal_subtitle
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.label_client_idp_settings_client_secret_renewal_timestamp
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.label_proxy_url
import com.swisscom.health.des.cdr.client.ui.cdr_client_ui.generated.resources.label_proxy_url_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdvancedSettingsGroup(
    modifier: Modifier,
    viewModel: CdrConfigViewModel,
    uiState: CdrConfigUiState,
    remoteViewValidations: CdrConfigViewRemoteValidations,
    canEdit: Boolean,
) {
    CollapsibleGroup(
        modifier = modifier,
        title = stringResource(Res.string.label_advanced_settings),
        initiallyExpanded = false,
    ) { _ ->
        // Proxy URL
        var proxyValidationResult: DTOs.ValidationResult by remember { mutableStateOf(DTOs.ValidationResult.Success) }
        LaunchedEffect(uiState.clientServiceConfig.proxyUrl) {
            proxyValidationResult = remoteViewValidations.validateProxyUrl(uiState.clientServiceConfig.proxyUrl)
        }
        ValidatedTextField(
            name = DomainObjects.ConfigurationItem.PROXY_URL,
            modifier = modifier.padding(8.dp).fillMaxWidth(),
            validatable = { proxyValidationResult },
            label = { Text(text = stringResource(Res.string.label_proxy_url)) },
            value = uiState.clientServiceConfig.proxyUrl,
            placeHolder = { Text(text = stringResource(Res.string.label_proxy_url_placeholder)) },
            onValueChange = {
                if (canEdit) viewModel.setProxyUrl(it)
            },
            enabled = canEdit,
        )

        Divider(modifier = modifier)

        // Client secret renewal
        OnOffSwitch(
            name = DomainObjects.ConfigurationItem.IDP_CLIENT_SECRET_RENWAL,
            modifier = modifier.padding(bottom = 16.dp),
            title = stringResource(Res.string.label_client_idp_settings_client_secret_renewal),
            subtitle = stringResource(Res.string.label_client_idp_settings_client_secret_renewal_subtitle),
            checked = uiState.clientServiceConfig.idpCredentials.renewCredential,
            onValueChange = { if (canEdit) viewModel.setIdpRenewClientSecret(it) },
            enabled = canEdit,
        )

        // Last credential renewal time
        DisabledTextField(
            name = DomainObjects.ConfigurationItem.IDP_CLIENT_SECRET_RENWAL_TIME,
            modifier = modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.label_client_idp_settings_client_secret_renewal_timestamp)) },
            value = uiState.clientServiceConfig.idpCredentials.lastCredentialRenewalTime.toString(),
        )

        Divider(modifier = modifier)

        // File busy test strategy
        DropDownList(
            name = DomainObjects.ConfigurationItem.FILE_BUSY_TEST_STRATEGY,
            modifier = modifier.padding(8.dp).fillMaxWidth(),
            initiallyExpanded = false,
            options = { DTOs.CdrClientConfig.FileBusyTestStrategy.entries.filter { it != DTOs.CdrClientConfig.FileBusyTestStrategy.ALWAYS_BUSY } },
            label = { Text(text = stringResource(Res.string.label_client_file_busy_strategy)) },
            placeHolder = { Text(text = stringResource(Res.string.label_client_file_busy_strategy_placeholder)) },
            value = uiState.clientServiceConfig.fileBusyTestStrategy.toString(),
            onValueChange = { if (canEdit) viewModel.setFileBusyTestStrategy(it) },
            validatable = { DTOs.ValidationResult.Success },
            enabled = canEdit,
        )

    }
}

