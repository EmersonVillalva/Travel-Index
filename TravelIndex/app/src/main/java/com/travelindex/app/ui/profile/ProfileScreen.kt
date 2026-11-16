package com.travelindex.app.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.travelindex.app.BuildConfig
import com.travelindex.app.R
import com.travelindex.app.data.FileActions
import com.travelindex.app.data.FileStorage
import com.travelindex.app.domain.DateFormatOption
import com.travelindex.app.domain.Formats
import com.travelindex.app.domain.ThemeMode
import com.travelindex.app.ui.components.ChoiceDialog
import com.travelindex.app.ui.components.ConfirmDialog
import com.travelindex.app.ui.components.Option
import com.travelindex.app.ui.components.currencyOptionsList
import com.travelindex.app.ui.components.SectionCard
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.Spacing
import java.io.File
import java.time.LocalDate

/** Página donde el usuario consigue su propia clave gratuita del servicio de datos de vuelos. */

private enum class Picking { CURRENCY, DATE }

/** Perfil y ajustes: datos personales, preferencias, notificaciones, privacidad, copia de seguridad y datos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var editingName by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var supportOpen by rememberSaveable { mutableStateOf(false) }

    // Estado real de las notificaciones en el sistema (el usuario puede desactivarlas en Ajustes de Android).
    var systemNotif by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { systemNotif = NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        systemNotif = granted || NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setPhoto(uri) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri.toString()
    }

    val messages = mapOf(
        ProfileMessage.BACKUP_OK to stringResource(R.string.msg_backup_ok),
        ProfileMessage.BACKUP_FAILED to stringResource(R.string.msg_backup_failed),
        ProfileMessage.RESTORE_FAILED to stringResource(R.string.msg_restore_failed),
        ProfileMessage.DATA_DELETED to stringResource(R.string.msg_data_deleted),
        ProfileMessage.DELETE_FAILED to stringResource(R.string.error_save),
        ProfileMessage.PHOTO_FAILED to stringResource(R.string.msg_photo_failed),
    )
    LaunchedEffect(Unit) { vm.messages.collect { m -> snackbar.showSnackbar(messages[m].orEmpty()) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_profile)) }) },
    ) { pad ->
        CenteredContent(Modifier.padding(pad)) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    // ---- Perfil ----
                    SectionCard(null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            Avatar(s.photoPath, s.userName)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.userName.ifBlank { stringResource(R.string.profile_no_name) },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (s.userName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                TextButton(onClick = { editingName = true }) { Text(stringResource(R.string.profile_edit_name)) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Text(stringResource(R.string.profile_change_photo))
                            }
                            if (s.photoPath != null) OutlinedButton(onClick = vm::removePhoto) { Text(stringResource(R.string.profile_remove_photo)) }
                        }
                    }

                    // ---- Preferencias ----
                    SectionCard(stringResource(R.string.sec_preferences)) {
                        SettingRow(stringResource(R.string.pref_currency), s.defaultCurrency) { picking = Picking.CURRENCY.name }
                        SettingRow(stringResource(R.string.pref_date_format), Formats(s.dateFormat).date(LocalDate.now())) { picking = Picking.DATE.name }
                        Text(stringResource(R.string.pref_theme), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            listOf(ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
                                .forEach { (mode, label) ->
                                    FilterChip(selected = s.themeMode == mode, onClick = { vm.setTheme(mode) }, label = { Text(stringResource(label)) })
                                }
                        }
                    }

                    // ---- Notificaciones ----
                    SectionCard(stringResource(R.string.sec_notifications)) {
                        SwitchRow(
                            stringResource(R.string.notif_enable), stringResource(R.string.notif_enable_hint), s.notificationsEnabled,
                        ) { on ->
                            vm.setNotifications(on)
                            if (on && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (s.notificationsEnabled && !systemNotif) {
                            Text(stringResource(R.string.notif_system_off), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = {
                                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                runCatching { ctx.startActivity(i) }.onFailure {
                                    Toast.makeText(ctx, ctx.getString(R.string.error_no_app), Toast.LENGTH_LONG).show()
                                }
                            }) { Text(stringResource(R.string.notif_open_settings)) }
                        }
                    }

                    // ---- Privacidad ----
                    SectionCard(stringResource(R.string.sec_privacy)) {
                        SwitchRow(stringResource(R.string.privacy_block_shots), stringResource(R.string.privacy_block_shots_hint), s.blockScreenshots) {
                            vm.setBlockScreenshots(it)
                        }
                        SettingRow(stringResource(R.string.privacy_policy), "") { onOpenPrivacy() }
                    }

                    // ---- Datos ----
                    SectionCard(stringResource(R.string.sec_data)) {
                        Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { exportLauncher.launch("TravelIndex-backup-${LocalDate.now()}.zip") },
                            enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.backup_export)) }
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                            enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.backup_restore)) }
                        OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.data_delete_all), color = MaterialTheme.colorScheme.error)
                        }
                    }

                    // ---- Información ----
                    SectionCard(stringResource(R.string.sec_about)) {
                        SettingRow(stringResource(R.string.about_title), "") { onOpenAbout() }
                        // Apoyo voluntario: una fila más, al final de Perfil. No hay avisos ni botones en el resto de la app.
                        if (BuildConfig.SHOW_DONATIONS) SupportRow { supportOpen = true }
                    }
                    Text(
                        stringResource(R.string.made_by), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
                    )
                }
                if (busy) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    // ---- Diálogos ----
    if (supportOpen) SupportDialog(onDismiss = { supportOpen = false })
    if (editingName) NameDialog(s.userName, onDismiss = { editingName = false }, onSave = { vm.setName(it); editingName = false })

    when (picking?.let { runCatching { Picking.valueOf(it) }.getOrNull() }) {
        Picking.CURRENCY -> ChoiceDialog(
            title = stringResource(R.string.pref_currency), options = currencyOptionsList(), selected = s.defaultCurrency,
            searchable = true, onDismiss = { picking = null }, onPick = { vm.setCurrency(it); picking = null },
        )
        Picking.DATE -> {
            val today = LocalDate.now()
            val options = DateFormatOption.entries.map {
                Option(it.name, stringResource(dateFormatName(it)) + " · " + Formats(it).date(today))
            }
            ChoiceDialog(
                title = stringResource(R.string.pref_date_format), options = options, selected = s.dateFormat.name, searchable = false,
                onDismiss = { picking = null },
                onPick = { k -> DateFormatOption.entries.firstOrNull { it.name == k }?.let(vm::setDateFormat); picking = null },
            )
        }
        null -> Unit
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title), body = stringResource(R.string.restore_confirm_body),
            confirmLabel = stringResource(R.string.backup_restore),
            onConfirm = { pendingRestore = null; vm.restoreBackup(Uri.parse(uri)) }, onDismiss = { pendingRestore = null },
        )
    }
    if (confirmDelete) ConfirmDialog(
        title = stringResource(R.string.data_delete_title), body = stringResource(R.string.data_delete_body),
        confirmLabel = stringResource(R.string.action_delete),
        onConfirm = { confirmDelete = false; vm.deleteAllTrips() }, onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun dateFormatName(o: DateFormatOption): Int = when (o) {
    DateFormatOption.SYSTEM -> R.string.df_system
    DateFormatOption.DMY -> R.string.df_dmy
    DateFormatOption.MDY -> R.string.df_mdy
    DateFormatOption.YMD -> R.string.df_ymd
    DateFormatOption.LONG -> R.string.df_long
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // toggleable con rol de interruptor: el lector enfoca la fila entera una sola vez («Recordatorios,
    // activado, interruptor») en lugar de leer los textos y el interruptor por separado.
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Avatar(photoName: String?, name: String) {
    val ctx = LocalContext.current
    val file = photoName?.let { File(File(ctx.filesDir, FileStorage.PROFILE_DIR), it) }
    Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (file != null && file.exists()) {
            AsyncImage(model = file, contentDescription = stringResource(R.string.profile_photo), contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        } else if (name.isNotBlank()) {
            Text(name.trim().first().uppercase(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
        } else {
            Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit_name)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { if (it.length <= 40) text = it }, singleLine = true,
                label = { Text(stringResource(R.string.profile_name)) }, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
