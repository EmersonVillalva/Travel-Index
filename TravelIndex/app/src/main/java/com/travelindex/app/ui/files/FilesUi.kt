package com.travelindex.app.ui.files

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Train
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.domain.AttachmentCategory
import com.travelindex.app.domain.FileGroup
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.TripBundle
import com.travelindex.app.domain.forms.ModuleItems
import com.travelindex.app.ui.components.FilePreview
import com.travelindex.app.ui.components.Option
import com.travelindex.app.ui.components.attachmentFile
import com.travelindex.app.ui.components.categoryLabel
import com.travelindex.app.ui.components.fileKindLabel
import com.travelindex.app.ui.components.ChoiceDialog
import com.travelindex.app.ui.components.humanSize
import com.travelindex.app.ui.components.iconForMime
import com.travelindex.app.ui.components.openAttachment
import com.travelindex.app.ui.components.shareAttachment
import com.travelindex.app.ui.modules.ModuleSpecs
import com.travelindex.app.ui.modules.moduleTitle
import com.travelindex.app.ui.theme.Spacing

@StringRes
fun fileGroupTitleRes(g: FileGroup): Int = when (g) {
    FileGroup.FLIGHTS -> R.string.mod_flights
    FileGroup.TRAINS -> R.string.mod_trains
    FileGroup.BUSES -> R.string.mod_buses
    FileGroup.SHIPS -> R.string.mod_ships
    FileGroup.CARS -> R.string.mod_cars
    FileGroup.TRANSPORT_OTHER -> R.string.files_group_transport_other
    FileGroup.LODGING -> R.string.mod_lodgings
    FileGroup.ACTIVITIES -> R.string.mod_activities
    FileGroup.RESTAURANTS -> R.string.mod_restaurants
    FileGroup.DOCUMENTS -> R.string.mod_docs
    FileGroup.OTHER -> R.string.files_group_other
}

@Composable
fun fileGroupTitle(g: FileGroup): String = stringResource(fileGroupTitleRes(g))

fun fileGroupIcon(g: FileGroup): ImageVector = when (g) {
    FileGroup.FLIGHTS -> Icons.Outlined.Flight
    FileGroup.TRAINS -> Icons.Outlined.Train
    FileGroup.BUSES -> Icons.Outlined.DirectionsBus
    FileGroup.SHIPS -> Icons.Outlined.DirectionsBoat
    FileGroup.CARS, FileGroup.TRANSPORT_OTHER -> Icons.Outlined.DirectionsCar
    FileGroup.LODGING -> Icons.Outlined.Hotel
    FileGroup.ACTIVITIES -> Icons.Outlined.Explore
    FileGroup.RESTAURANTS -> Icons.Outlined.Restaurant
    FileGroup.DOCUMENTS -> Icons.Outlined.Badge
    FileGroup.OTHER -> Icons.Outlined.Folder
}

/** Cabecera de un apartado de la lista de archivos: icono, nombre y cuántos archivos tiene. */
@Composable
fun FileGroupHeader(group: FileGroup, count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    // El lector de pantalla anuncia la cabecera entera con su estado («Vuelos, 3, desplegado») y qué hace el toque.
    val stateText = stringResource(if (expanded) R.string.a11y_expanded else R.string.a11y_collapsed)
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(
                    onClick = onToggle,
                    onClickLabel = stringResource(if (expanded) R.string.a11y_collapse_group else R.string.a11y_expand_group),
                )
                .semantics { stateDescription = stateText }
                .padding(horizontal = Spacing.m, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(fileGroupIcon(group), contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text(fileGroupTitle(group), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // La flecha es decorativa (el estado lo anuncia la fila) y gira suave al abrir y cerrar.
            val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevron),
            )
        }
    }
}

/** Nombre de cada reserva a la que hay archivos asociados ("TIPO:id" → "Iberia IB3456"), para rotular el archivo. */
@Composable
fun ownerTitles(b: TripBundle): Map<String, String> {
    val out = HashMap<String, String>()
    ModuleType.entries.filter { it.attachable }.forEach { t ->
        val spec = ModuleSpecs.of(t)
        ModuleItems.of(t, b).forEach { out["${t.name}:${it.id}"] = moduleTitle(spec, it.values) }
    }
    return out
}

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
    onOpen: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val open: () -> Unit = onOpen ?: { openAttachment(ctx, att) }
    var menu by remember { mutableStateOf(false) }
    // Card(onClick=...) de Material 3 no admite etiqueta de accesibilidad: el clic va en el modificador, recortado a la forma.
    Card(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .clickable(onClick = open, onClickLabel = stringResource(R.string.action_open)),
        shape = MaterialTheme.shapes.medium,
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
                // El título es la reserva a la que pertenece ("Free tour Edimburgo"). El nombre del archivo
                // (xxx.pdf) no se enseña nunca; sin asociar, se rotula por lo que es ("Documento PDF", "Imagen").
                Text(ownerLabel?.takeIf { it.isNotBlank() } ?: fileKindLabel(att.mimeType),
                    style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(categoryLabel(AttachmentCategory.fromName(att.category)), humanSize(att.sizeBytes)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_open)) }, onClick = { menu = false; open() })
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
