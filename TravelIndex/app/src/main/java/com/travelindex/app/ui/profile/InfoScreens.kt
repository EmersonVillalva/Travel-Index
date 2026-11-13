package com.travelindex.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.travelindex.app.BuildConfig
import com.travelindex.app.R
import com.travelindex.app.ui.components.InfoRow
import com.travelindex.app.ui.components.SectionCard
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.Spacing

/** Política de privacidad dentro de la app (describe exactamente lo que la app hace con los datos). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_policy)) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { pad ->
        CenteredContent(Modifier.padding(pad)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyLarge)
                listOf(
                    R.string.privacy_h_data to R.string.privacy_b_data,
                    R.string.privacy_h_network to R.string.privacy_b_network,
                    R.string.privacy_h_files to R.string.privacy_b_files,
                    R.string.privacy_h_permissions to R.string.privacy_b_permissions,
                    R.string.privacy_h_backup to R.string.privacy_b_backup,
                    R.string.privacy_h_sensitive to R.string.privacy_b_sensitive,
                    R.string.privacy_h_delete to R.string.privacy_b_delete,
                    R.string.privacy_h_contact to R.string.privacy_b_contact,
                ).forEach { (h, b) ->
                    SectionCard(stringResource(h)) { Text(stringResource(b), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { pad ->
        CenteredContent(Modifier.padding(pad)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary)
                Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyLarge)
                SectionCard(null) {
                    InfoRow(stringResource(R.string.about_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    InfoRow(stringResource(R.string.about_package), BuildConfig.APPLICATION_ID)
                    InfoRow(stringResource(R.string.about_author), stringResource(R.string.author_name))
                }
                SectionCard(stringResource(R.string.about_highlights_title)) {
                    Text(stringResource(R.string.about_highlights), style = MaterialTheme.typography.bodyMedium)
                }
                SectionCard(stringResource(R.string.about_limits_title)) {
                    Text(stringResource(R.string.about_limits), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.made_by), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
                )
            }
        }
    }
}
