package com.travelindex.app.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.domain.FileGroups
import com.travelindex.app.ui.components.ChoiceDialog
import com.travelindex.app.ui.components.ConfirmDialog
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.rememberAddFileFlow
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.Spacing

/**
 * Billetes y comprobantes de un viaje, separados por lo que son (vuelos, trenes, alojamientos, visados...). Se pueden subir
 * del dispositivo o escanear con la cámara, abrir, compartir, renombrar, categorizar, asociar a una reserva y eliminar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(onBack: () -> Unit, vm: TripFilesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<AttachmentEntity?>(null) }
    var categorizing by remember { mutableStateOf<AttachmentEntity?>(null) }
    var associating by remember { mutableStateOf<AttachmentEntity?>(null) }
    var deleting by remember { mutableStateOf<AttachmentEntity?>(null) }

    val addFile = rememberAddFileFlow { uris -> vm.add(uris) }
    val addedMsg = stringResource(R.string.file_added)
    val failedMsg = stringResource(R.string.error_attach)
    LaunchedEffect(Unit) {
        vm.results.collect { r -> snackbar.showSnackbar(if (r.failed > 0) failedMsg else addedMsg) }
    }

    val bundle = state.bundle
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mod_files)) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = addFile,
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.action_add_file)) },
            )
        },
    ) { pad ->
        if (bundle == null) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        val owners = ownerOptions(bundle)
        val titles = ownerTitles(bundle)
        val groups = remember(bundle.attachments, bundle.transports) {
            FileGroups.group(bundle.attachments, bundle.transports.associate { it.id to it.type })
        }

        CenteredContent(Modifier.padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                if (bundle.attachments.isEmpty()) {
                    EmptyState(
                        stringResource(R.string.files_empty_title), stringResource(R.string.files_empty_body),
                        icon = Icons.Outlined.Folder,
                        actionLabel = stringResource(R.string.action_add_file), onAction = addFile,
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        groups.forEach { entry ->
                            item(key = "group-${entry.group.name}") { FileGroupHeader(entry.group, entry.files.size) }
                            items(entry.files, key = { it.id }) { att ->
                                FileCard(
                                    att = att, ownerLabel = titles[ownerKeyOf(att)],
                                    onRename = { renaming = att }, onCategory = { categorizing = att },
                                    onAssociate = { associating = att }, onDelete = { deleting = att },
                                )
                            }
                        }
                    }
                }
            }
        }

        renaming?.let { att ->
            RenameDialog(att.displayName, onDismiss = { renaming = null }, onConfirm = { vm.rename(att, it); renaming = null })
        }
        categorizing?.let { att ->
            CategoryDialog(att.category, onDismiss = { categorizing = null }, onPick = { vm.setCategory(att, it); categorizing = null })
        }
        associating?.let { att ->
            ChoiceDialog(
                title = stringResource(R.string.action_associate), options = owners, selected = ownerKeyOf(att),
                searchable = owners.size > 8, onDismiss = { associating = null },
                onPick = { vm.associate(att, it); associating = null },
            )
        }
        deleting?.let { att ->
            ConfirmDialog(
                title = stringResource(R.string.delete_file_title), body = stringResource(R.string.delete_file_body, att.displayName),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = { vm.delete(att); deleting = null }, onDismiss = { deleting = null },
            )
        }
    }
}
