package io.github.zaxar1231.mycycle.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

/** Sections are provided by the app, so this module depends neither on the updater nor on storage. */
fun NavGraphBuilder.settingsScreen(
    appearanceSection: @Composable () -> Unit,
    securitySection: @Composable () -> Unit,
    updatesSection: @Composable () -> Unit,
    onCustomizeHome: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            appearanceSection = appearanceSection,
            securitySection = securitySection,
            updatesSection = updatesSection,
            onCustomizeHome = onCustomizeHome,
        )
    }
}

@Composable
fun SettingsScreen(
    appearanceSection: @Composable () -> Unit,
    securitySection: @Composable () -> Unit,
    updatesSection: @Composable () -> Unit,
    onCustomizeHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.settings_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onCustomizeHome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_customize_home)) }
                appearanceSection()
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleMedium)
                securitySection()
            }
        }
        updatesSection()
    }
}
