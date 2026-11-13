package com.travelindex.app.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.domain.AttachmentCategory
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.TripBundle
import com.travelindex.app.domain.forms.ModuleItems
import com.travelindex.app.ui.components.FilePreview
import com.travelindex.app.ui.components.Option
import com.travelindex.app.ui.components.attachmentFile
import com.travelindex.app.ui.components.ChoiceDialog
import com.travelindex.app.ui.components.humanSize
import com.travelindex.app.ui.components.iconForMime
import com.travelindex.app.ui.components.openAttachment
import com.travelindex.app.ui.components.shareAttachment
import com.travelindex.app.ui.modules.ModuleSpecs
import com.travelindex.app.ui.modules.moduleTitle
import com.travelindex.app.ui.theme.Spacing

@Composable
fun categoryLabel(c: AttachmentCategory): String = stringResource(
    when (c) {
        AttachmentCategory.BOARDING_PASS -> R.string.cat_boarding_pass
        AttachmentCategory.TICKET -> R.string.cat_ticket
        AttachmentCategory.RESERVATION -> R.string.cat_reservation
        AttachmentCategory.CONFIRMATION -> R.string.cat_confirmation
        AttachmentCategory.INSURANCE -> R.string.cat_insurance
        AttachmentCategory.VISA -> R.string.cat_visa
        AttachmentCategory.CONTRACT -> R.string.cat_contract
        AttachmentCategory.PHOTO -> R.string.cat_photo
        AttachmentCategory.OTHER -> R.string.cat_other
    }
)

/** Clave "TIPO:id" del elemento al que está asociado un archivo; "" si es un documento suelto. */
fun ownerKeyOf(att: AttachmentEntity): String =
    if (att.ownerType != null && att.ownerId != null) "${att.ownerType}:${att.ownerId}" else ""

fun AttachmentEntity.withOwner(key: String): AttachmentEntity {
    val parts = key.split(":")
    val type = parts.getOrNull(0)?.takeIf { it.isNotEmpty() && ModuleType.fromName(it) != null }
    val id = parts.getOrNull(1)?.toLongOrNull()
    return if (type != null && id != null) copy(ownerType = type, ownerId = id) else copy(ownerType = null, ownerId = null)
}

/** Opciones para asociar un archivo a un elemento del viaje (vuelo, hotel...). La primera es "sin asociar". */
@Composable
fun ownerOptions(b: TripBundle): List<Option> {
    val none = Option("", stringResource(R.string.owner_none))
    val items = ModuleType.entries.filter { it.attachable }.flatMap { t ->
        val spec = ModuleSpecs.of(t)
        val typeLabel = stringResource(spec.titleRes)
        ModuleItems.of(t, b).map { Option("${t.name}:${it.id}", "$typeLabel · ${moduleTitle(spec, it.values)}") }
    }
    return listOf(none) + items
}

/** Tarjeta de un archivo con su menú: abrir, compartir, renombrar, categoría, asociar y eliminar. */
@Composable
fun FileCard(
    att: AttachmentEntity,
    ownerLabel: String?,
    onRename: () -> Unit,
    onCategory: () -> Unit,
    onAssociate: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = { openAttachment(ctx, att) }, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        // Vista previa visible sin tener que abrir el archivo (imagen o primera página de un PDF).
        FilePreview(att.mimeType, Modifier.padding(start = Spacing.s, end = Spacing.s, top = Spacing.s), file = attachmentFile(ctx, att))
        Row(
            Modifier.padding(start = Spacing.m, top = Spacing.s, bottom = Spacing.s, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Icon(iconForMime(att.mimeType), null, tint = MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f)) {
                Text(att.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(categoryLabel(AttachmentCategory.fromName(att.category)), humanSize(att.sizeBytes)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!ownerLabel.isNullOrBlank()) Text(ownerLabel, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_open)) }, onClick = { menu = false; openAttachment(ctx, att) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_share)) }, onClick = { menu = false; shareAttachment(ctx, att) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_set_category)) }, onClick = { menu = false; onCategory() })
                if (onAssociate != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_associate)) }, onClick = { menu = false; onAssociate() })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val valid = text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { if (it.length <= 120) text = it }, singleLine = true,
                label = { Text(stringResource(R.string.file_name)) }, isError = !valid, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = valid) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun CategoryDialog(selected: String, onDismiss: () -> Unit, onPick: (AttachmentCategory) -> Unit) {
    val options = AttachmentCategory.entries.map { Option(it.name, categoryLabel(it)) }
    ChoiceDialog(
        title = stringResource(R.string.action_set_category), options = options, selected = selected,
        searchable = false, onDismiss = onDismiss, onPick = { onPick(AttachmentCategory.fromName(it)) },
    )
}
